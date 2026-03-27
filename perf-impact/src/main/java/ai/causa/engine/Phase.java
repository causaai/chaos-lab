package ai.causa.engine;

public enum Phase {
    BASELINE,
    RAMP,
    STRESS,
    RECOVERY;

    public static Phase fromOrdinal(int index) {
        Phase[] values = values();
        return values[index % values.length];
    }
}
