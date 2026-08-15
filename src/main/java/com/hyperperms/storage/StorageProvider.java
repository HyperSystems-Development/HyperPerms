package com.hyperperms.storage;

import com.hyperperms.model.Group;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for permission data storage backends.
 * <p>
 * All operations should be non-blocking where possible. Implementations
 * should handle their own threading and connection management.
 */
public interface StorageProvider {

    /**
     * Gets the name of this storage provider.
     *
     * @return the provider name
     */
    @NotNull
    String getName();

    /**
     * Initializes the storage provider.
     * Called once during plugin startup.
     *
     * @return a future that completes when initialization is done
     */
    CompletableFuture<Void> init();

    /**
     * Shuts down the storage provider.
     * Called once during plugin shutdown.
     *
     * @return a future that completes when shutdown is done
     */
    CompletableFuture<Void> shutdown();

    // ==================== User Operations ====================

    /**
     * Loads a user from storage.
     *
     * @param uuid the user's UUID
     * @return the user, or empty if not found
     */
    CompletableFuture<Optional<User>> loadUser(@NotNull UUID uuid);

    /**
     * Saves a user to storage.
     *
     * @param user the user to save
     * @return a future that completes when saved
     */
    CompletableFuture<Void> saveUser(@NotNull User user);

    /**
     * Deletes a user from storage.
     *
     * @param uuid the user's UUID
     * @return a future that completes when deleted
     */
    CompletableFuture<Void> deleteUser(@NotNull UUID uuid);

    /**
     * Loads all users from storage.
     *
     * @return a map of UUID to User
     */
    CompletableFuture<Map<UUID, User>> loadAllUsers();

    /**
     * Gets the UUIDs of all stored users.
     *
     * @return the set of UUIDs
     */
    CompletableFuture<Set<UUID>> getUserUuids();

    /**
     * Looks up a user's UUID by their username.
     *
     * @param username the username
     * @return the UUID, or empty if not found
     */
    CompletableFuture<Optional<UUID>> lookupUuid(@NotNull String username);

    /**
     * Finds every stored user who holds the given permission as a <em>direct</em> grant of their
     * own: a node stored on the user, with a positive value, that has not expired.
     * <p>
     * Deliberately narrow. A user who resolves the permission through a group, a track, or a
     * wildcard is <b>not</b> returned, because the grant belongs to the group rather than to the
     * user. This matches the contract of Hytale's
     * {@code PermissionProvider#getUsersWithPermission}, which backs {@code /whitelist list} and
     * {@code /whitelist clear}: revoking a group's grant from a user would not stick, so
     * reporting them as revoked would be a lie.
     * <p>
     * Contexts are ignored — a node that applies only in some contexts still counts as held.
     *
     * @param permission the exact permission node, matched literally (no wildcard expansion)
     * @return the UUIDs of users carrying the node directly; empty if none do
     */
    CompletableFuture<Set<UUID>> findUsersWithNode(@NotNull String permission);

    // ==================== Group Operations ====================

    /**
     * Loads a group from storage.
     *
     * @param name the group name
     * @return the group, or empty if not found
     */
    CompletableFuture<Optional<Group>> loadGroup(@NotNull String name);

    /**
     * Saves a group to storage.
     *
     * @param group the group to save
     * @return a future that completes when saved
     */
    CompletableFuture<Void> saveGroup(@NotNull Group group);

    /**
     * Deletes a group from storage.
     *
     * @param name the group name
     * @return a future that completes when deleted
     */
    CompletableFuture<Void> deleteGroup(@NotNull String name);

    /**
     * Loads all groups from storage.
     *
     * @return a map of name to Group
     */
    CompletableFuture<Map<String, Group>> loadAllGroups();

    /**
     * Gets the names of all stored groups.
     *
     * @return the set of group names
     */
    CompletableFuture<Set<String>> getGroupNames();

    // ==================== Track Operations ====================

    /**
     * Loads a track from storage.
     *
     * @param name the track name
     * @return the track, or empty if not found
     */
    CompletableFuture<Optional<Track>> loadTrack(@NotNull String name);

    /**
     * Saves a track to storage.
     *
     * @param track the track to save
     * @return a future that completes when saved
     */
    CompletableFuture<Void> saveTrack(@NotNull Track track);

    /**
     * Deletes a track from storage.
     *
     * @param name the track name
     * @return a future that completes when deleted
     */
    CompletableFuture<Void> deleteTrack(@NotNull String name);

    /**
     * Loads all tracks from storage.
     *
     * @return a map of name to Track
     */
    CompletableFuture<Map<String, Track>> loadAllTracks();

    /**
     * Gets the names of all stored tracks.
     *
     * @return the set of track names
     */
    CompletableFuture<Set<String>> getTrackNames();

    // ==================== Bulk Operations ====================

    /**
     * Saves all pending data.
     *
     * @return a future that completes when all data is saved
     */
    CompletableFuture<Void> saveAll();

    /**
     * Checks if the storage is functioning properly.
     *
     * @return true if healthy
     */
    boolean isHealthy();

    // ==================== Backup Operations ====================

    /**
     * Creates a backup with the specified name.
     *
     * @param name the backup name, or null for auto-generated name
     * @return a future that completes with the backup name
     */
    CompletableFuture<String> createBackup(@Nullable String name);

    /**
     * Restores from a backup.
     *
     * @param name the backup name to restore
     * @return a future that completes with true if successful
     */
    CompletableFuture<Boolean> restoreBackup(@NotNull String name);

    /**
     * Lists all available backups.
     *
     * @return a future that completes with the list of backup names (newest first)
     */
    CompletableFuture<java.util.List<String>> listBackups();

    /**
     * Deletes a backup.
     *
     * @param name the backup name to delete
     * @return a future that completes with true if deleted
     */
    CompletableFuture<Boolean> deleteBackup(@NotNull String name);

    /**
     * Gets the type/name of this storage provider.
     *
     * @return the storage type (e.g., "json", "sqlite")
     */
    @NotNull
    String getType();
}
