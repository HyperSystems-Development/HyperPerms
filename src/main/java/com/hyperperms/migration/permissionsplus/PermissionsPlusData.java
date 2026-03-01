package com.hyperperms.migration.permissionsplus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Data models for PermissionsPlus permission data.
 * <p>
 * These models represent the PermissionsPlus data format before transformation
 * to HyperPerms format.
 */
public final class PermissionsPlusData {

    private PermissionsPlusData() {}

    /**
     * Represents a PermissionsPlus group.
     * <p>
     * PP groups are flat lists of permission strings with no weight,
     * prefix/suffix, or inheritance.
     *
     * @param name        the group name (case-sensitive as stored)
     * @param permissions the permission strings for this group
     */
    public record PPGroup(
        @NotNull String name,
        @NotNull List<String> permissions
    ) {
        /**
         * Gets the count of permissions.
         */
        public int getPermissionCount() {
            return permissions.size();
        }
    }

    /**
     * Represents a PermissionsPlus user.
     *
     * @param uuid        the user's UUID
     * @param username    the user's last known username (from config.json userNames map), or null
     * @param groups      the groups this user belongs to
     * @param permissions per-user individual permissions (often empty)
     */
    public record PPUser(
        @NotNull UUID uuid,
        @Nullable String username,
        @NotNull List<String> groups,
        @NotNull List<String> permissions
    ) {
        /**
         * Checks if this user has individual permissions beyond group membership.
         */
        public boolean hasCustomPermissions() {
            return !permissions.isEmpty();
        }
    }

    /**
     * PermissionsPlus configuration data from config.json.
     *
     * @param defaultGroup the default group for new players
     * @param userNames    mapping of UUID to last known username
     */
    public record PPConfig(
        @NotNull String defaultGroup,
        @NotNull Map<UUID, String> userNames
    ) {}

    /**
     * Container for all PermissionsPlus data.
     *
     * @param groups the groups keyed by name
     * @param users  the users keyed by UUID
     * @param config the plugin configuration
     */
    public record PPDataSet(
        @NotNull Map<String, PPGroup> groups,
        @NotNull Map<UUID, PPUser> users,
        @NotNull PPConfig config
    ) {
        public static PPDataSet empty() {
            return new PPDataSet(
                Collections.emptyMap(),
                Collections.emptyMap(),
                new PPConfig("default", Collections.emptyMap())
            );
        }
    }
}
