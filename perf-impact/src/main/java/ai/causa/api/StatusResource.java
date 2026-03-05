package ai.causa.api;

import ai.causa.engine.Phase;
import ai.causa.engine.PhaseEngine;
import ai.causa.scenario.api.GcScenario;
import ai.causa.scenario.registry.ScenarioRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {
    @Inject
    PhaseEngine phaseEngine;

    @Inject
    ScenarioRegistry registry;

    @GET
    public Map<String, Object> status() {

        Phase phase = phaseEngine.currentPhase();
        GcScenario scenario = registry.activeScenario();

        return Map.of(
                "scenario", scenario.name(),
                "phase", phase.name(),
                "cycleSeconds", System.getProperty("gc.cycle.seconds", "60"),
                "time", System.currentTimeMillis()
        );
    }
}
