# Phase 1: Staged Lifecycle & Service Container — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the 250-line `HyperPerms.enable()` monolith with discrete Stage classes orchestrated by a PluginLifecycle, backed by a ServiceContainer for typed service access.

**Architecture:** A `ServiceContainer` holds all services as typed singletons. A `PluginLifecycle` runs `Stage` objects in order during startup and in reverse during shutdown. Each Stage encapsulates one logical initialization step. `HyperPerms.enable()` becomes ~20 lines that build stages and delegate to the lifecycle.

**Tech Stack:** Java 25, no new dependencies (uses existing Caffeine, HikariCP, GSON, etc.)

**Worktree:** `.worktrees/architecture-rehaul` on branch `refactor/architecture-rehaul`

**Git Author:** `ZenithDevHQ <scrubc1ty4ever@gmail.com>` — do NOT mention Claude or nmang004

**Base path:** `src/main/java/com/hyperperms/lifecycle/`

---

### Task 1: Create ServiceContainer

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/ServiceContainer.java`

**Step 1: Create the ServiceContainer class**

```java
package com.hyperperms.lifecycle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed singleton registry for plugin services.
 * Services are registered during initialization and retrieved by type.
 */
public final class ServiceContainer {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Register a service instance for the given type.
     *
     * @throws IllegalStateException if a service of this type is already registered
     */
    public <T> void register(@NotNull Class<T> type, @NotNull T instance) {
        Object existing = services.putIfAbsent(type, instance);
        if (existing != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    /**
     * Get a required service by type.
     *
     * @throws IllegalStateException if the service is not registered
     */
    @NotNull
    public <T> T get(@NotNull Class<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return type.cast(service);
    }

    /**
     * Get an optional service by type. Returns empty if not registered.
     */
    @NotNull
    public <T> Optional<T> getOptional(@NotNull Class<T> type) {
        Object service = services.get(type);
        return service != null ? Optional.of(type.cast(service)) : Optional.empty();
    }

    /**
     * Check if a service is registered.
     */
    public boolean has(@NotNull Class<?> type) {
        return services.containsKey(type);
    }

    /**
     * Remove all registered services.
     */
    public void clear() {
        services.clear();
    }
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/ServiceContainer.java
git commit -m "refactor(lifecycle): add ServiceContainer typed service registry"
```

---

### Task 2: Create Stage Interface and PluginLifecycle

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/Stage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/PluginLifecycle.java`

**Step 1: Create the Stage interface**

```java
package com.hyperperms.lifecycle;

import org.jetbrains.annotations.NotNull;

/**
 * A discrete initialization stage in the plugin lifecycle.
 * Stages run in {@link #order()} sequence during startup
 * and in reverse during shutdown.
 */
public interface Stage {

    /**
     * Human-readable name for logging (e.g. "Storage", "Managers").
     */
    @NotNull
    String name();

    /**
     * Execution order. Lower values run first.
     * Use increments of 100 to leave room for future stages.
     */
    int order();

    /**
     * Initialize this stage. Called during plugin startup.
     * May register services in the container for later stages to consume.
     *
     * @param container the service container
     * @throws Exception if initialization fails (aborts startup)
     */
    void initialize(@NotNull ServiceContainer container) throws Exception;

    /**
     * Shutdown this stage. Called during plugin disable in reverse order.
     * Should clean up resources registered during {@link #initialize}.
     *
     * @param container the service container
     */
    default void shutdown(@NotNull ServiceContainer container) {
        // Default no-op — most stages don't need explicit shutdown
    }
}
```

**Step 2: Create the PluginLifecycle orchestrator**

```java
package com.hyperperms.lifecycle;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates plugin startup and shutdown by running {@link Stage} objects
 * in order during initialization and in reverse during shutdown.
 */
public final class PluginLifecycle {

    private final ServiceContainer container;
    private final List<Stage> stages = new ArrayList<>();
    private final List<Stage> initializedStages = new ArrayList<>();

    public PluginLifecycle(@NotNull ServiceContainer container) {
        this.container = container;
    }

    /**
     * Add a stage to the lifecycle.
     */
    public void addStage(@NotNull Stage stage) {
        stages.add(stage);
    }

    /**
     * Initialize all stages in order. If any stage fails,
     * previously initialized stages are shut down in reverse.
     *
     * @throws Exception if a stage fails to initialize
     */
    public void initialize() throws Exception {
        stages.sort(Comparator.comparingInt(Stage::order));

        for (Stage stage : stages) {
            try {
                Logger.debug("[Lifecycle] Initializing stage: %s (order=%d)", stage.name(), stage.order());
                stage.initialize(container);
                initializedStages.add(stage);
            } catch (Exception e) {
                Logger.severe("Stage '%s' failed to initialize: %s", stage.name(), e.getMessage());
                // Shut down any stages that already initialized
                shutdownInitialized();
                throw e;
            }
        }
    }

    /**
     * Shutdown all initialized stages in reverse order.
     */
    public void shutdown() {
        shutdownInitialized();
        container.clear();
    }

    private void shutdownInitialized() {
        // Reverse order shutdown
        for (int i = initializedStages.size() - 1; i >= 0; i--) {
            Stage stage = initializedStages.get(i);
            try {
                Logger.debug("[Lifecycle] Shutting down stage: %s", stage.name());
                stage.shutdown(container);
            } catch (Exception e) {
                Logger.warn("Stage '%s' failed to shut down cleanly: %s", stage.name(), e.getMessage());
            }
        }
        initializedStages.clear();
    }
}
```

**Step 3: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/Stage.java src/main/java/com/hyperperms/lifecycle/PluginLifecycle.java
git commit -m "refactor(lifecycle): add Stage interface and PluginLifecycle orchestrator"
```

---

### Task 3: Create ConfigStage and StorageStage

These are the first two stages — config must load before storage can be created.

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/ConfigStage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/stages/StorageStage.java`

**Step 1: Create ConfigStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.util.Logger;
import com.hyperperms.util.SQLiteDriverLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads plugin configuration and prepares the lib directory.
 */
public final class ConfigStage implements Stage {

    private final Path dataDirectory;

    public ConfigStage(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Config";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        // Initialize lib directory for optional SQLite driver
        Path libDir = dataDirectory.resolve("lib");
        try {
            Files.createDirectories(libDir);
        } catch (IOException e) {
            Logger.warn("Failed to create lib directory: %s", e.getMessage());
        }
        SQLiteDriverLoader.setLibDirectory(libDir);
        Logger.debug("SQLite lib directory: %s", libDir);

        // Load configuration
        HyperPermsConfig config = new HyperPermsConfig(dataDirectory);
        config.load();
        container.register(HyperPermsConfig.class, config);
    }
}
```

**Step 2: Create StorageStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.util.Logger;
import com.hyperperms.storage.StorageFactory;
import com.hyperperms.storage.StorageProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Initializes the storage provider and permission cache.
 */
public final class StorageStage implements Stage {

    private final Path dataDirectory;

    public StorageStage(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Storage";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);

        // Initialize storage
        StorageProvider storage = StorageFactory.createStorage(config, dataDirectory);
        storage.init().join();
        container.register(StorageProvider.class, storage);

        // Initialize cache
        PermissionCache cache = new PermissionCache(
                config.getCacheMaxSize(),
                config.getCacheExpirySeconds(),
                config.isCacheEnabled()
        );
        container.register(PermissionCache.class, cache);

        CacheInvalidator cacheInvalidator = new CacheInvalidator(cache);
        container.register(CacheInvalidator.class, cacheInvalidator);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(StorageProvider.class).ifPresent(storage -> {
            try {
                storage.shutdown().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.warn("Failed to shutdown storage cleanly: %s", e.getMessage());
            }
        });
    }
}
```

**Step 3: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/ConfigStage.java src/main/java/com/hyperperms/lifecycle/stages/StorageStage.java
git commit -m "refactor(lifecycle): add ConfigStage and StorageStage"
```

---

### Task 4: Create CoreManagerStage

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/CoreManagerStage.java`

**Step 1: Create CoreManagerStage**

This stage creates the EventBus, all three managers, loads data, and ensures default groups exist.

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.api.events.EventBus;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Creates the EventBus and core managers (Group, Track, User), then loads all data.
 */
public final class CoreManagerStage implements Stage {

    @Override
    public @NotNull String name() {
        return "Core Managers";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        StorageProvider storage = container.get(StorageProvider.class);
        PermissionCache cache = container.get(PermissionCache.class);
        CacheInvalidator cacheInvalidator = container.get(CacheInvalidator.class);

        // Create event bus
        EventBus eventBus = new EventBus();
        container.register(EventBus.class, eventBus);

        // Create managers
        GroupManagerImpl groupManager = new GroupManagerImpl(storage, cacheInvalidator, eventBus);
        TrackManagerImpl trackManager = new TrackManagerImpl(storage);
        UserManagerImpl userManager = new UserManagerImpl(storage, cache, eventBus, config.getDefaultGroup());

        container.register(GroupManagerImpl.class, groupManager);
        container.register(TrackManagerImpl.class, trackManager);
        container.register(UserManagerImpl.class, userManager);

        // Load data
        groupManager.loadAll().join();
        trackManager.loadAll().join();
        userManager.loadAll().join();
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        // Save all user data
        container.getOptional(UserManagerImpl.class).ifPresent(userManager -> {
            try {
                userManager.saveAll().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.warn("Failed to save users on shutdown: %s", e.getMessage());
            }
        });

        // Clear event bus
        container.getOptional(EventBus.class).ifPresent(EventBus::clear);
    }
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/CoreManagerStage.java
git commit -m "refactor(lifecycle): add CoreManagerStage with EventBus and managers"
```

---

### Task 5: Create ResolverStage and RegistryStage

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/ResolverStage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/stages/RegistryStage.java`

**Step 1: Create ResolverStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.context.ContextManager;
import com.hyperperms.context.PlayerContextProvider;
import com.hyperperms.context.calculators.*;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.resolver.PermissionResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the permission resolver and context system.
 */
public final class ResolverStage implements Stage {

    @Override
    public @NotNull String name() {
        return "Resolver";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);

        // Initialize resolver
        PermissionResolver resolver = new PermissionResolver(groupManager::getGroup);
        container.register(PermissionResolver.class, resolver);

        // Initialize context system
        ContextManager contextManager = new ContextManager();
        container.register(ContextManager.class, contextManager);

        PlayerContextProvider playerContextProvider = PlayerContextProvider.EMPTY;
        container.register(PlayerContextProvider.class, playerContextProvider);

        // Register default context calculators
        contextManager.registerCalculator(new WorldContextCalculator());
        contextManager.registerCalculator(new GameModeContextCalculator());
        contextManager.registerCalculator(new TimeContextCalculator());
        contextManager.registerCalculator(new BiomeContextCalculator());
        contextManager.registerCalculator(new ServerContextCalculator());
        contextManager.registerCalculator(new RegionContextCalculator());
    }
}
```

**Step 2: Create RegistryStage**

Note: This stage needs access to `dataDirectory` and `resolvePluginsDirectory()`. The `resolvePluginsDirectory` method is currently on HyperPerms. We'll pass `dataDirectory` to the stage constructor and move the plugins directory resolution logic.

First, read `resolvePluginsDirectory` from HyperPerms.java to understand it:

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.registry.PermissionRegistry;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Initializes the permission registry and runtime permission discovery.
 */
public final class RegistryStage implements Stage {

    private final Path dataDirectory;

    public RegistryStage(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Registry";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        // Initialize permission registry
        PermissionRegistry permissionRegistry = PermissionRegistry.getInstance();
        permissionRegistry.registerBuiltInPermissions();
        container.register(PermissionRegistry.class, permissionRegistry);

        // Initialize runtime permission discovery
        Logger.info("[Discovery] Initializing runtime permission discovery...");
        Path pluginsDir = resolvePluginsDirectory(dataDirectory);

        RuntimePermissionDiscovery runtimeDiscovery = new RuntimePermissionDiscovery(dataDirectory, pluginsDir);
        runtimeDiscovery.load();
        java.util.Set<String> installedPlugins = runtimeDiscovery.scanInstalledPlugins();
        runtimeDiscovery.buildNamespaceMapping();
        runtimeDiscovery.scanJarPermissions(installedPlugins);
        runtimeDiscovery.pruneRemovedPlugins(installedPlugins);
        permissionRegistry.registerDiscoveredPermissions(runtimeDiscovery);

        container.register(RuntimePermissionDiscovery.class, runtimeDiscovery);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(RuntimePermissionDiscovery.class).ifPresent(discovery -> {
            try {
                discovery.save();
            } catch (Exception e) {
                Logger.warn("Failed to save discovered permissions on shutdown");
            }
        });
    }

    /**
     * Resolve the plugins/mods directory from the data directory.
     * Walks up from the data directory to find the parent mods/ or plugins/ folder.
     */
    private static Path resolvePluginsDirectory(Path dataDirectory) {
        // dataDirectory is typically: mods/com.hyperperms_HyperPerms/data
        // We want: mods/
        Path parent = dataDirectory;
        for (int i = 0; i < 3 && parent != null; i++) {
            parent = parent.getParent();
        }
        if (parent != null && Files.isDirectory(parent)) {
            return parent;
        }
        // Fallback: check for "plugins" or "mods" as sibling
        Path modsDir = dataDirectory.getParent();
        if (modsDir != null) {
            modsDir = modsDir.getParent();
        }
        if (modsDir != null && Files.isDirectory(modsDir)) {
            return modsDir;
        }
        Logger.warn("Could not resolve plugins directory from: %s", dataDirectory);
        return dataDirectory;
    }
}
```

**Important:** Check the actual `resolvePluginsDirectory` method in HyperPerms.java and match it exactly. The implementation above is approximate — the implementer should read `HyperPerms.java` and copy the exact logic.

**Step 3: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/ResolverStage.java src/main/java/com/hyperperms/lifecycle/stages/RegistryStage.java
git commit -m "refactor(lifecycle): add ResolverStage and RegistryStage"
```

---

### Task 6: Create ChatStage, IntegrationStage, and WebStage

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/ChatStage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/stages/IntegrationStage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/stages/WebStage.java`

**Step 1: Create ChatStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the chat manager and tab list manager.
 */
public final class ChatStage implements Stage {

    private final HyperPerms plugin;

    public ChatStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Chat";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        com.hyperperms.chat.ChatManager chatManager = new com.hyperperms.chat.ChatManager(plugin);
        chatManager.loadConfig();
        container.register(com.hyperperms.chat.ChatManager.class, chatManager);

        com.hyperperms.tablist.TabListManager tabListManager = new com.hyperperms.tablist.TabListManager(plugin);
        tabListManager.loadConfig();
        container.register(com.hyperperms.tablist.TabListManager.class, tabListManager);
    }
}
```

**Step 2: Create IntegrationStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.integration.*;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes soft-dependency plugin integrations.
 */
public final class IntegrationStage implements Stage {

    private final HyperPerms plugin;

    public IntegrationStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Integrations";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        com.hyperperms.chat.ChatManager chatManager = container.get(com.hyperperms.chat.ChatManager.class);

        // Faction integration
        Logger.debugIntegration("Initializing faction integration...");
        FactionIntegration factionIntegration = new FactionIntegration(plugin);
        factionIntegration.setEnabled(config.isFactionIntegrationEnabled());
        factionIntegration.setNoFactionDefault(config.getFactionNoFactionDefault());
        factionIntegration.setNoRankDefault(config.getFactionNoRankDefault());
        factionIntegration.setFactionFormat(config.getFactionFormat());
        factionIntegration.setPrefixEnabled(config.isFactionPrefixEnabled());
        factionIntegration.setPrefixFormat(config.getFactionPrefixFormat());
        factionIntegration.setShowRank(config.isFactionShowRank());
        factionIntegration.setPrefixWithRankFormat(config.getFactionPrefixWithRankFormat());
        chatManager.setFactionIntegration(factionIntegration);
        container.register(FactionIntegration.class, factionIntegration);

        // WerChat integration
        Logger.debugIntegration("Initializing WerChat integration...");
        WerChatIntegration werchatIntegration = new WerChatIntegration(plugin);
        werchatIntegration.setEnabled(config.isWerChatIntegrationEnabled());
        werchatIntegration.setNoChannelDefault(config.getWerChatNoChannelDefault());
        werchatIntegration.setChannelFormat(config.getWerChatChannelFormat());
        chatManager.setWerChatIntegration(werchatIntegration);
        container.register(WerChatIntegration.class, werchatIntegration);

        // PlaceholderAPI integration
        Logger.debugIntegration("Initializing PlaceholderAPI integration...");
        PlaceholderAPIIntegration placeholderApi = new PlaceholderAPIIntegration(plugin);
        placeholderApi.setEnabled(config.isPlaceholderAPIEnabled());
        placeholderApi.setParseExternal(config.isPlaceholderAPIParseExternal());
        chatManager.setPlaceholderAPIIntegration(placeholderApi);
        if (placeholderApi.isAvailable()) {
            Logger.info("PlaceholderAPI integration enabled - placeholders available");
        }
        container.register(PlaceholderAPIIntegration.class, placeholderApi);

        // MysticNameTags integration
        Logger.debugIntegration("Initializing MysticNameTags integration...");
        MysticNameTagsIntegration mysticNameTags = new MysticNameTagsIntegration(plugin);
        mysticNameTags.setEnabled(config.isMysticNameTagsEnabled());
        mysticNameTags.setRefreshOnPermissionChange(config.isMysticNameTagsRefreshOnPermissionChange());
        mysticNameTags.setRefreshOnGroupChange(config.isMysticNameTagsRefreshOnGroupChange());
        mysticNameTags.setTagPermissionPrefix(config.getMysticNameTagsPermissionPrefix());
        if (mysticNameTags.isAvailable()) {
            Logger.info("MysticNameTags integration enabled - tag permission sync active");
        }
        container.register(MysticNameTagsIntegration.class, mysticNameTags);

        // VaultUnlocked integration
        if (config.isVaultIntegrationEnabled()) {
            Logger.debugIntegration("Initializing VaultUnlocked integration...");
            VaultUnlockedIntegration.init(plugin);
        } else {
            Logger.debugIntegration("VaultUnlocked integration disabled in config");
        }
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        VaultUnlockedIntegration.shutdown();

        container.getOptional(MysticNameTagsIntegration.class)
                .ifPresent(MysticNameTagsIntegration::unregister);

        container.getOptional(PlaceholderAPIIntegration.class)
                .ifPresent(PlaceholderAPIIntegration::unregister);
    }
}
```

**Step 3: Create WebStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the web editor service and backup manager.
 */
public final class WebStage implements Stage {

    private final HyperPerms plugin;

    public WebStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Web";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        com.hyperperms.web.WebEditorService webEditorService = new com.hyperperms.web.WebEditorService(plugin);
        container.register(com.hyperperms.web.WebEditorService.class, webEditorService);

        com.hyperperms.backup.BackupManager backupManager = new com.hyperperms.backup.BackupManager(plugin);
        backupManager.start();
        container.register(com.hyperperms.backup.BackupManager.class, backupManager);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(com.hyperperms.backup.BackupManager.class)
                .ifPresent(com.hyperperms.backup.BackupManager::shutdown);
    }
}
```

**Step 4: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/ChatStage.java src/main/java/com/hyperperms/lifecycle/stages/IntegrationStage.java src/main/java/com/hyperperms/lifecycle/stages/WebStage.java
git commit -m "refactor(lifecycle): add ChatStage, IntegrationStage, and WebStage"
```

---

### Task 7: Create SchedulerStage and AnalyticsStage

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/SchedulerStage.java`
- Create: `src/main/java/com/hyperperms/lifecycle/stages/AnalyticsStage.java`

**Step 1: Create SchedulerStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.api.events.EventBus;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.task.ExpiryCleanupTask;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts scheduled tasks: expiry cleanup and discovery auto-save.
 */
public final class SchedulerStage implements Stage {

    private ScheduledExecutorService scheduler;

    @Override
    public @NotNull String name() {
        return "Scheduler";
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        UserManagerImpl userManager = container.get(UserManagerImpl.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);
        EventBus eventBus = container.get(EventBus.class);
        RuntimePermissionDiscovery runtimeDiscovery = container.get(RuntimePermissionDiscovery.class);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyperPerms-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                new ExpiryCleanupTask(userManager, groupManager, eventBus),
                config.getExpiryCheckInterval(),
                config.getExpiryCheckInterval(),
                TimeUnit.SECONDS
        );

        // Discovery auto-save every 5 minutes
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        runtimeDiscovery.save();
                    } catch (Exception e) {
                        Logger.warn("Failed to auto-save discovered permissions: %s", e.getMessage());
                    }
                },
                300, 300, TimeUnit.SECONDS
        );

        container.register(ScheduledExecutorService.class, scheduler);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

**Step 2: Create AnalyticsStage**

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.metrics.MetricsAPIImpl;
import com.hyperperms.query.QueryAPIImpl;
import com.hyperperms.api.MetricsAPI;
import com.hyperperms.api.QueryAPI;
import com.hyperperms.update.UpdateChecker;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Initializes analytics, update checking, console links, verbose mode, and API implementations.
 */
public final class AnalyticsStage implements Stage {

    private final HyperPerms plugin;
    private final Path dataDirectory;

    public AnalyticsStage(@NotNull HyperPerms plugin, @NotNull Path dataDirectory) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Analytics";
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        UserManagerImpl userManager = container.get(UserManagerImpl.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);
        TrackManagerImpl trackManager = container.get(TrackManagerImpl.class);
        PermissionCache cache = container.get(PermissionCache.class);

        // Verbose mode
        // Note: verboseMode is set on the HyperPerms instance directly

        // Console links
        com.hyperperms.util.ConsoleLinks.setEnabled(config.isConsoleClickableLinksEnabled());
        com.hyperperms.util.ConsoleLinks.setForceOsc8(config.isConsoleForceOsc8());
        Logger.debug("Console links: enabled=%s, forceOsc8=%s, osc8Supported=%s",
                config.isConsoleClickableLinksEnabled(),
                config.isConsoleForceOsc8(),
                com.hyperperms.util.ConsoleLinks.isOsc8Supported());

        // Analytics manager
        com.hyperperms.analytics.AnalyticsManager analyticsManager =
                new com.hyperperms.analytics.AnalyticsManager(plugin);
        analyticsManager.start();
        container.register(com.hyperperms.analytics.AnalyticsManager.class, analyticsManager);

        // Update checker
        if (config.isUpdateCheckEnabled()) {
            UpdateChecker updateChecker = new UpdateChecker(plugin, HyperPerms.VERSION, config.getUpdateCheckUrl());
            updateChecker.checkForUpdates().thenAccept(info -> {
                if (info != null) {
                    Logger.info("[Update] A new version is available: v%s (current: v%s)",
                            info.version(), HyperPerms.VERSION);
                    if (config.isUpdateChangelogEnabled() && info.changelog() != null && !info.changelog().isEmpty()) {
                        Logger.info("[Update] Changelog: %s", info.changelog());
                    }
                }
            });
            container.register(UpdateChecker.class, updateChecker);
        }

        // Update notification preferences
        com.hyperperms.update.UpdateNotificationPreferences notificationPrefs =
                new com.hyperperms.update.UpdateNotificationPreferences(dataDirectory);
        notificationPrefs.load();
        container.register(com.hyperperms.update.UpdateNotificationPreferences.class, notificationPrefs);

        // API implementations
        QueryAPI queryApi = new QueryAPIImpl(userManager, groupManager, () -> trackManager.getLoadedTracks());
        container.register(QueryAPI.class, queryApi);

        if (analyticsManager.isEnabled()) {
            MetricsAPI metricsApi = new MetricsAPIImpl(
                    analyticsManager,
                    () -> cache.getStatistics(),
                    () -> (int) cache.size()
            );
            container.register(MetricsAPI.class, metricsApi);
        }
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(com.hyperperms.analytics.AnalyticsManager.class)
                .ifPresent(com.hyperperms.analytics.AnalyticsManager::stop);
    }
}
```

**Step 3: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/SchedulerStage.java src/main/java/com/hyperperms/lifecycle/stages/AnalyticsStage.java
git commit -m "refactor(lifecycle): add SchedulerStage and AnalyticsStage"
```

---

### Task 8: Create DefaultGroupsStage

The default groups loading logic currently sits in `enable()` between manager loading and resolver creation. It needs its own stage.

**Files:**
- Create: `src/main/java/com/hyperperms/lifecycle/stages/DefaultGroupsStage.java`

**Step 1: Create DefaultGroupsStage**

Read `HyperPerms.java` for the `loadDefaultGroups()` method and the default group logic (lines ~229-236) to understand the exact behavior. The stage should:
1. Check if `groupManager.getLoadedGroups().isEmpty()`
2. If so, call the default groups loading logic (delegates to `HyperPerms.loadDefaultGroups()`)
3. Ensure default group exists via `groupManager.ensureDefaultGroup(config.getDefaultGroup())`

```java
package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Loads default groups on first run and ensures the configured default group exists.
 */
public final class DefaultGroupsStage implements Stage {

    private final HyperPerms plugin;

    public DefaultGroupsStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Default Groups";
    }

    @Override
    public int order() {
        return 350; // After CoreManagerStage (300), before ResolverStage (400)
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);

        // Load default groups on first run
        if (groupManager.getLoadedGroups().isEmpty()) {
            plugin.loadDefaultGroups();
        }

        // Ensure default group exists
        if (config.shouldCreateDefaultGroup()) {
            groupManager.ensureDefaultGroup(config.getDefaultGroup());
        }
    }
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/DefaultGroupsStage.java
git commit -m "refactor(lifecycle): add DefaultGroupsStage for first-run group setup"
```

---

### Task 9: Rewire HyperPerms.enable() and disable() to use lifecycle

This is the key integration step. We replace the monolith with lifecycle delegation while keeping all existing getters working.

**Files:**
- Modify: `src/main/java/com/hyperperms/HyperPerms.java`

**Step 1: Add lifecycle fields and imports**

Add to imports:
```java
import com.hyperperms.lifecycle.PluginLifecycle;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.stages.*;
```

Add fields (after the existing `verboseMode` field at line ~140):
```java
// Lifecycle
private ServiceContainer container;
private PluginLifecycle lifecycle;
```

**Step 2: Replace enable() method**

Replace the entire `enable()` method (lines 176-406) with:

```java
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
    this.verboseMode = config.isVerboseEnabledByDefault();
}
```

**Step 3: Replace disable() method**

Replace the entire `disable()` method (lines 411-492) with:

```java
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
```

**Step 4: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Verify the build produces a valid JAR**

Run: `cd .worktrees/architecture-rehaul && ./gradlew shadowJar 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/com/hyperperms/HyperPerms.java
git commit -m "refactor(lifecycle): rewire enable/disable to use staged lifecycle

Replace 250-line enable() monolith with staged lifecycle delegation.
All existing getters remain functional via populateFieldsFromContainer().
Shutdown now runs stages in reverse order automatically."
```

---

### Task 10: Add ServiceContainer getter to HyperPerms

Future phases (commands, chat rewrite) will need to access the container. Add a public getter.

**Files:**
- Modify: `src/main/java/com/hyperperms/HyperPerms.java`

**Step 1: Add getter method**

Add near the other getters:

```java
/**
 * Get the service container. Available after {@link #enable()} completes.
 *
 * @return the service container
 */
@NotNull
public ServiceContainer getServiceContainer() {
    if (container == null) {
        throw new IllegalStateException("Plugin not yet enabled");
    }
    return container;
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/HyperPerms.java
git commit -m "refactor(lifecycle): expose ServiceContainer getter on HyperPerms"
```

---

## Verification Checklist

After all tasks are complete:

1. `./gradlew compileJava` — passes with no errors
2. `./gradlew shadowJar` — produces a valid JAR
3. All existing getters on `HyperPerms` return the same types as before
4. No existing code outside `HyperPerms.java` needs modification
5. Startup log should show `[Lifecycle] Initializing stage: X` messages in order
6. Shutdown log should show `[Lifecycle] Shutting down stage: X` messages in reverse
7. Manual test on dev server: all commands, chat, integrations work identically
