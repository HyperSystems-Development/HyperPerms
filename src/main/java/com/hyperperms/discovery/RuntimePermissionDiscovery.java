package com.hyperperms.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperperms.registry.PermissionRegistry;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Captures, persists, and prunes permissions discovered at runtime.
 * <p>
 * Every permission check flows through {@link com.hyperperms.util.HyperPermsPermissionSet#contains(Object)}.
 * This class records each unique permission string seen there, persists discoveries to disk,
 * and prunes entries whose owning plugin is no longer installed.
 */
public final class RuntimePermissionDiscovery {

    /**
     * Metadata about a single discovered permission.
     */
    public static final class DiscoveredPermission {
        private final String permission;
        private String plugin;
        private String namespace;
        private long firstSeen;
        private long lastSeen;

        public DiscoveredPermission(@NotNull String permission, @NotNull String plugin,
                                     @NotNull String namespace, long firstSeen, long lastSeen) {
            this.permission = permission;
            this.plugin = plugin;
            this.namespace = namespace;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }

        public @NotNull String getPermission() { return permission; }
        public @NotNull String getPlugin() { return plugin; }
        public @NotNull String getNamespace() { return namespace; }
        public long getFirstSeen() { return firstSeen; }
        public long getLastSeen() { return lastSeen; }

        void updateLastSeen(long time) { this.lastSeen = time; }
    }

    /** All discovered permissions: permission node -> metadata */
    private final ConcurrentHashMap<String, DiscoveredPermission> discovered = new ConcurrentHashMap<>();

    /** Tracks whether new data has been added since last save */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** Path to the persistence file */
    private final Path dataFile;

    /** Mods directory for scanning installed plugins */
    private final Path modsDirectory;

    /** Namespace -> plugin name mapping (built once at startup) */
    private final Map<String, String> namespaceToPlugin = new ConcurrentHashMap<>();

    /** Set of built-in namespaces that should never be discovered at runtime */
    private static final Set<String> BUILT_IN_NAMESPACES = Set.of(
        "hyperperms", "hytale",
        "com", // com.* packages are handled by built-in registrations
        "mmoskilltree", "ziggfreed" // MMOSkillTree pre-registered
    );

    private static final Pattern JAR_NAME_PATTERN = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*?)(?:-\\d.*)?(?:\\.jar)?$");
    private static final Pattern DATA_DIR_PATTERN = Pattern.compile("^(?:com\\.[a-z]+_)?([A-Za-z][A-Za-z0-9_-]*)$");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Creates a new discovery instance.
     *
     * @param dataDirectory the plugin's data directory (discovered-permissions.json will be stored here)
     * @param modsDirectory the Hytale mods directory for scanning installed plugins
     */
    public RuntimePermissionDiscovery(@NotNull Path dataDirectory, @NotNull Path modsDirectory) {
        this.dataFile = dataDirectory.resolve("discovered-permissions.json");
        this.jarCacheFile = dataDirectory.resolve("jar-scan-cache.json");
        this.modsDirectory = modsDirectory;
    }

    /**
     * Records a permission seen during a contains() check.
     * <p>
     * This method is O(1) — just a ConcurrentHashMap put.
     * It skips permissions that are already in the built-in registry or have built-in namespaces.
     *
     * @param permission the raw permission string (not negation-prefixed)
     */
    public void record(@NotNull String permission) {
        String normalized = permission.toLowerCase();

        // Skip empty or wildcard-only
        if (normalized.isEmpty() || normalized.equals("*")) {
            return;
        }

        // Extract namespace (first segment)
        String namespace = extractNamespace(normalized);

        // Skip built-in namespaces
        if (BUILT_IN_NAMESPACES.contains(namespace)) {
            return;
        }

        // Skip if already registered in the built-in registry
        PermissionRegistry registry = PermissionRegistry.getInstance();
        if (registry.isBuiltIn(normalized)) {
            return;
        }

        // Record or update
        long now = System.currentTimeMillis();
        discovered.compute(normalized, (key, existing) -> {
            if (existing != null) {
                existing.updateLastSeen(now);
                return existing;
            }
            dirty.set(true);
            String pluginName = inferPluginName(namespace);
            Logger.info("[Discovery] New permission discovered: %s (plugin: %s)", normalized, pluginName);
            return new DiscoveredPermission(normalized, pluginName, namespace, now, now);
        });
    }

    /**
     * Saves discovered permissions to disk if dirty.
     */
    public void save() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }

        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);

            JsonObject permsObj = new JsonObject();
            for (var entry : discovered.entrySet()) {
                DiscoveredPermission dp = entry.getValue();
                JsonObject permObj = new JsonObject();
                permObj.addProperty("plugin", dp.getPlugin());
                permObj.addProperty("namespace", dp.getNamespace());
                permObj.addProperty("firstSeen", dp.getFirstSeen());
                permObj.addProperty("lastSeen", dp.getLastSeen());
                permsObj.add(entry.getKey(), permObj);
            }
            root.add("permissions", permsObj);

            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, gson.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Logger.debug("[Discovery] Saved %d discovered permissions", discovered.size());
        } catch (IOException e) {
            Logger.warn("[Discovery] Failed to save discovered permissions: %s", e.getMessage());
            // Re-set dirty so we try again next cycle
            dirty.set(true);
        }
    }

    /**
     * Loads discovered permissions from disk.
     */
    public void load() {
        if (!Files.exists(dataFile)) {
            Logger.debug("[Discovery] No discovered-permissions.json found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonObject permsObj = root.getAsJsonObject("permissions");
            if (permsObj == null) {
                return;
            }

            int loaded = 0;
            for (var entry : permsObj.entrySet()) {
                String permNode = entry.getKey();
                JsonObject permData = entry.getValue().getAsJsonObject();

                String plugin = permData.has("plugin") ? permData.get("plugin").getAsString() : "Unknown";
                String namespace = permData.has("namespace") ? permData.get("namespace").getAsString() : extractNamespace(permNode);
                long firstSeen = permData.has("firstSeen") ? permData.get("firstSeen").getAsLong() : System.currentTimeMillis();
                long lastSeen = permData.has("lastSeen") ? permData.get("lastSeen").getAsLong() : firstSeen;

                discovered.put(permNode, new DiscoveredPermission(permNode, plugin, namespace, firstSeen, lastSeen));
                loaded++;
            }

            Logger.info("[Discovery] Loaded %d discovered permissions from disk", loaded);
        } catch (Exception e) {
            Logger.warn("[Discovery] Failed to load discovered-permissions.json: %s", e.getMessage());
        }
    }

    /**
     * Prunes discovered permissions whose inferred plugin is no longer installed.
     * Built-in permissions are never pruned. User/group configs are never touched.
     *
     * @param installedPluginNames set of currently installed plugin names
     */
    public void pruneRemovedPlugins(@NotNull Set<String> installedPluginNames) {
        Set<String> installedLower = new HashSet<>();
        for (String name : installedPluginNames) {
            installedLower.add(name.toLowerCase());
        }

        int pruned = 0;
        Iterator<Map.Entry<String, DiscoveredPermission>> it = discovered.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, DiscoveredPermission> entry = it.next();
            DiscoveredPermission dp = entry.getValue();

            // If the plugin is not in the installed set, prune it
            if (!installedLower.contains(dp.getPlugin().toLowerCase())) {
                Logger.info("[Discovery] Pruning permission '%s' (plugin '%s' no longer installed)",
                    entry.getKey(), dp.getPlugin());
                it.remove();
                pruned++;
            }
        }

        if (pruned > 0) {
            dirty.set(true);
            Logger.info("[Discovery] Pruned %d permissions from uninstalled plugins", pruned);
        }
    }

    /**
     * Registers all discovered permissions into the permission registry.
     *
     * @param registry the permission registry
     */
    public void registerAll(@NotNull PermissionRegistry registry) {
        int registered = 0;
        for (var entry : discovered.entrySet()) {
            DiscoveredPermission dp = entry.getValue();
            String description = "Discovered permission from " + dp.getPlugin();
            if (registry.register(entry.getKey(), description, "discovered", dp.getPlugin())) {
                registered++;
            }
        }
        if (registered > 0) {
            Logger.info("[Discovery] Registered %d discovered permissions into registry", registered);
        }
    }

    /**
     * Returns an unmodifiable view of all discovered permissions.
     */
    @NotNull
    public Map<String, DiscoveredPermission> getDiscoveredPermissions() {
        return Collections.unmodifiableMap(discovered);
    }

    /**
     * Scans the mods directory to determine which plugins are currently installed.
     *
     * @return set of installed plugin names
     */
    @NotNull
    public Set<String> scanInstalledPlugins() {
        Set<String> installed = new HashSet<>();

        // Always include built-in plugins
        installed.add("HyperPerms");
        installed.add("Hytale");

        if (!Files.isDirectory(modsDirectory)) {
            Logger.debug("[Discovery] Mods directory not found: %s", modsDirectory);
            return installed;
        }

        try (Stream<Path> entries = Files.list(modsDirectory)) {
            entries.forEach(path -> {
                String fileName = path.getFileName().toString();

                if (Files.isRegularFile(path) && fileName.endsWith(".jar")) {
                    // Extract plugin name from JAR filename
                    // e.g., "EconomySystem-1.0.8.1-beta.jar" -> "EconomySystem"
                    String nameWithoutJar = fileName.substring(0, fileName.length() - 4);
                    String pluginName = extractPluginNameFromJar(nameWithoutJar);
                    if (pluginName != null) {
                        installed.add(pluginName);
                        // Build namespace mapping
                        String namespace = pluginName.toLowerCase().replaceAll("[^a-z0-9]", "");
                        namespaceToPlugin.putIfAbsent(namespace, pluginName);
                    }
                } else if (Files.isDirectory(path)) {
                    // Extract plugin name from data directory
                    // e.g., "com.economy_EconomySystem" -> "EconomySystem"
                    // e.g., "com.hyperhomes_HyperHomes" -> "HyperHomes"
                    String pluginName = extractPluginNameFromDataDir(fileName);
                    if (pluginName != null) {
                        installed.add(pluginName);
                        // Build namespace mapping from data dir
                        String namespace = pluginName.toLowerCase().replaceAll("[^a-z0-9]", "");
                        namespaceToPlugin.putIfAbsent(namespace, pluginName);

                        // Also try to extract namespace from the directory prefix
                        // "com.economy_EconomySystem" -> namespace "economy"
                        // "Economy_EconomySystem" -> namespace "economy"
                        // "com.hyperhomes_HyperHomes" -> namespace "hyperhomes"
                        int underscoreIdx = fileName.indexOf('_');
                        if (underscoreIdx > 0) {
                            String prefix = fileName.substring(0, underscoreIdx);
                            // "com.economy" -> "economy"
                            int lastDot = prefix.lastIndexOf('.');
                            if (lastDot >= 0) {
                                String dirNamespace = prefix.substring(lastDot + 1).toLowerCase();
                                namespaceToPlugin.putIfAbsent(dirNamespace, pluginName);
                            } else {
                                // No dot - use the whole prefix as namespace (e.g., "Economy" -> "economy")
                                namespaceToPlugin.putIfAbsent(prefix.toLowerCase(), pluginName);
                            }
                        }

                        // Try reading manifest.json for the Name field
                        tryReadManifest(path, pluginName);
                    }
                }
            });
        } catch (IOException e) {
            Logger.warn("[Discovery] Failed to scan mods directory: %s", e.getMessage());
        }

        Logger.info("[Discovery] Found %d installed plugins: %s", installed.size(), installed);
        return installed;
    }

    /**
     * Builds the namespace-to-plugin mapping by scanning the mods directory.
     * Should be called once at startup after scanInstalledPlugins().
     */
    public void buildNamespaceMapping() {
        // The namespace mapping is already built during scanInstalledPlugins(),
        // but we can add additional mappings here if needed.

        // Common namespace mappings for known plugins
        // "theeconomy" -> "EconomySystem" (if EconomySystem is installed)
        // This handles cases where the permission namespace doesn't match the plugin name
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }

        try (Stream<Path> entries = Files.list(modsDirectory)) {
            entries.forEach(path -> {
                if (Files.isDirectory(path)) {
                    String dirName = path.getFileName().toString();
                    // Look for manifest.json to get the actual namespace
                    Path manifestPath = path.resolve("manifest.json");
                    if (Files.exists(manifestPath)) {
                        try {
                            String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
                            JsonObject manifest = JsonParser.parseString(content).getAsJsonObject();
                            if (manifest.has("Name")) {
                                String name = manifest.get("Name").getAsString();
                                // If manifest has a Namespace or ID field, map it
                                if (manifest.has("Namespace")) {
                                    String ns = manifest.get("Namespace").getAsString().toLowerCase();
                                    namespaceToPlugin.putIfAbsent(ns, name);
                                }
                                if (manifest.has("Id")) {
                                    String id = manifest.get("Id").getAsString().toLowerCase();
                                    // IDs often look like "com.economy.economysystem"
                                    // Extract last segment
                                    int lastDot = id.lastIndexOf('.');
                                    if (lastDot >= 0) {
                                        String idNamespace = id.substring(lastDot + 1);
                                        namespaceToPlugin.putIfAbsent(idNamespace, name);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Silently skip malformed manifests
                        }
                    }
                }
            });
        } catch (IOException e) {
            // Already warned during scanInstalledPlugins
        }

        Logger.info("[Discovery] Namespace mappings: %s", namespaceToPlugin);
    }

    /** Cache file for JAR scan results */
    private final Path jarCacheFile;

    /** Words that indicate a string is NOT a permission (class names, Java internals) */
    private static final Set<String> BLACKLIST_WORDS = Set.of(
        "exception", "error", "handler", "listener", "factory", "builder", "impl",
        "adapter", "wrapper", "proxy", "delegate", "callback", "invoker", "provider",
        "service", "manager", "controller", "processor", "executor", "scheduler",
        "registry", "repository", "accessor", "mutator", "validator", "converter",
        "serializer", "deserializer", "encoder", "decoder", "parser", "formatter",
        "logger", "writer", "reader", "stream", "buffer", "channel", "socket",
        "connection", "session", "transaction", "context", "scope", "bean", "component",
        "module", "package", "class", "interface", "abstract", "static", "final",
        "public", "private", "protected", "internal", "native", "synchronized",
        "assertion", "replication", "configuration", "initialization", "implementation",
        "instantiation", "invocation", "allocation", "deallocation", "serialization",
        "model", "entity", "dto", "dao", "pojo", "enum", "annotation", "aspect",
        "test", "spec", "mock", "stub", "fake", "spy", "fixture",
        "server", "client", "request", "response", "header", "body", "payload",
        "asset", "resource", "texture", "shader", "mesh", "material", "prefab",
        "hytale", "hypixel", "minecraft", "mojang", "bukkit", "spigot", "paper"
    );

    /** Known permission prefixes - strings starting with these are likely permissions */
    private static final Set<String> KNOWN_PERMISSION_PREFIXES = Set.of(
        // Economy
        "theeconomy.", "economy.", "shop.", "money.", "balance.",
        "trade.", "auction.", "market.", "bank.", "currency.", "pay.",
        // Admin/Management
        "adminui.", "admin.", "staff.", "mod.", "moderator.",
        // Teleportation
        "warp.", "spawn.", "home.", "teleport.", "tpa.", "back.", "rtp.",
        // Chat
        "chat.", "msg.", "message.", "pm.", "whisper.", "broadcast.", "announce.",
        // Punishment
        "kick.", "ban.", "mute.", "warn.", "punish.", "jail.", "freeze.",
        // Permissions
        "rank.", "group.", "permission.", "perm.", "role.", "prefix.", "suffix.",
        // Protection
        "claim.", "protect.", "region.", "zone.", "area.", "land.", "plot.",
        // Social
        "faction.", "guild.", "clan.", "party.", "team.", "ally.", "friend.",
        // Other plugins
        "essentials.", "nucleus.", "cmi.", "luckperms.", "vault.",
        "worldedit.", "worldguard.", "griefprevention.", "towny.",
        // Common namespaces
        "player.", "user.", "server.", "world.", "game.", "item.", "block.",
        "inventory.", "gui.", "menu.", "command.", "cmd.",
        // MMOSkillTree
        "mmoskilltree.", "ziggfreed."
    );

    /**
     * Scans plugin JAR files for permission strings and pre-registers discovered permissions.
     * Uses caching to avoid re-scanning unchanged JARs.
     *
     * @param installedPlugins the set of installed plugin names from scanInstalledPlugins()
     */
    public void scanJarPermissions(@NotNull Set<String> installedPlugins) {
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }

        // Load cache
        Map<String, JarCacheEntry> cache = loadJarCache();
        Map<String, JarCacheEntry> newCache = new HashMap<>();
        boolean cacheChanged = false;

        // Track permissions found
        Map<String, Set<String>> jarPermissions = new HashMap<>();

        try (Stream<Path> entries = Files.list(modsDirectory)) {
            List<Path> jarFiles = entries
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                .toList();

            for (Path jarPath : jarFiles) {
                String fileName = jarPath.getFileName().toString();
                String pluginName = extractPluginNameFromJar(fileName.substring(0, fileName.length() - 4));

                // Skip built-in namespaces (HyperPerms itself, Hytale)
                if (BUILT_IN_NAMESPACES.contains(pluginName.toLowerCase())) {
                    continue;
                }

                // Check cache
                long lastModified = Files.getLastModifiedTime(jarPath).toMillis();
                JarCacheEntry cached = cache.get(fileName);

                if (cached != null && cached.lastModified == lastModified) {
                    // Use cached results
                    newCache.put(fileName, cached);
                    if (!cached.permissions.isEmpty()) {
                        jarPermissions.put(cached.pluginName, cached.permissions);
                        // Update namespace mappings from cache
                        for (String perm : cached.permissions) {
                            String ns = extractNamespace(perm);
                            namespaceToPlugin.putIfAbsent(ns, cached.pluginName);
                        }
                    }
                    continue;
                }

                // Scan JAR
                cacheChanged = true;
                Set<String> foundPermissions = new HashSet<>();
                String actualPluginName = pluginName;
                String manifestGroup = null;

                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    // Read manifest.json if present
                    JarEntry manifestEntry = jar.getJarEntry("manifest.json");
                    if (manifestEntry != null) {
                        try (InputStream is = jar.getInputStream(manifestEntry)) {
                            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            JsonObject manifest = JsonParser.parseString(content).getAsJsonObject();
                            if (manifest.has("Name")) {
                                actualPluginName = manifest.get("Name").getAsString();
                            }
                            if (manifest.has("Group")) {
                                manifestGroup = manifest.get("Group").getAsString();
                            }
                        } catch (Exception e) {
                            // Ignore manifest read errors
                        }
                    }

                    // Scan class files for permission strings
                    Enumeration<JarEntry> jarEntries = jar.entries();
                    while (jarEntries.hasMoreElements()) {
                        JarEntry entry = jarEntries.nextElement();
                        if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                            try (InputStream is = jar.getInputStream(entry)) {
                                byte[] bytes = is.readAllBytes();
                                extractPermissionStrings(bytes, foundPermissions);
                            } catch (Exception e) {
                                // Skip unreadable entries
                            }
                        }
                    }

                } catch (Exception e) {
                    Logger.debug("[Discovery] Failed to scan JAR %s: %s", fileName, e.getMessage());
                }

                // Build accepted namespaces from all available metadata
                Set<String> acceptedNs = new HashSet<>();
                // JAR filename-derived name (e.g., "PlaceholderAPI" -> "placeholderapi")
                acceptedNs.add(pluginName.toLowerCase().replaceAll("[^a-z0-9]", ""));
                // Manifest Name (e.g., "WiFlowPlaceholderAPI" -> "wiflowplaceholderapi")
                String pluginNs = actualPluginName.toLowerCase().replaceAll("[^a-z0-9]", "");
                acceptedNs.add(pluginNs);
                // Manifest Group (e.g., "Ecotale" -> "ecotale")
                if (manifestGroup != null) {
                    String groupNs = manifestGroup.toLowerCase().replaceAll("[^a-z0-9]", "");
                    if (groupNs.length() >= 3) { // skip tiny groups like "io", "com"
                        acceptedNs.add(groupNs);
                    }
                }

                // Filter: only keep permissions whose namespace matches an accepted namespace
                // Uses prefix matching in both directions for family plugins (ecotale/ecotalecoins)
                int beforeFilter = foundPermissions.size();
                foundPermissions.removeIf(perm -> {
                    String ns = extractNamespace(perm);
                    for (String accepted : acceptedNs) {
                        if (ns.equals(accepted) || accepted.startsWith(ns) || ns.startsWith(accepted)) {
                            return false; // keep
                        }
                    }
                    return true; // remove
                });
                if (beforeFilter != foundPermissions.size()) {
                    Logger.debug("[Discovery] Namespace filter removed %d false positives from %s (accepted: %s)",
                        beforeFilter - foundPermissions.size(), fileName, acceptedNs);
                }

                // Update namespace mappings
                Set<String> namespaces = new HashSet<>();
                for (String perm : foundPermissions) {
                    String ns = extractNamespace(perm);
                    if (!BUILT_IN_NAMESPACES.contains(ns)) {
                        namespaces.add(ns);
                        namespaceToPlugin.putIfAbsent(ns, actualPluginName);
                    }
                }

                // Cache results
                newCache.put(fileName, new JarCacheEntry(actualPluginName, lastModified, foundPermissions));

                if (!foundPermissions.isEmpty()) {
                    Logger.info("[Discovery] Scanned %s: found %d permissions in namespaces %s",
                        fileName, foundPermissions.size(), namespaces);
                    jarPermissions.put(actualPluginName, foundPermissions);
                }
            }
        } catch (IOException e) {
            Logger.warn("[Discovery] Failed to list mods directory for JAR scanning: %s", e.getMessage());
        }

        // Save cache if changed
        if (cacheChanged) {
            saveJarCache(newCache);
        }

        // Pre-register discovered permissions (skip already built-in ones)
        int preRegistered = 0;
        PermissionRegistry registry = PermissionRegistry.getInstance();
        for (Map.Entry<String, Set<String>> entry : jarPermissions.entrySet()) {
            String pluginName = entry.getKey();
            for (String perm : entry.getValue()) {
                if (!discovered.containsKey(perm) && !registry.isBuiltIn(perm)) {
                    long now = System.currentTimeMillis();
                    String namespace = extractNamespace(perm);
                    discovered.put(perm, new DiscoveredPermission(perm, pluginName, namespace, now, now));
                    preRegistered++;
                }
            }
        }

        if (preRegistered > 0) {
            dirty.set(true);
            Logger.info("[Discovery] Pre-registered %d permissions from JAR scanning", preRegistered);
        }
    }

    /**
     * Extracts permission-like strings from class file bytes.
     * Uses strict filtering to avoid false positives.
     */
    private void extractPermissionStrings(byte[] bytes, Set<String> results) {
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c >= 32 && c < 127) {
                current.append(c);
            } else {
                if (current.length() >= 5 && current.length() <= 50) {
                    String str = current.toString();
                    if (isLikelyPermission(str)) {
                        results.add(str.toLowerCase());
                    }
                }
                current.setLength(0);
            }
        }
    }

    /**
     * Checks if a string is likely a permission node.
     */
    private boolean isLikelyPermission(String str) {
        // Must be lowercase or have specific format
        String lower = str.toLowerCase();

        // Quick reject: must contain a dot
        if (!lower.contains(".")) {
            return false;
        }

        // Must match permission pattern: word.word[.word...] with optional wildcards
        if (!lower.matches("^[a-z][a-z0-9_]*(?:\\.[a-z0-9_*]+)+$")) {
            return false;
        }

        String[] parts = lower.split("\\.");

        // Must have 2-5 segments (permissions are typically short)
        if (parts.length < 2 || parts.length > 5) {
            return false;
        }

        // First segment must be 3+ chars (namespaces like "theeconomy", "adminui")
        if (parts[0].length() < 3) {
            return false;
        }

        // Reject if ends with a number (like .0, .1 - usually class references)
        String lastPart = parts[parts.length - 1];
        if (lastPart.matches("^\\d+$")) {
            return false;
        }

        // Reject if any part contains blacklisted words
        for (String part : parts) {
            if (BLACKLIST_WORDS.contains(part)) {
                return false;
            }
            // Also check if part contains blacklisted word as substring
            for (String blacklisted : BLACKLIST_WORDS) {
                if (part.length() > 6 && part.contains(blacklisted)) {
                    return false;
                }
            }
        }

        // Reject common Java package patterns
        if (lower.startsWith("java.") || lower.startsWith("javax.") ||
            lower.startsWith("sun.") || lower.startsWith("com.sun.") ||
            lower.startsWith("org.") || lower.startsWith("io.") ||
            lower.startsWith("net.") || lower.startsWith("com.google.") ||
            lower.startsWith("com.mojang.") || lower.startsWith("com.hypixel.") ||
            lower.contains("$") || lower.contains("_") && lower.contains("impl")) {
            return false;
        }

        // Reject file extensions and versions
        if (lower.contains(".class") || lower.contains(".java") ||
            lower.contains(".json") || lower.contains(".yml") ||
            lower.contains(".xml") || lower.contains(".properties") ||
            lower.matches(".*\\d+\\.\\d+.*")) {
            return false;
        }

        // Accept if starts with known permission prefix
        for (String prefix : KNOWN_PERMISSION_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }

        // Accept if looks like a command permission (namespace.action or namespace.object.action)
        // Common patterns: plugin.command, plugin.use, plugin.admin, plugin.player.action
        if (parts.length >= 2) {
            String action = parts[parts.length - 1];
            // Common permission actions
            if (Set.of("use", "admin", "reload", "help", "info", "list", "create", "delete",
                       "modify", "edit", "set", "get", "add", "remove", "clear", "reset",
                       "give", "take", "send", "receive", "view", "manage", "bypass",
                       "self", "others", "all", "own", "tp", "teleport", "warp", "spawn",
                       "home", "sethome", "delhome", "pay", "balance", "money", "shop",
                       "buy", "sell", "trade", "kick", "ban", "mute", "warn", "jail",
                       "unban", "unmute", "pardon", "whitelist", "blacklist",
                       "open", "close", "access", "execute", "run", "start", "stop",
                       "enable", "disable", "toggle", "switch", "show", "hide",
                       "read", "write", "save", "load", "backup", "restore",
                       "claim", "unclaim", "trust", "untrust", "invite", "uninvite",
                       "join", "leave", "accept", "deny", "cancel", "confirm"
                ).contains(action) || action.endsWith("*")) {
                return true;
            }

            // Check second-to-last part for common objects
            if (parts.length >= 3) {
                String object = parts[parts.length - 2];
                if (Set.of("player", "user", "command", "item", "block", "entity",
                           "world", "server", "chat", "inventory", "gui", "menu"
                    ).contains(object)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Cache entry for JAR scan results */
    private static class JarCacheEntry {
        final String pluginName;
        final long lastModified;
        final Set<String> permissions;

        JarCacheEntry(String pluginName, long lastModified, Set<String> permissions) {
            this.pluginName = pluginName;
            this.lastModified = lastModified;
            this.permissions = permissions;
        }
    }

    /** Loads JAR scan cache from disk */
    private Map<String, JarCacheEntry> loadJarCache() {
        Map<String, JarCacheEntry> cache = new HashMap<>();
        if (!Files.exists(jarCacheFile)) {
            return cache;
        }

        try {
            String json = Files.readString(jarCacheFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject jars = root.getAsJsonObject("jars");
            if (jars == null) return cache;

            for (String jarName : jars.keySet()) {
                JsonObject entry = jars.getAsJsonObject(jarName);
                String pluginName = entry.get("pluginName").getAsString();
                long lastModified = entry.get("lastModified").getAsLong();
                Set<String> permissions = new HashSet<>();
                if (entry.has("permissions")) {
                    for (var perm : entry.getAsJsonArray("permissions")) {
                        permissions.add(perm.getAsString());
                    }
                }
                cache.put(jarName, new JarCacheEntry(pluginName, lastModified, permissions));
            }
        } catch (Exception e) {
            Logger.debug("[Discovery] Failed to load JAR cache: %s", e.getMessage());
        }

        return cache;
    }

    /** Saves JAR scan cache to disk */
    private void saveJarCache(Map<String, JarCacheEntry> cache) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);

            JsonObject jars = new JsonObject();
            for (var entry : cache.entrySet()) {
                JsonObject jarEntry = new JsonObject();
                jarEntry.addProperty("pluginName", entry.getValue().pluginName);
                jarEntry.addProperty("lastModified", entry.getValue().lastModified);

                com.google.gson.JsonArray perms = new com.google.gson.JsonArray();
                for (String perm : entry.getValue().permissions) {
                    perms.add(perm);
                }
                jarEntry.add("permissions", perms);

                jars.add(entry.getKey(), jarEntry);
            }
            root.add("jars", jars);

            Files.createDirectories(jarCacheFile.getParent());
            Files.writeString(jarCacheFile, gson.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Logger.debug("[Discovery] Saved JAR scan cache with %d entries", cache.size());
        } catch (Exception e) {
            Logger.warn("[Discovery] Failed to save JAR cache: %s", e.getMessage());
        }
    }

    // ==================== Internal Helpers ====================

    /**
     * Extracts the top-level namespace from a permission node.
     * e.g., "theeconomy.player.money" -> "theeconomy"
     */
    @NotNull
    private static String extractNamespace(@NotNull String permission) {
        int dotIndex = permission.indexOf('.');
        return dotIndex > 0 ? permission.substring(0, dotIndex) : permission;
    }

    /**
     * Infers a plugin name from a permission namespace.
     */
    @NotNull
    private String inferPluginName(@NotNull String namespace) {
        // Check our mapping first
        String mapped = namespaceToPlugin.get(namespace.toLowerCase());
        if (mapped != null) {
            return mapped;
        }

        // Fallback: capitalize first letter
        if (namespace.isEmpty()) {
            return "Unknown";
        }
        return Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
    }

    /**
     * Extracts plugin name from a JAR filename (without .jar extension).
     * e.g., "EconomySystem-1.0.8.1-beta" -> "EconomySystem"
     * e.g., "BetterMap-2.0" -> "BetterMap"
     */
    @NotNull
    private static String extractPluginNameFromJar(@NotNull String nameWithoutJar) {
        Matcher matcher = JAR_NAME_PATTERN.matcher(nameWithoutJar);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        // Fallback: split on first hyphen
        int hyphenIdx = nameWithoutJar.indexOf('-');
        if (hyphenIdx > 0) {
            return nameWithoutJar.substring(0, hyphenIdx);
        }
        return nameWithoutJar;
    }

    /**
     * Extracts plugin name from a data directory name.
     * e.g., "com.economy_EconomySystem" -> "EconomySystem"
     * e.g., "com.hyperhomes_HyperHomes" -> "HyperHomes"
     */
    @NotNull
    private static String extractPluginNameFromDataDir(@NotNull String dirName) {
        // Pattern: "com.xxx_PluginName" or just "PluginName"
        int underscoreIdx = dirName.lastIndexOf('_');
        if (underscoreIdx >= 0 && underscoreIdx < dirName.length() - 1) {
            return dirName.substring(underscoreIdx + 1);
        }
        // No underscore — might just be the plugin name
        Matcher matcher = DATA_DIR_PATTERN.matcher(dirName);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return dirName;
    }

    /**
     * Tries to read manifest.json inside a plugin data directory for additional name/namespace info.
     */
    private void tryReadManifest(@NotNull Path dataDir, @NotNull String fallbackName) {
        Path manifestPath = dataDir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            return;
        }

        try {
            String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
            JsonObject manifest = JsonParser.parseString(content).getAsJsonObject();

            if (manifest.has("Name")) {
                String name = manifest.get("Name").getAsString();
                String namespace = name.toLowerCase().replaceAll("[^a-z0-9]", "");
                namespaceToPlugin.putIfAbsent(namespace, name);
            }
        } catch (Exception e) {
            // Silently skip
        }
    }
}
