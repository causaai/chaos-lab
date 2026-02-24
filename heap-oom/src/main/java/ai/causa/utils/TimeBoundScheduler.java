package ai.causa.utils;

import ai.causa.svc.AllocatorService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TimeBoundScheduler {

    @ConfigProperty(name = "crash.time.tick-millis", defaultValue = "500")
    long tickMillis;

    @Inject
    AllocatorService svc;

    // Use a fixed-delay scheduler; the string must be constant, so pick among a few buckets.
    @Scheduled(every = "0.5s")
    void tickFast() {
        if (tickMillis == 500) svc.maybeAutoAllocateOnDeadline();
    }

    @Scheduled(every = "1s")
    void tick1s() {
        if (tickMillis == 1000) svc.maybeAutoAllocateOnDeadline();
    }

    @Scheduled(every = "0.1s")
    void tick100ms() {
        if (tickMillis == 100) svc.maybeAutoAllocateOnDeadline();
    }
}
