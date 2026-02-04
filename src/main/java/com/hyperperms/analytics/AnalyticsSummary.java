package com.hyperperms.analytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Summary data model for analytics reports.
 */
public final class AnalyticsSummary {

    private final long totalChecks;
    private final long totalGrants;
    private final long totalDenies;
    private final int uniquePermissions;
    private final int uniqueUsers;
    private final long periodStartMs;
    private final long periodEndMs;

    public AnalyticsSummary(
            long totalChecks,
            long totalGrants,
            long totalDenies,
            int uniquePermissions,
            int uniqueUsers,
            long periodStartMs,
            long periodEndMs
    ) {
        this.totalChecks = totalChecks;
        this.totalGrants = totalGrants;
        this.totalDenies = totalDenies;
        this.uniquePermissions = uniquePermissions;
        this.uniqueUsers = uniqueUsers;
        this.periodStartMs = periodStartMs;
        this.periodEndMs = periodEndMs;
    }

    public long getTotalChecks() {
        return totalChecks;
    }

    public long getTotalGrants() {
        return totalGrants;
    }

    public long getTotalDenies() {
        return totalDenies;
    }

    public int getUniquePermissions() {
        return uniquePermissions;
    }

    public int getUniqueUsers() {
        return uniqueUsers;
    }

    public long getPeriodStartMs() {
        return periodStartMs;
    }

    public long getPeriodEndMs() {
        return periodEndMs;
    }

    /**
     * Gets the grant rate as a percentage.
     */
    public double getGrantRate() {
        if (totalChecks == 0) return 0.0;
        return (double) totalGrants / totalChecks * 100.0;
    }

    /**
     * Permission check statistics.
     */
    public record PermissionStats(
            @NotNull String permission,
            long checkCount,
            long grantCount,
            long denyCount,
            long lastCheckedMs
    ) {
        /**
         * Gets the grant rate as a percentage.
         */
        public double getGrantRate() {
            if (checkCount == 0) return 0.0;
            return (double) grantCount / checkCount * 100.0;
        }
    }

    /**
     * Audit log entry.
     */
    public record AuditEntry(
            long id,
            long timestamp,
            @NotNull String holderType,
            @NotNull String holderId,
            @NotNull String action,
            @Nullable String permission,
            @Nullable Boolean oldValue,
            @Nullable Boolean newValue,
            @Nullable String executor,
            @NotNull String source
    ) {
        /**
         * Gets a human-readable description of the change.
         */
        @NotNull
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append(action).append(" ");
            if (permission != null) {
                sb.append(permission);
            }
            if (holderType.equals("group")) {
                sb.append(" on group ").append(holderId);
            } else {
                sb.append(" on user ").append(holderId);
            }
            if (executor != null && !executor.isEmpty()) {
                sb.append(" by ").append(executor);
            }
            return sb.toString();
        }
    }

    /**
     * Report of unused permissions.
     */
    public record UnusedPermissionsReport(
            @NotNull List<String> unusedPermissions,
            int daysSinceLastCheck,
            long reportGeneratedMs
    ) {}

    /**
     * Report of hotspot (frequently checked) permissions.
     */
    public record HotspotsReport(
            @NotNull List<PermissionStats> hotspots,
            long reportGeneratedMs
    ) {}
}
