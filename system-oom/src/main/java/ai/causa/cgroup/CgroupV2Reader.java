package ai.causa.cgroup;

import java.nio.file.Files;
import java.nio.file.Path;

public class CgroupV2Reader implements CgroupReader {

    private static final Path LIMIT = Path.of("/sys/fs/cgroup/memory.max");
    private static final Path USAGE = Path.of("/sys/fs/cgroup/memory.current");

    @Override
    public long memoryLimitBytes() {
        try {
            String v = Files.readString(LIMIT).trim();
            if ("max".equals(v)) {
                throw new IllegalStateException("memory.max is unlimited");
            }
            return Long.parseLong(v);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long memoryUsageBytes() {
        try {
            return Long.parseLong(Files.readString(USAGE).trim());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String version() {
        return "v2";
    }
}
