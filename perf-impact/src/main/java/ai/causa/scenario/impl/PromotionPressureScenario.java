package ai.causa.scenario.impl;

import ai.causa.engine.Phase;
import ai.causa.memory.retention.RetainedHeap;
import ai.causa.scenario.api.GcScenario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Promotion Pressure Scenario
 *
 * Simulates medium-lived objects that survive young GC cycles
 * and get promoted to old generation.
 */
@ApplicationScoped
public class PromotionPressureScenario implements GcScenario {

    @Inject
    RetainedHeap retainedHeap;

    private volatile Object blackhole;

    @Override
    public void execute(Phase phase) {
        Runtime runtime = Runtime.getRuntime();

        long maxHeap = runtime.maxMemory();
        long totalHeap = runtime.totalMemory();
        long freeHeap = runtime.freeMemory();

        long usedHeap = totalHeap - freeHeap;
        long availableHeap = maxHeap - usedHeap;

        double allocationFactor;
        double retentionProbability;

        switch (phase) {

            case BASELINE -> {
                allocationFactor = 0.025;
                retentionProbability = 0.15;
            }
            case RAMP -> {
                allocationFactor = 0.07;
                retentionProbability = 0.50;
            }
            case STRESS -> {
                allocationFactor = 0.1;
                retentionProbability = 0.85;
            }
            case RECOVERY -> {
                retainedHeap.releaseHalf();
                allocationFactor = 0.0005;
                retentionProbability = 0.02;
            }
            default -> {
                allocationFactor = 0.001;
                retentionProbability = 0.05;
            }
        }

        long bytesToAllocate = (long) (availableHeap * allocationFactor);

        allocate(bytesToAllocate, retentionProbability);
    }

    private void allocate(long totalBytes, double retentionProbability) {

        long allocated = 0;

        while (allocated < totalBytes) {

            int size = ThreadLocalRandom.current().nextInt(512, 4096);

            byte[] data = new byte[size];

            blackhole = data;

            if (ThreadLocalRandom.current().nextDouble() < retentionProbability) {
                retainedHeap.retain(data);
            }

            allocated += size;
        }
    }

    @Override
    public String name() {
        return "PROMOTION_PRESSURE";
    }
}