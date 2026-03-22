package com.hyperperms.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads and manages permission templates from bundled resources and custom directories.
 * <p>
 * Bundled templates are loaded from the JAR resources under {@code /templates/}.
 * Custom templates are loaded from the configured templates directory.
 */
public final class TemplateLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * List of bundled template names (without .json extension).
     */
    private static final List<String> BUNDLED_TEMPLATES = List.of(
            "factions",
            "survival",
            "creative",
            "minigames",
            "smp",
            "skyblock",
            "prison",
            "rpg",
            "towny",
            "vanilla",
            "staff"
    );

    private final Path customTemplatesDir;
    private final Map<String, PermissionTemplate> templates = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    /**
     * Creates a new template loader.
     *
     * @param customTemplatesDir the directory for custom templates
     */
    public TemplateLoader(@NotNull Path customTemplatesDir) {
        this.customTemplatesDir = Objects.requireNonNull(customTemplatesDir);
    }

    /**
     * Loads all templates (bundled and custom).
     * <p>
     * Synchronized to prevent concurrent calls from clearing the map mid-load.
     */
    public synchronized void loadAll() {
        templates.clear();
        loadBundledTemplates();
        loadCustomTemplates();
        loaded = true;
        Logger.info("[Templates] Loaded %d templates (%d bundled, %d custom)",
                templates.size(),
                (int) templates.values().stream().filter(PermissionTemplate::isBundled).count(),
                (int) templates.values().stream().filter(t -> !t.isBundled()).count());
    }

    /**
     * Loads bundled templates from JAR resources.
     */
    private void loadBundledTemplates() {
        for (String name : BUNDLED_TEMPLATES) {
            String resourcePath = "/templates/" + name + ".json";
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    Logger.debug("[Templates] Bundled template not found: %s", name);
                    continue;
                }
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                PermissionTemplate template = parseTemplate(name, json, true);
                if (template != null) {
                    templates.put(name, template);
                    Logger.debug("[Templates] Loaded bundled template: %s", name);
                }
            } catch (IOException e) {
                Logger.warn("[Templates] Failed to load bundled template %s: %s", name, e.getMessage());
            }
        }
    }

    /**
     * Loads custom templates from the templates directory.
     */
    private void loadCustomTemplates() {
        if (!Files.exists(customTemplatesDir)) {
            try {
                Files.createDirectories(customTemplatesDir);
                Logger.debug("[Templates] Created custom templates directory: %s", customTemplatesDir);
            } catch (IOException e) {
                Logger.warn("[Templates] Failed to create templates directory: %s", e.getMessage());
                return;
            }
        }

        try (Stream<Path> paths = Files.list(customTemplatesDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadCustomTemplate);
        } catch (IOException e) {
            Logger.warn("[Templates] Failed to list custom templates: %s", e.getMessage());
        }
    }

    /**
     * Loads a single custom template file.
     *
     * @param path the path to the template file
     */
    private void loadCustomTemplate(Path path) {
        String name = path.getFileName().toString().replace(".json", "");
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            PermissionTemplate template = parseTemplate(name, json, false);
            if (template != null) {
                // Custom templates override bundled ones
                templates.put(name, template);
                Logger.debug("[Templates] Loaded custom template: %s", name);
            }
        } catch (IOException e) {
            Logger.warn("[Templates] Failed to load custom template %s: %s", name, e.getMessage());
        }
    }

    /**
     * Parses a template from JSON.
     *
     * @param name    the template name
     * @param json    the JSON content
     * @param bundled whether this is a bundled template
     * @return the parsed template, or null if parsing failed
     */
    @Nullable
    private PermissionTemplate parseTemplate(String name, String json, boolean bundled) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                Logger.warn("[Templates] Template %s is empty", name);
                return null;
            }

            // Parse basic fields
            String displayName = getStringOrDefault(root, "displayName", name);
            String description = getStringOrDefault(root, "description", "");
            String version = getStringOrDefault(root, "version", "1.0.0");
            String author = getStringOrDefault(root, "author", "HyperPerms");

            // Parse groups
            Map<String, TemplateGroup> groups = new LinkedHashMap<>();
            if (root.has("groups") && root.get("groups").isJsonObject()) {
                JsonObject groupsObj = root.getAsJsonObject("groups");
                for (String groupName : groupsObj.keySet()) {
                    TemplateGroup group = parseGroup(groupName, groupsObj.getAsJsonObject(groupName));
                    if (group != null) {
                        groups.put(groupName, group);
                    }
                }
            }

            if (groups.isEmpty()) {
                Logger.warn("[Templates] Template %s has no groups", name);
                return null;
            }

            // Parse tracks
            Map<String, TemplateTrack> tracks = new LinkedHashMap<>();
            if (root.has("tracks") && root.get("tracks").isJsonObject()) {
                JsonObject tracksObj = root.getAsJsonObject("tracks");
                for (String trackName : tracksObj.keySet()) {
                    TemplateTrack track = parseTrack(trackName, tracksObj.get(trackName));
                    if (track != null) {
                        tracks.put(trackName, track);
                    }
                }
            }

            // Parse metadata
            PermissionTemplate.TemplateMetadata metadata = parseMetadata(root);

            return new PermissionTemplate(
                    name, displayName, description, version, author,
                    groups, tracks, metadata, bundled
            );

        } catch (JsonSyntaxException e) {
            Logger.warn("[Templates] Failed to parse template %s: %s", name, e.getMessage());
            return null;
        }
    }

    /**
     * Parses a group from JSON.
     */
    @Nullable
    private TemplateGroup parseGroup(String name, JsonObject obj) {
        if (obj == null) return null;

        // Parse permissions
        List<TemplatePermission> permissions = new ArrayList<>();
        if (obj.has("permissions") && obj.get("permissions").isJsonArray()) {
            JsonArray permsArray = obj.getAsJsonArray("permissions");
            for (JsonElement elem : permsArray) {
                if (elem.isJsonPrimitive()) {
                    // Simple string permission
                    permissions.add(new TemplatePermission(elem.getAsString()));
                } else if (elem.isJsonObject()) {
                    // Permission with description
                    JsonObject permObj = elem.getAsJsonObject();
                    String node = getStringOrDefault(permObj, "node", null);
                    String desc = getStringOrDefault(permObj, "description", null);
                    if (node != null) {
                        permissions.add(new TemplatePermission(node, desc));
                    }
                }
            }
        }

        // Parse parents
        List<String> parents = new ArrayList<>();
        if (obj.has("parents") && obj.get("parents").isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray("parents")) {
                if (elem.isJsonPrimitive()) {
                    parents.add(elem.getAsString());
                }
            }
        }

        int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 0;
        String prefix = getStringOrDefault(obj, "prefix", "");
        String suffix = getStringOrDefault(obj, "suffix", "");
        String displayName = getStringOrDefault(obj, "displayName", null);

        return new TemplateGroup(name, permissions, parents, weight, prefix, suffix, displayName);
    }

    /**
     * Parses a track from JSON.
     */
    @Nullable
    private TemplateTrack parseTrack(String name, JsonElement elem) {
        if (elem == null) return null;

        List<String> groups = new ArrayList<>();
        if (elem.isJsonArray()) {
            for (JsonElement e : elem.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    groups.add(e.getAsString());
                }
            }
        }

        if (groups.isEmpty()) return null;
        return new TemplateTrack(name, groups);
    }

    /**
     * Parses metadata from JSON.
     */
    private PermissionTemplate.TemplateMetadata parseMetadata(JsonObject root) {
        if (!root.has("metadata") || !root.get("metadata").isJsonObject()) {
            return PermissionTemplate.TemplateMetadata.empty();
        }

        JsonObject meta = root.getAsJsonObject("metadata");
        
        List<String> recommendedPlugins = new ArrayList<>();
        if (meta.has("recommendedPlugins") && meta.get("recommendedPlugins").isJsonArray()) {
            for (JsonElement elem : meta.getAsJsonArray("recommendedPlugins")) {
                if (elem.isJsonPrimitive()) {
                    recommendedPlugins.add(elem.getAsString());
                }
            }
        }

        String notes = getStringOrDefault(meta, "notes", null);
        String minVersion = getStringOrDefault(meta, "minHyperPermsVersion", null);

        return new PermissionTemplate.TemplateMetadata(recommendedPlugins, notes, minVersion);
    }

    @Nullable
    private String getStringOrDefault(JsonObject obj, String key, @Nullable String defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    // ==================== Public API ====================

    /**
     * Ensures templates are loaded (double-checked locking).
     */
    private void ensureLoaded() {
        if (!loaded) {
            loadAll();
        }
    }

    /**
     * Gets a template by name.
     *
     * @param name the template name
     * @return the template, or null if not found
     */
    @Nullable
    public PermissionTemplate getTemplate(@NotNull String name) {
        ensureLoaded();
        return templates.get(name.toLowerCase());
    }

    /**
     * Gets all loaded templates.
     *
     * @return an unmodifiable collection of templates
     */
    @NotNull
    public Collection<PermissionTemplate> getAllTemplates() {
        ensureLoaded();
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * Gets all template names.
     *
     * @return an unmodifiable set of template names
     */
    @NotNull
    public Set<String> getTemplateNames() {
        ensureLoaded();
        return Collections.unmodifiableSet(templates.keySet());
    }

    /**
     * Gets only bundled templates.
     *
     * @return a list of bundled templates
     */
    @NotNull
    public List<PermissionTemplate> getBundledTemplates() {
        ensureLoaded();
        return templates.values().stream()
                .filter(PermissionTemplate::isBundled)
                .toList();
    }

    /**
     * Gets only custom templates.
     *
     * @return a list of custom templates
     */
    @NotNull
    public List<PermissionTemplate> getCustomTemplates() {
        ensureLoaded();
        return templates.values().stream()
                .filter(t -> !t.isBundled())
                .toList();
    }

    /**
     * Checks if a template exists.
     *
     * @param name the template name
     * @return true if the template exists
     */
    public boolean hasTemplate(@NotNull String name) {
        ensureLoaded();
        return templates.containsKey(name.toLowerCase());
    }

    /**
     * Reloads all templates.
     */
    public void reload() {
        loadAll();
    }

    /**
     * Gets the custom templates directory.
     *
     * @return the path to the custom templates directory
     */
    @NotNull
    public Path getCustomTemplatesDir() {
        return customTemplatesDir;
    }
}
