package ai.causa.engine;

import ai.causa.scenario.api.GcScenario;
import ai.causa.scenario.registry.ScenarioRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;

/**
 * Periodically invokes the active scenario.
 * This is the runtime driver of memory behavior.
 */
@ApplicationScoped
public class ScenarioScheduler {
    @Inject
    PhaseEngine phaseEngine;

    @Inject
    ScenarioRegistry registry;

    /**
     * Runs every 200ms.
     *
     * This frequency is fast enough to:
     * - generate allocation pressure
     * - react to phase changes
     * - simulate real GC load
     *
     * but slow enough to not overload CPU artificially.
     */
    @Scheduled(every = "PT0.2S")
    void tick() {

        GcScenario scenario = registry.activeScenario();
        Phase phase = phaseEngine.currentPhase();
        scenario.execute(phase);
    }
}
