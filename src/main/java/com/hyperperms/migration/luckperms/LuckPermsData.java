package com.hyperperms.migration.luckperms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Data models for LuckPerms permission data.
 * <p>
 * These models represent the LuckPerms data format before transformation
 * to HyperPerms format.
 */
public final class LuckPermsData {

    private LuckPermsData() {}

    /**
     * Represents a LuckPerms permission node.
     *
     * @param permission the permission string
     * @param value      the value (true = grant, false = deny)
     * @param expiry     unix timestamp when this permission expires, or 0 for permanent
     * @param contexts   context restrictions for this permission
     */
    public record LPNode(
        @NotNull String permission,
        boolean value,
        long expiry,
        @NotNull Map<String, String> contexts
    ) {
        /**
         * Creates a simple permanent granted permission.
         */
        public LPNode(@NotNull String permission) {
            this(permission, true, 0, Collections.emptyMap());
        }

        /**
         * Checks if this is a group inheritance node.
         */
        public boolean isGroupNode() {
            return permission.startsWith("group.");
        }

        /**
         * Gets the group name if this is a group node.
         */
        @Nullable
        public String getGroupName() {
            if (!isGroupNode()) return null;
            return permission.substring("group.".length());
        }

        /**
         * Checks if this permission has expired.
         */
        public boolean isExpired() {
            return expiry > 0 && System.currentTimeMillis() / 1000 > expiry;
        }

        /**
         * Checks if this is a temporary permission.
         */
        public boolean isTemporary() {
            return expiry > 0;
        }

        /**
         * Checks if this permission has context restrictions.
         */
        public boolean hasContexts() {
            return !contexts.isEmpty();
        }
    }

    /**
     * Represents a LuckPerms group.
     *
     * @param name           the group name (lowercase identifier)
     * @param weight         the group weight (higher = more priority)
     * @param prefix         the group's prefix for chat display
     * @param suffix         the group's suffix for chat display
     * @param prefixPriority priority for prefix (higher = preferred)
     * @param suffixPriority priority for suffix (higher = preferred)
     * @param nodes          all permission nodes for this group
     * @param parents        parent groups this group inherits from (extracted from nodes)
     */
    public record LPGroup(
        @NotNull String name,
        int weight,
        @Nullable String prefix,
        @Nullable String suffix,
        int prefixPriority,
        int suffixPriority,
        @NotNull List<LPNode> nodes,
        @NotNull Set<String> parents
    ) {
        /**
         * Gets non-group permission nodes.
         */
        public List<LPNode> getPermissionNodes() {
            return nodes.stream()
                .filter(n -> !n.isGroupNode())
                .toList();
        }

        /**
         * Gets the count of non-group permission nodes.
         */
        public int getPermissionCount() {
            return (int) nodes.stream()
                .filter(n -> !n.isGroupNode())
                .count();
        }
    }

    /**
     * Represents a LuckPerms user.
     *
     * @param uuid         the user's UUID
     * @param username     the user's last known username
     * @param primaryGroup the user's primary group
     * @param nodes        all permission nodes for this user
     */
    public record LPUser(
        @NotNull UUID uuid,
        @Nullable String username,
        @NotNull String primaryGroup,
        @NotNull List<LPNode> nodes
    ) {
        /**
         * Gets non-group permission nodes.
         */
        public List<LPNode> getPermissionNodes() {
            return nodes.stream()
                .filter(n -> !n.isGroupNode())
                .toList();
        }

        /**
         * Gets the groups this user belongs to (extracted from nodes).
         */
        public Set<String> getGroups() {
            Set<String> groups = new LinkedHashSet<>();
            for (LPNode node : nodes) {
                if (node.isGroupNode() && node.value() && !node.isExpired()) {
                    String groupName = node.getGroupName();
                    if (groupName != null) {
                        groups.add(groupName);
                    }
                }
            }
            return groups;
        }

        /**
         * Checks if this user has custom permissions beyond group membership.
         */
        public boolean hasCustomPermissions() {
            return nodes.stream().anyMatch(n -> !n.isGroupNode());
        }
    }

    /**
     * Represents a LuckPerms track (promotion ladder).
     *
     * @param name   the track name
     * @param groups ordered list of groups in the track
     */
    public record LPTrack(
        @NotNull String name,
        @NotNull List<String> groups
    ) {}

    /**
     * Container for all LuckPerms data.
     *
     * @param groups the groups keyed by name
     * @param users  the users keyed by UUID
     * @param tracks the tracks keyed by name
     */
    public record LPDataSet(
        @NotNull Map<String, LPGroup> groups,
        @NotNull Map<UUID, LPUser> users,
        @NotNull Map<String, LPTrack> tracks
    ) {
        public static LPDataSet empty() {
            return new LPDataSet(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
            );
        }
    }
}
