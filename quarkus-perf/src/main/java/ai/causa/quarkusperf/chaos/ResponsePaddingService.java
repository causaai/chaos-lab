package ai.causa.quarkusperf.chaos;

import ai.causa.quarkusperf.model.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chaos knob: HTTP large-response padding.
 *
 * <h3>What it models</h3>
 * In high-concurrency Quarkus/Vert.x deployments, pairing a long HTTP idle timeout
 * with large response bodies creates sustained heap pressure:
 *
 * <pre>
 *   live_heap ≈ concurrent_connections × response_size_bytes
 * </pre>
 *
 * At 50 concurrent workers, 256 KB per response and a 60-second idle window,
 * the JVM must hold roughly 12–50 MB of live response data at any given moment —
 * enough to tip a 384 MB heap into GC pressure and eventual OOM within minutes.
 *
 * <h3>How to activate</h3>
 * Set {@code CHAOS_HTTP_LARGE_RESPONSE_ENABLED=true} in the pod environment.
 * Adjust {@code CHAOS_HTTP_LARGE_RESPONSE_KB} to tune pressure level.
 * Set {@code CHAOS_HTTP_IDLE_TIMEOUT_ENABLED=true} to extend connection lifetime.
 */
@ApplicationScoped
public class ResponsePaddingService {

    private static final Logger LOG = Logger.getLogger(ResponsePaddingService.class);

    /** Total bytes padded across all responses since startup. */
    private static final AtomicLong TOTAL_PADDING_BYTES = new AtomicLong();

    @Inject
    ChaosConfig chaosConfig;

    @Inject
    MeterRegistry meterRegistry;

    private volatile Counter paddedResponseCounter;

    /**
     * If the large-response knob is active, allocates a byte array of the configured
     * size, Base64-encodes it, and sets it on the response's {@code debugPadding} field.
     * The Base64 string is roughly 1.33× the raw byte size, so effective per-response
     * heap cost during Jackson serialisation is:
     *
     * <pre>
     *   raw_bytes (byte[]) + base64_string (char[] inside String)
     *   ≈ paddingKb * 1024  +  paddingKb * 1024 * 4/3
     *   ≈ 2.33 × paddingKb KB
     * </pre>
     *
     * @param response the {@link ApiResponse} to pad; mutated in-place
     */
    public <T> void pad(ApiResponse<T> response) {
        if (!chaosConfig.http().largeResponseEnabled()) {
            return;
        }

        int size = Math.max(1, chaosConfig.http().largeResponseKb()) * 1024;
        byte[] raw = new byte[size];
        Arrays.fill(raw, (byte) 'P');
        String encoded = Base64.getEncoder().encodeToString(raw);
        response.setDebugPadding(encoded);

        long total = TOTAL_PADDING_BYTES.addAndGet(size);

        // Lazily register counter once MeterRegistry is available
        if (paddedResponseCounter == null) {
            paddedResponseCounter = meterRegistry.counter("quarkus_perf_http_padded_responses_total");
        }
        paddedResponseCounter.increment();

        LOG.warnf("[CHAOS-HTTP] Response padded with %d KB. Total padding issued: %d MB. " +
                        "Set CHAOS_HTTP_LARGE_RESPONSE_ENABLED=false to stop.",
                chaosConfig.http().largeResponseKb(), total / (1024 * 1024));
    }

    /** Returns cumulative bytes padded since startup (for metrics). */
    public long getTotalPaddingBytes() {
        return TOTAL_PADDING_BYTES.get();
    }

    public boolean isEnabled() {
        return chaosConfig.http().largeResponseEnabled();
    }

    public int getPaddingKb() {
        return chaosConfig.http().largeResponseKb();
    }
}
