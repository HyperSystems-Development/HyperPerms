package com.hyperperms.cache;

import com.hyperperms.api.ChatAPI;
import com.hyperperms.api.TabListAPI;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages targeted cache invalidation by tracking user-group memberships.
 * <p>
 * This allows efficient invalidation when a group changes - only users
 * belonging to that group (directly or through inheritance) need to be
 * invalidated, rather than clearing the entire cache.
 */
public final class CacheInvalidator {

    private final PermissionCache cache;

    // Maps group name -> set of user UUIDs that inherit from this group
    private final Map<String, Set<UUID>> groupMembers = new ConcurrentHashMap<>();

    // Maps user UUID -> set of groups they belong to (for reverse lookup)
    private final Map<UUID, Set<String>> userGroups = new ConcurrentHashMap<>();

    // Optional listener called after each user invalidation to sync permissions to Hytale
    @Nullable
    private volatile Consumer<UUID> syncListener;

    // Reentrant guard: prevents infinite recursion if sync triggers another invalidation
    private final ThreadLocal<Boolean> syncing = ThreadLocal.withInitial(() -> false);

    /**
     * Creates a new cache invalidator.
     *
     * @param cache the permission cache to invalidate
     */
    public CacheInvalidator(@NotNull PermissionCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
    }

    /**
     * Sets the sync listener that is called for each affected user UUID
     * whenever permissions are invalidated.
     *
     * @param listener the sync listener, or null to disable
     */
    public void setSyncListener(@Nullable Consumer<UUID> listener) {
        this.syncListener = listener;
    }

    /**
     * Registers a user's group memberships.
     * <p>
     * This should be called when a user's permissions are resolved,
     * passing all groups they inherit from (directly or indirectly).
     *
     * @param uuid   the user's UUID
     * @param groups the groups the user belongs to
     */
    public void registerUserGroups(@NotNull UUID uuid, @NotNull Collection<String> groups) {
        // Build new group set
        Set<String> newGroups = ConcurrentHashMap.newKeySet();
        newGroups.addAll(groups);

        // Atomically replace user→groups mapping and get the old value
        Set<String> oldGroups = userGroups.put(uuid, newGroups);

        // Remove old group→user mappings that are no longer relevant
        if (oldGroups != null) {
            for (String group : oldGroups) {
                if (!newGroups.contains(group)) {
                    Set<UUID> members = groupMembers.get(group);
                    if (members != null) {
                        members.remove(uuid);
                    }
                }
            }
        }

        // Add new group→user mappings
        for (String group : groups) {
            groupMembers.computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        }
    }

    /**
     * Unregisters a user's group memberships.
     * <p>
     * This should be called when a user logs out or is unloaded.
     *
     * @param uuid the user's UUID
     */
    public void unregisterUser(@NotNull UUID uuid) {
        Set<String> groups = userGroups.remove(uuid);
        if (groups != null) {
            for (String group : groups) {
                Set<UUID> members = groupMembers.get(group);
                if (members != null) {
                    members.remove(uuid);
                }
            }
        }
    }

    /**
     * Invalidates cache entries for all users in a group.
     * <p>
     * This is called when a group's permissions change.
     *
     * @param groupName the group that changed
     * @return the number of users invalidated
     */
    public int invalidateGroup(@NotNull String groupName) {
        Set<UUID> members = groupMembers.get(groupName.toLowerCase());
        if (members == null || members.isEmpty()) {
            Logger.debug("No cached users in group '%s' to invalidate", groupName);
            return 0;
        }

        // Take a snapshot to avoid issues with concurrent modification during iteration
        List<UUID> snapshot = List.copyOf(members);
        for (UUID uuid : snapshot) {
            cache.invalidate(uuid);
            ChatAPI.invalidate(uuid);
            TabListAPI.invalidate(uuid);
        }

        // Notify sync listener for affected users
        notifySyncAll(snapshot);

        Logger.debug("Invalidated cache for %d users in group '%s'", snapshot.size(), groupName);
        return snapshot.size();
    }

    /**
     * Invalidates cache entries for all users in multiple groups.
     *
     * @param groupNames the groups that changed
     * @return the number of unique users invalidated
     */
    public int invalidateGroups(@NotNull Collection<String> groupNames) {
        Set<UUID> toInvalidate = new HashSet<>();

        for (String groupName : groupNames) {
            Set<UUID> members = groupMembers.get(groupName.toLowerCase());
            if (members != null) {
                toInvalidate.addAll(members);
            }
        }

        if (toInvalidate.isEmpty()) {
            return 0;
        }

        for (UUID uuid : toInvalidate) {
            cache.invalidate(uuid);
            ChatAPI.invalidate(uuid);
            TabListAPI.invalidate(uuid);
        }

        // Notify sync listener for affected users
        notifySyncAll(toInvalidate);

        Logger.debug("Invalidated cache for %d users across %d groups",
                toInvalidate.size(), groupNames.size());
        return toInvalidate.size();
    }

    /**
     * Invalidates a specific user's cache.
     *
     * @param uuid the user's UUID
     */
    public void invalidateUser(@NotNull UUID uuid) {
        cache.invalidate(uuid);
        ChatAPI.invalidate(uuid);
        TabListAPI.invalidate(uuid);
        notifySync(uuid);
    }

    /**
     * Invalidates a specific user's cache.
     * Alias for {@link #invalidateUser(UUID)}.
     *
     * @param uuid the user's UUID
     */
    public void invalidate(@NotNull UUID uuid) {
        cache.invalidate(uuid);
        ChatAPI.invalidate(uuid);
        TabListAPI.invalidate(uuid);
        notifySync(uuid);
    }

    /**
     * Invalidates all cached entries.
     */
    public void invalidateAll() {
        cache.invalidateAll();
        ChatAPI.invalidateAll();
        TabListAPI.invalidateAll();

        // Notify sync listener for all tracked users
        Set<UUID> allUsers = new HashSet<>(userGroups.keySet());
        notifySyncAll(allUsers);

        Logger.debug("Invalidated all cache entries (%d users synced)", allUsers.size());
    }

    /**
     * Invalidates context-sensitive cache entries for a user.
     * <p>
     * This is called when a player's context changes (e.g., world change,
     * game mode change) to ensure fresh permission resolution.
     *
     * @param uuid the user's UUID
     */
    public void invalidateContextCache(@NotNull UUID uuid) {
        cache.invalidate(uuid);
        ChatAPI.invalidate(uuid);
        TabListAPI.invalidate(uuid);
        notifySync(uuid);
        Logger.debug("Invalidated context cache for user %s", uuid);
    }

    /**
     * Notifies the sync listener for a single user UUID.
     * Guarded against reentrant calls to prevent infinite recursion.
     *
     * @param uuid the user's UUID
     */
    private void notifySync(@NotNull UUID uuid) {
        Consumer<UUID> listener = syncListener;
        if (listener == null) return;
        if (syncing.get()) return; // prevent recursion
        try {
            syncing.set(true);
            listener.accept(uuid);
        } catch (Exception e) {
            Logger.warn("Sync listener failed for %s: %s", uuid, e.getMessage());
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Notifies the sync listener for multiple user UUIDs.
     *
     * @param uuids the user UUIDs
     */
    private void notifySyncAll(@NotNull Collection<UUID> uuids) {
        Consumer<UUID> listener = syncListener;
        if (listener == null) return;
        if (syncing.get()) return;
        try {
            syncing.set(true);
            for (UUID uuid : uuids) {
                try {
                    listener.accept(uuid);
                } catch (Exception e) {
                    Logger.warn("Sync listener failed for %s: %s", uuid, e.getMessage());
                }
            }
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Gets all users that belong to a group.
     *
     * @param groupName the group name
     * @return unmodifiable set of user UUIDs
     */
    @NotNull
    public Set<UUID> getUsersInGroup(@NotNull String groupName) {
        Set<UUID> members = groupMembers.get(groupName.toLowerCase());
        if (members == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    /**
     * Gets all groups a user belongs to.
     *
     * @param uuid the user's UUID
     * @return unmodifiable set of group names
     */
    @NotNull
    public Set<String> getGroupsForUser(@NotNull UUID uuid) {
        Set<String> groups = userGroups.get(uuid);
        if (groups == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(groups));
    }

    /**
     * Gets the number of tracked users.
     *
     * @return the user count
     */
    public int getTrackedUserCount() {
        return userGroups.size();
    }

    /**
     * Gets the number of tracked groups.
     *
     * @return the group count
     */
    public int getTrackedGroupCount() {
        return groupMembers.size();
    }

    /**
     * Clears all tracking data.
     */
    public void clear() {
        groupMembers.clear();
        userGroups.clear();
    }

    @Override
    public String toString() {
        return String.format("CacheInvalidator{users=%d, groups=%d}",
                getTrackedUserCount(), getTrackedGroupCount());
    }
}
