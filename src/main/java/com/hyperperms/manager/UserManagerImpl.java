package com.hyperperms.manager;

import com.hyperperms.api.HyperPermsAPI.UserManager;
import com.hyperperms.api.PermissionHolder;
import com.hyperperms.api.PermissionHolderListener;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.api.events.*;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Implementation of the user manager.
 */
public final class UserManagerImpl implements UserManager {

    private final StorageProvider storage;
    private final PermissionCache cache;
    private final EventBus eventBus;
    private final Map<UUID, User> loadedUsers = new ConcurrentHashMap<>();
    private final Map<UUID, Object> userLocks = new ConcurrentHashMap<>();
    private final String defaultGroup;
    private final UserPermissionListener permissionListener;

    public UserManagerImpl(@NotNull StorageProvider storage, @NotNull PermissionCache cache,
                           @NotNull EventBus eventBus, @NotNull String defaultGroup) {
        this.storage = storage;
        this.cache = cache;
        this.eventBus = eventBus;
        this.defaultGroup = defaultGroup;
        this.permissionListener = new UserPermissionListener();
    }

    @Override
    public CompletableFuture<Optional<User>> loadUser(@NotNull UUID uuid) {
        // Check cache first - if already loaded, return immediately
        User cached = loadedUsers.get(uuid);
        if (cached != null) {
            Logger.debug("loadUser: %s already in loadedUsers (total: %d)", uuid, loadedUsers.size());
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        Logger.debug("loadUser: loading %s from storage (not in loadedUsers, total: %d)", uuid, loadedUsers.size());

        return storage.loadUser(uuid).thenApply(opt -> {
            boolean isNew = opt.isEmpty();
            User loaded;

            if (opt.isPresent()) {
                loaded = opt.get();
                Logger.debug("loadUser: storage returned existing user for %s", uuid);
            } else {
                // Create a new user with default settings
                loaded = new User(uuid, null);
                loaded.setPrimaryGroup(defaultGroup);
                Logger.debug("loadUser: no storage entry for %s, created new user with group '%s'", uuid, defaultGroup);
            }

            // Set the listener on the user
            loaded.setListener(permissionListener);

            // compute() is atomic - first writer wins to prevent race condition
            // where a concurrent loadUser replaces a user whose username was
            // already set by onPlayerConnect's thenAccept callback
            User result = loadedUsers.compute(uuid, (key, existing) -> {
                if (existing == null) {
                    return loaded;
                }
                return existing;
            });

            cache.invalidate(uuid);

            Logger.debug("loadUser: %s now in loadedUsers (total: %d)", uuid, loadedUsers.size());

            // Fire user load event
            eventBus.fire(new UserLoadEvent(result, UserLoadEvent.LoadSource.STORAGE, isNew));

            return Optional.of(result);
        });
    }

    @Override
    @Nullable
    public User getUser(@NotNull UUID uuid) {
        return loadedUsers.get(uuid);
    }

    @Override
    @NotNull
    public User getOrCreateUser(@NotNull UUID uuid) {
        return loadedUsers.computeIfAbsent(uuid, id -> {
            User user = new User(id, null);
            user.setPrimaryGroup(defaultGroup);
            user.setListener(permissionListener);

            // Fire user load event for new user
            eventBus.fire(new UserLoadEvent(user, UserLoadEvent.LoadSource.API, true));

            return user;
        });
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull User user) {
        loadedUsers.put(user.getUuid(), user);
        return storage.saveUser(user);
    }

    @Override
    public CompletableFuture<Void> modifyUser(@NotNull UUID uuid, @NotNull Consumer<User> action) {
        // Use per-entity locks to prevent concurrent modification lost updates
        Object lock = userLocks.computeIfAbsent(uuid, k -> new Object());

        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                User user = getOrCreateUser(uuid);
                action.accept(user);
                return user;
            }
        }).thenCompose(this::saveUser).thenRun(() -> {
            // Invalidate cache AFTER save completes to prevent stale reads
            cache.invalidate(uuid);
        });
    }

    @Override
    @NotNull
    public Set<User> getLoadedUsers() {
        return Collections.unmodifiableSet(new HashSet<>(loadedUsers.values()));
    }

    @Override
    public boolean isLoaded(@NotNull UUID uuid) {
        return loadedUsers.containsKey(uuid);
    }

    @Override
    public void unload(@NotNull UUID uuid) {
        User user = loadedUsers.remove(uuid);
        userLocks.remove(uuid);
        cache.invalidate(uuid);

        // Fire user unload event
        eventBus.fire(new UserUnloadEvent(uuid, user, UserUnloadEvent.UnloadReason.API));
    }

    /**
     * Unloads a user with a specific reason.
     *
     * @param uuid   the user UUID
     * @param reason the reason for unloading
     */
    public void unload(@NotNull UUID uuid, @NotNull UserUnloadEvent.UnloadReason reason) {
        User user = loadedUsers.remove(uuid);
        userLocks.remove(uuid);
        cache.invalidate(uuid);
        eventBus.fire(new UserUnloadEvent(uuid, user, reason));
    }

    /**
     * Loads all users from storage.
     *
     * @return a future that completes when loaded
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadAllUsers().thenAccept(users -> {
            for (User user : users.values()) {
                user.setListener(permissionListener);
            }
            loadedUsers.putAll(users);
            Logger.info("Loaded %d users from storage", users.size());
        });
    }

    /**
     * Loads multiple users from storage.
     *
     * @param uuids the UUIDs to load
     * @return a future that completes with a map of loaded users
     */
    public CompletableFuture<Map<UUID, User>> loadUsers(@NotNull Collection<UUID> uuids) {
        List<CompletableFuture<Optional<User>>> futures = new ArrayList<>();
        Map<UUID, User> results = new ConcurrentHashMap<>();

        for (UUID uuid : uuids) {
            User cached = loadedUsers.get(uuid);
            if (cached != null) {
                results.put(uuid, cached);
            } else {
                futures.add(loadUser(uuid).thenApply(opt -> {
                    opt.ifPresent(user -> results.put(uuid, user));
                    return opt;
                }));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> results);
    }

    /**
     * Saves multiple users to storage.
     *
     * @param users the users to save
     * @return a future that completes when all users are saved
     */
    public CompletableFuture<Void> saveUsers(@NotNull Collection<User> users) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (User user : users) {
            if (user.hasData()) {
                futures.add(saveUser(user));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Applies a modification to multiple users and saves them.
     *
     * @param uuids  the UUIDs to modify
     * @param action the modification to apply
     * @return a future that completes when all users are saved
     */
    public CompletableFuture<Void> batchModify(@NotNull Collection<UUID> uuids, @NotNull Consumer<User> action) {
        return loadUsers(uuids).thenCompose(users -> {
            for (User user : users.values()) {
                Object lock = userLocks.computeIfAbsent(user.getUuid(), k -> new Object());
                synchronized (lock) {
                    action.accept(user);
                }
            }
            return saveUsers(users.values());
        }).thenRun(() -> {
            for (UUID uuid : uuids) {
                cache.invalidate(uuid);
            }
        });
    }

    /**
     * Gets all known user UUIDs from storage.
     *
     * @return a future that completes with the set of all known UUIDs
     */
    public CompletableFuture<Set<UUID>> getAllKnownUUIDs() {
        return storage.getUserUuids();
    }

    /**
     * Saves all loaded users.
     *
     * @return a future that completes when saved
     */
    public CompletableFuture<Void> saveAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (User user : loadedUsers.values()) {
            if (user.hasData()) {
                futures.add(storage.saveUser(user));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Cleans up expired permissions for all loaded users.
     *
     * @return the total number of expired permissions removed
     */
    public int cleanupExpired() {
        int total = 0;
        for (User user : loadedUsers.values()) {
            int removed = user.cleanupExpired();
            if (removed > 0) {
                total += removed;
                cache.invalidate(user.getUuid());
                // Capture user reference to avoid issues if unloaded during save
                final User userToSave = user;
                storage.saveUser(userToSave).exceptionally(e -> {
                    Logger.severe("Failed to save user after expired permission cleanup: " + userToSave.getUuid(), e);
                    return null;
                });
            }
        }
        return total;
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
     * Internal listener that fires events when user permissions change.
     */
    private class UserPermissionListener implements PermissionHolderListener {

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
            if (result == PermissionHolder.DataMutateResult.SUCCESS && holder instanceof User user) {
                eventBus.fire(new UserGroupChangeEvent(user, UserGroupChangeEvent.ChangeType.ADD, groupName));
            }
        }

        @Override
        public void onGroupRemoved(@NotNull PermissionHolder holder, @NotNull String groupName,
                                   @NotNull PermissionHolder.DataMutateResult result) {
            if (result == PermissionHolder.DataMutateResult.SUCCESS && holder instanceof User user) {
                eventBus.fire(new UserGroupChangeEvent(user, UserGroupChangeEvent.ChangeType.REMOVE, groupName));
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
