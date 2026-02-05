package com.hyperperms.api.metrics;

/**
 * Statistics about permission checks.
 *
 * @param totalChecks   total number of permission checks performed
 * @param grantedCount  number of checks that returned true (permission granted)
 * @param deniedCount   number of checks that returned false (permission denied)
 * @param undefinedCount number of checks that returned undefined
 */
public record PermissionCheckStats(
        long totalChecks,
        long grantedCount,
        long deniedCount,
        long undefinedCount
) {
    /**
     * Gets the grant rate as a percentage.
     *
     * @return the percentage of checks that were granted
     */
    public double getGrantRate() {
        return totalChecks > 0 ? (double) grantedCount / totalChecks * 100.0 : 0.0;
    }

    /**
     * Gets the deny rate as a percentage.
     *
     * @return the percentage of checks that were denied
     */
    public double getDenyRate() {
        return totalChecks > 0 ? (double) deniedCount / totalChecks * 100.0 : 0.0;
    }
}
