package com.hyperperms.api.metrics;

/**
 * Represents a frequently checked permission.
 *
 * @param permission the permission string
 * @param checkCount number of times this permission was checked
 * @param grantCount number of times this permission was granted
 * @param grantRate  percentage of checks that were granted
 */
public record PermissionHotspot(
        String permission,
        long checkCount,
        long grantCount,
        double grantRate
) {
    /**
     * Gets the deny count.
     *
     * @return the number of times this permission was denied
     */
    public long getDenyCount() {
        return checkCount - grantCount;
    }

    /**
     * Gets the deny rate.
     *
     * @return the percentage of checks that were denied
     */
    public double getDenyRate() {
        return 100.0 - grantRate;
    }
}
