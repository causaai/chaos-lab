package ai.causa.cgroup;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class CgroupInfo {

    private CgroupReader reader;
    private long baselineUsage;

    @PostConstruct
    void init() {
        if (Files.exists(Path.of("/sys/fs/cgroup/memory.max"))) {
            reader = new CgroupV2Reader();
        } else if (Files.exists(Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"))) {
            reader = new CgroupV1Reader();
        } else {
            throw new IllegalStateException("No cgroup memory controller detected");
        }

        // Baseline captured BEFORE accepting requests
        baselineUsage = reader.memoryUsageBytes();

        System.out.printf(
                "Detected cgroup %s | limit=%dMB | baseline=%dMB%n",
                reader.version(),
                reader.memoryLimitBytes() / 1024 / 1024,
                baselineUsage / 1024 / 1024
        );
    }

    public String version() {
        return reader.version();
    }

    public long limitBytes() {
        return reader.memoryLimitBytes();
    }

    public long currentUsageBytes() {
        return reader.memoryUsageBytes();
    }

    public long baselineUsageBytes() {
        return baselineUsage;
    }
}
