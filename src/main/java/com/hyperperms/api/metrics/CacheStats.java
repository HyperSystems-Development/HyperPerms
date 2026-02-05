package com.hyperperms.api.metrics;

/**
 * Statistics about the permission cache.
 *
 * @param hits        number of cache hits
 * @param misses      number of cache misses
 * @param hitRate     hit rate as a decimal (0.0 to 1.0)
 * @param evictions   number of cache evictions
 * @param currentSize current number of entries in the cache
 */
public record CacheStats(
        long hits,
        long misses,
        double hitRate,
        long evictions,
        int currentSize
) {
    /**
     * Gets the hit rate as a percentage.
     *
     * @return the hit rate percentage (0 to 100)
     */
    public double getHitRatePercent() {
        return hitRate * 100.0;
    }

    /**
     * Gets the total number of requests (hits + misses).
     *
     * @return the total request count
     */
    public long getTotalRequests() {
        return hits + misses;
    }
}
