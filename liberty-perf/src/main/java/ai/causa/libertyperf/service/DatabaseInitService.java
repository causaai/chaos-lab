package ai.causa.libertyperf.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ai.causa.libertyperf.repository.AccountRepository;
import ai.causa.libertyperf.repository.BookingRepository;

import java.util.logging.Logger;

/**
 * Initializes the database schema and seed data on application startup.
 */
@ApplicationScoped
public class DatabaseInitService {

    private static final Logger LOG = Logger.getLogger(DatabaseInitService.class.getName());

    @Inject
    AccountRepository accountRepository;

    @Inject
    BookingRepository bookingRepository;

    @PostConstruct
    public void init() {
        LOG.info("Initialising database schema...");
        accountRepository.ensureSchema();
        bookingRepository.ensureSchema();
        LOG.info("Database schema initialisation complete.");
    }
}
