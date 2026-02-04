package com.hyperperms.analytics;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.events.*;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * Manages permission analytics tracking.
 * <p>
 * Features:
 * <ul>
 *   <li>Permission check tracking with in-memory aggregation</li>
 *   <li>Audit log for permission changes</li>
 *   <li>Periodic flush to SQLite storage</li>
 *   <li>Automatic retention cleanup</li>
 * </ul>
 */
public final class AnalyticsManager {

    private final HyperPerms hyperPerms;
    private final AnalyticsStorage storage;
    private final ScheduledExecutorService scheduler;

    // Configuration
    private volatile boolean enabled;
    private volatile boolean trackChecks;
    private volatile boolean trackChanges;
    private volatile int flushIntervalSeconds;
    private volatile int retentionDays;

    // In-memory counters for high-frequency check aggregation
    // Key = permission, Value = [checkCount, grantCount, denyCount]
    private final ConcurrentHashMap<String, long[]> checkCounters = new ConcurrentHashMap<>();

    // Event subscriptions
    private EventBus.Subscription checkSubscription;
    private EventBus.Subscription changeSubscription;

    // Scheduled tasks
    private ScheduledFuture<?> flushTask;
    private ScheduledFuture<?> cleanupTask;

    /**
     * Creates a new analytics manager.
     *
     * @param hyperPerms the HyperPerms instance
     */
    public AnalyticsManager(@NotNull HyperPerms hyperPerms) {
        this.hyperPerms = hyperPerms;
        
        // Initialize storage
        Path analyticsDb = hyperPerms.getDataDirectory().resolve("data/analytics.db");
        this.storage = new SQLiteAnalyticsStorage(analyticsDb);
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyperPerms-Analytics");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the analytics manager.
     */
    public void start() {
        loadConfig();
        
        if (!enabled) {
            Logger.info("[Analytics] Analytics is disabled in config");
            return;
        }

        // Initialize storage
        storage.init().join();

        // Subscribe to events
        EventBus eventBus = hyperPerms.getEventBus();
        
        if (trackChecks) {
            checkSubscription = eventBus.subscribe(PermissionCheckEvent.class, this::onPermissionCheck);
            Logger.debug("[Analytics] Subscribed to permission check events");
        }
        
        if (trackChanges) {
            changeSubscription = eventBus.subscribe(PermissionChangeEvent.class, this::onPermissionChange);
            Logger.debug("[Analytics] Subscribed to permission change events");
        }

        // Schedule periodic flush
        flushTask = scheduler.scheduleAtFixedRate(
                this::flushCounters,
                flushIntervalSeconds,
                flushIntervalSeconds,
                TimeUnit.SECONDS
        );

        // Schedule daily cleanup
        cleanupTask = scheduler.scheduleAtFixedRate(
                this::runCleanup,
                24, 24, TimeUnit.HOURS
        );

        Logger.info("[Analytics] Started (trackChecks=%s, trackChanges=%s, flushInterval=%ds)",
                trackChecks, trackChanges, flushIntervalSeconds);
    }

    /**
     * Stops the analytics manager.
     */
    public void stop() {
        // Unsubscribe from events
        if (checkSubscription != null) {
            checkSubscription.unsubscribe();
            checkSubscription = null;
        }
        if (changeSubscription != null) {
            changeSubscription.unsubscribe();
            changeSubscription = null;
        }

        // Cancel scheduled tasks
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }

        // Final flush
        if (enabled && !checkCounters.isEmpty()) {
            flushCounters();
        }

        // Shutdown storage
        storage.shutdown().join();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Logger.info("[Analytics] Stopped");
    }

    /**
     * Loads configuration.
     */
    public void loadConfig() {
        var config = hyperPerms.getConfig();
        enabled = config.isAnalyticsEnabled();
        trackChecks = config.isAnalyticsTrackChecks();
        trackChanges = config.isAnalyticsTrackChanges();
        flushIntervalSeconds = config.getAnalyticsFlushIntervalSeconds();
        retentionDays = config.getAnalyticsRetentionDays();
    }

    /**
     * Handles permission check events.
     */
    private void onPermissionCheck(PermissionCheckEvent event) {
        if (!enabled || !trackChecks) return;

        String permission = event.getPermission();
        boolean granted = event.getResult().asBoolean();

        // Update in-memory counters
        checkCounters.compute(permission, (k, v) -> {
            if (v == null) {
                v = new long[3]; // [checkCount, grantCount, denyCount]
            }
            v[0]++; // checkCount
            if (granted) {
                v[1]++; // grantCount
            } else {
                v[2]++; // denyCount
            }
            return v;
        });
    }

    /**
     * Handles permission change events.
     */
    private void onPermissionChange(PermissionChangeEvent event) {
        if (!enabled || !trackChanges) return;

        String holderType = (event.getHolder() instanceof com.hyperperms.model.User) ? "user" : "group";
        String holderId = event.getHolder().getIdentifier();
        String action = event.getChangeType().name();
        String permission = event.getNode().getPermission();
        Boolean newValue = event.getNode().getValue();

        // Determine old value (opposite of change for ADD/REMOVE)
        Boolean oldValue = null;
        if (event.getChangeType() == PermissionChangeEvent.ChangeType.REMOVE) {
            oldValue = true; // Was granted, now removed
        } else if (event.getChangeType() == PermissionChangeEvent.ChangeType.ADD) {
            // oldValue stays null (didn't exist before)
        }

        storage.recordAuditEntry(
                holderType,
                holderId,
                action,
                permission,
                oldValue,
                newValue,
                null, // executor not available from event
                "api" // source
        );
    }

    /**
     * Flushes in-memory counters to storage.
     */
    private void flushCounters() {
        if (checkCounters.isEmpty()) return;

        // Swap out the counters
        Map<String, long[]> toFlush = new ConcurrentHashMap<>(checkCounters);
        checkCounters.clear();

        // Write to storage
        storage.bulkUpdatePermissionChecks(toFlush).whenComplete((v, e) -> {
            if (e != null) {
                Logger.warn("[Analytics] Failed to flush counters: %s", e.getMessage());
                // Put back unflushed data
                toFlush.forEach((k, counts) -> 
                    checkCounters.merge(k, counts, (existing, newCounts) -> {
                        existing[0] += newCounts[0];
                        existing[1] += newCounts[1];
                        existing[2] += newCounts[2];
                        return existing;
                    })
                );
            } else {
                Logger.debug("[Analytics] Flushed %d permission counters", toFlush.size());
            }
        });
    }

    /**
     * Runs retention cleanup.
     */
    private void runCleanup() {
        storage.cleanupOldData(retentionDays).whenComplete((count, e) -> {
            if (e != null) {
                Logger.warn("[Analytics] Cleanup failed: %s", e.getMessage());
            } else if (count > 0) {
                Logger.info("[Analytics] Cleaned up %d old records", count);
            }
        });
    }

    // ==================== Public API ====================

    /**
     * Checks if analytics is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the analytics summary.
     */
    public CompletableFuture<AnalyticsSummary> getSummary() {
        return storage.getSummary();
    }

    /**
     * Gets the most frequently checked permissions.
     */
    public CompletableFuture<List<AnalyticsSummary.PermissionStats>> getHotspots(int limit) {
        return storage.getHotspots(limit);
    }

    /**
     * Gets permissions that haven't been checked recently.
     */
    public CompletableFuture<List<String>> getUnusedPermissions(int days) {
        return storage.getUnusedPermissions(days);
    }

    /**
     * Gets the audit log for a holder.
     */
    public CompletableFuture<List<AnalyticsSummary.AuditEntry>> getAuditLog(
            @NotNull String holderType,
            @NotNull String holderId,
            int limit
    ) {
        return storage.getAuditLog(holderType, holderId, limit);
    }

    /**
     * Gets recent audit log entries.
     */
    public CompletableFuture<List<AnalyticsSummary.AuditEntry>> getRecentAuditLog(int limit) {
        return storage.getRecentAuditLog(limit);
    }

    /**
     * Exports analytics data.
     */
    public CompletableFuture<Boolean> export(@NotNull String format, @NotNull String filePath) {
        return storage.export(format, filePath);
    }

    /**
     * Records an audit entry manually.
     */
    public void recordAudit(
            @NotNull String holderType,
            @NotNull String holderId,
            @NotNull String action,
            @Nullable String permission,
            @Nullable Boolean oldValue,
            @Nullable Boolean newValue,
            @Nullable String executor,
            @NotNull String source
    ) {
        if (!enabled || !trackChanges) return;
        storage.recordAuditEntry(holderType, holderId, action, permission, oldValue, newValue, executor, source);
    }

    /**
     * Gets the underlying storage.
     */
    public AnalyticsStorage getStorage() {
        return storage;
    }
}
