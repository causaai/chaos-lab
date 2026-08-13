package ai.causa.quarkusperf.health;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * Readiness probe: reports DOWN if a DB ping fails (pool exhausted or
 * datasource unavailable).
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(ReadinessCheck.class);

    @Inject
    AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection c = dataSource.getConnection()) {
            ResultSet rs = c.createStatement().executeQuery("SELECT 1");
            rs.next();
            int result = rs.getInt(1);
            return HealthCheckResponse.named("quarkus-perf/readiness")
                    .up()
                    .withData("db.ping", result)
                    .withData("db.url",  dataSource.getConfiguration().connectionPoolConfiguration()
                                                    .connectionFactoryConfiguration().jdbcUrl())
                    .build();
        } catch (Exception e) {
            LOG.warnf("[CHAOS] DB readiness ping failed: %s", e.getMessage());
            return HealthCheckResponse.named("quarkus-perf/readiness")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
