package com.hyperperms.api;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an entity that can hold permissions.
 * <p>
 * This interface is implemented by both {@link com.hyperperms.model.User} and
 * {@link com.hyperperms.model.Group}.
 */
public interface PermissionHolder {

    /**
     * Gets the unique identifier for this holder.
     *
     * @return the identifier (UUID string for users, name for groups)
     */
    @NotNull
    String getIdentifier();

    /**
     * Gets the display-friendly name of this holder.
     *
     * @return the friendly name
     */
    @NotNull
    String getFriendlyName();

    /**
     * Gets all permission nodes directly assigned to this holder.
     * Does not include inherited permissions.
     *
     * @return an unmodifiable set of nodes
     */
    @NotNull
    Set<Node> getNodes();

    /**
     * Gets all permission nodes that match the given contexts.
     *
     * @param contexts the contexts to match
     * @return nodes that apply in the given context
     */
    @NotNull
    Set<Node> getNodes(@NotNull ContextSet contexts);

    /**
     * Gets all permission nodes, optionally filtering out expired nodes.
     *
     * @param includeExpired whether to include expired nodes
     * @return the set of nodes
     */
    @NotNull
    Set<Node> getNodes(boolean includeExpired);

    /**
     * Checks if this holder has a specific node.
     *
     * @param node the node to check
     * @return true if the exact node exists
     */
    boolean hasNode(@NotNull Node node);

    /**
     * Adds a node to this holder.
     *
     * @param node the node to add
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult addNode(@NotNull Node node);

    /**
     * Removes a node from this holder.
     *
     * @param node the node to remove
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult removeNode(@NotNull Node node);

    /**
     * Removes all nodes matching the given permission.
     *
     * @param permission the permission to match
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult removeNode(@NotNull String permission);

    /**
     * Sets a node, replacing any existing node with the same permission and contexts.
     *
     * @param node the node to set
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult setNode(@NotNull Node node);

    /**
     * Clears all nodes from this holder.
     */
    void clearNodes();

    /**
     * Clears all nodes in the given context.
     *
     * @param contexts the contexts to match
     */
    void clearNodes(@NotNull ContextSet contexts);

    /**
     * Gets the names of all groups this holder directly inherits from.
     *
     * @return the group names
     */
    @NotNull
    Set<String> getInheritedGroups();

    /**
     * Gets the names of all groups this holder inherits from in the given context.
     *
     * @param contexts the contexts to match
     * @return the group names
     */
    @NotNull
    Set<String> getInheritedGroups(@NotNull ContextSet contexts);

    /**
     * Adds a group to inherit from.
     *
     * @param groupName the group name
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult addGroup(@NotNull String groupName);

    /**
     * Removes a group inheritance.
     *
     * @param groupName the group name
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult removeGroup(@NotNull String groupName);

    /**
     * Removes all expired nodes from this holder.
     *
     * @return the number of nodes removed
     */
    int cleanupExpired();

    // ==================== Temporary Permission Methods ====================

    /**
     * Sets a permission with a duration-based expiry.
     *
     * @param permission the permission string
     * @param value      true to grant, false to deny
     * @param duration   the duration until the permission expires
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult setPermission(@NotNull String permission, boolean value, @NotNull Duration duration);

    /**
     * Sets a permission with an instant-based expiry.
     *
     * @param permission the permission string
     * @param value      true to grant, false to deny
     * @param expiry     the instant when the permission expires
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult setPermission(@NotNull String permission, boolean value, @NotNull Instant expiry);

    /**
     * Sets a granted permission with a duration-based expiry.
     *
     * @param permission the permission string
     * @param duration   the duration until the permission expires
     * @return the result of the operation
     */
    @NotNull
    default DataMutateResult setPermission(@NotNull String permission, @NotNull Duration duration) {
        return setPermission(permission, true, duration);
    }

    /**
     * Sets a granted permission with an instant-based expiry.
     *
     * @param permission the permission string
     * @param expiry     the instant when the permission expires
     * @return the result of the operation
     */
    @NotNull
    default DataMutateResult setPermission(@NotNull String permission, @NotNull Instant expiry) {
        return setPermission(permission, true, expiry);
    }

    /**
     * Checks if a permission is temporary (has an expiry time and is not yet expired).
     *
     * @param permission the permission to check
     * @return true if the permission is temporary
     */
    boolean isTemporaryPermission(@NotNull String permission);

    /**
     * Gets the expiry instant for a permission.
     *
     * @param permission the permission to check
     * @return the expiry instant, or null if permanent or not found
     */
    @Nullable
    Instant getPermissionExpiry(@NotNull String permission);

    /**
     * Gets the remaining duration for a temporary permission.
     * <p>
     * Returns {@code null} if the permission is not found or is permanent.
     * Returns {@link Duration#ZERO} if the permission has already expired.
     *
     * @param permission the permission to check
     * @return the remaining duration, or null if not temporary
     */
    @Nullable
    default Duration getPermissionRemaining(@NotNull String permission) {
        Instant expiry = getPermissionExpiry(permission);
        if (expiry == null) {
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), expiry);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Sets or removes the expiry on an existing permission.
     * <p>
     * Pass {@code null} to make the permission permanent.
     *
     * @param permission the permission to modify
     * @param expiry     the new expiry instant, or null for permanent
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult setPermissionExpiry(@NotNull String permission, @Nullable Instant expiry);

    /**
     * Adjusts the expiry on an existing temporary permission by a duration.
     * <p>
     * Positive durations extend the expiry; negative durations shorten it.
     * If the permission is currently permanent, it becomes temporary with
     * an expiry of {@code Instant.now().plus(adjustment)}.
     *
     * @param permission the permission to modify
     * @param adjustment the duration to add (can be negative)
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult adjustPermissionExpiry(@NotNull String permission, @NotNull Duration adjustment);

    /**
     * Gets all non-expired temporary permissions held by this holder.
     *
     * @return an unmodifiable set of temporary permission info objects
     */
    @NotNull
    Set<TemporaryPermissionInfo> getTemporaryPermissions();

    /**
     * Adds a group membership with an expiry time.
     *
     * @param groupName the group name
     * @param expiry    the expiry time, or null for permanent
     * @return the result of the operation
     */
    @NotNull
    DataMutateResult addGroup(@NotNull String groupName, @Nullable Instant expiry);

    /**
     * Adds a group membership with a duration-based expiry.
     *
     * @param groupName the group name
     * @param duration  the duration until the group membership expires
     * @return the result of the operation
     */
    @NotNull
    default DataMutateResult addGroup(@NotNull String groupName, @NotNull Duration duration) {
        return addGroup(groupName, Instant.now().plus(duration));
    }

    /**
     * Result of a data mutation operation.
     */
    enum DataMutateResult {
        /**
         * The operation completed successfully.
         */
        SUCCESS,

        /**
         * The operation failed because the data already exists.
         */
        ALREADY_EXISTS,

        /**
         * The operation failed because the data does not exist.
         */
        DOES_NOT_EXIST,

        /**
         * The operation failed for an unspecified reason.
         */
        FAILURE
    }
}
