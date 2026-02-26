package ai.causa.scenario.impl;

import ai.causa.engine.Phase;
import ai.causa.scenario.api.GcScenario;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Allocation Storm Scenario
 *
 * Simulates increasing allocation pressure across phases.
 * This primarily stresses Young GC frequency.
 */
@ApplicationScoped
public class AllocationStormScenario implements GcScenario {
    /**
     * Prevents JIT escape analysis from eliminating allocations.
     */
    private volatile Object blackhole;

    @Override
    public void execute(Phase phase) {
        long maxHeap = Runtime.getRuntime().maxMemory();

        // Define allocation intensity as percentage of heap per tick
        double factor;

        switch (phase) {
            case BASELINE -> factor = 0.001;   // 0.1% heap/sec
            case RAMP -> factor = 0.01;       // 1% heap/sec
            case STRESS -> factor = 0.10;     // 10% heap/sec
            case RECOVERY -> factor = 0.0005; // 0.05% heap/sec
            default -> factor = 0.001;
        }

        long bytesToAllocate = (long) (maxHeap * factor);

        allocateBytes(bytesToAllocate);
    }

    private void allocateBytes(long totalBytes) {
        long allocated = 0;

        while (allocated < totalBytes) {
            int size = ThreadLocalRandom.current()
                    .nextInt(512, 4096);
            byte[] data = new byte[size];
            blackhole = data;
            allocated += size;
        }
    }

    @Override
    public String name() {
        return "ALLOCATION_STORM";
    }
}
