package ai.causa.engine;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PhaseEngine {
    @ConfigProperty(name = "gc.cycle.seconds", defaultValue = "60")
    long cycleSeconds;

    public Phase currentPhase() {

        // Current time in seconds
        long now = System.currentTimeMillis() / 1000;

        /*
         * We divide current time by cycleSeconds.
         *
         * Example:
         * If cycleSeconds = 60
         * then every 60 seconds the value of (now / cycleSeconds) increments.
         *
         * So:
         * 0–59 sec   → cycle = 0
         * 60–119 sec → cycle = 1
         * 120–179    → cycle = 2
         * and so on...
         */
        long cycle = now / cycleSeconds;

        /*
         * We map the cycle number to one of the Phase enum values.
         *
         * Since we have 4 phases:
         * BASELINE, RAMP, STRESS, RECOVERY
         *
         * We use modulo to loop continuously:
         *
         * cycle % 4
         *
         * This guarantees:
         * 0 → BASELINE
         * 1 → RAMP
         * 2 → STRESS
         * 3 → RECOVERY
         * 4 → BASELINE (repeat)
         */
        int index = (int) (cycle % Phase.values().length);

        // Convert computed index to actual Phase enum
        return Phase.fromOrdinal(index);
    }
}
