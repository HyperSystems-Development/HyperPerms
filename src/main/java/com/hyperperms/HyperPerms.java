package com.hyperperms;

import com.hyperperms.api.AsyncPermissionCheckBuilder;
import com.hyperperms.api.HyperPermsAPI;
import com.hyperperms.api.MetricsAPI;
import com.hyperperms.api.PermissionCheckBuilder;
import com.hyperperms.api.QueryAPI;
import com.hyperperms.api.TriState;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.api.events.EventBus;
import com.hyperperms.api.events.PermissionCheckEvent;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.resolver.PermissionTrace;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.context.ContextManager;
import com.hyperperms.context.PlayerContextProvider;
import com.hyperperms.context.calculators.BiomeContextCalculator;
import com.hyperperms.context.calculators.GameModeContextCalculator;
import com.hyperperms.context.calculators.RegionContextCalculator;
import com.hyperperms.context.calculators.ServerContextCalculator;
import com.hyperperms.context.calculators.TimeContextCalculator;
import com.hyperperms.context.calculators.WorldContextCalculator;
import com.hyperperms.integration.FactionIntegration;
import com.hyperperms.integration.MysticNameTagsIntegration;
import com.hyperperms.integration.PlaceholderAPIIntegration;
import com.hyperperms.integration.WerChatIntegration;
import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.lifecycle.PluginLifecycle;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.stages.*;
import com.hyperperms.update.UpdateChecker;
import com.hyperperms.registry.PermissionRegistry;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.model.User;
import com.hyperperms.resolver.PermissionResolver;
import com.hyperperms.resolver.WildcardMatcher;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Main plugin class for HyperPerms.
 * <p>
 * This is the entry point for the plugin. Use {@link #getApi()} to access the API.
 */
public final class HyperPerms implements HyperPermsAPI {

    public static final String VERSION = BuildInfo.VERSION;
    
    private static volatile HyperPerms instance;

    private final Path dataDirectory;
    private final java.util.logging.Logger parentLogger;

    // Core components
    private HyperPermsConfig config;
    private StorageProvider storage;
    private PermissionCache cache;
    private CacheInvalidator cacheInvalidator;
    private PermissionResolver resolver;
    private EventBus eventBus;
    private ContextManager contextManager;
    private PlayerContextProvider playerContextProvider;
    private com.hyperperms.registry.PermissionRegistry permissionRegistry;
    private RuntimePermissionDiscovery runtimeDiscovery;

    // Chat system
    private com.hyperperms.chat.ChatManager chatManager;

    // Tab list system
    private com.hyperperms.tablist.TabListManager tabListManager;
    
    // Faction integration (optional - soft dependency on HyFactions)
    @Nullable
    private FactionIntegration factionIntegration;
    
    // WerChat integration (optional - soft dependency on WerChat)
    @Nullable
    private WerChatIntegration werchatIntegration;

    // PlaceholderAPI integration (optional - soft dependency on PlaceholderAPI)
    @Nullable
    private PlaceholderAPIIntegration placeholderApiIntegration;

    // MysticNameTags integration (optional - soft dependency on MysticNameTags)
    @Nullable
    private MysticNameTagsIntegration mysticNameTagsIntegration;

    // Web editor
    private com.hyperperms.web.WebEditorService webEditorService;

    // Backup system
    private com.hyperperms.backup.BackupManager backupManager;

    // Update checker
    @Nullable
    private UpdateChecker updateChecker;

    // Update notification preferences
    @Nullable
    private com.hyperperms.update.UpdateNotificationPreferences notificationPreferences;

    // Analytics
    @Nullable
    private com.hyperperms.analytics.AnalyticsManager analyticsManager;

    // API implementations
    @Nullable
    private QueryAPI queryApi;
    @Nullable
    private MetricsAPI metricsApi;

    // Managers
    private UserManagerImpl userManager;
    private GroupManagerImpl groupManager;
    private TrackManagerImpl trackManager;

    // Tasks
    private ScheduledExecutorService scheduler;

    // State
    private volatile boolean enabled = false;
    private volatile boolean verboseMode = false;

    // Lifecycle
    private ServiceContainer container;
    private PluginLifecycle lifecycle;

    /**
     * Creates a new HyperPerms instance.
     *
     * @param dataDirectory the plugin data directory
     * @param parentLogger  the parent logger
     */
    public HyperPerms(@NotNull Path dataDirectory, @NotNull java.util.logging.Logger parentLogger) {
        this.dataDirectory = dataDirectory;
        this.parentLogger = parentLogger;
    }

    /**
     * Gets the API instance.
     *
     * @return the API, or null if not enabled
     */
    @Nullable
    public static HyperPermsAPI getApi() {
        return instance;
    }

    /**
     * Gets the plugin instance.
     *
     * @return the instance, or null if not enabled
     */
    @Nullable
    public static HyperPerms getInstance() {
        return instance;
    }

    /**
     * Enables the plugin.
     */
    public void enable() {
        if (enabled) {
            return;
        }

        long startTime = System.currentTimeMillis();
        instance = this;

        try {
            // Initialize logger
            Logger.init(parentLogger);
            Logger.info("Enabling HyperPerms...");

            // Build and run lifecycle
            container = new ServiceContainer();
            lifecycle = new PluginLifecycle(container);

            lifecycle.addStage(new ConfigStage(dataDirectory));
            lifecycle.addStage(new StorageStage(dataDirectory));
            lifecycle.addStage(new CoreManagerStage());
            lifecycle.addStage(new DefaultGroupsStage(this));
            lifecycle.addStage(new ResolverStage());
            lifecycle.addStage(new RegistryStage(dataDirectory));
            lifecycle.addStage(new ChatStage(this));
            lifecycle.addStage(new IntegrationStage(this));
            lifecycle.addStage(new WebStage(this));
            lifecycle.addStage(new SchedulerStage());
            lifecycle.addStage(new AnalyticsStage(this, dataDirectory));

            lifecycle.initialize();

            // Populate fields from container for backward-compatible getter access
            populateFieldsFromContainer();

            enabled = true;
            long elapsed = System.currentTimeMillis() - startTime;
            Logger.info("HyperPerms enabled in %dms", elapsed);

        } catch (Exception e) {
            Logger.severe("Failed to enable HyperPerms", e);
            disable();
            throw new RuntimeException("Failed to enable HyperPerms", e);
        }
    }

    /**
     * Disables the plugin.
     */
    public void disable() {
        if (!enabled && instance == null) {
            return;
        }

        Logger.info("Disabling HyperPerms...");

        if (lifecycle != null) {
            lifecycle.shutdown();
        }

        enabled = false;
        instance = null;
        Logger.info("HyperPerms disabled");
    }

    /**
     * Populate instance fields from the service container for backward-compatible getter access.
     * This bridges the old field-based access pattern with the new container-based lifecycle.
     */
    private void populateFieldsFromContainer() {
        this.config = container.get(HyperPermsConfig.class);
        this.storage = container.get(StorageProvider.class);
        this.cache = container.get(PermissionCache.class);
        this.cacheInvalidator = container.get(CacheInvalidator.class);
        this.eventBus = container.get(EventBus.class);
        this.groupManager = container.get(GroupManagerImpl.class);
        this.trackManager = container.get(TrackManagerImpl.class);
        this.userManager = container.get(UserManagerImpl.class);
        this.resolver = container.get(PermissionResolver.class);
        this.contextManager = container.get(ContextManager.class);
        this.playerContextProvider = container.get(PlayerContextProvider.class);
        this.permissionRegistry = container.get(PermissionRegistry.class);
        this.runtimeDiscovery = container.get(RuntimePermissionDiscovery.class);
        this.chatManager = container.get(com.hyperperms.chat.ChatManager.class);
        this.tabListManager = container.get(com.hyperperms.tablist.TabListManager.class);
        this.webEditorService = container.get(com.hyperperms.web.WebEditorService.class);
        this.backupManager = container.get(com.hyperperms.backup.BackupManager.class);
        this.analyticsManager = container.getOptional(com.hyperperms.analytics.AnalyticsManager.class).orElse(null);
        this.updateChecker = container.getOptional(UpdateChecker.class).orElse(null);
        this.notificationPreferences = container.getOptional(com.hyperperms.update.UpdateNotificationPreferences.class).orElse(null);
        this.queryApi = container.getOptional(QueryAPI.class).orElse(null);
        this.metricsApi = container.getOptional(MetricsAPI.class).orElse(null);
        this.factionIntegration = container.getOptional(FactionIntegration.class).orElse(null);
        this.werchatIntegration = container.getOptional(WerChatIntegration.class).orElse(null);
        this.placeholderApiIntegration = container.getOptional(PlaceholderAPIIntegration.class).orElse(null);
        this.mysticNameTagsIntegration = container.getOptional(MysticNameTagsIntegration.class).orElse(null);
        this.scheduler = container.getOptional(ScheduledExecutorService.class).orElse(null);
        this.verboseMode = config.isVerboseEnabledByDefault();
    }

    /**
     * Reloads the plugin configuration and data.
     */
    public void reload() {
        Logger.info("Reloading HyperPerms...");

        // Reload config
        config.reload();

        // Clear caches
        cache.invalidateAll();

        // Reload data
        groupManager.loadAll().join();
        trackManager.loadAll().join();

        // Update cache settings
        cache.setEnabled(config.isCacheEnabled());

        Logger.info("HyperPerms reloaded");
    }

    // ==================== HyperPermsAPI Implementation ====================

    @Override
    public boolean hasPermission(@NotNull UUID uuid, @NotNull String permission) {
        return hasPermission(uuid, permission, ContextSet.empty());
    }

    @Override
    public boolean hasPermission(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts) {
        // Try cache first
        var cachedPerms = cache.get(uuid, contexts);
        if (cachedPerms != null) {
            if (verboseMode) {
                PermissionTrace trace = cachedPerms.checkWithTrace(permission);
                fireCheckEvent(uuid, permission, contexts, trace.result(), trace);
                return trace.result().asBoolean();
            } else {
                WildcardMatcher.TriState result = cachedPerms.check(permission);
                fireCheckEvent(uuid, permission, contexts, result, null);
                return result.asBoolean();
            }
        }

        // Load user from memory, or from storage if not yet loaded
        User user = userManager.getUser(uuid);
        if (user == null) {
            // User not in memory - load from storage synchronously
            // This ensures we get the correct permissions even if called before async load completes
            var loadResult = userManager.loadUser(uuid).join();
            if (loadResult.isPresent()) {
                user = loadResult.get();
            } else {
                user = userManager.getOrCreateUser(uuid);
            }
        }

        var resolved = resolver.resolve(user, contexts);
        cache.put(uuid, contexts, resolved);

        if (verboseMode) {
            PermissionTrace trace = resolved.checkWithTrace(permission);
            fireCheckEvent(uuid, permission, contexts, trace.result(), trace);
            return trace.result().asBoolean();
        } else {
            WildcardMatcher.TriState result = resolved.check(permission);
            fireCheckEvent(uuid, permission, contexts, result, null);
            return result.asBoolean();
        }
    }

    /**
     * Checks a permission and returns the TriState result using the cache.
     * This is used by HyperPermsPermissionSet for efficient permission checks
     * that need the full TriState (TRUE/FALSE/UNDEFINED) rather than just boolean.
     *
     * @param uuid the player UUID
     * @param permission the permission to check
     * @param contexts the contexts to check in
     * @return the TriState result of the permission check
     */
    @NotNull
    public WildcardMatcher.TriState checkPermission(@NotNull UUID uuid, @NotNull String permission, @NotNull ContextSet contexts) {
        // Try cache first
        var cachedPerms = cache.get(uuid, contexts);
        if (cachedPerms != null) {
            return cachedPerms.check(permission);
        }

        // Load user from memory, or from storage if not yet loaded
        User user = userManager.getUser(uuid);
        if (user == null) {
            var loadResult = userManager.loadUser(uuid).join();
            if (loadResult.isPresent()) {
                user = loadResult.get();
            } else {
                user = userManager.getOrCreateUser(uuid);
            }
        }

        var resolved = resolver.resolve(user, contexts);
        cache.put(uuid, contexts, resolved);
        return resolved.check(permission);
    }

    /**
     * Creates a permission check builder for fluent permission checks with contexts.
     * <p>
     * Example usage:
     * <pre>
     * boolean canBuild = HyperPerms.getInstance()
     *     .check(playerUuid)
     *     .permission("build.place")
     *     .inWorld("nether")
     *     .withGamemode("survival")
     *     .result();
     * </pre>
     *
     * @param uuid the player UUID
     * @return a new permission check builder
     */
    @NotNull
    public PermissionCheckBuilder check(@NotNull UUID uuid) {
        return new PermissionCheckBuilder(this, uuid);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> hasPermissionAsync(@NotNull UUID uuid, @NotNull String permission,
                                                          @NotNull ContextSet contexts) {
        return CompletableFuture.supplyAsync(() -> hasPermission(uuid, permission, contexts));
    }

    @Override
    @NotNull
    public TriState getPermissionValue(@NotNull UUID uuid, @NotNull String permission,
                                        @NotNull ContextSet contexts) {
        com.hyperperms.resolver.WildcardMatcher.TriState internal = checkPermission(uuid, permission, contexts);
        return TriState.fromInternal(internal);
    }

    @Override
    @NotNull
    public CompletableFuture<TriState> getPermissionValueAsync(@NotNull UUID uuid, @NotNull String permission,
                                                                @NotNull ContextSet contexts) {
        return CompletableFuture.supplyAsync(() -> getPermissionValue(uuid, permission, contexts));
    }

    @Override
    @NotNull
    public AsyncPermissionCheckBuilder checkAsync(@NotNull UUID uuid) {
        return new AsyncPermissionCheckBuilder(this, uuid);
    }

    @Override
    @NotNull
    public java.util.concurrent.Executor getSyncExecutor() {
        // Return the scheduler executor if available, otherwise the common pool
        // In a real implementation, this would be the main thread executor from the platform
        return scheduler != null ? scheduler : java.util.concurrent.ForkJoinPool.commonPool();
    }

    private void fireCheckEvent(UUID uuid, String permission, ContextSet contexts,
                                 com.hyperperms.resolver.WildcardMatcher.TriState result,
                                 PermissionTrace trace) {
        if (verboseMode) {
            if (trace != null) {
                Logger.debug("Permission check: %s has %s = %s (from %s via %s)",
                        uuid, permission, result, trace.getSourceDescription(), trace.matchType());
            } else {
                Logger.debug("Permission check: %s has %s = %s", uuid, permission, result);
            }
        }
        eventBus.fire(new PermissionCheckEvent(uuid, permission, contexts, result, "resolver", trace));
    }

    @Override
    @NotNull
    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    @NotNull
    public GroupManager getGroupManager() {
        return groupManager;
    }

    @Override
    @NotNull
    public TrackManager getTrackManager() {
        return trackManager;
    }

    @Override
    @NotNull
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    @NotNull
    public ContextSet getContexts(@NotNull UUID uuid) {
        return contextManager.getContexts(uuid);
    }

    @Override
    @NotNull
    public Set<String> getResolvedPermissions(@NotNull UUID uuid) {
        User user = userManager.getUser(uuid);
        if (user == null) {
            var loadResult = userManager.loadUser(uuid).join();
            if (loadResult.isPresent()) {
                user = loadResult.get();
            } else {
                user = userManager.getOrCreateUser(uuid);
            }
        }
        ContextSet contexts = contextManager.getContexts(uuid);
        var resolved = resolver.resolve(user, contexts);
        return resolved.getGrantedPermissions();
    }

    @Override
    @NotNull
    public QueryAPI getQuery() {
        if (queryApi == null) {
            throw new IllegalStateException("QueryAPI not initialized - plugin may not be fully enabled");
        }
        return queryApi;
    }

    @Override
    @Nullable
    public MetricsAPI getMetrics() {
        return metricsApi;
    }

    // ==================== Accessors ====================

    /**
     * Gets the configuration.
     *
     * @return the config
     */
    @NotNull
    public HyperPermsConfig getConfig() {
        return config;
    }

    /**
     * Gets the storage provider.
     *
     * @return the storage
     */
    @NotNull
    public StorageProvider getStorage() {
        return storage;
    }

    /**
     * Gets the permission cache.
     *
     * @return the cache
     */
    @NotNull
    public PermissionCache getCache() {
        return cache;
    }

    /**
     * Gets the cache invalidator.
     *
     * @return the cache invalidator
     */
    @NotNull
    public CacheInvalidator getCacheInvalidator() {
        return cacheInvalidator;
    }

    /**
     * Gets the permission resolver.
     *
     * @return the resolver
     */
    @NotNull
    public PermissionResolver getResolver() {
        return resolver;
    }

    /**
     * Checks if verbose mode is enabled.
     *
     * @return true if verbose
     */
    public boolean isVerboseMode() {
        return verboseMode;
    }

    /**
     * Sets verbose mode.
     *
     * @param verbose true to enable
     */
    public void setVerboseMode(boolean verbose) {
        this.verboseMode = verbose;
    }

    /**
     * Checks if the plugin is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the context manager.
     *
     * @return the context manager
     */
    @NotNull
    public ContextManager getContextManager() {
        return contextManager;
    }

    /**
     * Gets the permission registry.
     * <p>
     * The permission registry tracks all registered permissions from HyperPerms
     * and external plugins, with descriptions and categories.
     *
     * @return the permission registry
     */
    @NotNull
    public com.hyperperms.registry.PermissionRegistry getPermissionRegistry() {
        return permissionRegistry;
    }

    /**
     * Gets the runtime permission discovery system.
     * <p>
     * The discovery system captures permissions checked at runtime that are not
     * registered in the built-in registry, persists them to disk, and prunes
     * permissions from plugins that are no longer installed.
     *
     * @return the runtime discovery, or null if not yet initialized
     */
    @Nullable
    public RuntimePermissionDiscovery getRuntimeDiscovery() {
        return runtimeDiscovery;
    }

    /**
     * Gets the player context provider.
     *
     * @return the player context provider
     */
    @NotNull
    public PlayerContextProvider getPlayerContextProvider() {
        return playerContextProvider;
    }

    /**
     * Sets the player context provider.
     * <p>
     * This should be called by the platform adapter to provide
     * player-specific context data like world and game mode.
     *
     * @param provider the player context provider
     */
    public void setPlayerContextProvider(@NotNull PlayerContextProvider provider) {
        this.playerContextProvider = provider;
        // Re-register calculators with new provider
        contextManager.clear();
        registerDefaultContextCalculators();
    }

    /**
     * Registers the default context calculators.
     */
    private void registerDefaultContextCalculators() {
        // World context
        contextManager.registerCalculator(new WorldContextCalculator(playerContextProvider));

        // Game mode context
        contextManager.registerCalculator(new GameModeContextCalculator(playerContextProvider));

        // Time context (day/night/dawn/dusk)
        contextManager.registerCalculator(new com.hyperperms.context.calculators.TimeContextCalculator(playerContextProvider));

        // Biome context
        contextManager.registerCalculator(new com.hyperperms.context.calculators.BiomeContextCalculator(playerContextProvider));

        // Region context
        contextManager.registerCalculator(new com.hyperperms.context.calculators.RegionContextCalculator(playerContextProvider));

        // Server context (only if configured)
        String serverName = config.getServerName();
        if (!serverName.isEmpty()) {
            contextManager.registerCalculator(new ServerContextCalculator(serverName));
        }

        Logger.debug("Registered %d context calculators", contextManager.getCalculatorCount());
    }

    /**
     * Loads default groups from the default-groups.json resource.
     * <p>
     * This is called on first run when no groups exist in storage.
     * It creates a standard group hierarchy: default -> member -> builder -> moderator -> admin -> owner
     */
    public void loadDefaultGroups() {
        Logger.info("No groups found, loading default groups...");
        
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("default-groups.json")) {
            if (inputStream == null) {
                Logger.warn("default-groups.json not found in resources, skipping default group creation");
                return;
            }

            String json = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            com.google.gson.JsonObject groups = root.getAsJsonObject("groups");

            if (groups == null) {
                Logger.warn("No 'groups' object found in default-groups.json");
                return;
            }

            int created = 0;
            for (var entry : groups.entrySet()) {
                String groupName = entry.getKey();
                com.google.gson.JsonObject groupData = entry.getValue().getAsJsonObject();

                // Create the group
                com.hyperperms.model.Group group = groupManager.createGroup(groupName);

                // Set weight
                if (groupData.has("weight")) {
                    group.setWeight(groupData.get("weight").getAsInt());
                }

                // Set prefix
                if (groupData.has("prefix")) {
                    group.setPrefix(groupData.get("prefix").getAsString());
                }

                // Set suffix
                if (groupData.has("suffix")) {
                    group.setSuffix(groupData.get("suffix").getAsString());
                }

                // Add permissions
                if (groupData.has("permissions")) {
                    for (var perm : groupData.getAsJsonArray("permissions")) {
                        group.addNode(com.hyperperms.model.Node.builder(perm.getAsString()).build());
                    }
                }

                // Add parent groups (will be resolved after all groups are created)
                if (groupData.has("parents")) {
                    for (var parent : groupData.getAsJsonArray("parents")) {
                        group.addParent(parent.getAsString());
                    }
                }

                // Save the group
                groupManager.saveGroup(group).join();
                created++;
                Logger.debug("Created default group: %s (weight=%d)", groupName, group.getWeight());
            }

            Logger.info("Loaded %d default groups from default-groups.json", created);

        } catch (Exception e) {
            Logger.warn("Failed to load default groups: %s", e.getMessage());
            Logger.debug("Stack trace: ", e);
        }
    }

    /**
     * Gets the chat manager.
     * <p>
     * The chat manager handles prefix/suffix resolution and chat formatting.
     *
     * @return the chat manager, or null if not yet initialized
     */
    @Nullable
    public com.hyperperms.chat.ChatManager getChatManager() {
        return chatManager;
    }

    /**
     * Gets the tab list manager.
     * <p>
     * The tab list manager handles tab list name formatting.
     *
     * @return the tab list manager, or null if not yet initialized
     */
    @Nullable
    public com.hyperperms.tablist.TabListManager getTabListManager() {
        return tabListManager;
    }

    /**
     * Gets the faction integration.
     * <p>
     * The faction integration provides HyFactions support for chat placeholders.
     * Returns null if HyFactions is not installed.
     *
     * @return the faction integration, or null if HyFactions is not available
     */
    @Nullable
    public FactionIntegration getFactionIntegration() {
        return factionIntegration;
    }

    /**
     * Gets the WerChat integration.
     * <p>
     * The WerChat integration provides WerChat support for chat channel placeholders.
     * Returns null if WerChat is not installed.
     *
     * @return the WerChat integration, or null if WerChat is not available
     */
    @Nullable
    public WerChatIntegration getWerChatIntegration() {
        return werchatIntegration;
    }

    /**
     * Gets the PlaceholderAPI integration.
     * <p>
     * The PlaceholderAPI integration provides two-way integration:
     * <ul>
     *   <li>Exposes HyperPerms placeholders to other plugins</li>
     *   <li>Parses external PAPI placeholders in chat format</li>
     * </ul>
     * Returns null if PlaceholderAPI is not installed.
     *
     * @return the PlaceholderAPI integration, or null if not available
     */
    @Nullable
    public PlaceholderAPIIntegration getPlaceholderAPIIntegration() {
        return placeholderApiIntegration;
    }

    /**
     * Gets the MysticNameTags integration.
     * <p>
     * The MysticNameTags integration provides tag cache invalidation when
     * permissions or groups change, ensuring tag availability updates immediately.
     * Returns null if MysticNameTags is not installed.
     *
     * @return the MysticNameTags integration, or null if not available
     */
    @Nullable
    public MysticNameTagsIntegration getMysticNameTagsIntegration() {
        return mysticNameTagsIntegration;
    }

    /**
     * Gets the backup manager.
     * <p>
     * The backup manager handles automatic and manual backups.
     *
     * @return the backup manager, or null if not yet initialized
     */
    @Nullable
    public com.hyperperms.backup.BackupManager getBackupManager() {
        return backupManager;
    }


    /**
     * Gets the web editor service.
     * <p>
     * The web editor service handles communication with the remote web editor.
     *
     * @return the web editor service, or null if not yet initialized
     */
    @Nullable
    public com.hyperperms.web.WebEditorService getWebEditorService() {
        return webEditorService;
    }


    /**
     * Gets the plugin version.
     *
     * @return the current plugin version
     */
    @NotNull
    public String getVersion() {
        return VERSION;
    }

    /**
     * Gets the update checker.
     * <p>
     * The update checker handles checking for and downloading plugin updates.
     *
     * @return the update checker, or null if update checking is disabled
     */
    @Nullable
    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    /**
     * Gets the analytics manager.
     * <p>
     * The analytics manager tracks permission check statistics and audit logs.
     *
     * @return the analytics manager, or null if analytics is disabled
     */
    @Nullable
    public com.hyperperms.analytics.AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    /**
     * Gets the update notification preferences.
     * <p>
     * The notification preferences track which players want to receive
     * update notifications on join.
     *
     * @return the notification preferences, or null if not yet initialized
     */
    @Nullable
    public com.hyperperms.update.UpdateNotificationPreferences getNotificationPreferences() {
        return notificationPreferences;
    }

    /**
     * Gets the plugin data directory.
     *
     * @return the data directory path
     */
    @NotNull
    public Path getDataDirectory() {
        return dataDirectory;
    }

}
