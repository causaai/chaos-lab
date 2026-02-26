package ai.causa.cgroup;

import java.nio.file.Files;
import java.nio.file.Path;

public class CgroupV1Reader implements CgroupReader {

    private static final Path LIMIT =
            Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
    private static final Path USAGE =
            Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes");

    @Override
    public long memoryLimitBytes() {
        try {
            return Long.parseLong(Files.readString(LIMIT).trim());
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
        return "v1";
    }
}
