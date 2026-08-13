package ai.causa.quarkusperf.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

/**
 * Startup probe: always UP once the Quarkus application finishes booting.
 * Kubernetes uses this to gate liveness/readiness checks until startup completes.
 */
@Startup
@ApplicationScoped
public class StartupCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("quarkus-perf/startup")
                .up()
                .withData("status", "Application started successfully")
                .build();
    }
}
