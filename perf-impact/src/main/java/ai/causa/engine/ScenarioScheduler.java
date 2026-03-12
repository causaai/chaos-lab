package ai.causa.engine;

import ai.causa.scenario.api.GcScenario;
import ai.causa.scenario.registry.ScenarioRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Periodically invokes the active scenario.
 * This is the runtime driver of memory behavior.
 */
@ApplicationScoped
public class ScenarioScheduler {
    private static final Logger LOG = Logger.getLogger(ScenarioScheduler.class);
    @Inject
    PhaseEngine phaseEngine;

    @Inject
    ScenarioRegistry registry;

    private boolean firstRun = true;

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
    @Scheduled(every = "1s", delay = 60, delayUnit = TimeUnit.SECONDS)
    void tick() {

        GcScenario scenario = registry.activeScenario();
        if  (firstRun) {
            LOG.infof("GC SCENARIO: %s STARTED", scenario.name());
            firstRun = false;
        }
        Phase phase = phaseEngine.currentPhase();
        scenario.execute(phase);
    }
}
