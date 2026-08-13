package ai.causa.quarkusperf.metrics;

import ai.causa.quarkusperf.chaos.ResponsePaddingService;
import ai.causa.quarkusperf.service.TransactionService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;

/**
 * Registers custom application-level OTEL/Prometheus gauges.
 *
 * <p>Metrics exposed (Prometheus format at {@code /q/metrics}):
 * <ul>
 *   <li>{@code quarkus_perf_heap_used_bytes}          — current JVM heap usage</li>
 *   <li>{@code quarkus_perf_heap_max_bytes}           — configured JVM max heap</li>
 *   <li>{@code quarkus_perf_heap_used_ratio}          — heap utilisation (0–1)</li>
 *   <li>{@code quarkus_perf_leak_cache_entries}       — background heap-leak cache entries</li>
 *   <li>{@code quarkus_perf_leak_cache_bytes}         — bytes held by background leak cache</li>
 *   <li>{@code quarkus_perf_http_padding_total_bytes} — cumulative bytes padded into HTTP responses</li>
 *   <li>{@code quarkus_perf_http_padding_enabled}     — 1.0 if HTTP large-response chaos is active</li>
 * </ul>
 *
 * <p>All metrics are automatically forwarded to the configured OTEL collector via the
 * Micrometer OTEL bridge (when {@code quarkus.otel.exporter.otlp.endpoint} is set).
 */
@ApplicationScoped
public class CustomMetrics {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    TransactionService transactionService;

    @Inject
    ResponsePaddingService paddingService;

    @PostConstruct
    void register() {
        Gauge.builder("quarkus_perf_heap_used_bytes", () -> {
            Runtime rt = Runtime.getRuntime();
            return (double) (rt.totalMemory() - rt.freeMemory());
        }).description("Current JVM heap usage in bytes").register(meterRegistry);

        Gauge.builder("quarkus_perf_heap_max_bytes",
                () -> (double) Runtime.getRuntime().maxMemory())
                .description("Configured JVM max heap in bytes").register(meterRegistry);

        Gauge.builder("quarkus_perf_heap_used_ratio", () -> {
            Runtime rt = Runtime.getRuntime();
            long max  = rt.maxMemory();
            long used = rt.totalMemory() - rt.freeMemory();
            return max > 0 ? (double) used / max : 0.0;
        }).description("Heap utilisation ratio (0–1)").register(meterRegistry);

        Gauge.builder("quarkus_perf_leak_cache_entries",
                transactionService, TransactionService::getLeakCacheSize)
                .description("Leaked objects in background memory cache").register(meterRegistry);

        Gauge.builder("quarkus_perf_leak_cache_bytes",
                transactionService, TransactionService::getLeakCacheBytes)
                .description("Estimated bytes held by background leak cache").register(meterRegistry);

        Gauge.builder("quarkus_perf_http_padding_total_bytes",
                paddingService, ResponsePaddingService::getTotalPaddingBytes)
                .description("Cumulative bytes padded into HTTP responses").register(meterRegistry);

        Gauge.builder("quarkus_perf_http_padding_enabled",
                paddingService, s -> s.isEnabled() ? 1.0 : 0.0)
                .description("1.0 if HTTP large-response chaos knob is active, 0.0 otherwise")
                .register(meterRegistry);
    }
}
