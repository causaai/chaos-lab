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
            case BASELINE -> factor = 0.0005;   // 0.05% of heap
            case RAMP -> factor = 0.002;        // 0.2% of heap
            case STRESS -> factor = 0.01;       // 1% of heap
            case RECOVERY -> factor = 0.0003;   // 0.03% of heap
            default -> factor = 0.0005;
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
