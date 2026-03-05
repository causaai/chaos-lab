package ai.causa.scenario.registry;

import ai.causa.scenario.api.GcScenario;
import ai.causa.constants.EnvKeys;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Discovers all scenario beans and selects one based on configuration.
 */
@ApplicationScoped
public class ScenarioRegistry {
    private final Map<String, GcScenario> scenarios = new HashMap<>();

    public ScenarioRegistry(Instance<GcScenario> discovered) {
        for (GcScenario scenario : discovered) {
            scenarios.put(scenario.name().toUpperCase(), scenario);
        }
    }

    /**
     * Returns the selected scenario based on environment/config value.
     */
    public GcScenario activeScenario() {

        String configured = ConfigProvider.getConfig().getOptionalValue(EnvKeys.SCENARIO, String.class)
                .orElseThrow(() ->
                        new IllegalStateException("No GC scenario configured. Set env var: " + EnvKeys.SCENARIO)
                );

        GcScenario scenario = scenarios.get(configured.toUpperCase());

        if (scenario == null) {
            throw new IllegalArgumentException("Unknown GC scenario: " + configured + ". Available: " + scenarios.keySet());
        }

        return scenario;
    }
}
