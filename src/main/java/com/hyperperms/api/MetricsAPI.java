package com.hyperperms.api;

import com.hyperperms.api.metrics.AuditEntry;
import com.hyperperms.api.metrics.CacheStats;
import com.hyperperms.api.metrics.PermissionCheckStats;
import com.hyperperms.api.metrics.PermissionHotspot;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API for accessing permission check metrics, cache statistics, and audit logs.
 * <p>
 * The Metrics API provides insight into the performance and usage of HyperPerms,
 * including permission check statistics, cache performance, and an audit log
 * of all permission changes.
 * <p>
 * Example usage:
 * <pre>
 * MetricsAPI metrics = api.getMetrics();
 * if (metrics != null) {
 *     // Get cache stats
 *     CacheStats cache = metrics.getCacheStats();
 *     System.out.println("Cache hit rate: " + cache.getHitRatePercent() + "%");
 *
 *     // Get hotspots
 *     metrics.getHotspots(10).thenAccept(hotspots -> {
 *         hotspots.forEach(h -> System.out.println(h.permission() + ": " + h.checkCount()));
 *     });
 * }
 * </pre>
 */
public interface MetricsAPI {

    // ==================== Permission Check Metrics ====================

    /**
     * Gets the total number of permission checks performed.
     *
     * @return the total check count
     */
    long getTotalChecks();

    /**
     * Gets detailed permission check statistics.
     *
     * @return the check statistics
     */
    @NotNull
    PermissionCheckStats getCheckStats();

    /**
     * Gets the most frequently checked permissions.
     *
     * @param limit the maximum number of hotspots to return
     * @return a future that completes with the hotspot list
     */
    @NotNull
    CompletableFuture<List<PermissionHotspot>> getHotspots(int limit);

    // ==================== Cache Metrics ====================

    /**
     * Gets cache statistics.
     *
     * @return the cache statistics
     */
    @NotNull
    CacheStats getCacheStats();

    /**
     * Gets the current cache size.
     *
     * @return the number of entries in the cache
     */
    int getCacheSize();

    /**
     * Gets the cache hit rate.
     *
     * @return the hit rate as a decimal (0.0 to 1.0)
     */
    double getCacheHitRate();

    // ==================== Timing Metrics ====================

    /**
     * Gets the average permission check time in nanoseconds.
     *
     * @return the average check time
     */
    long getAverageCheckTimeNanos();

    /**
     * Gets the 95th percentile permission check time in nanoseconds.
     *
     * @return the P95 check time
     */
    long getP95CheckTimeNanos();

    // ==================== Audit Log ====================

    /**
     * Gets recent audit log entries.
     *
     * @param limit the maximum number of entries to return
     * @return a future that completes with the audit entries
     */
    @NotNull
    CompletableFuture<List<AuditEntry>> getRecentAuditLog(int limit);

    /**
     * Gets audit log entries for a specific holder.
     *
     * @param holderType the holder type ("user" or "group")
     * @param holderId   the holder identifier
     * @param limit      the maximum number of entries to return
     * @return a future that completes with the audit entries
     */
    @NotNull
    CompletableFuture<List<AuditEntry>> getAuditLog(@NotNull String holderType, @NotNull String holderId, int limit);

    // ==================== Reset ====================

    /**
     * Resets all metrics counters.
     * <p>
     * This does not clear the audit log.
     */
    void resetMetrics();
}
