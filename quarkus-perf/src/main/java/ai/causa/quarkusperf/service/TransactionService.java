package ai.causa.quarkusperf.service;

import ai.causa.quarkusperf.chaos.ChaosConfig;
import ai.causa.quarkusperf.model.Account;
import ai.causa.quarkusperf.model.Transaction;
import ai.causa.quarkusperf.repository.AccountRepository;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business logic for account and transaction operations.
 *
 * <p><strong>Primary chaos scenario — HTTP large-response + keep-alive:</strong><br>
 * See {@link ai.causa.quarkusperf.chaos.ResponsePaddingService}. Activated via
 * {@code CHAOS_HTTP_LARGE_RESPONSE_ENABLED=true}.
 *
 * <p><strong>Secondary chaos knob — background heap leak:</strong><br>
 * When {@code CHAOS_MEMORY_CACHE_ENABLED=true} each transaction response is
 * additionally cached in a static map that is never evicted, growing heap indefinitely
 * regardless of HTTP traffic.
 */
@ApplicationScoped
public class TransactionService {

    private static final Logger LOG = Logger.getLogger(TransactionService.class);

    @Inject
    AccountRepository accountRepository;

    @Inject
    ChaosConfig chaosConfig;

    @Inject
    MeterRegistry meterRegistry;

    /** Static map that is never cleared when chaos cache is on. */
    private static final Map<String, List<byte[]>> LEAK_CACHE = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Account operations
    // -------------------------------------------------------------------------

    @Timed(value = "quarkus_perf_account_lookup", description = "Time to look up an account by ID")
    public Optional<Account> getAccount(String accountId) {
        LOG.debugf("Looking up account: %s", accountId);
        return accountRepository.findById(accountId);
    }

    @Timed(value = "quarkus_perf_account_list", description = "Time to list all accounts")
    public List<Account> listAccounts() {
        LOG.debug("Listing all accounts");
        return accountRepository.findAll();
    }

    @Counted(value = "quarkus_perf_accounts_created_total", description = "Total accounts created")
    @Timed(value = "quarkus_perf_account_create", description = "Time to create a new account")
    public Account createAccount(String ownerName, Account.AccountType accountType, BigDecimal initialBalance) {
        Account a = new Account();
        a.setAccountId(UUID.randomUUID().toString());
        a.setOwnerId(UUID.randomUUID().toString());
        a.setOwnerName(ownerName);
        a.setAccountType(accountType);
        a.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);
        a.setCurrency("USD");
        a.setActive(true);
        a.setCreatedAt(Instant.now());
        a.setTransactionCount(0);
        return accountRepository.save(a);
    }

    // -------------------------------------------------------------------------
    // Transaction operations
    // -------------------------------------------------------------------------

    @Counted(value = "quarkus_perf_transactions_submitted_total", description = "Total transactions submitted")
    @Timed(value = "quarkus_perf_transaction_process", description = "End-to-end transaction processing time")
    public Transaction processTransaction(String accountId, Transaction.Type type,
                                          BigDecimal amount, String currency,
                                          String description) {

        String correlationId = UUID.randomUUID().toString();
        LOG.infof("[%s] Processing %s transaction for account %s amount=%s %s",
                correlationId, type, accountId, amount, currency);

        Optional<Account> maybeAccount = accountRepository.findById(accountId);
        if (maybeAccount.isEmpty()) {
            LOG.warnf("[%s] Account not found: %s", correlationId, accountId);
            throw new IllegalArgumentException("Account not found: " + accountId);
        }

        Account account = maybeAccount.get();
        BigDecimal newBalance = computeNewBalance(account.getBalance(), type, amount);

        Transaction tx = Transaction.newTransaction(accountId, type, amount, currency, description);
        tx.setCorrelationId(correlationId);

        accountRepository.saveTransaction(tx);
        accountRepository.updateBalance(accountId, newBalance);

        tx.setStatus(Transaction.Status.COMPLETED);
        tx.setCompletedAt(Instant.now());

        LOG.infof("[%s] Transaction %s COMPLETED. New balance: %s",
                correlationId, tx.getTransactionId(), newBalance);

        // Chaos: optionally leak memory per transaction
        if (chaosConfig.memory().cacheEnabled()) {
            simulateMemoryLeak(correlationId);
        }

        return tx;
    }

    @Timed(value = "quarkus_perf_transaction_history", description = "Time to retrieve transaction history")
    public List<Transaction> getTransactionHistory(String accountId, int limit) {
        LOG.debugf("Retrieving transaction history for %s", accountId);
        return accountRepository.findTransactionsByAccount(accountId, limit);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BigDecimal computeNewBalance(BigDecimal current, Transaction.Type type, BigDecimal amount) {
        return switch (type) {
            case CREDIT   -> current.add(amount);
            case DEBIT    -> current.subtract(amount);
            case TRANSFER -> current.subtract(amount);
        };
    }

    /**
     * Allocates byte arrays that are stored in {@link #LEAK_CACHE} and never released.
     * Each call can grow the heap by {@code objectsPerTx * 64KB}.
     */
    private void simulateMemoryLeak(String correlationId) {
        int objectSize = 64 * 1024; // 64 KB per object
        int count = chaosConfig.memory().objectsPerTx();
        List<byte[]> blobs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] blob = new byte[objectSize];
            Arrays.fill(blob, (byte) 'X');
            blobs.add(blob);
        }
        LEAK_CACHE.put(correlationId, blobs);
        LOG.warnf("[CHAOS] Memory leak: cached %d object(s) (%d KB) for correlationId=%s. " +
                "Leak cache size: %d entries. Approx heap allocated: %d KB. " +
                "Set CHAOS_MEMORY_CACHE_ENABLED=false to stop leaking.",
                count, (count * objectSize) / 1024, correlationId,
                LEAK_CACHE.size(), (LEAK_CACHE.size() * count * objectSize) / 1024);

        // Record to OTEL via Micrometer gauge
        meterRegistry.gauge("quarkus_perf_leak_cache_entries", LEAK_CACHE, Map::size);
    }

    /** Returns current leak cache size for metrics/health exposure. */
    public int getLeakCacheSize() {
        return LEAK_CACHE.size();
    }

    /** Returns approximate heap held by the leak cache in bytes. */
    public long getLeakCacheBytes() {
        return (long) LEAK_CACHE.size() * chaosConfig.memory().objectsPerTx() * 64 * 1024;
    }
}
