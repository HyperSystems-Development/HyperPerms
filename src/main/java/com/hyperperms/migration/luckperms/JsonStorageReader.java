package com.hyperperms.migration.luckperms;

import com.google.gson.*;
import com.hyperperms.migration.luckperms.LuckPermsData.*;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Reads LuckPerms data from JSON file storage.
 * <p>
 * Expected directory structure:
 * <pre>
 * json-storage/
 * ├── groups/
 * │   ├── default.json
 * │   ├── admin.json
 * │   └── ...
 * ├── users/
 * │   ├── {uuid}.json
 * │   └── ...
 * └── tracks/
 *     ├── staff.json
 *     └── ...
 * </pre>
 */
public final class JsonStorageReader implements LuckPermsStorageReader {
    
    private final Path storageDir;
    private final Gson gson;
    
    public JsonStorageReader(@NotNull Path storageDir) {
        this.storageDir = storageDir;
        this.gson = new GsonBuilder().create();
    }
    
    @Override
    @NotNull
    public LuckPermsStorageType getStorageType() {
        return LuckPermsStorageType.JSON;
    }
    
    @Override
    @NotNull
    public String getStorageDescription() {
        return "JSON storage (" + storageDir + ")";
    }
    
    @Override
    public boolean isAvailable() {
        return Files.isDirectory(storageDir) &&
               (Files.isDirectory(storageDir.resolve("groups")) ||
                Files.isDirectory(storageDir.resolve("users")));
    }
    
    @Override
    @NotNull
    public Map<String, LPGroup> readGroups() throws IOException {
        Logger.debugMigration("Reading groups from %s", getStorageDescription());
        Path groupsDir = storageDir.resolve("groups");
        if (!Files.isDirectory(groupsDir)) {
            return Collections.emptyMap();
        }

        Map<String, LPGroup> groups = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(groupsDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        LPGroup group = readGroupFile(file);
                        if (group != null) {
                            groups.put(group.name(), group);
                        }
                    } catch (Exception e) {
                        Logger.warn("Failed to read group file %s: %s", file.getFileName(), e.getMessage());
                    }
                });
        }

        Logger.debugMigration("Read %d groups from %s", groups.size(), getStorageDescription());
        return groups;
    }
    
    @Override
    @NotNull
    public Map<UUID, LPUser> readUsers() throws IOException {
        Logger.debugMigration("Reading users from %s", getStorageDescription());
        Path usersDir = storageDir.resolve("users");
        if (!Files.isDirectory(usersDir)) {
            return Collections.emptyMap();
        }

        Map<UUID, LPUser> users = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(usersDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        LPUser user = readUserFile(file);
                        if (user != null) {
                            users.put(user.uuid(), user);
                        }
                    } catch (Exception e) {
                        Logger.warn("Failed to read user file %s: %s", file.getFileName(), e.getMessage());
                    }
                });
        }

        Logger.debugMigration("Read %d users from %s", users.size(), getStorageDescription());
        return users;
    }
    
    @Override
    @NotNull
    public Map<String, LPTrack> readTracks() throws IOException {
        Logger.debugMigration("Reading tracks from %s", getStorageDescription());
        Path tracksDir = storageDir.resolve("tracks");
        if (!Files.isDirectory(tracksDir)) {
            return Collections.emptyMap();
        }

        Map<String, LPTrack> tracks = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(tracksDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        LPTrack track = readTrackFile(file);
                        if (track != null) {
                            tracks.put(track.name(), track);
                        }
                    } catch (Exception e) {
                        Logger.warn("Failed to read track file %s: %s", file.getFileName(), e.getMessage());
                    }
                });
        }

        Logger.debugMigration("Read %d tracks from %s", tracks.size(), getStorageDescription());
        return tracks;
    }
    
    @Override
    public int estimateUserCount() {
        Path usersDir = storageDir.resolve("users");
        if (!Files.isDirectory(usersDir)) {
            return 0;
        }
        
        try (Stream<Path> files = Files.list(usersDir)) {
            return (int) files.filter(p -> p.toString().endsWith(".json")).count();
        } catch (IOException e) {
            return -1;
        }
    }
    
    /**
     * Reads a group from a JSON file.
     * <p>
     * LuckPerms group JSON format:
     * <pre>
     * {
     *   "name": "admin",
     *   "permissions": [
     *     { "permission": "essentials.fly", "value": true },
     *     { "permission": "group.moderator", "value": true }
     *   ],
     *   "parents": ["moderator"],
     *   "prefixes": [{ "priority": 100, "prefix": "&c[Admin] " }],
     *   "suffixes": [{ "priority": 100, "suffix": " &7(Admin)" }],
     *   "meta": { "weight": 100 }
     * }
     * </pre>
     */
    @Nullable
    private LPGroup readGroupFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            
            String name = getFileName(file);
            int weight = 0;
            
            // Extract weight from meta
            if (data.has("meta") && data.get("meta").isJsonObject()) {
                JsonObject meta = data.getAsJsonObject("meta");
                if (meta.has("weight")) {
                    weight = meta.get("weight").getAsInt();
                }
            }
            
            // Parse permissions
            List<LPNode> nodes = parsePermissions(data);
            
            // Parse parents
            Set<String> parents = new LinkedHashSet<>();
            if (data.has("parents") && data.get("parents").isJsonArray()) {
                for (JsonElement parent : data.getAsJsonArray("parents")) {
                    String parentName = parent.getAsString().toLowerCase();
                    parents.add(parentName);
                    // Add as group node
                    nodes.add(new LPNode("group." + parentName, true, 0, Collections.emptyMap()));
                }
            }
            
            // Parse prefix/suffix
            String prefix = null;
            String suffix = null;
            int prefixPriority = 0;
            int suffixPriority = 0;
            
            if (data.has("prefixes") && data.get("prefixes").isJsonArray()) {
                JsonObject best = findHighestPriority(data.getAsJsonArray("prefixes"), "prefix");
                if (best != null) {
                    prefix = best.get("prefix").getAsString();
                    prefixPriority = best.has("priority") ? best.get("priority").getAsInt() : 0;
                }
            }
            
            if (data.has("suffixes") && data.get("suffixes").isJsonArray()) {
                JsonObject best = findHighestPriority(data.getAsJsonArray("suffixes"), "suffix");
                if (best != null) {
                    suffix = best.get("suffix").getAsString();
                    suffixPriority = best.has("priority") ? best.get("priority").getAsInt() : 0;
                }
            }
            
            return new LPGroup(name, weight, prefix, suffix, prefixPriority, suffixPriority, nodes, parents);
        }
    }
    
    /**
     * Reads a user from a JSON file.
     */
    @Nullable
    private LPUser readUserFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            
            // UUID from filename
            String uuidStr = getFileName(file);
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                Logger.warn("Invalid UUID in filename: %s", file.getFileName());
                return null;
            }
            
            String username = data.has("name") ? data.get("name").getAsString() : null;
            String primaryGroup = data.has("primaryGroup") ? 
                data.get("primaryGroup").getAsString() : "default";
            
            List<LPNode> nodes = parsePermissions(data);
            
            return new LPUser(uuid, username, primaryGroup, nodes);
        }
    }
    
    /**
     * Reads a track from a JSON file.
     */
    @Nullable
    private LPTrack readTrackFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            
            String name = getFileName(file);
            List<String> groups = new ArrayList<>();
            
            if (data.has("groups") && data.get("groups").isJsonArray()) {
                for (JsonElement g : data.getAsJsonArray("groups")) {
                    groups.add(g.getAsString().toLowerCase());
                }
            }
            
            return new LPTrack(name, groups);
        }
    }
    
    /**
     * Parses permissions from JSON data.
     */
    private List<LPNode> parsePermissions(JsonObject data) {
        List<LPNode> nodes = new ArrayList<>();
        
        if (!data.has("permissions") || !data.get("permissions").isJsonArray()) {
            return nodes;
        }
        
        for (JsonElement elem : data.getAsJsonArray("permissions")) {
            if (elem.isJsonObject()) {
                LPNode node = parsePermissionObject(elem.getAsJsonObject());
                if (node != null) {
                    nodes.add(node);
                }
            } else if (elem.isJsonPrimitive()) {
                // Simple string format
                String perm = elem.getAsString();
                boolean value = true;
                if (perm.startsWith("-")) {
                    perm = perm.substring(1);
                    value = false;
                }
                nodes.add(new LPNode(perm, value, 0, Collections.emptyMap()));
            }
        }
        
        return nodes;
    }
    
    /**
     * Parses a single permission object.
     */
    @Nullable
    private LPNode parsePermissionObject(JsonObject obj) {
        if (!obj.has("permission")) {
            return null;
        }
        
        String permission = obj.get("permission").getAsString();
        boolean value = true;
        
        // Check for negation prefix
        if (permission.startsWith("-")) {
            permission = permission.substring(1);
            value = false;
        }
        
        // Check explicit value
        if (obj.has("value")) {
            value = obj.get("value").getAsBoolean();
        }
        
        // Parse expiry
        long expiry = 0;
        if (obj.has("expiry")) {
            expiry = obj.get("expiry").getAsLong();
        }
        
        // Parse contexts
        Map<String, String> contexts = new LinkedHashMap<>();
        if (obj.has("context") && obj.get("context").isJsonObject()) {
            JsonObject contextObj = obj.getAsJsonObject("context");
            for (Map.Entry<String, JsonElement> entry : contextObj.entrySet()) {
                contexts.put(entry.getKey().toLowerCase(), entry.getValue().getAsString());
            }
        }
        
        return new LPNode(permission, value, expiry, contexts);
    }
    
    /**
     * Finds the highest priority entry in a JSON array.
     */
    @Nullable
    private JsonObject findHighestPriority(JsonArray array, String key) {
        JsonObject best = null;
        int bestPriority = Integer.MIN_VALUE;
        
        for (JsonElement elem : array) {
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has(key)) {
                    int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 0;
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        best = obj;
                    }
                }
            }
        }
        
        return best;
    }
    
    private String getFileName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }
}
