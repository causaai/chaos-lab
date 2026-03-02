package ai.causa.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ai.causa.model.TargetSamples;
import ai.causa.registry.Registry;
import org.jboss.logging.Logger;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class WriterScheduler {
    private static final Logger LOG =
            Logger.getLogger(ScrapeScheduler.class);

    @Inject
    Registry registry;

    ObjectMapper mapper = new ObjectMapper();

    Path file = Paths.get("/tmp/metrics.json");

    @Scheduled(every = "5s")
    void write() throws Exception {
        int size = registry.targets.size();
        LOG.infof("Writer started. targets=%d", size);

        List<TargetSamples> snapshot =
                new ArrayList<>(registry.targets.values());

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            for (TargetSamples ts : snapshot) {

                writer.write(mapper.writeValueAsString(ts));
                writer.newLine();

                Thread.sleep(1); // simulate IO slowness
            }
        }
        LOG.infof("Writer completed. wrote=%d entries", snapshot.size());
        long used =
                Runtime.getRuntime().totalMemory()
                        - Runtime.getRuntime().freeMemory();

        LOG.infof("Heap used=%d MB", used / (1024*1024));
    }
}