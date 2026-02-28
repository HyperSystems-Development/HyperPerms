package com.hyperperms.registry;

import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for tracking all registered permissions.
 * <p>
 * This registry allows HyperPerms and external plugins to register their permissions
 * with descriptions and categories. This information is used for:
 * <ul>
 *   <li>Permission auto-completion in commands</li>
 *   <li>Permission listing and searching</li>
 *   <li>Documentation generation</li>
 *   <li>Verbose mode output</li>
 * </ul>
 */
public final class PermissionRegistry {

    private static PermissionRegistry instance;

    private final Map<String, PermissionInfo> permissions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> byCategory = new ConcurrentHashMap<>();
    private final Set<String> builtInPermissions = ConcurrentHashMap.newKeySet();

    /**
     * Information about a registered permission.
     */
    public static class PermissionInfo {
        private final String permission;
        private final String description;
        private final String category;
        private final String plugin;
        private final boolean isWildcard;

        /**
         * Creates a new permission info.
         *
         * @param permission  the permission node
         * @param description human-readable description
         * @param category    the permission category
         * @param plugin      the plugin that registered this permission
         */
        public PermissionInfo(@NotNull String permission, @NotNull String description,
                              @NotNull String category, @NotNull String plugin) {
            this.permission = permission;
            this.description = description;
            this.category = category;
            this.plugin = plugin;
            this.isWildcard = permission.contains("*");
        }

        public @NotNull String getPermission() {
            return permission;
        }

        public @NotNull String getDescription() {
            return description;
        }

        public @NotNull String getCategory() {
            return category;
        }

        public @NotNull String getPlugin() {
            return plugin;
        }

        public boolean isWildcard() {
            return isWildcard;
        }

        @Override
        public String toString() {
            return String.format("%s - %s [%s]", permission, description, category);
        }
    }

    /**
     * Gets the singleton instance.
     *
     * @return the permission registry
     */
    @NotNull
    public static PermissionRegistry getInstance() {
        if (instance == null) {
            instance = new PermissionRegistry();
        }
        return instance;
    }

    /**
     * Creates a new permission registry.
     */
    private PermissionRegistry() {
    }

    /**
     * Registers a permission with description and category.
     *
     * @param permission  the permission node (e.g., "hyperperms.command.reload")
     * @param description a human-readable description
     * @param category    the category (e.g., "admin", "user", "group")
     * @return true if registered, false if already exists
     */
    public boolean register(@NotNull String permission, @NotNull String description, @NotNull String category) {
        return register(permission, description, category, "HyperPerms");
    }

    /**
     * Registers a permission with description, category, and plugin name.
     *
     * @param permission  the permission node
     * @param description a human-readable description
     * @param category    the category
     * @param plugin      the plugin registering this permission
     * @return true if registered, false if already exists
     */
    public boolean register(@NotNull String permission, @NotNull String description,
                            @NotNull String category, @NotNull String plugin) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(plugin, "plugin");

        String normalizedPerm = permission.toLowerCase();
        String normalizedCategory = category.toLowerCase();

        PermissionInfo info = new PermissionInfo(normalizedPerm, description, normalizedCategory, plugin);

        if (permissions.putIfAbsent(normalizedPerm, info) == null) {
            // Add to category index
            byCategory.computeIfAbsent(normalizedCategory, k -> ConcurrentHashMap.newKeySet())
                    .add(normalizedPerm);

            Logger.debug("Registered permission: %s [%s] from %s", normalizedPerm, normalizedCategory, plugin);
            return true;
        }

        return false;
    }

    /**
     * Registers multiple permissions at once.
     *
     * @param permissions map of permission -> description
     * @param category    the category for all permissions
     */
    public void registerAll(@NotNull Map<String, String> permissions, @NotNull String category) {
        permissions.forEach((perm, desc) -> register(perm, desc, category));
    }

    /**
     * Unregisters a permission.
     *
     * @param permission the permission to unregister
     * @return true if removed, false if not found
     */
    public boolean unregister(@NotNull String permission) {
        String normalizedPerm = permission.toLowerCase();
        PermissionInfo removed = permissions.remove(normalizedPerm);

        if (removed != null) {
            Set<String> categorySet = byCategory.get(removed.category);
            if (categorySet != null) {
                categorySet.remove(normalizedPerm);
            }
            return true;
        }

        return false;
    }

    /**
     * Gets information about a registered permission.
     *
     * @param permission the permission node
     * @return the permission info, or null if not registered
     */
    @Nullable
    public PermissionInfo get(@NotNull String permission) {
        return permissions.get(permission.toLowerCase());
    }

    /**
     * Checks if a permission is registered.
     *
     * @param permission the permission node
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String permission) {
        return permissions.containsKey(permission.toLowerCase());
    }

    /**
     * Gets all registered permissions.
     *
     * @return unmodifiable collection of all permission infos
     */
    @NotNull
    public Collection<PermissionInfo> getAll() {
        return Collections.unmodifiableCollection(permissions.values());
    }

    /**
     * Gets all permissions in a specific category.
     *
     * @param category the category to filter by
     * @return list of permission infos in the category
     */
    @NotNull
    public List<PermissionInfo> getByCategory(@NotNull String category) {
        String normalizedCategory = category.toLowerCase();
        Set<String> perms = byCategory.get(normalizedCategory);

        if (perms == null || perms.isEmpty()) {
            return Collections.emptyList();
        }

        return perms.stream()
                .map(permissions::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PermissionInfo::getPermission))
                .collect(Collectors.toList());
    }

    /**
     * Gets all available categories.
     *
     * @return unmodifiable set of category names
     */
    @NotNull
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(byCategory.keySet());
    }

    /**
     * Searches for permissions matching a query.
     * <p>
     * The search matches against both permission nodes and descriptions.
     *
     * @param query the search query (case-insensitive)
     * @return list of matching permission infos
     */
    @NotNull
    public List<PermissionInfo> search(@NotNull String query) {
        String lowerQuery = query.toLowerCase();

        return permissions.values().stream()
                .filter(info -> info.permission.contains(lowerQuery) ||
                        info.description.toLowerCase().contains(lowerQuery))
                .sorted(Comparator.comparing(PermissionInfo::getPermission))
                .collect(Collectors.toList());
    }

    /**
     * Gets permissions by the plugin that registered them.
     *
     * @param plugin the plugin name
     * @return list of permission infos from that plugin
     */
    @NotNull
    public List<PermissionInfo> getByPlugin(@NotNull String plugin) {
        return permissions.values().stream()
                .filter(info -> info.plugin.equalsIgnoreCase(plugin))
                .sorted(Comparator.comparing(PermissionInfo::getPermission))
                .collect(Collectors.toList());
    }

    /**
     * Gets the total count of registered permissions.
     *
     * @return the count
     */
    public int size() {
        return permissions.size();
    }

    /**
     * Clears all registered permissions.
     */
    public void clear() {
        permissions.clear();
        byCategory.clear();
    }


    /**
     * Gets all known permissions that match a wildcard pattern.
     * <p>
     * This is used to expand wildcards into concrete permissions for
     * systems that don't support wildcard matching (like Hytale's
     * PermissionProvider interface).
     *
     * @param wildcard the wildcard pattern (e.g., "hyperhomes.*")
     * @return set of matching permission nodes
     */
    @NotNull
    public Set<String> getMatchingPermissions(@NotNull String wildcard) {
        String lowerWildcard = wildcard.toLowerCase();

        // Universal wildcard - return all non-wildcard permissions
        if (lowerWildcard.equals("*")) {
            return permissions.values().stream()
                    .map(PermissionInfo::getPermission)
                    .filter(p -> !p.contains("*"))
                    .collect(Collectors.toCollection(HashSet::new));
        }

        // Prefix wildcard (e.g., "hyperhomes.*")
        if (lowerWildcard.endsWith(".*")) {
            String prefix = lowerWildcard.substring(0, lowerWildcard.length() - 1); // "hyperhomes."
            return permissions.values().stream()
                    .map(PermissionInfo::getPermission)
                    .filter(p -> p.startsWith(prefix) && !p.equals(lowerWildcard))
                    .collect(Collectors.toCollection(HashSet::new));
        }

        // Not a wildcard - return empty set
        return Collections.emptySet();
    }

    /**
     * Checks if a permission is a built-in (hardcoded) permission.
     *
     * @param permission the permission node
     * @return true if the permission was registered as a built-in
     */
    public boolean isBuiltIn(@NotNull String permission) {
        return builtInPermissions.contains(permission.toLowerCase());
    }

    /**
     * Registers all discovered permissions from the runtime discovery system.
     *
     * @param discovery the runtime permission discovery instance
     */
    public void registerDiscoveredPermissions(@NotNull RuntimePermissionDiscovery discovery) {
        discovery.registerAll(this);
    }

    /**
     * Registers HyperPerms' built-in permissions.
     */
    public void registerBuiltInPermissions() {
        // Admin wildcards
        register("hyperperms.*", "Full administrative access to HyperPerms", "admin");
        register("hyperperms.command.*", "Access to all HyperPerms commands", "admin");

        // User commands
        register("hyperperms.command.user.info", "View user permission information", "user");
        register("hyperperms.command.user.info.others", "View other users' permission information", "user");
        register("hyperperms.command.user.permission", "Modify user permissions", "user");
        register("hyperperms.command.user.group", "Modify user group memberships", "user");
        register("hyperperms.command.user.promote", "Promote users on tracks", "user");
        register("hyperperms.command.user.demote", "Demote users on tracks", "user");
        register("hyperperms.command.user.clear", "Clear user permission data", "user");
        register("hyperperms.command.user.clone", "Clone permissions from one user to another", "user");

        // Group commands
        register("hyperperms.command.group.info", "View group information", "group");
        register("hyperperms.command.group.list", "List all groups", "group");
        register("hyperperms.command.group.create", "Create new groups", "group");
        register("hyperperms.command.group.delete", "Delete groups", "group");
        register("hyperperms.command.group.permission", "Modify group permissions", "group");
        register("hyperperms.command.group.parent", "Modify group inheritance", "group");
        register("hyperperms.command.group.modify", "Modify group metadata", "group");

        // Track commands
        register("hyperperms.command.track.info", "View track information", "track");
        register("hyperperms.command.track.list", "List all tracks", "track");
        register("hyperperms.command.track.create", "Create new tracks", "track");
        register("hyperperms.command.track.delete", "Delete tracks", "track");
        register("hyperperms.command.track.modify", "Modify track configuration", "track");

        // Administrative commands
        register("hyperperms.command.reload", "Reload plugin configuration", "admin");
        register("hyperperms.command.verbose", "Toggle verbose permission checking", "admin");
        register("hyperperms.command.cache", "Manage permission cache", "admin");
        register("hyperperms.command.export", "Export permission data", "admin");
        register("hyperperms.command.import", "Import permission data", "admin");
        register("hyperperms.command.backup", "Manage backups", "admin");
        register("hyperperms.command.check", "Check permissions for users", "admin");
        register("hyperperms.command.check.self", "Check your own permissions", "utility");
        register("hyperperms.command.check.others", "Check other users' permissions", "admin");

        // Debug commands
        register("hyperperms.command.debug", "Access debug commands", "debug");
        register("hyperperms.command.debug.tree", "View user inheritance tree", "debug");
        register("hyperperms.command.debug.resolve", "Debug permission resolution", "debug");
        register("hyperperms.command.debug.contexts", "View user contexts", "debug");

        // Web editor
        register("hyperperms.command.editor", "Open the web editor", "editor");
        register("hyperperms.command.apply", "Apply web editor changes", "editor");

        // Chat permissions
        register("hyperperms.chat.color", "Use color codes in chat", "chat");
        register("hyperperms.chat.format", "Use formatting codes in chat", "chat");

        // Register Hytale permissions for wildcard expansion
        registerHytalePermissions();

        // Register HyperHomes permissions for wildcard expansion
        registerHyperHomesPermissions();

        // Register HyperWarps permissions for wildcard expansion
        registerHyperWarpsPermissions();

        // Register HyperFactions permissions for wildcard expansion
        registerHyperFactionsPermissions();

        // Register MMOSkillTree permissions for wildcard expansion
        registerMMOSkillTreePermissions();

        // Snapshot all currently registered permissions as built-in
        builtInPermissions.addAll(permissions.keySet());

        Logger.info("Registered %d built-in permissions", size());
    }

    /**
     * Registers known Hytale permissions for wildcard expansion.
     * <p>
     * Permissions are extracted from HytaleServer.jar and include both:
     * <ul>
     *   <li>Simplified aliases (e.g., hytale.command.gamemode) for user convenience</li>
     *   <li>Actual Hytale paths (e.g., hytale.command.player.gamemode) for proper checking</li>
     *   <li>Legacy system format (e.g., hytale.system.command.gamemode) for backwards compatibility</li>
     * </ul>
     * <p>
     * The PermissionAliases class handles mapping between simplified and actual permissions.
     */
    private void registerHytalePermissions() {
        // ==================== Wildcards ====================
        register("hytale.*", "Full access to all Hytale features", "hytale", "Hytale");
        register("hytale.command.*", "Access to all Hytale commands", "hytale", "Hytale");
        register("hytale.editor.*", "Access to all editor features", "hytale", "Hytale");
        register("hytale.camera.*", "Access to all camera features", "hytale", "Hytale");
        register("hytale.system.*", "Legacy system permissions", "hytale", "Hytale");
        register("hytale.system.command.*", "Legacy system command permissions", "hytale", "Hytale");

        // ==================== Legacy System Format (hytale.system.command.*) ====================
        register("hytale.system.command.gamemode", "Change game mode (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.give", "Give items (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.kick", "Kick players (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.ban", "Ban players (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.unban", "Unban players (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.kill", "Kill entities (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.heal", "Heal entities (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.tp", "Teleport (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.teleport", "Teleport (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.spawn", "Spawn commands (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.stop", "Stop server (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.op", "Operator commands (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.opadd", "Add operator (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.opremove", "Remove operator (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.who", "List players (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.help", "Help command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.version", "Version command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.ping", "Ping command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.time", "Time command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.weather", "Weather command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.clear", "Clear inventory (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.effect", "Effect commands (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.emote", "Emote command (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.plugin", "Plugin commands (legacy format)", "hytale", "Hytale");
        register("hytale.system.command.backup", "Backup command (legacy format)", "hytale", "Hytale");

        // ==================== Core Static Permissions (from HytalePermissions.class) ====================
        register("hytale.editor.asset", "Access asset editor", "hytale", "Hytale");
        register("hytale.editor.packs.create", "Create editor packs", "hytale", "Hytale");
        register("hytale.editor.packs.edit", "Edit editor packs", "hytale", "Hytale");
        register("hytale.editor.packs.delete", "Delete editor packs", "hytale", "Hytale");
        register("hytale.editor.builderTools", "Access builder tools (camelCase)", "hytale", "Hytale");
        register("hytale.editor.buildertools", "Access builder tools (alias)", "hytale", "Hytale");
        register("hytale.editor.brush.use", "Use brush tools", "hytale", "Hytale");
        register("hytale.editor.brush.config", "Configure brush settings", "hytale", "Hytale");
        register("hytale.editor.prefab.use", "Use prefabs", "hytale", "Hytale");
        register("hytale.editor.prefab.manage", "Manage prefabs", "hytale", "Hytale");
        register("hytale.editor.selection.use", "Use selection tools", "hytale", "Hytale");
        register("hytale.editor.selection.clipboard", "Use clipboard", "hytale", "Hytale");
        register("hytale.editor.selection.modify", "Modify selections", "hytale", "Hytale");
        register("hytale.editor.history", "Access edit history", "hytale", "Hytale");
        register("hytale.camera.flycam", "Use fly camera", "hytale", "Hytale");

        // ==================== Simplified Aliases (for user convenience) ====================
        // These map to actual Hytale paths via PermissionAliases
        register("hytale.command.gamemode", "Change game mode (alias)", "hytale", "Hytale");
        register("hytale.command.gamemode.*", "All gamemode permissions", "hytale", "Hytale");
        register("hytale.command.gamemode.self", "Change your own game mode", "hytale", "Hytale");
        register("hytale.command.gamemode.others", "Change other players' game mode", "hytale", "Hytale");
        register("hytale.command.give", "Give items (alias)", "hytale", "Hytale");
        register("hytale.command.give.*", "All give permissions", "hytale", "Hytale");
        register("hytale.command.give.self", "Give items to yourself", "hytale", "Hytale");
        register("hytale.command.give.others", "Give items to others", "hytale", "Hytale");
        register("hytale.command.clear", "Clear inventory (alias)", "hytale", "Hytale");
        register("hytale.command.kick", "Kick players (alias)", "hytale", "Hytale");
        register("hytale.command.kill", "Kill entities (alias)", "hytale", "Hytale");
        register("hytale.command.damage", "Damage entities (alias)", "hytale", "Hytale");
        register("hytale.command.stop", "Stop server (alias)", "hytale", "Hytale");
        register("hytale.command.backup", "Backup server (alias)", "hytale", "Hytale");

        // ==================== Additional Web Editor Simplified Permissions ====================
        // Moderation
        register("hytale.command.ban", "Ban players (simplified)", "hytale", "Hytale");
        register("hytale.command.unban", "Unban players (simplified)", "hytale", "Hytale");
        register("hytale.command.opadd", "Add operator (simplified)", "hytale", "Hytale");
        register("hytale.command.opremove", "Remove operator (simplified)", "hytale", "Hytale");
        register("hytale.command.opself", "Op yourself (simplified)", "hytale", "Hytale");
        // Whitelist
        register("hytale.command.whitelist", "Whitelist commands", "hytale", "Hytale");
        register("hytale.command.whitelist.*", "All whitelist commands", "hytale", "Hytale");
        register("hytale.command.whitelist.add", "Add to whitelist", "hytale", "Hytale");
        register("hytale.command.whitelist.remove", "Remove from whitelist", "hytale", "Hytale");
        register("hytale.command.whitelist.list", "List whitelist", "hytale", "Hytale");
        register("hytale.command.whitelist.on", "Enable whitelist", "hytale", "Hytale");
        register("hytale.command.whitelist.off", "Disable whitelist", "hytale", "Hytale");
        // Teleportation
        register("hytale.command.tp", "Teleport (simplified)", "hytale", "Hytale");
        register("hytale.command.teleport", "Teleport (simplified)", "hytale", "Hytale");
        register("hytale.command.tpall", "Teleport all players", "hytale", "Hytale");
        register("hytale.command.tpback", "Teleport back", "hytale", "Hytale");
        register("hytale.command.tpforward", "Teleport forward", "hytale", "Hytale");
        register("hytale.command.tptop", "Teleport to top", "hytale", "Hytale");
        register("hytale.command.tphome", "Teleport to home", "hytale", "Hytale");
        register("hytale.command.spawnset", "Set spawn point", "hytale", "Hytale");
        // Building/Editor
        register("hytale.command.fill", "Fill selection", "hytale", "Hytale");
        register("hytale.command.walls", "Create walls", "hytale", "Hytale");
        register("hytale.command.hollow", "Hollow selection", "hytale", "Hytale");
        register("hytale.command.replace", "Replace blocks", "hytale", "Hytale");
        register("hytale.command.copy", "Copy selection", "hytale", "Hytale");
        register("hytale.command.cut", "Cut selection", "hytale", "Hytale");
        register("hytale.command.paste", "Paste clipboard", "hytale", "Hytale");
        register("hytale.command.undo", "Undo action", "hytale", "Hytale");
        register("hytale.command.redo", "Redo action", "hytale", "Hytale");
        register("hytale.command.pos1", "Set position 1", "hytale", "Hytale");
        register("hytale.command.pos2", "Set position 2", "hytale", "Hytale");
        register("hytale.command.brush", "Brush commands", "hytale", "Hytale");
        register("hytale.command.prefab", "Prefab commands", "hytale", "Hytale");
        // Entity
        register("hytale.command.entity", "Entity commands", "hytale", "Hytale");
        register("hytale.command.entity.spawn", "Spawn entities", "hytale", "Hytale");
        register("hytale.command.entity.remove", "Remove entities", "hytale", "Hytale");
        register("hytale.command.entity.clone", "Clone entities", "hytale", "Hytale");
        register("hytale.command.npc", "NPC commands", "hytale", "Hytale");
        register("hytale.command.mount", "Mount command", "hytale", "Hytale");
        register("hytale.command.dismount", "Dismount command", "hytale", "Hytale");
        register("hytale.command.hitbox", "Hitbox debug", "hytale", "Hytale");
        // World
        register("hytale.command.worldgen", "Worldgen commands", "hytale", "Hytale");
        register("hytale.command.chunk", "Chunk commands", "hytale", "Hytale");
        register("hytale.command.warp", "Warp commands", "hytale", "Hytale");
        register("hytale.command.time", "Time commands", "hytale", "Hytale");
        register("hytale.command.time.set", "Set time", "hytale", "Hytale");
        register("hytale.command.time.get", "Get time", "hytale", "Hytale");
        register("hytale.command.weather", "Weather commands", "hytale", "Hytale");
        register("hytale.command.weather.set", "Set weather", "hytale", "Hytale");
        register("hytale.command.weather.get", "Get weather", "hytale", "Hytale");
        // Audio/Visual
        register("hytale.command.sound", "Sound commands", "hytale", "Hytale");
        register("hytale.command.sound.play", "Play sounds", "hytale", "Hytale");
        register("hytale.command.particle", "Particle commands", "hytale", "Hytale");
        register("hytale.command.ambience", "Ambience commands", "hytale", "Hytale");
        register("hytale.command.tint", "Tint commands", "hytale", "Hytale");
        register("hytale.command.lighting", "Lighting commands", "hytale", "Hytale");
        // Server/Debug
        register("hytale.command.debug", "Debug commands", "hytale", "Hytale");
        register("hytale.command.log", "Log commands", "hytale", "Hytale");
        register("hytale.command.network", "Network commands", "hytale", "Hytale");
        register("hytale.command.stresstest", "Stress test commands", "hytale", "Hytale");
        // Player state
        register("hytale.command.player", "Player commands", "hytale", "Hytale");
        register("hytale.command.inventory", "Inventory commands", "hytale", "Hytale");
        register("hytale.command.sleep", "Sleep commands", "hytale", "Hytale");
        register("hytale.command.effect", "Effect commands", "hytale", "Hytale");
        register("hytale.command.stats", "Stats commands", "hytale", "Hytale");
        // Instance/Auth
        register("hytale.command.instance", "Instance commands", "hytale", "Hytale");
        register("hytale.command.auth", "Auth commands", "hytale", "Hytale");
        register("hytale.command.hub", "Hub command", "hytale", "Hytale");
        register("hytale.command.leave", "Leave command", "hytale", "Hytale");

        // ==================== Server Ban/Unban (actual paths) ====================
        register("hytale.command.server.ban", "Ban players", "hytale", "Hytale");
        register("hytale.command.server.unban", "Unban players", "hytale", "Hytale");
        register("hytale.command.server.whitelist", "Whitelist commands", "hytale", "Hytale");
        register("hytale.command.server.whitelist.add", "Add to whitelist", "hytale", "Hytale");
        register("hytale.command.server.whitelist.remove", "Remove from whitelist", "hytale", "Hytale");
        register("hytale.command.server.whitelist.list", "List whitelist", "hytale", "Hytale");
        register("hytale.command.server.whitelist.on", "Enable whitelist", "hytale", "Hytale");
        register("hytale.command.server.whitelist.off", "Disable whitelist", "hytale", "Hytale");
        register("hytale.command.server.instance", "Instance commands", "hytale", "Hytale");
        register("hytale.command.server.hub", "Hub command", "hytale", "Hytale");
        register("hytale.command.server.leave", "Leave command", "hytale", "Hytale");

        // ==================== Player Teleport (actual paths) ====================
        register("hytale.command.player.teleport", "Teleport command", "hytale", "Hytale");
        register("hytale.command.player.teleport.all", "Teleport all", "hytale", "Hytale");
        register("hytale.command.player.teleport.back", "Teleport back", "hytale", "Hytale");
        register("hytale.command.player.teleport.forward", "Teleport forward", "hytale", "Hytale");
        register("hytale.command.player.teleport.top", "Teleport to top", "hytale", "Hytale");
        register("hytale.command.player.teleport.home", "Teleport home", "hytale", "Hytale");
        register("hytale.command.player.mount", "Mount command", "hytale", "Hytale");
        register("hytale.command.player.dismount", "Dismount command", "hytale", "Hytale");

        // ==================== World Time/Weather (actual paths) ====================
        register("hytale.command.world.time", "Time commands", "hytale", "Hytale");
        register("hytale.command.world.time.set", "Set time", "hytale", "Hytale");
        register("hytale.command.world.time.get", "Get time", "hytale", "Hytale");
        register("hytale.command.world.weather", "Weather commands", "hytale", "Hytale");
        register("hytale.command.world.weather.set", "Set weather", "hytale", "Hytale");
        register("hytale.command.world.weather.get", "Get weather", "hytale", "Hytale");
        register("hytale.command.world.warp", "Warp commands", "hytale", "Hytale");
        register("hytale.command.world.entity.spawn", "Spawn entities", "hytale", "Hytale");
        register("hytale.command.world.entity.npc", "NPC commands", "hytale", "Hytale");

        // ==================== Editor Selection (actual paths) ====================
        register("hytale.editor.selection.fill", "Fill selection", "hytale", "Hytale");
        register("hytale.editor.selection.walls", "Create walls", "hytale", "Hytale");
        register("hytale.editor.selection.hollow", "Hollow selection", "hytale", "Hytale");
        register("hytale.editor.selection.replace", "Replace blocks", "hytale", "Hytale");

        // ==================== Utility Commands (actual paths) ====================
        register("hytale.command.utility.particle", "Particle effects", "hytale", "Hytale");
        register("hytale.command.utility.ambience", "Ambience commands", "hytale", "Hytale");

        // ==================== Actual Player Commands (hytale.command.player.*) ====================
        register("hytale.command.player", "Player command root", "hytale", "Hytale");
        register("hytale.command.player.*", "All player commands", "hytale", "Hytale");
        register("hytale.command.player.gamemode", "Change game mode", "hytale", "Hytale");
        register("hytale.command.player.damage", "Damage player", "hytale", "Hytale");
        register("hytale.command.player.kill", "Kill player", "hytale", "Hytale");
        register("hytale.command.player.hide", "Hide player", "hytale", "Hytale");
        register("hytale.command.player.reset", "Reset player", "hytale", "Hytale");
        register("hytale.command.player.respawn", "Respawn player", "hytale", "Hytale");
        register("hytale.command.player.zone", "Player zone info", "hytale", "Hytale");
        register("hytale.command.player.refer", "Refer player", "hytale", "Hytale");
        register("hytale.command.player.sudo", "Execute as player", "hytale", "Hytale");
        register("hytale.command.player.whereami", "Show location", "hytale", "Hytale");
        register("hytale.command.player.whoami", "Show player info", "hytale", "Hytale");
        register("hytale.command.player.toggleblockplacementoverride", "Toggle block placement", "hytale", "Hytale");
        // Camera
        register("hytale.command.player.camera", "Camera commands", "hytale", "Hytale");
        register("hytale.command.player.camera.reset", "Reset camera", "hytale", "Hytale");
        register("hytale.command.player.camera.sidescroller", "Side-scroller camera", "hytale", "Hytale");
        register("hytale.command.player.camera.topdown", "Top-down camera", "hytale", "Hytale");
        // Effect
        register("hytale.command.player.effect", "Player effects", "hytale", "Hytale");
        register("hytale.command.player.effect.apply", "Apply effect", "hytale", "Hytale");
        register("hytale.command.player.effect.clear", "Clear effects", "hytale", "Hytale");
        // Inventory
        register("hytale.command.player.inventory", "Inventory commands", "hytale", "Hytale");
        register("hytale.command.player.inventory.give", "Give items", "hytale", "Hytale");
        register("hytale.command.player.inventory.givearmor", "Give armor", "hytale", "Hytale");
        register("hytale.command.player.inventory.clear", "Clear inventory", "hytale", "Hytale");
        register("hytale.command.player.inventory.backpack", "Backpack commands", "hytale", "Hytale");
        register("hytale.command.player.inventory.item", "Item commands", "hytale", "Hytale");
        register("hytale.command.player.inventory.see", "View inventory", "hytale", "Hytale");
        register("hytale.command.player.inventory.itemstate", "Item state", "hytale", "Hytale");
        // Stats
        register("hytale.command.player.stats", "Player stats", "hytale", "Hytale");
        register("hytale.command.player.stats.add", "Add stats", "hytale", "Hytale");
        register("hytale.command.player.stats.get", "Get stats", "hytale", "Hytale");
        register("hytale.command.player.stats.set", "Set stats", "hytale", "Hytale");
        register("hytale.command.player.stats.reset", "Reset stats", "hytale", "Hytale");
        register("hytale.command.player.stats.dump", "Dump stats", "hytale", "Hytale");
        register("hytale.command.player.stats.settomax", "Set stats to max", "hytale", "Hytale");
        // View radius
        register("hytale.command.player.viewradius", "View radius commands", "hytale", "Hytale");
        register("hytale.command.player.viewradius.get", "Get view radius", "hytale", "Hytale");
        register("hytale.command.player.viewradius.set", "Set view radius", "hytale", "Hytale");

        // ==================== Actual Server Commands (hytale.command.server.*) ====================
        register("hytale.command.server", "Server command root", "hytale", "Hytale");
        register("hytale.command.server.*", "All server commands", "hytale", "Hytale");
        register("hytale.command.server.kick", "Kick players", "hytale", "Hytale");
        register("hytale.command.server.stop", "Stop server", "hytale", "Hytale");
        register("hytale.command.server.who", "List online players", "hytale", "Hytale");
        register("hytale.command.server.maxplayers", "Set max players", "hytale", "Hytale");
        // Auth
        register("hytale.command.server.auth", "Auth commands", "hytale", "Hytale");
        register("hytale.command.server.auth.login", "Login", "hytale", "Hytale");
        register("hytale.command.server.auth.logout", "Logout", "hytale", "Hytale");
        register("hytale.command.server.auth.status", "Auth status", "hytale", "Hytale");
        register("hytale.command.server.auth.cancel", "Cancel auth", "hytale", "Hytale");
        register("hytale.command.server.auth.select", "Select auth", "hytale", "Hytale");
        register("hytale.command.server.auth.persistence", "Auth persistence", "hytale", "Hytale");

        // ==================== Actual Utility Commands (hytale.command.utility.*) ====================
        register("hytale.command.utility.*", "All utility commands", "hytale", "Hytale");
        register("hytale.command.utility.backup", "Backup server", "hytale", "Hytale");
        register("hytale.command.utility.help", "Help command", "hytale", "Hytale");
        register("hytale.command.utility.convertprefabs", "Convert prefabs", "hytale", "Hytale");
        register("hytale.command.utility.eventtitle", "Event title", "hytale", "Hytale");
        register("hytale.command.utility.notify", "Send notifications", "hytale", "Hytale");
        register("hytale.command.utility.stash", "Stash commands", "hytale", "Hytale");
        register("hytale.command.utility.validatecpb", "Validate CPB", "hytale", "Hytale");

        // ==================== Actual World Commands (hytale.command.world.*) ====================
        register("hytale.command.world.*", "All world commands", "hytale", "Hytale");
        register("hytale.command.world.spawnblock", "Spawn block", "hytale", "Hytale");
        // Chunk
        register("hytale.command.world.chunk", "Chunk commands", "hytale", "Hytale");
        register("hytale.command.world.chunk.info", "Chunk info", "hytale", "Hytale");
        register("hytale.command.world.chunk.load", "Load chunk", "hytale", "Hytale");
        register("hytale.command.world.chunk.unload", "Unload chunk", "hytale", "Hytale");
        register("hytale.command.world.chunk.regenerate", "Regenerate chunk", "hytale", "Hytale");
        // Entity
        register("hytale.command.world.entity", "Entity commands", "hytale", "Hytale");
        register("hytale.command.world.entity.remove", "Remove entity", "hytale", "Hytale");
        register("hytale.command.world.entity.clone", "Clone entity", "hytale", "Hytale");
        register("hytale.command.world.entity.count", "Count entities", "hytale", "Hytale");
        // Worldgen
        register("hytale.command.world.worldgen", "Worldgen commands", "hytale", "Hytale");
        register("hytale.command.world.worldgen.reload", "Reload worldgen", "hytale", "Hytale");

        // ==================== Debug Commands (hytale.command.debug.*) ====================
        register("hytale.command.debug.*", "All debug commands", "hytale", "Hytale");
        register("hytale.command.debug.version", "Show version", "hytale", "Hytale");
        register("hytale.command.debug.ping", "Check ping", "hytale", "Hytale");
        register("hytale.command.debug.log", "Log commands", "hytale", "Hytale");
        register("hytale.command.debug.assets", "Assets debug", "hytale", "Hytale");
        register("hytale.command.debug.server", "Server debug", "hytale", "Hytale");
        register("hytale.command.debug.server.gc", "Force GC", "hytale", "Hytale");
        register("hytale.command.debug.server.stats", "Server stats", "hytale", "Hytale");

        // ==================== Permission Commands ====================
        register("hytale.command.perm", "Permission commands", "hytale", "Hytale");
        register("hytale.command.perm.*", "All permission commands", "hytale", "Hytale");
        register("hytale.command.op", "Operator commands", "hytale", "Hytale");
        register("hytale.command.op.*", "All operator commands", "hytale", "Hytale");
        register("hytale.command.op.self", "Op yourself", "hytale", "Hytale");
        register("hytale.command.op.add", "Add operator", "hytale", "Hytale");
        register("hytale.command.op.remove", "Remove operator", "hytale", "Hytale");

        // ==================== Plugin Commands ====================
        register("hytale.command.plugin", "Plugin commands", "hytale", "Hytale");
        register("hytale.command.plugin.*", "All plugin commands", "hytale", "Hytale");
        register("hytale.command.plugin.list", "List plugins", "hytale", "Hytale");
        register("hytale.command.plugin.load", "Load plugin", "hytale", "Hytale");
        register("hytale.command.plugin.unload", "Unload plugin", "hytale", "Hytale");
        register("hytale.command.plugin.reload", "Reload plugin", "hytale", "Hytale");

        // ==================== Other Commands ====================
        register("hytale.command.emote", "Emote command", "hytale", "Hytale");

        // ==================== Mods Permissions ====================
        register("hytale.mods.*", "All mod-related permissions", "hytale", "Hytale");
        register("hytale.mods.outdated.notify", "Receive outdated mod notifications", "hytale", "Hytale");

        Logger.debug("Registered Hytale permissions for wildcard expansion");
    }

    /**
     * Registers known HyperHomes permissions for wildcard expansion.
     * <p>
     * IMPORTANT: Hytale uses full package path for plugin commands:
     * "com.hyperhomes.hyperhomes.command.homes" instead of "hyperhomes.homes"
     */
    private void registerHyperHomesPermissions() {
        // ==================== Standard HyperHomes permissions ====================
        // Wildcards
        register("hyperhomes.*", "Full access to all HyperHomes features", "hyperhomes", "HyperHomes");

        // Core permissions
        register("hyperhomes.use", "Basic access to HyperHomes", "hyperhomes", "HyperHomes");
        register("hyperhomes.gui", "Access to homes GUI", "hyperhomes", "HyperHomes");
        register("hyperhomes.list", "List your homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.set", "Set homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.delete", "Delete homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.teleport", "Teleport to homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.home", "Use the /home command", "hyperhomes", "HyperHomes");
        register("hyperhomes.sethome", "Use the /sethome command", "hyperhomes", "HyperHomes");
        register("hyperhomes.delhome", "Use the /delhome command", "hyperhomes", "HyperHomes");
        register("hyperhomes.homes", "Use the /homes command", "hyperhomes", "HyperHomes");

        // Sharing permissions
        register("hyperhomes.share", "Share homes with others", "hyperhomes", "HyperHomes");
        register("hyperhomes.share.public", "Make homes public", "hyperhomes", "HyperHomes");
        register("hyperhomes.share.invite", "Invite players to homes", "hyperhomes", "HyperHomes");

        // Admin permissions
        register("hyperhomes.admin", "Admin access to HyperHomes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.teleport.others", "Teleport to any player's homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.list", "List any player's homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.delete", "Delete any player's homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.modify", "Modify any player's homes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.reload", "Reload HyperHomes configuration", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.update", "Update HyperHomes", "hyperhomes", "HyperHomes");
        register("hyperhomes.admin.migrate", "Migrate HyperHomes data", "hyperhomes", "HyperHomes");

        // Limit bypass permissions
        register("hyperhomes.unlimited", "Bypass home limits", "hyperhomes", "HyperHomes");
        register("hyperhomes.bypass.cooldown", "Bypass teleport cooldowns", "hyperhomes", "HyperHomes");
        register("hyperhomes.bypass.warmup", "Bypass teleport warmups", "hyperhomes", "HyperHomes");

        // ==================== Hytale command path format ====================
        // Hytale uses full Java package path for plugin commands
        // NOTE: com.* is intentionally NOT registered - it would grant ALL plugin commands
        register("com.hyperhomes.*", "All HyperHomes package permissions", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.*", "All HyperHomes commands", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.*", "All HyperHomes command permissions", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.homes", "Use /homes command", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.homes.*", "All /homes subcommands", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.home", "Use /home command", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.home.*", "All /home subcommands", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.sethome", "Use /sethome command", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.sethome.*", "All /sethome subcommands", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.delhome", "Use /delhome command", "hyperhomes", "HyperHomes");
        register("com.hyperhomes.hyperhomes.command.delhome.*", "All /delhome subcommands", "hyperhomes", "HyperHomes");

        Logger.debug("Registered HyperHomes permissions for wildcard expansion");
    }

    /**
     * Registers known HyperWarps permissions for wildcard expansion.
     * <p>
     * IMPORTANT: Hytale uses full package path for plugin commands:
     * "com.hyperwarps.hyperwarps.command.warp" instead of "hyperwarps.warp"
     */
    private void registerHyperWarpsPermissions() {
        // ==================== Standard HyperWarps permissions ====================
        // Wildcards
        register("hyperwarps.*", "Full access to all HyperWarps features", "hyperwarps", "HyperWarps");

        // Core permissions
        register("hyperwarps.use", "Basic access to HyperWarps", "hyperwarps", "HyperWarps");
        register("hyperwarps.admin", "Admin access to HyperWarps", "hyperwarps", "HyperWarps");

        // Warp commands
        register("hyperwarps.warp", "Use /warp command", "hyperwarps", "HyperWarps");
        register("hyperwarps.warps", "Use /warps command", "hyperwarps", "HyperWarps");
        register("hyperwarps.setwarp", "Use /setwarp command", "hyperwarps", "HyperWarps");
        register("hyperwarps.delwarp", "Use /delwarp command", "hyperwarps", "HyperWarps");
        register("hyperwarps.warpinfo", "Use /warpinfo command", "hyperwarps", "HyperWarps");

        // Spawn commands
        register("hyperwarps.spawn", "Use /spawn command", "hyperwarps", "HyperWarps");
        register("hyperwarps.spawns", "Use /spawns command", "hyperwarps", "HyperWarps");
        register("hyperwarps.setspawn", "Use /setspawn command", "hyperwarps", "HyperWarps");
        register("hyperwarps.delspawn", "Use /delspawn command", "hyperwarps", "HyperWarps");
        register("hyperwarps.spawninfo", "Use /spawninfo command", "hyperwarps", "HyperWarps");

        // TPA commands
        register("hyperwarps.tpa", "Use /tpa command", "hyperwarps", "HyperWarps");
        register("hyperwarps.tpahere", "Use /tpahere command", "hyperwarps", "HyperWarps");
        register("hyperwarps.tpaccept", "Use /tpaccept command", "hyperwarps", "HyperWarps");
        register("hyperwarps.tpdeny", "Use /tpdeny command", "hyperwarps", "HyperWarps");
        register("hyperwarps.tpcancel", "Use /tpcancel command", "hyperwarps", "HyperWarps");
        register("hyperwarps.tptoggle", "Use /tptoggle command", "hyperwarps", "HyperWarps");

        // Back command
        register("hyperwarps.back", "Use /back command", "hyperwarps", "HyperWarps");

        // Bypass permissions
        register("hyperwarps.bypass.warmup", "Bypass teleport warmup", "hyperwarps", "HyperWarps");
        register("hyperwarps.bypass.cooldown", "Bypass teleport cooldown", "hyperwarps", "HyperWarps");
        register("hyperwarps.bypass.toggle", "Bypass TPA toggle", "hyperwarps", "HyperWarps");

        // ==================== Hytale command path format ====================
        // Hytale uses full Java package path for plugin commands
        register("com.hyperwarps.*", "All HyperWarps package permissions", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.*", "All HyperWarps commands", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.*", "All HyperWarps command permissions", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.warp", "Use /warp command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.warps", "Use /warps command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.setwarp", "Use /setwarp command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.delwarp", "Use /delwarp command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.warpinfo", "Use /warpinfo command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.spawn", "Use /spawn command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.spawns", "Use /spawns command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.setspawn", "Use /setspawn command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.delspawn", "Use /delspawn command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.spawninfo", "Use /spawninfo command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tpa", "Use /tpa command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tpahere", "Use /tpahere command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tpaccept", "Use /tpaccept command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tpdeny", "Use /tpdeny command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tpcancel", "Use /tpcancel command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.tptoggle", "Use /tptoggle command", "hyperwarps", "HyperWarps");
        register("com.hyperwarps.hyperwarps.command.back", "Use /back command", "hyperwarps", "HyperWarps");

        Logger.debug("Registered HyperWarps permissions for wildcard expansion");
    }

    /**
     * Registers known HyperFactions permissions for wildcard expansion.
     * <p>
     * IMPORTANT: Hytale uses full package path for plugin commands:
     * "com.hyperfactions.hyperfactions.command.faction" instead of "hyperfactions.faction"
     */
    private void registerHyperFactionsPermissions() {
        // ==================== Category Wildcards ====================
        register("hyperfactions.*", "Full access to all HyperFactions features", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.*", "All faction management permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.*", "All membership management permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.territory.*", "All territory claim permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.teleport.*", "All teleportation permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.relation.*", "All diplomatic relation permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.chat.*", "All chat permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.*", "All information viewing permissions", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.*", "Bypass all protections", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.*", "All admin permissions", "hyperfactions", "HyperFactions");

        // ==================== Basic Access ====================
        register("hyperfactions.use", "Basic faction access - required for GUI", "hyperfactions", "HyperFactions");

        // ==================== Faction Management (hyperfactions.faction.*) ====================
        register("hyperfactions.faction.create", "Create a new faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.disband", "Disband your faction (leader only)", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.rename", "Rename your faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.description", "Set faction description", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.tag", "Set faction tag", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.color", "Set faction color", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.open", "Make faction open (anyone can join)", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.close", "Make faction closed (invite only)", "hyperfactions", "HyperFactions");
        register("hyperfactions.faction.permissions", "Edit faction territory permissions", "hyperfactions", "HyperFactions");

        // ==================== Membership (hyperfactions.member.*) ====================
        register("hyperfactions.member.invite", "Invite players to your faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.join", "Accept faction invites / request to join", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.leave", "Leave your faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.kick", "Kick members from your faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.promote", "Promote faction members", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.demote", "Demote faction members", "hyperfactions", "HyperFactions");
        register("hyperfactions.member.transfer", "Transfer faction leadership", "hyperfactions", "HyperFactions");

        // ==================== Territory (hyperfactions.territory.*) ====================
        register("hyperfactions.territory.claim", "Claim territory chunks", "hyperfactions", "HyperFactions");
        register("hyperfactions.territory.unclaim", "Unclaim territory chunks", "hyperfactions", "HyperFactions");
        register("hyperfactions.territory.overclaim", "Overclaim enemy territory", "hyperfactions", "HyperFactions");
        register("hyperfactions.territory.map", "View faction territory map", "hyperfactions", "HyperFactions");

        // ==================== Teleportation (hyperfactions.teleport.*) ====================
        register("hyperfactions.teleport.home", "Teleport to faction home", "hyperfactions", "HyperFactions");
        register("hyperfactions.teleport.sethome", "Set faction home location", "hyperfactions", "HyperFactions");
        register("hyperfactions.teleport.delhome", "Delete faction home location", "hyperfactions", "HyperFactions");
        register("hyperfactions.teleport.stuck", "Use the /f stuck command", "hyperfactions", "HyperFactions");

        // ==================== Diplomacy (hyperfactions.relation.*) ====================
        register("hyperfactions.relation.ally", "Request/accept ally relations", "hyperfactions", "HyperFactions");
        register("hyperfactions.relation.enemy", "Declare enemy relations", "hyperfactions", "HyperFactions");
        register("hyperfactions.relation.neutral", "Set neutral relations", "hyperfactions", "HyperFactions");
        register("hyperfactions.relation.view", "View faction relations", "hyperfactions", "HyperFactions");

        // ==================== Communication (hyperfactions.chat.*) ====================
        register("hyperfactions.chat.faction", "Send faction chat messages", "hyperfactions", "HyperFactions");
        register("hyperfactions.chat.ally", "Send ally chat messages", "hyperfactions", "HyperFactions");

        // ==================== Information (hyperfactions.info.*) ====================
        register("hyperfactions.info.faction", "View faction info", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.list", "View faction list", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.player", "View player info", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.power", "View power info", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.members", "View faction members", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.logs", "View faction activity logs", "hyperfactions", "HyperFactions");
        register("hyperfactions.info.help", "View help", "hyperfactions", "HyperFactions");

        // ==================== Bypass (hyperfactions.bypass.*) ====================
        register("hyperfactions.bypass.build", "Bypass block placement/breaking protection", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.interact", "Bypass interaction protection (doors, buttons)", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.container", "Bypass container protection (chests)", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.damage", "Bypass entity damage protection", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.use", "Bypass item use protection", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.warmup", "Bypass home warmup delay", "hyperfactions", "HyperFactions");
        register("hyperfactions.bypass.cooldown", "Bypass home cooldown timer", "hyperfactions", "HyperFactions");

        // ==================== Admin (hyperfactions.admin.*) ====================
        register("hyperfactions.admin.use", "Base admin access (opens admin GUI)", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.reload", "Reload configuration", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.debug", "Debug commands", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.zones", "Manage safezones and warzones", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.disband", "Force disband any faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.modify", "Modify any faction", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.bypass.limits", "Bypass claim limits", "hyperfactions", "HyperFactions");
        register("hyperfactions.admin.backup", "Manage backups (create, restore, delete)", "hyperfactions", "HyperFactions");

        // ==================== Limit Permissions ====================
        register("hyperfactions.limit.claims.*", "Maximum claims limit (e.g., hyperfactions.limit.claims.50)", "hyperfactions", "HyperFactions");
        register("hyperfactions.limit.power.*", "Maximum power limit (e.g., hyperfactions.limit.power.100)", "hyperfactions", "HyperFactions");

        // ==================== Hytale command path format ====================
        // Hytale uses full Java package path for plugin commands
        register("com.hyperfactions.*", "All HyperFactions package permissions", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.*", "All HyperFactions commands", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.command.*", "All HyperFactions command permissions", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.command.faction", "Use /faction command", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.command.faction.*", "All /faction subcommands", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.command.f", "Use /f alias", "hyperfactions", "HyperFactions");
        register("com.hyperfactions.hyperfactions.command.f.*", "All /f subcommands", "hyperfactions", "HyperFactions");

        Logger.debug("Registered HyperFactions permissions for wildcard expansion");
    }

    /**
     * Registers known MMOSkillTree permissions for wildcard expansion.
     * <p>
     * MMOSkillTree (by Ziggfreed) uses two permission prefixes:
     * <ul>
     *   <li>{@code mmoskilltree.*} - Primary permission prefix</li>
     *   <li>{@code ziggfreed.mmoskilltree.*} - Alternate prefix (checked by PermissionUtil.checkPermission())</li>
     * </ul>
     * <p>
     * MMST also registers commands via Hytale's command system as:
     * {@code com.ziggfreed.mmoskilltree.command.*}
     */
    private void registerMMOSkillTreePermissions() {
        // ==================== Admin (mmoskilltree-admin) ====================
        register("mmoskilltree.admin", "MMOSkillTree admin access (mmoconfig, mmoadmin, mmoboost)", "mmoskilltree-admin", "MMOSkillTree");

        // ==================== Commands (mmoskilltree-command) ====================
        register("mmoskilltree.command.*", "Access to all MMOSkillTree commands", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.xp", "/mmoxp command", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.skilltree", "/skilltree command", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.xpdisplay", "/xpdisplay command", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.setxp", "/setmmoxp command", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.setxp.others", "Set other players' XP", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.quest", "/quest command", "mmoskilltree-command", "MMOSkillTree");
        register("mmoskilltree.command.config", "/mmoconfig command", "mmoskilltree-command", "MMOSkillTree");

        // ==================== Skills (mmoskilltree-skill) ====================
        register("mmoskilltree.skill.*", "Gain XP in all skills", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.mining", "Gain Mining XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.woodcutting", "Gain Woodcutting XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.excavation", "Gain Excavation XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.harvesting", "Gain Harvesting XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.fishing", "Gain Fishing XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.swords", "Gain Swords XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.daggers", "Gain Daggers XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.polearms", "Gain Polearms XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.staves", "Gain Staves XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.axes", "Gain Axes XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.blunt", "Gain Blunt XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.archery", "Gain Archery XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.unarmed", "Gain Unarmed XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.defense", "Gain Defense XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.taming", "Gain Taming XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.acrobatics", "Gain Acrobatics XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.crafting", "Gain Crafting XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.repair", "Gain Repair XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.alchemy", "Gain Alchemy XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.enchanting", "Gain Enchanting XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.cooking", "Gain Cooking XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.smithing", "Gain Smithing XP", "mmoskilltree-skill", "MMOSkillTree");
        register("mmoskilltree.skill.building", "Gain Building XP", "mmoskilltree-skill", "MMOSkillTree");

        // ==================== XP Boosts (mmoskilltree-boost) ====================
        register("mmoskilltree.xpboosts.*", "All XP boosts", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.*.self.*", "All personal XP boosts", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.*.all.*", "All global/server XP boosts", "mmoskilltree-boost", "MMOSkillTree");
        // Representative boost nodes for discoverability (format: target.scope.multiplier.duration.cooldown)
        register("mmoskilltree.xpboosts.all.self.1_25.15.120", "Personal 1.25x boost, 15min, 2hr cooldown", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.all.self.1_5.30.120", "Personal 1.5x boost, 30min, 2hr cooldown", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.all.self.2_0.30.180", "Personal 2x boost, 30min, 3hr cooldown", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.all.all.1_5.30.300", "Global 1.5x boost, 30min, 5hr cooldown", "mmoskilltree-boost", "MMOSkillTree");
        register("mmoskilltree.xpboosts.all.all.2_0.60.600", "Global 2x boost, 60min, 10hr cooldown", "mmoskilltree-boost", "MMOSkillTree");

        // ==================== Hytale Command Paths (mmoskilltree-command) ====================
        register("com.ziggfreed.mmoskilltree.command.*", "All MMST Hytale command paths", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.skilltree", "/skilltree command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.mmoxp", "/mmoxp command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.xpdisplay", "/xpdisplay command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.setmmoxp", "/setmmoxp command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.quest", "/quest command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.mmoconfig", "/mmoconfig command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.mmoadmin", "/mmoadmin command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");
        register("com.ziggfreed.mmoskilltree.command.mmoboost", "/mmoboost command (Hytale path)", "mmoskilltree-command", "MMOSkillTree");

        // ==================== Alternate Prefix (mmoskilltree-alternate) ====================
        register("ziggfreed.mmoskilltree.*", "Alternate prefix wildcard", "mmoskilltree-alternate", "MMOSkillTree");
        register("ziggfreed.mmoskilltree.skill.*", "Alternate prefix - all skills", "mmoskilltree-alternate", "MMOSkillTree");
        register("ziggfreed.mmoskilltree.command.*", "Alternate prefix - all commands", "mmoskilltree-alternate", "MMOSkillTree");
        register("ziggfreed.mmoskilltree.admin", "Alternate prefix - admin access", "mmoskilltree-alternate", "MMOSkillTree");
        register("ziggfreed.mmoskilltree.xpboosts.*", "Alternate prefix - all XP boosts", "mmoskilltree-alternate", "MMOSkillTree");

        Logger.debug("Registered MMOSkillTree permissions for wildcard expansion");
    }
}
