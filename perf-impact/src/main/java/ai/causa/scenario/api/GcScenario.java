package ai.causa.scenario.api;

import ai.causa.engine.Phase;

/**
 * A GC scenario represents one type of JVM memory behavior pattern
 * that can degrade performance.
 *
 * Implementations must be deterministic and phase-driven.
 */
public interface GcScenario {
    /**
     * Called repeatedly by the scheduler.
     * Scenario should perform memory activity depending on phase.
     */
    void execute(Phase phase);

    /**
     * Unique scenario name used for selection.
     * Must match environment variable value.
     */
    String name();
}
