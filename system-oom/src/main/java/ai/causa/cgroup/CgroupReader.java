package ai.causa.cgroup;

public interface CgroupReader {
    long memoryLimitBytes();
    long memoryUsageBytes();
    String version();
}
