package com.hyperperms.manager;

import com.hyperperms.api.HyperPermsAPI.GroupManager;
import com.hyperperms.api.PermissionHolder;
import com.hyperperms.api.PermissionHolderListener;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.api.events.*;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Implementation of the group manager.
 */
public final class GroupManagerImpl implements GroupManager {

    private final StorageProvider storage;
    private final CacheInvalidator cacheInvalidator;
    private final EventBus eventBus;
    private final Map<String, Group> loadedGroups = new ConcurrentHashMap<>();
    private final Map<String, Object> groupLocks = new ConcurrentHashMap<>();
    private final GroupPermissionListener permissionListener;

    public GroupManagerImpl(@NotNull StorageProvider storage, @NotNull CacheInvalidator cacheInvalidator,
                            @NotNull EventBus eventBus) {
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
        this.eventBus = eventBus;
        this.permissionListener = new GroupPermissionListener();
    }

    /**
     * Creates a group manager without event bus support.
     * @deprecated Use the constructor with EventBus parameter
     */
    @Deprecated
    public GroupManagerImpl(@NotNull StorageProvider storage, @NotNull CacheInvalidator cacheInvalidator) {
        this(storage, cacheInvalidator, new EventBus());
    }

    @Override
    @Nullable
    public Group getGroup(@NotNull String name) {
        return loadedGroups.get(name.toLowerCase());
    }

    @Override
    public CompletableFuture<Optional<Group>> loadGroup(@NotNull String name) {
        String lowerName = name.toLowerCase();
        Group cached = loadedGroups.get(lowerName);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        return storage.loadGroup(lowerName).thenApply(opt -> {
            opt.ifPresent(group -> {
                group.setListener(permissionListener);
                loadedGroups.put(lowerName, group);
            });
            return opt;
        });
    }

    @Override
    @NotNull
    public Group createGroup(@NotNull String name) {
        String lowerName = name.toLowerCase();

        // Fire PRE event - check if cancelled
        GroupCreateEvent preEvent = eventBus.fire(new GroupCreateEvent(lowerName));
        if (preEvent.isCancelled()) {
            throw new IllegalStateException("Group creation was cancelled by event handler");
        }

        Group group = new Group(lowerName);
        group.setListener(permissionListener);

        // putIfAbsent is atomic - prevents concurrent duplicate creation
        Group existing = loadedGroups.putIfAbsent(lowerName, group);
        if (existing != null) {
            throw new IllegalArgumentException("Group already exists: " + name);
        }

        storage.saveGroup(group);
        Logger.info("Created group: " + name);

        // Fire POST event
        eventBus.fire(new GroupCreateEvent(group));

        return group;
    }

    @Override
    public CompletableFuture<Void> deleteGroup(@NotNull String name) {
        String lowerName = name.toLowerCase();
        Group group = loadedGroups.get(lowerName);

        // Fire PRE event if group exists - check if cancelled
        if (group != null) {
            GroupDeleteEvent preEvent = eventBus.fire(new GroupDeleteEvent(group));
            if (preEvent.isCancelled()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Group deletion was cancelled by event handler"));
            }
        }

        loadedGroups.remove(lowerName);

        // Invalidate cache AFTER delete completes to prevent stale reads
        return storage.deleteGroup(lowerName).thenRun(() -> {
            // Invalidate all users in the deleted group
            cacheInvalidator.invalidateGroup(lowerName);

            // Fire POST event
            eventBus.fire(new GroupDeleteEvent(lowerName));
        });
    }

    @Override
    public CompletableFuture<Void> saveGroup(@NotNull Group group) {
        loadedGroups.put(group.getName(), group);
        // Invalidate cache AFTER save completes to prevent stale reads
        return storage.saveGroup(group).thenRun(() -> {
            // Targeted invalidation: only invalidate users in this group
            cacheInvalidator.invalidateGroup(group.getName());
        });
    }

    @Override
    public CompletableFuture<Void> modifyGroup(@NotNull String name, @NotNull Consumer<Group> action) {
        String lowerName = name.toLowerCase();
        Group group = getGroup(lowerName);
        if (group == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Group not found: " + name));
        }

        // Use per-entity locks to prevent concurrent modification lost updates
        Object lock = groupLocks.computeIfAbsent(lowerName, k -> new Object());

        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
                action.accept(group);
            }
        }).thenCompose(v -> saveGroup(group));
    }

    @Override
    @NotNull
    public Set<Group> getLoadedGroups() {
        return Collections.unmodifiableSet(new HashSet<>(loadedGroups.values()));
    }

    @Override
    @NotNull
    public Set<String> getGroupNames() {
        return Collections.unmodifiableSet(new HashSet<>(loadedGroups.keySet()));
    }

    /**
     * Loads all groups from storage.
     *
     * @return a future that completes when loaded
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadAllGroups().thenAccept(groups -> {
            for (Group group : groups.values()) {
                group.setListener(permissionListener);
            }
            loadedGroups.putAll(groups);
            Logger.info("Loaded %d groups from storage", groups.size());
        });
    }

    /**
     * Saves all groups to storage.
     *
     * @return a future that completes when saved
     */
    public CompletableFuture<Void> saveAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Group group : loadedGroups.values()) {
            futures.add(storage.saveGroup(group));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Creates the default group if it doesn't exist.
     *
     * @param defaultGroupName the default group name
     */
    public void ensureDefaultGroup(@NotNull String defaultGroupName) {
        if (getGroup(defaultGroupName) == null) {
            Group defaultGroup = new Group(defaultGroupName, 0);
            defaultGroup.setDisplayName("Default");
            defaultGroup.setListener(permissionListener);
            loadedGroups.put(defaultGroupName, defaultGroup);
            storage.saveGroup(defaultGroup);
            Logger.info("Created default group: " + defaultGroupName);

            // Fire POST event for the default group
            eventBus.fire(new GroupCreateEvent(defaultGroup));
        }
    }

    /**
     * Cleans up expired permissions for all loaded groups.
     *
     * @return the total number of expired permissions removed
     */
    public int cleanupExpired() {
        int total = 0;
        List<String> affectedGroups = new ArrayList<>();
        for (Group group : loadedGroups.values()) {
            int removed = group.cleanupExpired();
            if (removed > 0) {
                total += removed;
                affectedGroups.add(group.getName());
                storage.saveGroup(group).exceptionally(e -> {
                    Logger.severe("Failed to save group after expired permission cleanup: " + group.getName(), e);
                    return null;
                });
            }
        }
        if (!affectedGroups.isEmpty()) {
            cacheInvalidator.invalidateGroups(affectedGroups);
        }
        return total;
    }

    /**
     * Fires a group modify event.
     * <p>
     * This should be called by code that modifies group properties directly
     * (such as setWeight, setPrefix, etc.) if they want events to fire.
     *
     * @param group      the modified group
     * @param modifyType the type of modification
     * @param oldValue   the old value
     * @param newValue   the new value
     */
    public void fireModifyEvent(@NotNull Group group, @NotNull GroupModifyEvent.ModifyType modifyType,
                                @Nullable Object oldValue, @Nullable Object newValue) {
        eventBus.fire(new GroupModifyEvent(group, modifyType, oldValue, newValue));
    }

    /**
     * Gets the event bus used by this manager.
     *
     * @return the event bus
     */
    @NotNull
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Internal listener that fires events when group permissions change.
     */
    private class GroupPermissionListener implements PermissionHolderListener {

        @Override
        public void onNodeAdded(@NotNull PermissionHolder holder, @NotNull Node node,
                                @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS) {
                eventBus.fire(new PermissionChangeEvent(holder, node, PermissionChangeEvent.ChangeType.ADD));
            }
        }

        @Override
        public void onNodeRemoved(@NotNull PermissionHolder holder, @NotNull Node node,
                                  @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS) {
                eventBus.fire(new PermissionChangeEvent(holder, node, PermissionChangeEvent.ChangeType.REMOVE));
            }
        }

        @Override
        public void onNodeSet(@NotNull PermissionHolder holder, @NotNull Node node,
                              @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS) {
                eventBus.fire(new PermissionChangeEvent(holder, node, PermissionChangeEvent.ChangeType.UPDATE));
            }
        }

        @Override
        public void onGroupAdded(@NotNull PermissionHolder holder, @NotNull String groupName,
                                 @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS && holder instanceof Group group) {
                eventBus.fire(new GroupModifyEvent(group, GroupModifyEvent.ModifyType.PARENT_ADD, null, groupName));
            }
        }

        @Override
        public void onGroupRemoved(@NotNull PermissionHolder holder, @NotNull String groupName,
                                   @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS && holder instanceof Group group) {
                eventBus.fire(new GroupModifyEvent(group, GroupModifyEvent.ModifyType.PARENT_REMOVE, groupName, null));
            }
        }

        @Override
        public void onNodesCleared(@NotNull PermissionHolder holder, @Nullable ContextSet contexts) {
            // Create a synthetic node to represent the clear operation
            Node clearNode = Node.builder("*").build();
            eventBus.fire(new PermissionChangeEvent(holder, clearNode, PermissionChangeEvent.ChangeType.CLEAR));
        }
    }
}
