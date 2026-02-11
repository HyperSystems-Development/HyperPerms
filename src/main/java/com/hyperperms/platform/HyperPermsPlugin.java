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
 * <p>
 * This class integrates HyperPerms with the Hytale server by:
 * <ul>
 *   <li>Registering as a permission provider</li>
 *   <li>Listening to player connect/disconnect events</li>
 *   <li>Tracking world and game mode changes for contexts</li>
 * </ul>
 */
public class HyperPermsPlugin extends JavaPlugin {

    private HyperPerms hyperPerms;
    private HyperPermsPermissionProvider permissionProvider;
    private HytaleAdapter adapter;
    private com.hyperperms.chat.ChatListener chatListener;
    private com.hyperperms.tablist.TabListListener tabListListener;
    private com.hyperperms.update.UpdateNotificationListener updateNotificationListener;
    private volatile boolean shuttingDown = false;

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
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to register permission provider");
        }
    }

    /**
     * Ensures HyperPerms is the first permission provider in the chain.
     * <p>
     * This is critical for compatibility with plugins like EssentialsPlus that call
     * {@code getFirstPermissionProvider().getGroupPermissions()} to enumerate permissions.
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

        Logger.debug("Player connecting: %s (%s)", username, uuid);

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
                    Logger.debug("Created and saved new user %s with default group: %s",
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

            Logger.debug("Loaded permissions for %s", username);
        }).exceptionally(e -> {
            Logger.severe("Failed to load permissions for %s", e, username);
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

        Logger.debug("Player disconnecting: %s", username);

        // Save user data
        var user = hyperPerms.getUserManager().getUser(uuid);
        if (user != null) {
            if (shuttingDown) {
                // During shutdown, save synchronously to avoid classloader teardown race
                try {
                    hyperPerms.getUserManager().saveUser(user).join();
                    Logger.debug("Saved permissions for %s (shutdown)", username);
                } catch (Exception e) {
                    Logger.severe("Failed to save permissions for %s", e, username);
                }
            } else {
                hyperPerms.getUserManager().saveUser(user).thenRun(() -> {
                    Logger.debug("Saved permissions for %s", username);
                }).exceptionally(e -> {
                    Logger.severe("Failed to save permissions for %s", e, username);
                    return null;
                });
            }
        }

        // Clear from cache
        hyperPerms.getCache().invalidate(uuid);

        // Clear ChatAPI cache for external plugins
        com.hyperperms.api.ChatAPI.invalidate(uuid);

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
     * Syncs resolved permissions to Hytale's internal permission system.
     * <p>
     * Since Hytale doesn't query our PermissionProvider for permission checks,
     * we must proactively push permission changes. Specifically:
     * - Remove negated permissions so Hytale's internal storage doesn't grant them
     * - This handles cases where a child group negates a parent group's permission
     *
     * @param uuid the player's UUID
     * @param user the HyperPerms user
     */
    public void syncPermissionsToHytale(java.util.UUID uuid, com.hyperperms.model.User user) {
        try {
            var contexts = hyperPerms.getContexts(uuid);
            var resolved = hyperPerms.getResolver().resolve(user, contexts);

            // Get permissions that are explicitly DENIED (negated)
            java.util.Set<String> deniedPerms = resolved.getDeniedPermissions();

            if (!deniedPerms.isEmpty()) {
                // Expand denied permissions (handle wildcards and aliases)
                java.util.Set<String> expandedDenied = new java.util.HashSet<>(deniedPerms);
                var aliases = com.hyperperms.registry.PermissionAliases.getInstance();
                var registry = hyperPerms.getPermissionRegistry();

                for (String perm : deniedPerms) {
                    // Expand aliases
                    expandedDenied.addAll(aliases.expand(perm));

                    // Expand wildcards
                    if (perm.endsWith(".*") || perm.equals("*")) {
                        expandedDenied.addAll(registry.getMatchingPermissions(perm));
                    }
                }

                Logger.info("Removing %d negated permissions from Hytale for %s", expandedDenied.size(), user.getUsername());
                for (String perm : expandedDenied) {
                    Logger.debug("  Removing negated: %s", perm);
                }

                // Remove negated permissions from Hytale's internal storage
                // IMPORTANT: Only remove from OTHER providers, not our own!
                // Calling our own removeUserPermissions() would cause infinite recursion
                // because it triggers syncUserPermissions() which calls this method again.
                PermissionsModule.get().getProviders().forEach(provider -> {
                    if (provider != permissionProvider) {
                        // Remove from other providers (like HytalePermissionsProvider)
                        try {
                            provider.removeUserPermissions(uuid, expandedDenied);
                        } catch (Exception e) {
                            Logger.debug("Could not remove from provider %s: %s", provider.getName(), e.getMessage());
                        }
                    }
                });
            }

            Logger.debug("Permission sync complete for %s", user.getUsername());
        } catch (Exception e) {
            Logger.severe("Failed to sync permissions to Hytale for %s", e, user.getUsername());
        }
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
