package com.hyperperms.lifecycle.stages;

import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.registry.PermissionRegistry;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

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
        Set<String> installedPlugins = runtimeDiscovery.scanInstalledPlugins();
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
     * Resolves the plugins/mods directory from the data directory.
     * Supports both "mods" and "plugins" folder names for compatibility.
     */
    @NotNull
    private static Path resolvePluginsDirectory(@NotNull Path dataDirectory) {
        // Try to derive from dataDirectory structure: mods/com.hyperperms_HyperPerms/data -> mods/
        Path derivedDir = null;
        if (dataDirectory.getParent() != null && dataDirectory.getParent().getParent() != null) {
            derivedDir = dataDirectory.getParent().getParent();
        }

        if (derivedDir != null && Files.isDirectory(derivedDir)) {
            String dirName = derivedDir.getFileName().toString().toLowerCase();
            if (dirName.equals("mods") || dirName.equals("plugins")) {
                Logger.debug("[Discovery] Using plugins directory: %s", derivedDir.toAbsolutePath());
                return derivedDir;
            } else {
                Logger.warn("[Discovery] Plugin folder has unexpected name '%s'. Expected 'mods' or 'plugins'.",
                        derivedDir.getFileName().toString());
                Logger.warn("[Discovery] Plugin discovery will still scan '%s', but consider renaming to 'mods' or 'plugins'.",
                        derivedDir.toAbsolutePath());
                return derivedDir;
            }
        }

        // Fallback: try to find mods or plugins in working directory
        Path workingDir = Path.of("").toAbsolutePath();
        Path modsDir = workingDir.resolve("mods");
        Path pluginsDir = workingDir.resolve("plugins");

        if (Files.isDirectory(modsDir)) {
            Logger.debug("[Discovery] Using mods directory: %s", modsDir);
            return modsDir;
        } else if (Files.isDirectory(pluginsDir)) {
            Logger.debug("[Discovery] Using plugins directory: %s", pluginsDir);
            return pluginsDir;
        }

        // Neither exists - log warning and return default
        Logger.warn("[Discovery] Could not find 'mods' or 'plugins' directory!");
        Logger.warn("[Discovery] Checked locations:");
        if (derivedDir != null) {
            Logger.warn("[Discovery]   - Derived: %s (does not exist)", derivedDir.toAbsolutePath());
        }
        Logger.warn("[Discovery]   - %s (does not exist)", modsDir);
        Logger.warn("[Discovery]   - %s (does not exist)", pluginsDir);
        Logger.warn("[Discovery] Plugin permission discovery will be limited. Please ensure your plugins are in a 'mods' or 'plugins' folder.");

        // Return mods as default even if it doesn't exist
        return modsDir;
    }
}
