package com.hyperperms.migration.luckperms;

import com.hyperperms.migration.luckperms.LuckPermsData.*;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Reads LuckPerms data from YAML file storage.
 * <p>
 * Expected directory structure:
 * <pre>
 * yaml-storage/
 * ├── groups/
 * │   ├── default.yml
 * │   ├── admin.yml
 * │   └── ...
 * ├── users/
 * │   ├── {uuid}.yml
 * │   └── ...
 * └── tracks/
 *     ├── staff.yml
 *     └── ...
 * </pre>
 */
public final class YamlStorageReader implements LuckPermsStorageReader {
    
    private final Path storageDir;
    private final Yaml yaml;
    
    public YamlStorageReader(@NotNull Path storageDir) {
        this.storageDir = storageDir;
        this.yaml = new Yaml();
    }
    
    @Override
    @NotNull
    public LuckPermsStorageType getStorageType() {
        return LuckPermsStorageType.YAML;
    }
    
    @Override
    @NotNull
    public String getStorageDescription() {
        return "YAML storage (" + storageDir + ")";
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
            files.filter(p -> p.toString().endsWith(".yml"))
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
            files.filter(p -> p.toString().endsWith(".yml"))
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
            files.filter(p -> p.toString().endsWith(".yml"))
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
            return (int) files.filter(p -> p.toString().endsWith(".yml")).count();
        } catch (IOException e) {
            return -1;
        }
    }
    
    /**
     * Reads a group from a YAML file.
     * <p>
     * LuckPerms group YAML format:
     * <pre>
     * name: admin
     * permissions:
     * - permission.node
     * - permission: another.node
     *   value: true
     *   context:
     *     server: lobby
     * - permission: group.helper
     *   value: true
     * parents:
     * - default
     * prefixes:
     * - prefix: "&c[Admin] "
     *   priority: 100
     * suffixes:
     * - suffix: " &7(Admin)"
     *   priority: 100
     * meta:
     *   weight: 100
     * </pre>
     */
    @Nullable
    private LPGroup readGroupFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                return null;
            }
            
            String name = getFileName(file);
            int weight = extractWeight(data);
            
            // Parse permissions
            List<LPNode> nodes = parsePermissions(data);
            
            // Parse parents (also add as group.parent nodes)
            Set<String> parents = parseParents(data, nodes);
            
            // Parse prefix/suffix
            String prefix = null;
            String suffix = null;
            int prefixPriority = 0;
            int suffixPriority = 0;
            
            Object prefixes = data.get("prefixes");
            if (prefixes instanceof List<?> prefixList && !prefixList.isEmpty()) {
                Map<String, Object> firstPrefix = findHighestPriority(prefixList, "prefix");
                if (firstPrefix != null) {
                    prefix = getString(firstPrefix, "prefix");
                    prefixPriority = getInt(firstPrefix, "priority", 0);
                }
            }
            
            Object suffixes = data.get("suffixes");
            if (suffixes instanceof List<?> suffixList && !suffixList.isEmpty()) {
                Map<String, Object> firstSuffix = findHighestPriority(suffixList, "suffix");
                if (firstSuffix != null) {
                    suffix = getString(firstSuffix, "suffix");
                    suffixPriority = getInt(firstSuffix, "priority", 0);
                }
            }
            
            return new LPGroup(name, weight, prefix, suffix, prefixPriority, suffixPriority, nodes, parents);
        }
    }
    
    /**
     * Reads a user from a YAML file.
     * <p>
     * LuckPerms user YAML format:
     * <pre>
     * uuid: 550e8400-e29b-41d4-a716-446655440000
     * name: PlayerName
     * primary-group: vip
     * permissions:
     * - permission.node
     * - permission: group.vip
     *   value: true
     * </pre>
     */
    @Nullable
    private LPUser readUserFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                return null;
            }
            
            // UUID from filename (without .yml extension)
            String uuidStr = getFileName(file);
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                Logger.warn("Invalid UUID in filename: %s", file.getFileName());
                return null;
            }
            
            String username = getString(data, "name");
            String primaryGroup = getString(data, "primary-group");
            if (primaryGroup == null || primaryGroup.isEmpty()) {
                primaryGroup = "default";
            }
            
            List<LPNode> nodes = parsePermissions(data);
            
            return new LPUser(uuid, username, primaryGroup, nodes);
        }
    }
    
    /**
     * Reads a track from a YAML file.
     * <p>
     * LuckPerms track YAML format:
     * <pre>
     * name: staff
     * groups:
     * - default
     * - helper
     * - moderator
     * - admin
     * - owner
     * </pre>
     */
    @Nullable
    private LPTrack readTrackFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                return null;
            }
            
            String name = getFileName(file);
            List<String> groups = new ArrayList<>();
            
            Object groupsObj = data.get("groups");
            if (groupsObj instanceof List<?> groupList) {
                for (Object g : groupList) {
                    if (g instanceof String s) {
                        groups.add(s.toLowerCase());
                    }
                }
            }
            
            return new LPTrack(name, groups);
        }
    }
    
    /**
     * Parses permissions from YAML data.
     * Handles both simple string format and complex object format.
     */
    private List<LPNode> parsePermissions(Map<String, Object> data) {
        List<LPNode> nodes = new ArrayList<>();
        
        Object perms = data.get("permissions");
        if (perms instanceof List<?> permList) {
            for (Object perm : permList) {
                LPNode node = parsePermissionEntry(perm);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        
        return nodes;
    }
    
    /**
     * Parses a single permission entry.
     * Can be either a simple string or a map with additional properties.
     */
    @Nullable
    private LPNode parsePermissionEntry(Object entry) {
        if (entry instanceof String permission) {
            // Simple format: just the permission string
            // Check for negation prefix
            boolean value = true;
            if (permission.startsWith("-")) {
                permission = permission.substring(1);
                value = false;
            }
            return new LPNode(permission, value, 0, Collections.emptyMap());
        }
        
        if (entry instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> permMap = (Map<String, Object>) map;
            
            String permission = getString(permMap, "permission");
            if (permission == null || permission.isEmpty()) {
                return null;
            }
            
            // Check for negation prefix in permission string
            boolean value = true;
            if (permission.startsWith("-")) {
                permission = permission.substring(1);
                value = false;
            }
            
            // Check explicit value field (overrides prefix)
            Object valueObj = permMap.get("value");
            if (valueObj instanceof Boolean b) {
                value = b;
            }
            
            // Parse expiry
            long expiry = 0;
            Object expiryObj = permMap.get("expiry");
            if (expiryObj instanceof Number num) {
                expiry = num.longValue();
            }
            
            // Parse contexts
            Map<String, String> contexts = new LinkedHashMap<>();
            Object contextObj = permMap.get("context");
            if (contextObj instanceof Map<?, ?> contextMap) {
                for (Map.Entry<?, ?> e : contextMap.entrySet()) {
                    if (e.getKey() instanceof String key && e.getValue() != null) {
                        contexts.put(key.toLowerCase(), e.getValue().toString());
                    }
                }
            }
            
            return new LPNode(permission, value, expiry, contexts);
        }
        
        return null;
    }
    
    /**
     * Parses parent groups and adds them as group nodes.
     */
    private Set<String> parseParents(Map<String, Object> data, List<LPNode> nodes) {
        Set<String> parents = new LinkedHashSet<>();
        
        Object parentsObj = data.get("parents");
        if (parentsObj instanceof List<?> parentList) {
            for (Object p : parentList) {
                if (p instanceof String parentName) {
                    parents.add(parentName.toLowerCase());
                    // Add as a group node if not already present
                    String groupPerm = "group." + parentName.toLowerCase();
                    boolean hasGroupNode = nodes.stream()
                        .anyMatch(n -> n.permission().equals(groupPerm));
                    if (!hasGroupNode) {
                        nodes.add(new LPNode(groupPerm, true, 0, Collections.emptyMap()));
                    }
                }
            }
        }
        
        return parents;
    }
    
    /**
     * Extracts weight from meta section.
     */
    private int extractWeight(Map<String, Object> data) {
        Object meta = data.get("meta");
        if (meta instanceof Map<?, ?> metaMap) {
            Object weight = metaMap.get("weight");
            if (weight instanceof Number num) {
                return num.intValue();
            }
        }
        return 0;
    }
    
    /**
     * Finds the highest priority prefix/suffix entry.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private Map<String, Object> findHighestPriority(List<?> list, String key) {
        Map<String, Object> best = null;
        int bestPriority = Integer.MIN_VALUE;
        
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> entry = (Map<String, Object>) map;
                if (entry.containsKey(key)) {
                    int priority = getInt(entry, "priority", 0);
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        best = entry;
                    }
                }
            }
        }
        
        return best;
    }
    
    // Helper methods
    
    private String getFileName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".yml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
    
    @Nullable
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }
}
