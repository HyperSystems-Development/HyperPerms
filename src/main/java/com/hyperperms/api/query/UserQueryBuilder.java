package com.hyperperms.api.query;

import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for querying users based on various criteria.
 * <p>
 * Example usage:
 * <pre>
 * Set&lt;UUID&gt; admins = api.getQuery().queryUsers()
 *     .inGroup("admin")
 *     .withPermission("hyperperms.admin")
 *     .execute()
 *     .join();
 * </pre>
 */
public interface UserQueryBuilder {

    /**
     * Filters users who have the specified permission.
     *
     * @param permission the exact permission to match
     * @return this builder
     */
    @NotNull
    UserQueryBuilder withPermission(@NotNull String permission);

    /**
     * Filters users who have a permission matching the pattern.
     * <p>
     * Supports wildcard patterns like "admin.*" or "*.teleport".
     *
     * @param pattern the permission pattern
     * @return this builder
     */
    @NotNull
    UserQueryBuilder withPermissionPattern(@NotNull String pattern);

    /**
     * Filters users who are members of the specified group.
     *
     * @param groupName the group name
     * @return this builder
     */
    @NotNull
    UserQueryBuilder inGroup(@NotNull String groupName);

    /**
     * Filters users who are members of any of the specified groups.
     *
     * @param groupNames the group names
     * @return this builder
     */
    @NotNull
    UserQueryBuilder inAnyGroup(@NotNull String... groupNames);

    /**
     * Filters users whose primary group is the specified group.
     *
     * @param groupName the group name
     * @return this builder
     */
    @NotNull
    UserQueryBuilder withPrimaryGroup(@NotNull String groupName);

    /**
     * Filters users who have a permission in the specified context.
     *
     * @param key   the context key
     * @param value the context value
     * @return this builder
     */
    @NotNull
    UserQueryBuilder withContext(@NotNull String key, @NotNull String value);

    /**
     * Limits the number of results.
     *
     * @param limit the maximum number of results
     * @return this builder
     */
    @NotNull
    UserQueryBuilder limit(int limit);

    /**
     * Skips the first N results.
     *
     * @param offset the number of results to skip
     * @return this builder
     */
    @NotNull
    UserQueryBuilder offset(int offset);

    /**
     * Executes the query and returns the matching user UUIDs.
     *
     * @return a future that completes with the set of matching UUIDs
     */
    @NotNull
    CompletableFuture<Set<UUID>> execute();

    /**
     * Executes the query and loads the matching users.
     *
     * @return a future that completes with the list of matching users
     */
    @NotNull
    CompletableFuture<List<User>> executeAndLoad();

    /**
     * Counts the number of users matching the criteria.
     *
     * @return a future that completes with the count
     */
    @NotNull
    CompletableFuture<Integer> count();
}
