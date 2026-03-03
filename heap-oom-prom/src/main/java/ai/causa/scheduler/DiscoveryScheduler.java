package ai.causa.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ai.causa.model.ContainerTarget;
import ai.causa.model.TargetSamples;
import ai.causa.registry.Registry;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

@ApplicationScoped
public class DiscoveryScheduler {

    private static final Logger LOG =
            Logger.getLogger(DiscoveryScheduler.class);

    private int insertCounter = 0;

    @Inject
    Registry registry;

    Random random = new Random();
    long start = System.currentTimeMillis();

    @Scheduled(every = "1s")
    void discover() {

        int rate = elapsedSeconds() < 60
                ? 50
                : 5000;  // explosion phase

        for (int i = 0; i < rate; i++) {

            ContainerTarget t = new ContainerTarget();
            t.namespace = "ns-" + random.nextInt(2000);
            t.deployment = "deploy-" + UUID.randomUUID();
            t.pod = "pod-" + UUID.randomUUID();
            t.container = "ctr-" + random.nextInt(10);

            String key = t.namespace + t.deployment + t.pod + t.container;

            registry.targets.putIfAbsent(
                    key,
                    new TargetSamples(t, new ArrayList<>())
            );

            insertCounter++;

            if (insertCounter % 1000 == 0) {
                LOG.infof("Inserted %d targets. Current registry size=%d",
                        insertCounter,
                        registry.targets.size());
            }
        }
    }

    long elapsedSeconds() {
        return (System.currentTimeMillis() - start) / 1000;
    }
}