package ai.causa.quarkusperf.service;

import ai.causa.quarkusperf.repository.AccountRepository;
import ai.causa.quarkusperf.repository.BookingRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Initialises the H2 database schema and seeds default data at startup.
 * Listens for the Quarkus {@link StartupEvent} to run after the container is fully
 * initialised and datasource is available.
 */
@ApplicationScoped
public class DatabaseInitService {

    private static final Logger LOG = Logger.getLogger(DatabaseInitService.class);

    @Inject
    AccountRepository accountRepository;

    @Inject
    BookingRepository bookingRepository;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Initialising database schema...");
        accountRepository.ensureSchema();
        bookingRepository.ensureSchema();
        LOG.info("Database schema initialisation complete.");
    }
}
