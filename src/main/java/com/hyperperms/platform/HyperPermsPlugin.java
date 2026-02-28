package com.hyperperms.platform;

import com.hyperperms.HyperPerms;
import com.hyperperms.HyperPermsBootstrap;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.logging.Level;

/**
 * Main Hytale plugin class for HyperPerms.
 *
 * This class integrates HyperPerms with the Hytale server by:
 * - Registering as a permission provider
 * - Listening to player connect/disconnect events
 * - Tracking world and game mode changes for contexts
 */
public class HyperPermsPlugin extends JavaPlugin {

    private HyperPerms hyperPerms;
    private HyperPermsPermissionProvider permissionProvider;
    private HytaleAdapter adapter;
    private com.hyperperms.chat.ChatListener chatListener;
    private com.hyperperms.tablist.TabListListener tabListListener;
    private com.hyperperms.update.UpdateNotificationListener updateNotificationListener;
    private volatile boolean shuttingDown = false;

    // Per-UUID lock map for serializing syncPermissionsToHytale calls.
    // Prevents concurrent syncs for the same user from racing on Hytale's
    // non-thread-safe HashSet views (getUserPermissions returns a live view).
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Object> syncLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Creates a new HyperPermsPlugin instance.
     * Called by the Hytale plugin loader.
     *
     * @param init the plugin initialization data
     */
    public HyperPermsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Initialize HyperPerms core
        hyperPerms = new HyperPerms(getDataDirectory(), java.util.logging.Logger.getLogger("HyperPerms"));

        // Register the global instance for API access
        HyperPermsBootstrap.setInstance(hyperPerms);
        HyperPermsBootstrap.setPlugin(this);

        // Create the platform adapter
        adapter = new HytaleAdapter(hyperPerms, this);

        // Create the permission provider
        permissionProvider = new HyperPermsPermissionProvider(hyperPerms);

        getLogger().at(Level.INFO).log("HyperPerms setup complete");
    }

    @Override
    protected void start() {
        // Enable HyperPerms core
        hyperPerms.enable();

        // Set the player context provider for context calculators
        hyperPerms.setPlayerContextProvider(adapter);

        // Register as a permission provider with Hytale
        registerPermissionProvider();

        // Wire up centralized sync: whenever any user's cache is invalidated,
        // automatically sync their resolved permissions to Hytale's provider
        hyperPerms.getCacheInvalidator().setSyncListener(uuid -> {
            if (adapter.isOnline(uuid)) {
                var user = hyperPerms.getUserManager().getUser(uuid);
                if (user != null) {
                    syncPermissionsToHytale(uuid, user);
                }
            }
        });

        // Clean up any permission pollution from previous versions
        cleanupHytalePermissions();

        // Register commands
        registerCommands();

        // Register event listeners
        registerEventListeners();

        getLogger().at(Level.INFO).log("HyperPerms v%s enabled!", getManifest().getVersion());
    }

    @Override
    protected void shutdown() {
        shuttingDown = true;

        // Unregister chat listener
        if (chatListener != null) {
            try {
                chatListener.unregister(getEventRegistry());
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to unregister chat listener");
            }
        }

        // Unregister tab list listener
        if (tabListListener != null) {
            try {
                tabListListener.unregister(getEventRegistry());
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to unregister tab list listener");
            }
        }

        // Unregister update notification listener
        if (updateNotificationListener != null) {
            try {
                updateNotificationListener.unregister(getEventRegistry());
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to unregister update notification listener");
            }
        }

        // Unregister permission provider
        if (permissionProvider != null) {
            try {
                PermissionsModule.get().removeProvider(permissionProvider);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to unregister permission provider");
            }
        }

        // Disable HyperPerms core
        if (hyperPerms != null) {
            hyperPerms.disable();
        }

        // Clear the global instances
        HyperPermsBootstrap.setInstance(null);
        HyperPermsBootstrap.setPlugin(null);

        // Clear the adapter
        if (adapter != null) {
            adapter.shutdown();
        }

        getLogger().at(Level.INFO).log("HyperPerms disabled");
    }

    /**
     * Registers HyperPerms as a permission provider with Hytale.
     */
    private void registerPermissionProvider() {
        try {
            PermissionsModule module = PermissionsModule.get();
            module.addProvider(permissionProvider);

            // Ensure HyperPerms is the FIRST provider.
            // Plugins like EssentialsPlus call getFirstPermissionProvider().getGroupPermissions()
            // to enumerate permissions. If HyperPerms isn't first, the native provider
            // won't understand our virtual user groups and returns empty.
            ensureFirstProvider(module);

            Logger.info("Registered HyperPerms as permission provider");

            // Warn about vanilla OP/Default group overwrite behavior.
            // HytalePermissionsProvider.read() forcibly re-inserts DEFAULT_GROUPS with put()
            // (not putIfAbsent()), meaning any custom permissions added to vanilla's OP or
            // Default groups via /perm are silently lost on server restart.
            warnAboutVanillaGroupOverwrite(module);
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to register permission provider");
        }
    }

    /**
     * Logs a warning about Hytale's vanilla OP/Default group overwrite behavior.
     *
     * Hytale's HytalePermissionsProvider forcibly overwrites the OP and Default groups
     * with hardcoded defaults on every server load (using put(), not putIfAbsent()).
     * Any custom permissions added to these groups via vanilla's /perm command are
     * silently discarded on restart. This method warns server admins about this
     * behavior so they know to use HyperPerms groups instead.
     *
     * @param module the permissions module
     */
    private void warnAboutVanillaGroupOverwrite(PermissionsModule module) {
        try {
            // Check if the vanilla provider has custom permissions on OP or Default groups.
            // The vanilla defaults are: OP = ["*"], Default = [].
            for (var provider : module.getProviders()) {
                if (provider == permissionProvider) {
                    continue; // Skip our own provider
                }

                java.util.Set<String> opPerms = provider.getGroupPermissions("OP");
                java.util.Set<String> defaultPerms = provider.getGroupPermissions("Default");

                // OP group: vanilla default is ["*"]; anything else means custom perms were added
                boolean opCustomized = !opPerms.isEmpty() && !(opPerms.size() == 1 && opPerms.contains("*"));
                // Default group: vanilla default is []; any permissions means it was customized
                boolean defaultCustomized = !defaultPerms.isEmpty();

                if (opCustomized || defaultCustomized) {
                    Logger.warn("=======================================================");
                    Logger.warn("VANILLA GROUP OVERWRITE WARNING");
                    Logger.warn("=======================================================");
                    if (opCustomized) {
                        Logger.warn("The vanilla OP group has custom permissions beyond '*'.");
                        Logger.warn("These WILL BE LOST on next server restart!");
                        Logger.warn("  Current OP perms: %s", opPerms);
                    }
                    if (defaultCustomized) {
                        Logger.warn("The vanilla Default group has custom permissions.");
                        Logger.warn("These WILL BE LOST on next server restart!");
                        Logger.warn("  Current Default perms: %s", defaultPerms);
                    }
                    Logger.warn("Hytale forcibly resets OP and Default groups on every load.");
                    Logger.warn("Use HyperPerms groups instead: /hp group create <name>");
                    Logger.warn("=======================================================");
                }
            }
        } catch (Exception e) {
            Logger.debug("Could not check vanilla group customization: %s", e.getMessage());
        }
    }

    /**
     * Ensures HyperPerms is the first permission provider in the chain.
     *
     * This is critical for compatibility with plugins like EssentialsPlus that call
     * getFirstPermissionProvider().getGroupPermissions() to enumerate permissions.
     * If the native Hytale provider is first, it won't understand HyperPerms' virtual
     * user groups and returns empty results.
     *
     * @param module the permissions module
     */
    private void ensureFirstProvider(PermissionsModule module) {
        try {
            var providers = new java.util.ArrayList<>(module.getProviders());

            // Already first? Nothing to do
            if (!providers.isEmpty() && providers.getFirst() == permissionProvider) {
                Logger.debug("HyperPerms is already the first permission provider");
                return;
            }

            // Remove all providers, then re-add with HyperPerms first
            for (var p : providers) {
                module.removeProvider(p);
            }
            module.addProvider(permissionProvider);
            for (var p : providers) {
                if (p != permissionProvider) {
                    module.addProvider(p);
                }
            }

            Logger.info("Reordered permission providers - HyperPerms is now the primary provider");
        } catch (Exception e) {
            Logger.warn("Could not reorder permission providers: %s", e.getMessage());
            Logger.warn("Some plugins may not be able to enumerate permissions via the native API");
        }
    }

    /**
     * Registers HyperPerms commands with Hytale.
     */
    private void registerCommands() {
        try {
            HyperPermsCommand command = new HyperPermsCommand(hyperPerms);
            getCommandRegistry().registerCommand(command);
            getLogger().at(Level.INFO).log("Registered /hp command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to register commands");
        }
    }

    /**
     * Registers event listeners for player lifecycle events.
     */
    private void registerEventListeners() {
        // Player connect event - load user data
        getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);

        // Player disconnect event - save and cleanup
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Player added to world event - track world changes (keyed event)
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);

        // Game mode change event - update context cache
        getEventRegistry().registerGlobal(ChangeGameModeEvent.class, this::onGameModeChange);

        // Register chat listener (if chat formatting is enabled)
        registerChatListener();

        // Register tab list listener (if tab list formatting is enabled)
        registerTabListListener();

        // Register update notification listener
        registerUpdateNotificationListener();

        getLogger().at(Level.INFO).log("Registered event listeners");
    }

    /**
     * Registers the chat listener for formatting player chat messages.
     */
    private void registerChatListener() {
        try {
            var chatManager = hyperPerms.getChatManager();
            if (chatManager != null && chatManager.isEnabled()) {
                chatListener = new com.hyperperms.chat.ChatListener(hyperPerms, chatManager);

                // Load config settings
                var config = hyperPerms.getConfig();
                if (config != null) {
                    chatListener.setAllowPlayerColors(config.isAllowPlayerColors());
                    chatListener.setColorPermission(config.getColorPermission());
                }

                chatListener.register(getEventRegistry());
                getLogger().at(Level.INFO).log("Chat formatting enabled");
            } else {
                getLogger().at(Level.INFO).log("Chat formatting is disabled in config");
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Failed to register chat listener");
        }
    }

    /**
     * Registers the tab list listener for formatting player tab list names.
     */
    private void registerTabListListener() {
        try {
            var tabListManager = hyperPerms.getTabListManager();
            if (tabListManager != null && tabListManager.isEnabled()) {
                tabListListener = new com.hyperperms.tablist.TabListListener(hyperPerms, tabListManager);
                tabListListener.register(getEventRegistry());
                getLogger().at(Level.INFO).log("Tab list formatting enabled");
            } else {
                getLogger().at(Level.INFO).log("Tab list formatting is disabled in config");
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Failed to register tab list listener");
        }
    }

    /**
     * Registers the update notification listener for notifying operators about updates.
     */
    private void registerUpdateNotificationListener() {
        try {
            // Only register if update checking is enabled
            if (hyperPerms.getUpdateChecker() != null) {
                updateNotificationListener = new com.hyperperms.update.UpdateNotificationListener(hyperPerms);
                updateNotificationListener.register(getEventRegistry());
                getLogger().at(Level.INFO).log("Update notification listener enabled");
            } else {
                getLogger().at(Level.INFO).log("Update notifications disabled (update checking is disabled)");
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Failed to register update notification listener");
        }
    }

    /**
     * Handles player connect event.
     * Loads or creates the user's permission data.
     *
     * @param event the player connect event
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        java.util.UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();
        String worldName = event.getWorld() != null ? event.getWorld().getName() : null;

        Logger.info("Player connecting: %s (%s) world=%s", username, uuid, worldName);

        // Track the player in the adapter
        adapter.trackPlayer(playerRef, worldName);

        // Load user permissions async
        hyperPerms.getUserManager().loadUser(uuid).thenAccept(opt -> {
            boolean isNewUser = opt.isEmpty();
            var user = opt.orElseGet(() -> hyperPerms.getUserManager().getOrCreateUser(uuid));
            user.setUsername(username);

            // Save new users to persist their default group assignment
            if (isNewUser) {
                hyperPerms.getUserManager().saveUser(user).thenRun(() -> {
                    Logger.info("Created and saved new user %s with default group: %s",
                        username, user.getPrimaryGroup());
                }).exceptionally(e -> {
                    Logger.severe("Failed to save new user: " + username, e);
                    return null;
                });
            }

            // Prime the cache
            hyperPerms.getContextManager().getContexts(uuid);

            // Preload ChatAPI cache for external plugins
            com.hyperperms.api.ChatAPI.preload(uuid);

            // Sync resolved permissions to Hytale's internal system
            // This ensures negations are properly applied
            syncPermissionsToHytale(uuid, user);

            int loadedCount = hyperPerms.getUserManager().getLoadedUsers().size();
            Logger.info("Loaded permissions for %s (%s), group=%s, loadedUsers=%d",
                    username, uuid, user.getPrimaryGroup(), loadedCount);
        }).exceptionally(e -> {
            Logger.severe("Failed to load permissions for " + username + " (" + uuid + ")", e);
            return null;
        });
    }

    /**
     * Handles player disconnect event.
     * Saves user data and cleans up caches.
     *
     * @param event the player disconnect event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        java.util.UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        Logger.info("Player disconnecting: %s (%s)", username, uuid);

        // Save user data
        var user = hyperPerms.getUserManager().getUser(uuid);
        if (user != null) {
            if (shuttingDown) {
                // During shutdown, save synchronously to avoid classloader teardown race
                try {
                    hyperPerms.getUserManager().saveUser(user).join();
                    Logger.info("Saved permissions for %s (shutdown)", username);
                } catch (Exception e) {
                    Logger.severe("Failed to save permissions for " + username, e);
                }
            } else {
                hyperPerms.getUserManager().saveUser(user).thenRun(() -> {
                    Logger.debug("Saved permissions for %s", username);
                }).exceptionally(e -> {
                    Logger.severe("Failed to save permissions for " + username, e);
                    return null;
                });
            }
        }

        // Clear from cache
        hyperPerms.getCacheInvalidator().invalidate(uuid);

        // Clear ChatAPI cache for external plugins
        com.hyperperms.api.ChatAPI.invalidate(uuid);

        // Clean up per-UUID sync lock
        syncLocks.remove(uuid);

        // Untrack the player
        adapter.untrackPlayer(uuid);
    }

    /**
     * Handles player added to world event.
     * Updates context data when player changes world.
     *
     * @param event the add player to world event
     */
    private void onPlayerAddedToWorld(AddPlayerToWorldEvent event) {
        // Get player from holder
        var holder = event.getHolder();
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());

        if (playerRef == null) {
            return;
        }

        java.util.UUID uuid = playerRef.getUuid();
        String worldName = event.getWorld() != null ? event.getWorld().getName() : null;

        Logger.debug("Player %s moved to world: %s", playerRef.getUsername(), worldName);

        // Update world tracking
        adapter.updatePlayerWorld(uuid, worldName);

        // Invalidate context-sensitive cache entries
        hyperPerms.getCacheInvalidator().invalidateContextCache(uuid);
    }

    /**
     * Handles game mode change event.
     * Updates context data when player's game mode changes.
     *
     * @param event the game mode change event
     */
    private void onGameModeChange(ChangeGameModeEvent event) {
        Logger.debug("Game mode change detected: %s", event.getGameMode().name());

        // ECS events don't expose the target entity to global listeners directly.
        // Invalidate all context caches so permissions re-resolve with updated game modes.
        // Game mode changes are infrequent so full invalidation is acceptable.
        hyperPerms.getCacheInvalidator().invalidateAll();
    }

    /**
     * Syncs resolved permissions to Hytale's internal permission system using diff-based logic.
     *
     * Computes the delta between what Hytale currently has and what HyperPerms resolves,
     * then only adds missing permissions and removes stale ones. This prevents:
     * - Permission pollution (hundreds of perms accumulating in permissions.json)
     * - Unnecessary disk writes (no-op if nothing changed)
     * - Race conditions (permissions are never fully cleared)
     *
     * @param uuid the player's UUID
     * @param user the HyperPerms user
     */
    public void syncPermissionsToHytale(java.util.UUID uuid, com.hyperperms.model.User user) {
        // Per-UUID lock: serializes concurrent sync calls for the same user.
        // Multiple threads can trigger this (command thread, scheduler, CF pool, web editor)
        // and Hytale's getUserPermissions() returns a live view of a non-thread-safe HashSet.
        Object lock = syncLocks.computeIfAbsent(uuid, k -> new Object());
        synchronized (lock) {
            try {
                var contexts = hyperPerms.getContexts(uuid);
                var resolved = hyperPerms.getResolver().resolve(user, contexts);
                var registry = hyperPerms.getPermissionRegistry();

                // Get GRANTED permissions (expanded with wildcards + aliases)
                java.util.Set<String> expandedGranted = resolved.getExpandedPermissions(registry);

                // Get DENIED permissions (expanded with wildcards + aliases)
                java.util.Set<String> deniedPerms = resolved.getDeniedPermissions();
                java.util.Set<String> expandedDenied = new java.util.HashSet<>(deniedPerms);
                var aliases = com.hyperperms.registry.PermissionAliases.getInstance();

                for (String perm : deniedPerms) {
                    expandedDenied.addAll(aliases.expand(perm));
                    if (perm.endsWith(".*") || perm.equals("*")) {
                        expandedDenied.addAll(registry.getMatchingPermissions(perm));
                    }
                }

                // Compute the correct resolved set: granted minus denied
                java.util.Set<String> resolvedSet = new java.util.HashSet<>(expandedGranted);
                resolvedSet.removeAll(expandedDenied);

                // IMPORTANT: Only modify OTHER providers, not our own!
                PermissionsModule.get().getProviders().forEach(provider -> {
                    if (provider != permissionProvider) {
                        try {
                            // Defensive copy: getUserPermissions() returns an unmodifiable VIEW
                            // of a live HashSet. The provider's readLock is released on return,
                            // so another thread could structurally modify the backing set.
                            // Copy immediately to get a stable snapshot for diff computation.
                            java.util.Set<String> currentInHytale =
                                    new java.util.HashSet<>(provider.getUserPermissions(uuid));

                            // Compute diff
                            java.util.Set<String> toAdd = new java.util.HashSet<>(resolvedSet);
                            toAdd.removeAll(currentInHytale);

                            java.util.Set<String> toRemove = new java.util.HashSet<>(currentInHytale);
                            toRemove.removeAll(resolvedSet);

                            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                                Logger.debug("Permission sync for %s: no changes needed", user.getUsername());
                                return;
                            }

                            if (!toAdd.isEmpty()) {
                                provider.addUserPermissions(uuid, toAdd);
                            }
                            if (!toRemove.isEmpty()) {
                                provider.removeUserPermissions(uuid, toRemove);
                            }

                            Logger.debug("Permission sync for %s: +%d -%d (total: %d)",
                                    user.getUsername(), toAdd.size(), toRemove.size(), resolvedSet.size());
                        } catch (Exception e) {
                            Logger.debug("Could not sync to provider %s: %s", provider.getName(), e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                Logger.severe("Failed to sync permissions to Hytale for " + user.getUsername(), e);
            }
        }
    }

    /**
     * Logs that startup cleanup will happen lazily via diff-based sync.
     *
     * The Hytale PermissionProvider API doesn't expose a way to enumerate stored UUIDs,
     * so we cannot proactively clean polluted permissions at startup. Instead, the
     * diff-based sync in {@link #syncPermissionsToHytale} removes stale permissions
     * when each player connects — any permissions in Hytale's provider that don't match
     * HyperPerms' resolved set are removed as part of the diff.
     */
    private void cleanupHytalePermissions() {
        Logger.info("Diff-based sync enabled: stale permissions will be cleaned on player connect");
    }

    /**
     * Gets the HyperPerms instance.
     *
     * @return the HyperPerms instance
     */
    public HyperPerms getHyperPerms() {
        return hyperPerms;
    }

    /**
     * Gets the platform adapter.
     *
     * @return the adapter
     */
    public HytaleAdapter getAdapter() {
        return adapter;
    }
}
