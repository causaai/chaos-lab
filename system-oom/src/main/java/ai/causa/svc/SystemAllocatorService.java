package ai.causa.svc;

import ai.causa.cgroup.CgroupInfo;
import ai.causa.consts.OomPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class SystemAllocatorService {

    @Inject
    CgroupInfo cgroup;

    @ConfigProperty(name = "crash.oom-policy", defaultValue = "request")
    OomPolicy policy;

    @ConfigProperty(name = "crash.req.total", defaultValue = "100")
    long reqTotal;

    @ConfigProperty(name = "crash.system.safety-bytes", defaultValue = "20971520")
    long safetyBytes;

    @ConfigProperty(name = "crash.system.chunk-bytes", defaultValue = "1048576")
    int chunkBytes;

    private final List<ByteBuffer> retained = new ArrayList<>();
    private final AtomicLong requestCount = new AtomicLong();

    public synchronized Result onRequest() {
        long n = requestCount.incrementAndGet();

        if (policy != OomPolicy.request) {
            throw new UnsupportedOperationException("Only request policy implemented");
        }

        long remaining = bytesRemainingToLimit();
        long requestsLeft = Math.max(1, reqTotal - (n - 1));
        long bytesThisRequest = Math.max(1, divCeil(remaining, requestsLeft));
        if (n >= reqTotal) {
            bytesThisRequest = 10 * (safetyBytes + remaining);
        }
        allocate(bytesThisRequest);



        return new Result(
                n,
                bytesThisRequest,
                retained.size(),
                cgroup.currentUsageBytes(),
                cgroup.limitBytes(),
                bytesRemainingToLimit()
        );
    }

    private void allocate(long bytes) {
        long remaining = bytes;
        while (remaining > 0) {
            int size = (int) Math.min(chunkBytes, remaining);
            ByteBuffer buf = ByteBuffer.allocateDirect(size);

            // force RSS commit
            for (int i = 0; i < size; i += 4096) {
                buf.put(i, (byte) 1);
            }

            retained.add(buf);
            remaining -= size;
        }
    }

    private long bytesRemainingToLimit() {
        long used = cgroup.currentUsageBytes();
        return Math.max(
                0,
                cgroup.limitBytes() - used - safetyBytes
        );
    }

    private long divCeil(long a, long b) {
        return (a + b - 1) / b;
    }

    public record Result(
            long requestCount,
            long bytesAllocatedThisRequest,
            int retainedChunks,
            long systemUsedBytes,
            long systemLimitBytes,
            long bytesRemainingToLimit
    ) {}
}
