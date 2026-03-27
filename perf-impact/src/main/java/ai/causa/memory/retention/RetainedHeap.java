package ai.causa.memory.retention;

import ai.causa.constants.Constants;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores objects temporarily so they survive multiple GC cycles.
 * This increases the probability that objects get promoted to old generation.
 *
 * The structure is intentionally simple and bounded so that we
 * do not risk uncontrolled heap growth.
 */
@ApplicationScoped
public class RetainedHeap {

    private final long maxHeap = Runtime.getRuntime().maxMemory();
    private final long maxRetainedBytes = (long) (maxHeap * Constants.HeapRetention.RETENTION_HEAP_LIMIT);
    private long retainedBytes = 0;

    private final List<Object> retained = new ArrayList<>();

    /**
     * Retain an object so it survives GC.
     */
    public void retain(Object obj) {
        if (!(obj instanceof byte[] data)) {
            retained.add(obj);
            return;
        }

        int size = data.length;

        if (retainedBytes + size > maxRetainedBytes) {
            return;
        }

        retained.add(obj);
        retainedBytes += size;
    }

    /**
     * Release a portion of retained objects.
     * Used during RECOVERY phase.
     */
    public void releaseHalf() {

        int size = retained.size();

        if (size == 0) {
            return;
        }

        int removeCount = size / 2;

        for (int i = 0; i < removeCount; i++) {
            Object obj = retained.removeFirst();

            if (obj instanceof byte[] data) {
                retainedBytes -= data.length;
            }
        }
    }

    /**
     * Completely clear retained objects.
     */
    public void clear() {
        retained.clear();
        retainedBytes = 0;
    }

    /**
     * Current retained object count.
     */
    public int size() {
        return retained.size();
    }
}