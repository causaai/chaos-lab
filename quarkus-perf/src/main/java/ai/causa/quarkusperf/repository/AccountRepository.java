package ai.causa.quarkusperf.repository;

import ai.causa.quarkusperf.chaos.ChaosConfig;
import ai.causa.quarkusperf.model.Account;
import ai.causa.quarkusperf.model.Transaction;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Repository for Accounts and Transactions backed by JDBC (Agroal pool).
 *
 * <p><strong>Chaos knobs:</strong>
 * <ul>
 *   <li>{@code CHAOS_DB_LEAK_ENABLED=true} — acquired connections are never returned to
 *       the Agroal pool, simulating a connection leak. Lower {@code quarkus.datasource.jdbc.max-size}
 *       to observe pool exhaustion quickly.</li>
 *   <li>{@code CHAOS_DB_SLOW_QUERY_MS} — adds an artificial sleep on every query to mimic
 *       a slow backend, forcing threads to hold connections longer.</li>
 * </ul>
 */
@ApplicationScoped
public class AccountRepository {

    private static final Logger LOG = Logger.getLogger(AccountRepository.class);

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ChaosConfig chaosConfig;

    // -------------------------------------------------------------------------
    // Schema bootstrap
    // -------------------------------------------------------------------------

    public void ensureSchema() {
        try (Connection c = dataSource.getConnection();
             Statement s  = c.createStatement()) {

            s.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        account_id        VARCHAR(36)    NOT NULL PRIMARY KEY,
                        owner_id          VARCHAR(36)    NOT NULL,
                        owner_name        VARCHAR(255)   NOT NULL,
                        account_type      VARCHAR(32)    NOT NULL,
                        balance           DECIMAL(15, 2) NOT NULL DEFAULT 0,
                        currency          VARCHAR(3)     NOT NULL DEFAULT 'USD',
                        active            BOOLEAN        NOT NULL DEFAULT TRUE,
                        created_at        TIMESTAMP      NOT NULL,
                        transaction_count BIGINT         NOT NULL DEFAULT 0
                    )""");

            s.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        transaction_id  VARCHAR(36)    NOT NULL PRIMARY KEY,
                        account_id      VARCHAR(36)    NOT NULL,
                        type            VARCHAR(16)    NOT NULL,
                        amount          DECIMAL(15, 2) NOT NULL,
                        currency        VARCHAR(3)     NOT NULL,
                        status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
                        created_at      TIMESTAMP      NOT NULL,
                        completed_at    TIMESTAMP,
                        description     VARCHAR(512),
                        correlation_id  VARCHAR(36)
                    )""");

            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM accounts");
            rs.next();
            if (rs.getInt(1) == 0) {
                insertSeedAccounts(c);
            }

            LOG.info("Account/Transaction schema initialised successfully");
        } catch (SQLException e) {
            LOG.errorf("Schema initialisation failed: %s", e.getMessage());
            throw new RuntimeException("Schema init failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Account operations
    // -------------------------------------------------------------------------

    public Optional<Account> findById(String accountId) {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM accounts WHERE account_id = ?");
            ps.setString(1, accountId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapAccount(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            LOG.warnf("findById failed for %s: %s", accountId, e.getMessage());
            return Optional.empty();
        } finally {
            maybeClose(conn);
        }
    }

    public List<Account> findAll() {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        List<Account> result = new ArrayList<>();
        try {
            Statement s  = conn.createStatement();
            ResultSet rs = s.executeQuery("SELECT * FROM accounts WHERE active = TRUE LIMIT 100");
            while (rs.next()) {
                result.add(mapAccount(rs));
            }
        } catch (SQLException e) {
            LOG.warnf("findAll failed: %s", e.getMessage());
        } finally {
            maybeClose(conn);
        }
        return result;
    }

    public Account save(Account account) {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        try {
            PreparedStatement ps = conn.prepareStatement("""
                    MERGE INTO accounts
                    (account_id, owner_id, owner_name, account_type, balance, currency,
                     active, created_at, transaction_count)
                    KEY (account_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, account.getAccountId());
            ps.setString(2, account.getOwnerId());
            ps.setString(3, account.getOwnerName());
            ps.setString(4, account.getAccountType().name());
            ps.setBigDecimal(5, account.getBalance());
            ps.setString(6, account.getCurrency());
            ps.setBoolean(7, account.isActive());
            ps.setTimestamp(8, Timestamp.from(account.getCreatedAt()));
            ps.setLong(9, account.getTransactionCount());
            ps.executeUpdate();
            return account;
        } catch (SQLException e) {
            LOG.warnf("save account failed: %s", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            maybeClose(conn);
        }
    }

    public void updateBalance(String accountId, BigDecimal newBalance) {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE accounts SET balance = ?, transaction_count = transaction_count + 1 WHERE account_id = ?");
            ps.setBigDecimal(1, newBalance);
            ps.setString(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warnf("updateBalance failed: %s", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            maybeClose(conn);
        }
    }

    // -------------------------------------------------------------------------
    // Transaction operations
    // -------------------------------------------------------------------------

    public Transaction saveTransaction(Transaction tx) {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        try {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions
                    (transaction_id, account_id, type, amount, currency,
                     status, created_at, completed_at, description, correlation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, tx.getTransactionId());
            ps.setString(2, tx.getAccountId());
            ps.setString(3, tx.getType().name());
            ps.setBigDecimal(4, tx.getAmount());
            ps.setString(5, tx.getCurrency());
            ps.setString(6, tx.getStatus().name());
            ps.setTimestamp(7, Timestamp.from(tx.getCreatedAt()));
            ps.setTimestamp(8, tx.getCompletedAt() != null ? Timestamp.from(tx.getCompletedAt()) : null);
            ps.setString(9, tx.getDescription());
            ps.setString(10, tx.getCorrelationId());
            ps.executeUpdate();
            return tx;
        } catch (SQLException e) {
            LOG.warnf("saveTransaction failed: %s", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            maybeClose(conn);
        }
    }

    public List<Transaction> findTransactionsByAccount(String accountId, int limit) {
        simulateSlowQuery();
        Connection conn = acquireConnection();
        List<Transaction> result = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM transactions WHERE account_id = ? ORDER BY created_at DESC LIMIT ?");
            ps.setString(1, accountId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(mapTransaction(rs));
            }
        } catch (SQLException e) {
            LOG.warnf("findTransactions failed for %s: %s", accountId, e.getMessage());
        } finally {
            maybeClose(conn);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Acquires a connection from the Agroal pool.
     * When {@code chaos.db.leak.enabled=true} the connection is never returned —
     * this is the core DB chaos mechanism.
     */
    private Connection acquireConnection() {
        try {
            Connection conn = dataSource.getConnection();
            if (chaosConfig.db().leakEnabled()) {
                LOG.warn("[CHAOS] Connection acquired and intentionally NOT returned to Agroal pool — " +
                        "pool will exhaust under sustained load. " +
                        "Set CHAOS_DB_LEAK_ENABLED=false to restore normal behaviour.");
            }
            return conn;
        } catch (SQLException e) {
            LOG.errorf("[CHAOS] Failed to acquire DB connection — pool may be exhausted: %s", e.getMessage());
            throw new RuntimeException("Could not acquire DB connection", e);
        }
    }

    /** Closes the connection only when leak mode is disabled. */
    private void maybeClose(Connection conn) {
        if (!chaosConfig.db().leakEnabled() && conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOG.warnf("Failed to close connection: %s", e.getMessage());
            }
        }
    }

    private void simulateSlowQuery() {
        long ms = chaosConfig.db().slowQueryMs();
        if (ms > 0) {
            try {
                LOG.debugf("[CHAOS] Simulating slow query: sleeping %dms", ms);
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private Account mapAccount(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setAccountId(rs.getString("account_id"));
        a.setOwnerId(rs.getString("owner_id"));
        a.setOwnerName(rs.getString("owner_name"));
        a.setAccountType(Account.AccountType.valueOf(rs.getString("account_type")));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setCurrency(rs.getString("currency"));
        a.setActive(rs.getBoolean("active"));
        a.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        a.setTransactionCount(rs.getLong("transaction_count"));
        return a;
    }

    private Transaction mapTransaction(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setTransactionId(rs.getString("transaction_id"));
        t.setAccountId(rs.getString("account_id"));
        t.setType(Transaction.Type.valueOf(rs.getString("type")));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setCurrency(rs.getString("currency"));
        t.setStatus(Transaction.Status.valueOf(rs.getString("status")));
        t.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp comp = rs.getTimestamp("completed_at");
        if (comp != null) t.setCompletedAt(comp.toInstant());
        t.setDescription(rs.getString("description"));
        t.setCorrelationId(rs.getString("correlation_id"));
        return t;
    }

    // -------------------------------------------------------------------------
    // Seed data
    // -------------------------------------------------------------------------

    private void insertSeedAccounts(Connection c) throws SQLException {
        String[][] seeds = {
            {"ACC-001", "USR-001", "Alice Johnson",    "CHECKING"},
            {"ACC-002", "USR-002", "Bob Smith",        "SAVINGS"},
            {"ACC-003", "USR-003", "Carol Williams",   "CREDIT"},
            {"ACC-004", "USR-004", "David Brown",      "CHECKING"},
            {"ACC-005", "USR-005", "Eve Davis",        "FREQUENT_FLYER"},
            {"ACC-006", "USR-006", "Frank Miller",     "SAVINGS"},
            {"ACC-007", "USR-007", "Grace Wilson",     "CHECKING"},
            {"ACC-008", "USR-008", "Henry Moore",      "CREDIT"},
            {"ACC-009", "USR-009", "Iris Taylor",      "FREQUENT_FLYER"},
            {"ACC-010", "USR-010", "Jack Anderson",    "CHECKING"},
        };
        for (String[] row : seeds) {
            PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO accounts
                    (account_id, owner_id, owner_name, account_type, balance, currency, active, created_at, transaction_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            ps.setString(3, row[2]);
            ps.setString(4, row[3]);
            ps.setBigDecimal(5, new BigDecimal("10000.00"));
            ps.setString(6, "USD");
            ps.setBoolean(7, true);
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
            ps.setLong(9, 0L);
            ps.executeUpdate();
        }
        LOG.infof("Seeded %d accounts into database", seeds.length);
    }
}
