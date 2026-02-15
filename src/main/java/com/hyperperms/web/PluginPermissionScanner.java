package com.hyperperms.web;

import com.hyperperms.HyperPerms;
import com.hyperperms.registry.PermissionRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans the PermissionRegistry to extract installed plugin permissions
 * for the web editor's dynamic permission support.
 */
public final class PluginPermissionScanner {

    private final HyperPerms hyperPerms;

    public PluginPermissionScanner(@NotNull HyperPerms hyperPerms) {
        this.hyperPerms = hyperPerms;
    }

    /**
     * Scans all registered permissions and groups them by plugin.
     *
     * @return list of PluginPermissions for each plugin with registered permissions
     */
    public List<PluginPermissions> scanInstalledPlugins() {
        PermissionRegistry registry = hyperPerms.getPermissionRegistry();

        // Group permissions by plugin name, excluding Hytale command path format (com.*)
        // which are needed for wildcard resolution but not useful in the web UI
        Map<String, List<PermissionRegistry.PermissionInfo>> byPlugin =
            registry.getAll().stream()
                .filter(p -> !p.getPermission().startsWith("com."))
                .collect(Collectors.groupingBy(PermissionRegistry.PermissionInfo::getPlugin));

        List<PluginPermissions> result = new ArrayList<>();

        for (Map.Entry<String, List<PermissionRegistry.PermissionInfo>> entry : byPlugin.entrySet()) {
            String pluginName = entry.getKey();
            List<PluginPermissions.PermissionInfo> perms = entry.getValue().stream()
                .map(p -> new PluginPermissions.PermissionInfo(
                    p.getPermission(),
                    p.getDescription(),
                    "op" // Default value - most plugin permissions require op by default
                ))
                .sorted(Comparator.comparing(PluginPermissions.PermissionInfo::node))
                .collect(Collectors.toList());

            result.add(new PluginPermissions(pluginName, "1.0.0", perms));
        }

        // Sort plugins alphabetically
        result.sort(Comparator.comparing(PluginPermissions::pluginName));

        return result;
    }
}
