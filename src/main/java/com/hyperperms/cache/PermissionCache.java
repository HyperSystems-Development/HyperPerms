package com.hyperperms.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.resolver.PermissionResolver.ResolvedPermissions;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * High-performance permission cache using Caffeine with statistics tracking.
 */
public final class PermissionCache {

    private final Cache<CacheKey, ResolvedPermissions> cache;
    private final CacheStatistics statistics;
    private volatile boolean enabled;

    /**
     * Creates a new permission cache.
     *
     * @param maxSize       maximum cache size
     * @param expirySeconds entry expiry time in seconds
     * @param enabled       whether caching is enabled
     */
    public PermissionCache(int maxSize, int expirySeconds, boolean enabled) {
        this.enabled = enabled;
        this.statistics = new CacheStatistics();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expirySeconds, TimeUnit.SECONDS)
                .removalListener((key, value, cause) -> {
                    if (cause == RemovalCause.SIZE || cause == RemovalCause.EXPIRED) {
                        statistics.recordEviction();
                    }
                })
                .build();

        if (enabled) {
            Logger.info("Permission cache initialized (maxSize=%d, expiry=%ds)", maxSize, expirySeconds);
        } else {
            Logger.info("Permission caching is disabled");
        }
    }

    /**
     * Gets cached permissions for a user in a context.
     *
     * @param uuid     the user UUID
     * @param contexts the context set
     * @return cached permissions, or null if not cached
     */
    @Nullable
    public ResolvedPermissions get(@NotNull UUID uuid, @NotNull ContextSet contexts) {
        if (!enabled) {
            statistics.recordMiss();
            return null;
        }
        ResolvedPermissions result = cache.getIfPresent(new CacheKey(uuid, contexts));
        if (result != null) {
            statistics.recordHit();
        } else {
            statistics.recordMiss();
        }
        return result;
    }

    /**
     * Caches resolved permissions for a user in a context.
     *
     * @param uuid        the user UUID
     * @param contexts    the context set
     * @param permissions the resolved permissions
     */
    public void put(@NotNull UUID uuid, @NotNull ContextSet contexts, @NotNull ResolvedPermissions permissions) {
        if (!enabled) {
            return;
        }
        cache.put(new CacheKey(uuid, contexts), permissions);
    }

    /**
     * Invalidates all cached permissions for a user.
     *
     * @param uuid the user UUID
     */
    public void invalidate(@NotNull UUID uuid) {
        // Caffeine doesn't support partial key invalidation efficiently,
        // so we collect matching keys first, then invalidate them.
        //
        // Known limitation: there is a small race window between collecting
        // keys and invalidating them — a concurrent put() for the same UUID
        // could insert a new entry that we miss. This is acceptable because
        // the cache has a short TTL and the next permission check will
        // repopulate with fresh data. Eliminating this race entirely would
        // require a secondary index (UUID -> Set<CacheKey>), which adds
        // complexity that isn't justified given the TTL safety net.
        var keysToInvalidate = cache.asMap().keySet().stream()
                .filter(key -> key.uuid.equals(uuid))
                .toList();

        keysToInvalidate.forEach(cache::invalidate);

        if (!keysToInvalidate.isEmpty()) {
            statistics.recordInvalidations(keysToInvalidate.size());
        }
    }

    /**
     * Invalidates a specific cached entry.
     *
     * @param uuid     the user UUID
     * @param contexts the context set
     */
    public void invalidate(@NotNull UUID uuid, @NotNull ContextSet contexts) {
        cache.invalidate(new CacheKey(uuid, contexts));
        statistics.recordInvalidation();
    }

    /**
     * Invalidates all cached entries.
     */
    public void invalidateAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        if (size > 0) {
            statistics.recordInvalidations(size);
        }
        Logger.debug("Permission cache cleared (%d entries)", size);
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of cached entries
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Enables or disables the cache.
     *
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            invalidateAll();
        }
    }

    /**
     * Checks if caching is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the cache statistics.
     *
     * @return the statistics tracker
     */
    @NotNull
    public CacheStatistics getStatistics() {
        return statistics;
    }

    /**
     * Resets cache statistics.
     */
    public void resetStatistics() {
        statistics.reset();
    }

    /**
     * Gets cache statistics as a string.
     *
     * @return statistics string
     */
    @NotNull
    public String getStats() {
        return String.format(
                "PermissionCache{enabled=%s, size=%d, %s}",
                enabled, size(), statistics
        );
    }

    /**
     * Cache key combining user UUID and context.
     */
    private record CacheKey(@NotNull UUID uuid, @NotNull ContextSet contexts) {
        private CacheKey {
            Objects.requireNonNull(uuid, "uuid cannot be null");
            Objects.requireNonNull(contexts, "contexts cannot be null");
        }
    }
}
