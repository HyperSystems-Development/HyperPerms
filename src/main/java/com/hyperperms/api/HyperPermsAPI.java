package com.hyperperms.api;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.api.events.EventBus;
import com.hyperperms.model.Group;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Main API interface for HyperPerms.
 * <p>
 * Obtain an instance using {@code HyperPerms.getApi()}.
 */
public interface HyperPermsAPI {

    // ==================== Permission Checks ====================

    /**
     * Checks if a user has a permission.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @return true if the user has the permission
     */
    boolean hasPermission(@NotNull UUID uuid, @NotNull String permission);

    /**
     * Checks if a user has a permission in a specific context.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @param contexts   the contexts to check in
     * @return true if the user has the permission
     */
    boolean hasPermission(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts);

    // ==================== Async Permission Checks ====================

    /**
     * Asynchronously checks if a user has a permission.
     * <p>
     * This method is useful for checking permissions off the main thread.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @return a future that completes with true if the user has the permission
     */
    @NotNull
    default CompletableFuture<Boolean> hasPermissionAsync(@NotNull UUID uuid, @NotNull String permission) {
        return hasPermissionAsync(uuid, permission, ContextSet.empty());
    }

    /**
     * Asynchronously checks if a user has a permission in a specific context.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @param contexts   the contexts to check in
     * @return a future that completes with true if the user has the permission
     */
    @NotNull
    CompletableFuture<Boolean> hasPermissionAsync(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts);

    /**
     * Gets the raw permission value as a TriState.
     * <p>
     * This allows distinguishing between "denied" (FALSE) and "not set" (UNDEFINED).
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @return the permission value as a TriState
     */
    @NotNull
    default TriState getPermissionValue(@NotNull UUID uuid, @NotNull String permission) {
        return getPermissionValue(uuid, permission, ContextSet.empty());
    }

    /**
     * Gets the raw permission value as a TriState in a specific context.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @param contexts   the contexts to check in
     * @return the permission value as a TriState
     */
    @NotNull
    TriState getPermissionValue(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts);

    /**
     * Asynchronously gets the raw permission value as a TriState.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @return a future that completes with the permission value
     */
    @NotNull
    default CompletableFuture<TriState> getPermissionValueAsync(@NotNull UUID uuid, @NotNull String permission) {
        return getPermissionValueAsync(uuid, permission, ContextSet.empty());
    }

    /**
     * Asynchronously gets the raw permission value as a TriState in a specific context.
     *
     * @param uuid       the user UUID
     * @param permission the permission to check
     * @param contexts   the contexts to check in
     * @return a future that completes with the permission value
     */
    @NotNull
    CompletableFuture<TriState> getPermissionValueAsync(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts);

    /**
     * Creates a fluent async permission check builder.
     * <p>
     * Example usage:
     * <pre>
     * api.checkAsync(uuid)
     *    .permission("build.place")
     *    .inWorld("nether")
     *    .result()
     *    .thenAccept(canBuild -> { ... });
     * </pre>
     *
     * @param uuid the user UUID
     * @return a new async permission check builder
     */
    @NotNull
    AsyncPermissionCheckBuilder checkAsync(@NotNull UUID uuid);

    /**
     * Gets the executor used for synchronous callbacks.
     * <p>
     * This executor runs tasks on the main thread (if applicable).
     *
     * @return the sync executor
     */
    @NotNull
    Executor getSyncExecutor();

    // ==================== User Management ====================

    /**
     * Gets the user manager.
     *
     * @return the user manager
     */
    @NotNull
    UserManager getUserManager();

    /**
     * Gets the group manager.
     *
     * @return the group manager
     */
    @NotNull
    GroupManager getGroupManager();

    /**
     * Gets the track manager.
     *
     * @return the track manager
     */
    @NotNull
    TrackManager getTrackManager();

    // ==================== Events ====================

    /**
     * Gets the event bus for subscribing to events.
     *
     * @return the event bus
     */
    @NotNull
    EventBus getEventBus();

    // ==================== Permission Enumeration ====================

    /**
     * Gets all resolved, granted permission strings for a user.
     * <p>
     * Includes permissions from direct nodes and group inheritance.
     * Useful for plugins that need to scan permissions matching a prefix
     * (e.g., {@code "essentialsplus.sethome.limit."} to find numeric limits).
     *
     * @param uuid the user UUID
     * @return the set of granted permission strings
     */
    @NotNull
    Set<String> getResolvedPermissions(@NotNull UUID uuid);

    // ==================== Context ====================

    /**
     * Gets the current context for a user.
     *
     * @param uuid the user UUID
     * @return the current contexts
     */
    @NotNull
    ContextSet getContexts(@NotNull UUID uuid);

    // ==================== Query API ====================

    /**
     * Gets the Query API for searching users and groups.
     *
     * @return the query API
     */
    @NotNull
    QueryAPI getQuery();

    // ==================== Metrics API ====================

    /**
     * Gets the Metrics API for accessing analytics and statistics.
     * <p>
     * Returns null if analytics is disabled.
     *
     * @return the metrics API, or null if unavailable
     */
    @Nullable
    MetricsAPI getMetrics();

    // ==================== User Manager Interface ====================

    /**
     * Manager for user permission data.
     */
    interface UserManager {

        /**
         * Gets a user, loading from storage if necessary.
         *
         * @param uuid the user UUID
         * @return the user, or empty if not found
         */
        CompletableFuture<Optional<User>> loadUser(@NotNull UUID uuid);

        /**
         * Gets a cached user, or null if not loaded.
         *
         * @param uuid the user UUID
         * @return the user, or null
         */
        @Nullable
        User getUser(@NotNull UUID uuid);

        /**
         * Gets or creates a user.
         *
         * @param uuid the user UUID
         * @return the user (never null)
         */
        @NotNull
        User getOrCreateUser(@NotNull UUID uuid);

        /**
         * Saves a user to storage.
         *
         * @param user the user to save
         * @return a future that completes when saved
         */
        CompletableFuture<Void> saveUser(@NotNull User user);

        /**
         * Modifies a user and saves the changes.
         *
         * @param uuid   the user UUID
         * @param action the modification to apply
         * @return a future that completes when saved
         */
        CompletableFuture<Void> modifyUser(@NotNull UUID uuid, @NotNull Consumer<User> action);

        /**
         * Gets all loaded users.
         *
         * @return set of loaded users
         */
        @NotNull
        Set<User> getLoadedUsers();

        /**
         * Checks if a user is loaded.
         *
         * @param uuid the user UUID
         * @return true if loaded
         */
        boolean isLoaded(@NotNull UUID uuid);

        /**
         * Unloads a user from cache.
         *
         * @param uuid the user UUID
         */
        void unload(@NotNull UUID uuid);

        // ==================== Bulk Operations ====================

        /**
         * Loads multiple users from storage.
         *
         * @param uuids the UUIDs to load
         * @return a future that completes with a map of loaded users
         */
        @NotNull
        default CompletableFuture<Map<UUID, User>> loadUsers(@NotNull Collection<UUID> uuids) {
            throw new UnsupportedOperationException("Bulk load not supported by this implementation");
        }

        /**
         * Saves multiple users to storage.
         *
         * @param users the users to save
         * @return a future that completes when all users are saved
         */
        @NotNull
        default CompletableFuture<Void> saveUsers(@NotNull Collection<User> users) {
            throw new UnsupportedOperationException("Bulk save not supported by this implementation");
        }

        /**
         * Applies a modification to multiple users and saves them.
         *
         * @param uuids  the UUIDs to modify
         * @param action the modification to apply
         * @return a future that completes when all users are saved
         */
        @NotNull
        default CompletableFuture<Void> batchModify(@NotNull Collection<UUID> uuids, @NotNull Consumer<User> action) {
            throw new UnsupportedOperationException("Batch modify not supported by this implementation");
        }

        /**
         * Gets all known user UUIDs from storage.
         *
         * @return a future that completes with the set of all known UUIDs
         */
        @NotNull
        default CompletableFuture<Set<UUID>> getAllKnownUUIDs() {
            throw new UnsupportedOperationException("Get all UUIDs not supported by this implementation");
        }
    }

    // ==================== Group Manager Interface ====================

    /**
     * Manager for permission groups.
     */
    interface GroupManager {

        /**
         * Gets a group by name.
         *
         * @param name the group name
         * @return the group, or null if not found
         */
        @Nullable
        Group getGroup(@NotNull String name);

        /**
         * Gets a group, loading from storage if necessary.
         *
         * @param name the group name
         * @return the group, or empty if not found
         */
        CompletableFuture<Optional<Group>> loadGroup(@NotNull String name);

        /**
         * Creates a new group.
         *
         * @param name the group name
         * @return the created group
         * @throws IllegalArgumentException if the group already exists
         */
        @NotNull
        Group createGroup(@NotNull String name);

        /**
         * Deletes a group.
         *
         * @param name the group name
         * @return a future that completes when deleted
         */
        CompletableFuture<Void> deleteGroup(@NotNull String name);

        /**
         * Saves a group to storage.
         *
         * @param group the group to save
         * @return a future that completes when saved
         */
        CompletableFuture<Void> saveGroup(@NotNull Group group);

        /**
         * Modifies a group and saves the changes.
         *
         * @param name   the group name
         * @param action the modification to apply
         * @return a future that completes when saved
         */
        CompletableFuture<Void> modifyGroup(@NotNull String name, @NotNull Consumer<Group> action);

        /**
         * Gets all loaded groups.
         *
         * @return set of loaded groups
         */
        @NotNull
        Set<Group> getLoadedGroups();

        /**
         * Gets the names of all groups.
         *
         * @return set of group names
         */
        @NotNull
        Set<String> getGroupNames();

        // ==================== Bulk Operations ====================

        /**
         * Loads all groups from storage.
         *
         * @return a future that completes when loaded
         */
        @NotNull
        default CompletableFuture<Void> loadAll() {
            throw new UnsupportedOperationException("Load all not supported by this implementation");
        }

        /**
         * Saves all groups to storage.
         *
         * @return a future that completes when saved
         */
        @NotNull
        default CompletableFuture<Void> saveAll() {
            throw new UnsupportedOperationException("Save all not supported by this implementation");
        }
    }

    // ==================== Track Manager Interface ====================

    /**
     * Manager for promotion tracks.
     */
    interface TrackManager {

        /**
         * Gets a track by name.
         *
         * @param name the track name
         * @return the track, or null if not found
         */
        @Nullable
        Track getTrack(@NotNull String name);

        /**
         * Gets a track, loading from storage if necessary.
         *
         * @param name the track name
         * @return the track, or empty if not found
         */
        CompletableFuture<Optional<Track>> loadTrack(@NotNull String name);

        /**
         * Creates a new track.
         *
         * @param name the track name
         * @return the created track
         * @throws IllegalArgumentException if the track already exists
         */
        @NotNull
        Track createTrack(@NotNull String name);

        /**
         * Deletes a track.
         *
         * @param name the track name
         * @return a future that completes when deleted
         */
        CompletableFuture<Void> deleteTrack(@NotNull String name);

        /**
         * Saves a track to storage.
         *
         * @param track the track to save
         * @return a future that completes when saved
         */
        CompletableFuture<Void> saveTrack(@NotNull Track track);

        /**
         * Gets all loaded tracks.
         *
         * @return set of loaded tracks
         */
        @NotNull
        Set<Track> getLoadedTracks();

        /**
         * Gets the names of all tracks.
         *
         * @return set of track names
         */
        @NotNull
        Set<String> getTrackNames();
    }
}
