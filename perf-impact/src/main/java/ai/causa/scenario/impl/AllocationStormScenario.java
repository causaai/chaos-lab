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
        int allocations;

        switch (phase) {
            case BASELINE -> allocations = 200;
            case RAMP -> allocations = 2_000;
            case STRESS -> allocations = 15_000;
            case RECOVERY -> allocations = 100;
            default -> allocations = 50;
        }

        allocateObjects(allocations);
    }

    private void allocateObjects(int count) {

        for (int i = 0; i < count; i++) {
            // allocate random sized objects to mimic real allocation patterns
            byte[] data = new byte[ThreadLocalRandom.current().nextInt(256, 4096)];

            // prevent JIT eliminating allocation
            blackhole = data;
        }
    }

    @Override
    public String name() {
        return "ALLOCATION_STORM";
    }
}
