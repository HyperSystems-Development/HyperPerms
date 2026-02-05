package com.hyperperms.metrics;

import com.hyperperms.analytics.AnalyticsManager;
import com.hyperperms.analytics.AnalyticsSummary;
import com.hyperperms.api.MetricsAPI;
import com.hyperperms.api.metrics.AuditEntry;
import com.hyperperms.api.metrics.CacheStats;
import com.hyperperms.api.metrics.PermissionCheckStats;
import com.hyperperms.api.metrics.PermissionHotspot;
import com.hyperperms.cache.CacheStatistics;
import com.hyperperms.cache.PermissionCache;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of the MetricsAPI.
 */
public final class MetricsAPIImpl implements MetricsAPI {

    private final AnalyticsManager analyticsManager;
    private final Supplier<CacheStatistics> cacheStatsSupplier;
    private final Supplier<Integer> cacheSizeSupplier;

    // Timing metrics
    private final AtomicLong totalCheckTimeNanos = new AtomicLong(0);
    private final AtomicLong checkCount = new AtomicLong(0);
    private final AtomicLong p95CheckTimeNanos = new AtomicLong(0);

    public MetricsAPIImpl(@NotNull AnalyticsManager analyticsManager,
                          @NotNull Supplier<CacheStatistics> cacheStatsSupplier,
                          @NotNull Supplier<Integer> cacheSizeSupplier) {
        this.analyticsManager = analyticsManager;
        this.cacheStatsSupplier = cacheStatsSupplier;
        this.cacheSizeSupplier = cacheSizeSupplier;
    }

    @Override
    public long getTotalChecks() {
        CacheStatistics stats = cacheStatsSupplier.get();
        return stats != null ? stats.getTotalRequests() : 0;
    }

    @Override
    @NotNull
    public PermissionCheckStats getCheckStats() {
        return analyticsManager.getSummary()
                .thenApply(summary -> new PermissionCheckStats(
                        summary.totalChecks(),
                        summary.grantedChecks(),
                        summary.deniedChecks(),
                        summary.totalChecks() - summary.grantedChecks() - summary.deniedChecks()
                ))
                .exceptionally(e -> new PermissionCheckStats(0, 0, 0, 0))
                .join();
    }

    @Override
    @NotNull
    public CompletableFuture<List<PermissionHotspot>> getHotspots(int limit) {
        return analyticsManager.getHotspots(limit)
                .thenApply(hotspots -> hotspots.stream()
                        .map(h -> new PermissionHotspot(
                                h.permission(),
                                h.checkCount(),
                                h.grantCount(),
                                h.checkCount() > 0 ? (double) h.grantCount() / h.checkCount() * 100.0 : 0.0
                        ))
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public CacheStats getCacheStats() {
        CacheStatistics internal = cacheStatsSupplier.get();
        if (internal == null) {
            return new CacheStats(0, 0, 0.0, 0, 0);
        }
        return new CacheStats(
                internal.getHits(),
                internal.getMisses(),
                internal.getTotalRequests() > 0 ? internal.getHitRate() / 100.0 : 0.0,
                internal.getEvictions(),
                cacheSizeSupplier.get()
        );
    }

    @Override
    public int getCacheSize() {
        return cacheSizeSupplier.get();
    }

    @Override
    public double getCacheHitRate() {
        CacheStatistics internal = cacheStatsSupplier.get();
        if (internal == null || internal.getTotalRequests() == 0) {
            return 0.0;
        }
        return internal.getHitRate() / 100.0;
    }

    @Override
    public long getAverageCheckTimeNanos() {
        long count = checkCount.get();
        return count > 0 ? totalCheckTimeNanos.get() / count : 0;
    }

    @Override
    public long getP95CheckTimeNanos() {
        return p95CheckTimeNanos.get();
    }

    /**
     * Records a permission check timing.
     *
     * @param nanos the check time in nanoseconds
     */
    public void recordCheckTime(long nanos) {
        totalCheckTimeNanos.addAndGet(nanos);
        checkCount.incrementAndGet();
        // Simple P95 estimation: update if this is higher than 95% of previous checks
        // This is a rough approximation; a proper implementation would use a histogram
        p95CheckTimeNanos.updateAndGet(current -> Math.max(current, (long) (nanos * 0.95)));
    }

    @Override
    @NotNull
    public CompletableFuture<List<AuditEntry>> getRecentAuditLog(int limit) {
        return analyticsManager.getRecentAuditLog(limit)
                .thenApply(entries -> entries.stream()
                        .map(this::convertAuditEntry)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public CompletableFuture<List<AuditEntry>> getAuditLog(@NotNull String holderType, @NotNull String holderId, int limit) {
        return analyticsManager.getAuditLog(holderType, holderId, limit)
                .thenApply(entries -> entries.stream()
                        .map(this::convertAuditEntry)
                        .collect(Collectors.toList()));
    }

    private AuditEntry convertAuditEntry(AnalyticsSummary.AuditEntry internal) {
        return new AuditEntry(
                internal.timestamp(),
                internal.holderType(),
                internal.holderId(),
                internal.action(),
                internal.permission(),
                internal.executor()
        );
    }

    @Override
    public void resetMetrics() {
        CacheStatistics stats = cacheStatsSupplier.get();
        if (stats != null) {
            stats.reset();
        }
        totalCheckTimeNanos.set(0);
        checkCount.set(0);
        p95CheckTimeNanos.set(0);
    }
}
