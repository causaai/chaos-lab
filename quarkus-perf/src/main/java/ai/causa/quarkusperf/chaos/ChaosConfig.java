package ai.causa.quarkusperf.chaos;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Central configuration interface for all chaos knobs.
 *
 * <p>All settings are externalized as environment variables following the pattern:
 * config key {@code chaos.db.leak.enabled} maps to env var {@code CHAOS_DB_LEAK_ENABLED}.
 *
 * <table>
 * <caption>Chaos knob summary</caption>
 * <tr><th>Env var</th><th>Default</th><th>Effect</th></tr>
 * <tr><td>CHAOS_DB_LEAK_ENABLED</td><td>false</td>
 *     <td>Connections acquired and never returned — exhausts Agroal pool</td></tr>
 * <tr><td>CHAOS_DB_SLOW_QUERY_MS</td><td>0</td>
 *     <td>Artificial sleep per JDBC call — amplifies thread starvation</td></tr>
 * <tr><td>CHAOS_MEMORY_CACHE_ENABLED</td><td>false</td>
 *     <td>Unbounded static ConcurrentHashMap — heap grows linearly with load</td></tr>
 * <tr><td>CHAOS_MEMORY_OBJECTS_PER_TX</td><td>1</td>
 *     <td>64 KB byte-arrays leaked per transaction in cache mode</td></tr>
 * <tr><td>CHAOS_HTTP_LARGE_RESPONSE_ENABLED</td><td>false</td>
 *     <td>Pads every HTTP response with Base64 data — raises per-connection heap cost</td></tr>
 * <tr><td>CHAOS_HTTP_LARGE_RESPONSE_KB</td><td>256</td>
 *     <td>Padding size in KB per response</td></tr>
 * <tr><td>CHAOS_HTTP_IDLE_TIMEOUT_ENABLED</td><td>false</td>
 *     <td>Extends Vert.x HTTP idle timeout to amplify keep-alive heap pressure</td></tr>
 * <tr><td>CHAOS_HTTP_IDLE_TIMEOUT_S</td><td>60</td>
 *     <td>Idle timeout in seconds when CHAOS_HTTP_IDLE_TIMEOUT_ENABLED=true</td></tr>
 * </table>
 */
@ConfigMapping(prefix = "chaos")
public interface ChaosConfig {

    /** DB connection-pool chaos settings. */
    Db db();

    /** JVM heap chaos settings. */
    Memory memory();

    /** HTTP keep-alive / large-response chaos settings. */
    Http http();

    interface Db {
        @WithName("leak.enabled")
        @WithDefault("false")
        boolean leakEnabled();

        @WithName("slow-query-ms")
        @WithDefault("0")
        long slowQueryMs();
    }

    interface Memory {
        @WithName("cache.enabled")
        @WithDefault("false")
        boolean cacheEnabled();

        @WithName("objects-per-tx")
        @WithDefault("1")
        int objectsPerTx();
    }

    interface Http {
        @WithName("large-response.enabled")
        @WithDefault("false")
        boolean largeResponseEnabled();

        @WithName("large-response.kb")
        @WithDefault("256")
        int largeResponseKb();

        @WithName("idle-timeout.enabled")
        @WithDefault("false")
        boolean idleTimeoutEnabled();

        @WithName("idle-timeout-s")
        @WithDefault("60")
        int idleTimeoutS();
    }
}
