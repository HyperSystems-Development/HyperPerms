package com.hyperperms.api.query;

import com.hyperperms.model.Group;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Fluent builder for querying groups based on various criteria.
 * <p>
 * Example usage:
 * <pre>
 * Set&lt;String&gt; staffGroups = api.getQuery().queryGroups()
 *     .withWeightBetween(50, 100)
 *     .withPrefix()
 *     .execute();
 * </pre>
 */
public interface GroupQueryBuilder {

    /**
     * Filters groups that have the specified permission.
     *
     * @param permission the exact permission to match
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder withPermission(@NotNull String permission);

    /**
     * Filters groups with weight in the specified range.
     *
     * @param min the minimum weight (inclusive)
     * @param max the maximum weight (inclusive)
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder withWeightBetween(int min, int max);

    /**
     * Filters groups that inherit from the specified parent group.
     *
     * @param parentGroup the parent group name
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder inheritsFrom(@NotNull String parentGroup);

    /**
     * Filters groups that have a prefix set.
     *
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder withPrefix();

    /**
     * Filters groups that have a suffix set.
     *
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder withSuffix();

    /**
     * Filters groups that are on the specified track.
     *
     * @param trackName the track name
     * @return this builder
     */
    @NotNull
    GroupQueryBuilder onTrack(@NotNull String trackName);

    /**
     * Executes the query and returns the matching group names.
     *
     * @return the set of matching group names
     */
    @NotNull
    Set<String> execute();

    /**
     * Executes the query and returns the matching groups.
     *
     * @return the set of matching groups
     */
    @NotNull
    Set<Group> executeAndLoad();

    /**
     * Counts the number of groups matching the criteria.
     *
     * @return the count
     */
    int count();
}
