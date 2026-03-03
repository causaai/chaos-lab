package ai.causa.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ai.causa.model.MetricSample;
import ai.causa.model.TargetSamples;
import ai.causa.registry.Registry;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Random;

@ApplicationScoped
public class ScrapeScheduler {
    private static final Logger LOG =
            Logger.getLogger(ScrapeScheduler.class);

    @Inject
    Registry registry;

    Random random = new Random();

    @Scheduled(every = "5s")
    void scrape() {
        int size = registry.targets.size();

        LOG.infof("Scrape started. targets=%d", size);

        for (TargetSamples ts : registry.targets.values()) {

            for (int i = 0; i < 5; i++) {

                MetricSample m = new MetricSample();
                m.metric = "container_cpu_usage_seconds_total";
                m.value = random.nextDouble() * 100;
                m.timestamp = System.currentTimeMillis();

                m.labels = Map.of(
                        "namespace", ts.target.namespace,
                        "pod", ts.target.pod,
                        "container", ts.target.container
                );

                ts.samples.add(m);
            }
        }
        LOG.infof("Scrape completed. processed=%d targets", size);
    }
}