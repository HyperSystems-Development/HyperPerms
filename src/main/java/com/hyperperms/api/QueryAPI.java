package com.hyperperms.api;

import com.hyperperms.api.query.GroupQueryBuilder;
import com.hyperperms.api.query.UserQueryBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API for querying users and groups by various criteria.
 * <p>
 * The Query API provides powerful search capabilities for finding users and groups
 * based on permissions, group membership, and other attributes.
 * <p>
 * Example usage:
 * <pre>
 * QueryAPI query = api.getQuery();
 *
 * // Find all users with a permission
 * Set&lt;UUID&gt; users = query.findUsersWithPermission("admin.ban").join();
 *
 * // Use the fluent query builder
 * Set&lt;UUID&gt; admins = query.queryUsers()
 *     .inGroup("admin")
 *     .withPrimaryGroup("admin")
 *     .execute()
 *     .join();
 * </pre>
 */
public interface QueryAPI {

    // ==================== User Queries ====================

    /**
     * Finds all users who have the specified permission.
     *
     * @param permission the permission to search for
     * @return a future that completes with the set of matching UUIDs
     */
    @NotNull
    CompletableFuture<Set<UUID>> findUsersWithPermission(@NotNull String permission);

    /**
     * Finds all users who have a permission matching the pattern.
     * <p>
     * Supports wildcard patterns like "admin.*" or "*.teleport".
     *
     * @param pattern the permission pattern
     * @return a future that completes with the set of matching UUIDs
     */
    @NotNull
    CompletableFuture<Set<UUID>> findUsersWithPermissionPattern(@NotNull String pattern);

    /**
     * Finds all users who are members of the specified group.
     *
     * @param groupName the group name
     * @return a future that completes with the set of matching UUIDs
     */
    @NotNull
    CompletableFuture<Set<UUID>> findUsersInGroup(@NotNull String groupName);

    /**
     * Creates a fluent user query builder.
     *
     * @return a new user query builder
     */
    @NotNull
    UserQueryBuilder queryUsers();

    // ==================== Group Queries ====================

    /**
     * Finds all groups that have the specified permission.
     *
     * @param permission the permission to search for
     * @return the set of matching group names
     */
    @NotNull
    Set<String> findGroupsWithPermission(@NotNull String permission);

    /**
     * Finds all groups with weight in the specified range.
     *
     * @param min the minimum weight (inclusive)
     * @param max the maximum weight (inclusive)
     * @return the set of matching group names
     */
    @NotNull
    Set<String> findGroupsByWeight(int min, int max);

    /**
     * Creates a fluent group query builder.
     *
     * @return a new group query builder
     */
    @NotNull
    GroupQueryBuilder queryGroups();

    // ==================== Pattern Matching ====================

    /**
     * Checks if a permission matches a pattern.
     * <p>
     * Supports wildcard patterns like "admin.*" or "*.teleport".
     *
     * @param permission the permission to check
     * @param pattern    the pattern to match against
     * @return true if the permission matches the pattern
     */
    boolean matchesPattern(@NotNull String permission, @NotNull String pattern);

    /**
     * Gets all permissions from loaded users/groups that match the pattern.
     *
     * @param pattern the pattern to match
     * @return the set of matching permissions
     */
    @NotNull
    Set<String> getMatchingPermissions(@NotNull String pattern);
}
