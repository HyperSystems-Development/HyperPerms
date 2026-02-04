package com.hyperperms.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for analytics data storage.
 */
public interface AnalyticsStorage {

    /**
     * Initializes the storage (creates tables, etc.).
     */
    CompletableFuture<Void> init();

    /**
     * Shuts down the storage.
     */
    CompletableFuture<Void> shutdown();

    // ==================== Permission Check Tracking ====================

    /**
     * Increments permission check counters.
     *
     * @param permission the permission checked
     * @param granted    whether the permission was granted
     */
    CompletableFuture<Void> recordPermissionCheck(@NotNull String permission, boolean granted);

    /**
     * Bulk updates permission check counters (for flush operations).
     *
     * @param checks map of permission -> [checkCount, grantCount, denyCount]
     */
    CompletableFuture<Void> bulkUpdatePermissionChecks(@NotNull Map<String, long[]> checks);

    /**
     * Gets statistics for a specific permission.
     *
     * @param permission the permission
     * @return the stats, or null if not tracked
     */
    CompletableFuture<AnalyticsSummary.PermissionStats> getPermissionStats(@NotNull String permission);

    /**
     * Gets the most frequently checked permissions.
     *
     * @param limit maximum number to return
     * @return list of permission stats sorted by check count (descending)
     */
    CompletableFuture<List<AnalyticsSummary.PermissionStats>> getHotspots(int limit);

    /**
     * Gets permissions that haven't been checked in a while.
     *
     * @param daysSinceLastCheck minimum days since last check
     * @return list of permission names
     */
    CompletableFuture<List<String>> getUnusedPermissions(int daysSinceLastCheck);

    /**
     * Gets a summary of all permission check analytics.
     *
     * @return the summary
     */
    CompletableFuture<AnalyticsSummary> getSummary();

    // ==================== Audit Log ====================

    /**
     * Records a permission change in the audit log.
     *
     * @param holderType "user" or "group"
     * @param holderId   UUID for users, group name for groups
     * @param action     the action type (ADD, REMOVE, UPDATE, CLEAR, EXPIRE)
     * @param permission the permission affected (may be null for CLEAR)
     * @param oldValue   the old permission value (may be null)
     * @param newValue   the new permission value (may be null)
     * @param changedBy  who made the change (may be null for system actions)
     * @param source     where the change came from (command, api, web-editor, template)
     */
    CompletableFuture<Void> recordAuditEntry(
            @NotNull String holderType,
            @NotNull String holderId,
            @NotNull String action,
            @Nullable String permission,
            @Nullable Boolean oldValue,
            @Nullable Boolean newValue,
            @Nullable String changedBy,
            @NotNull String source
    );

    /**
     * Gets audit log entries for a specific holder.
     *
     * @param holderType "user" or "group"
     * @param holderId   the holder identifier
     * @param limit      maximum entries to return
     * @return list of audit entries (newest first)
     */
    CompletableFuture<List<AnalyticsSummary.AuditEntry>> getAuditLog(
            @NotNull String holderType,
            @NotNull String holderId,
            int limit
    );

    /**
     * Gets recent audit log entries.
     *
     * @param limit maximum entries to return
     * @return list of audit entries (newest first)
     */
    CompletableFuture<List<AnalyticsSummary.AuditEntry>> getRecentAuditLog(int limit);

    /**
     * Gets audit log entries within a time range.
     *
     * @param startMs start timestamp (inclusive)
     * @param endMs   end timestamp (exclusive)
     * @param limit   maximum entries to return
     * @return list of audit entries (newest first)
     */
    CompletableFuture<List<AnalyticsSummary.AuditEntry>> getAuditLogInRange(
            long startMs, long endMs, int limit
    );

    // ==================== Maintenance ====================

    /**
     * Deletes old data based on retention policy.
     *
     * @param retentionDays days to retain data
     * @return number of records deleted
     */
    CompletableFuture<Integer> cleanupOldData(int retentionDays);

    /**
     * Exports analytics data to a file.
     *
     * @param format   the export format (json or csv)
     * @param filePath the output file path
     * @return true if successful
     */
    CompletableFuture<Boolean> export(@NotNull String format, @NotNull String filePath);
}
