package com.hyperperms.storage.json;

import com.google.gson.*;
import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.storage.AbstractStorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JSON file-based storage provider.
 * <p>
 * Stores data in separate JSON files:
 * <ul>
 *   <li>{@code users/<uuid>.json} - User data</li>
 *   <li>{@code groups/<name>.json} - Group data</li>
 *   <li>{@code tracks/<name>.json} - Track data</li>
 * </ul>
 */
public final class JsonStorageProvider extends AbstractStorageProvider {

    private final Path dataDirectory;
    private final Path usersDirectory;
    private final Path groupsDirectory;
    private final Path tracksDirectory;
    private final Path backupsDirectory;
    private final Gson gson;

    public JsonStorageProvider(@NotNull Path dataDirectory) {
        super("HyperPerms-JsonStorage");
        this.dataDirectory = dataDirectory;
        this.usersDirectory = dataDirectory.resolve("users");
        this.groupsDirectory = dataDirectory.resolve("groups");
        this.tracksDirectory = dataDirectory.resolve("tracks");
        this.backupsDirectory = dataDirectory.resolve("backups");
        this.gson = createGson();
    }

    private Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(ContextSet.class, new ContextSetAdapter())
                .registerTypeAdapter(Node.class, new NodeAdapter())
                .registerTypeAdapter(User.class, new UserAdapter())
                .registerTypeAdapter(Group.class, new GroupAdapter())
                .registerTypeAdapter(Track.class, new TrackAdapter())
                .create();
    }

    @Override
    @NotNull
    public String getName() {
        return "JSON";
    }

    @Override
    public CompletableFuture<Void> init() {
        return runAsync(() -> {
            Logger.debugStorage("Initializing JSON storage at: %s", dataDirectory);
            try {
                Files.createDirectories(usersDirectory);
                Files.createDirectories(groupsDirectory);
                Files.createDirectories(tracksDirectory);
                Files.createDirectories(backupsDirectory);
                setHealthy(true);
                Logger.info("JSON storage initialized at: " + dataDirectory);
            } catch (IOException e) {
                setHealthy(false);
                throw new RuntimeException("Failed to initialize JSON storage", e);
            }
        });
    }

    // ==================== User Operations ====================

    @Override
    public CompletableFuture<Optional<User>> loadUser(@NotNull UUID uuid) {
        return executeAsync(() -> {
            Logger.debugStorage("Loading user: %s", uuid);
            Path file = usersDirectory.resolve(uuid + ".json");
            if (!Files.exists(file)) {
                Logger.debugStorage("User file not found: %s", uuid);
                return Optional.empty();
            }
            try {
                String json = Files.readString(file);
                User user = gson.fromJson(json, User.class);
                Logger.debugStorage("Loaded user: %s", uuid);
                return Optional.ofNullable(user);
            } catch (IOException e) {
                Logger.severe("Failed to load user: " + uuid, e);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull User user) {
        return runAsync(() -> {
            Logger.debugStorage("Saving user: %s", user.getUuid());
            Path file = usersDirectory.resolve(user.getUuid() + ".json");
            try {
                String json = gson.toJson(user);
                Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Logger.debugStorage("Saved user: %s (%d nodes)", user.getUuid(), user.getNodes().size());
            } catch (IOException e) {
                Logger.severe("Failed to save user: " + user.getUuid(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteUser(@NotNull UUID uuid) {
        return runAsync(() -> {
            Logger.debugStorage("Deleting user: %s", uuid);
            Path file = usersDirectory.resolve(uuid + ".json");
            try {
                Files.deleteIfExists(file);
                Logger.debugStorage("Deleted user: %s", uuid);
            } catch (IOException e) {
                Logger.severe("Failed to delete user: " + uuid, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, User>> loadAllUsers() {
        return executeAsync(() -> {
            Logger.debugStorage("Loading all users from JSON");
            Map<UUID, User> users = new HashMap<>();
            try (var stream = Files.list(usersDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String json = Files.readString(file);
                                User user = gson.fromJson(json, User.class);
                                if (user != null) {
                                    users.put(user.getUuid(), user);
                                }
                            } catch (IOException e) {
                                Logger.warn("Failed to load user file: " + file.getFileName());
                            }
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list users directory", e);
            }
            Logger.debugStorage("Loaded %d users from JSON", users.size());
            return users;
        });
    }

    @Override
    public CompletableFuture<Set<UUID>> getUserUuids() {
        return executeAsync(() -> {
            Set<UUID> uuids = new HashSet<>();
            try (var stream = Files.list(usersDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            String name = file.getFileName().toString();
                            String uuidStr = name.substring(0, name.length() - 5);
                            try {
                                uuids.add(UUID.fromString(uuidStr));
                            } catch (IllegalArgumentException ignored) {}
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list users", e);
            }
            return uuids;
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> lookupUuid(@NotNull String username) {
        return loadAllUsers().thenApply(users -> users.values().stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .map(User::getUuid)
                .findFirst());
    }

    // ==================== Group Operations ====================

    @Override
    public CompletableFuture<Optional<Group>> loadGroup(@NotNull String name) {
        return executeAsync(() -> {
            Logger.debugStorage("Loading group: %s", name);
            Path file = groupsDirectory.resolve(name.toLowerCase() + ".json");
            if (!Files.exists(file)) {
                Logger.debugStorage("Group file not found: %s", name);
                return Optional.empty();
            }
            try {
                String json = Files.readString(file);
                Group group = gson.fromJson(json, Group.class);
                Logger.debugStorage("Loaded group: %s", name);
                return Optional.ofNullable(group);
            } catch (IOException e) {
                Logger.severe("Failed to load group: " + name, e);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveGroup(@NotNull Group group) {
        return runAsync(() -> {
            Logger.debugStorage("Saving group: %s", group.getName());
            Path file = groupsDirectory.resolve(group.getName() + ".json");
            try {
                String json = gson.toJson(group);
                Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Logger.debugStorage("Saved group: %s (%d nodes)", group.getName(), group.getNodes().size());
            } catch (IOException e) {
                Logger.severe("Failed to save group: " + group.getName(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteGroup(@NotNull String name) {
        return runAsync(() -> {
            Logger.debugStorage("Deleting group: %s", name);
            Path file = groupsDirectory.resolve(name.toLowerCase() + ".json");
            try {
                Files.deleteIfExists(file);
                Logger.debugStorage("Deleted group: %s", name);
            } catch (IOException e) {
                Logger.severe("Failed to delete group: " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Group>> loadAllGroups() {
        return executeAsync(() -> {
            Logger.debugStorage("Loading all groups from JSON");
            Map<String, Group> groups = new HashMap<>();
            try (var stream = Files.list(groupsDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String json = Files.readString(file);
                                Group group = gson.fromJson(json, Group.class);
                                if (group != null) {
                                    groups.put(group.getName(), group);
                                }
                            } catch (IOException e) {
                                Logger.warn("Failed to load group file: " + file.getFileName());
                            }
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list groups directory", e);
            }
            Logger.debugStorage("Loaded %d groups from JSON", groups.size());
            return groups;
        });
    }

    @Override
    public CompletableFuture<Set<String>> getGroupNames() {
        return executeAsync(() -> {
            Set<String> names = new HashSet<>();
            try (var stream = Files.list(groupsDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            String name = file.getFileName().toString();
                            names.add(name.substring(0, name.length() - 5));
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list groups", e);
            }
            return names;
        });
    }

    // ==================== Track Operations ====================

    @Override
    public CompletableFuture<Optional<Track>> loadTrack(@NotNull String name) {
        return executeAsync(() -> {
            Logger.debugStorage("Loading track: %s", name);
            Path file = tracksDirectory.resolve(name.toLowerCase() + ".json");
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try {
                String json = Files.readString(file);
                Track track = gson.fromJson(json, Track.class);
                Logger.debugStorage("Loaded track: %s", name);
                return Optional.ofNullable(track);
            } catch (IOException e) {
                Logger.severe("Failed to load track: " + name, e);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveTrack(@NotNull Track track) {
        return runAsync(() -> {
            Logger.debugStorage("Saving track: %s", track.getName());
            Path file = tracksDirectory.resolve(track.getName() + ".json");
            try {
                String json = gson.toJson(track);
                Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Logger.debugStorage("Saved track: %s (%d groups)", track.getName(), track.getGroups().size());
            } catch (IOException e) {
                Logger.severe("Failed to save track: " + track.getName(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteTrack(@NotNull String name) {
        return runAsync(() -> {
            Logger.debugStorage("Deleting track: %s", name);
            Path file = tracksDirectory.resolve(name.toLowerCase() + ".json");
            try {
                Files.deleteIfExists(file);
                Logger.debugStorage("Deleted track: %s", name);
            } catch (IOException e) {
                Logger.severe("Failed to delete track: " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Track>> loadAllTracks() {
        return executeAsync(() -> {
            Logger.debugStorage("Loading all tracks from JSON");
            Map<String, Track> tracks = new HashMap<>();
            try (var stream = Files.list(tracksDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String json = Files.readString(file);
                                Track track = gson.fromJson(json, Track.class);
                                if (track != null) {
                                    tracks.put(track.getName(), track);
                                }
                            } catch (IOException e) {
                                Logger.warn("Failed to load track file: " + file.getFileName());
                            }
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list tracks directory", e);
            }
            Logger.debugStorage("Loaded %d tracks from JSON", tracks.size());
            return tracks;
        });
    }

    @Override
    public CompletableFuture<Set<String>> getTrackNames() {
        return executeAsync(() -> {
            Set<String> names = new HashSet<>();
            try (var stream = Files.list(tracksDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            String name = file.getFileName().toString();
                            names.add(name.substring(0, name.length() - 5));
                        });
            } catch (IOException e) {
                Logger.severe("Failed to list tracks", e);
            }
            return names;
        });
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        // JSON storage saves immediately, so this is a no-op
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Backup Operations ====================

    @Override
    public CompletableFuture<String> createBackup(@Nullable String name) {
        return executeAsync(() -> {
            String backupName = name != null ? name : 
                "backup-" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            
            Path backupDir = backupsDirectory.resolve(backupName);
            
            try {
                // Create backup directory
                Files.createDirectories(backupDir);
                Path backupUsersDir = backupDir.resolve("users");
                Path backupGroupsDir = backupDir.resolve("groups");
                Path backupTracksDir = backupDir.resolve("tracks");
                Files.createDirectories(backupUsersDir);
                Files.createDirectories(backupGroupsDir);
                Files.createDirectories(backupTracksDir);

                java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger(0);

                // Copy all user files
                if (Files.exists(usersDirectory)) {
                    try (var stream = Files.list(usersDirectory)) {
                        stream.filter(p -> p.toString().endsWith(".json"))
                              .forEach(file -> {
                                  try {
                                      Files.copy(file, backupUsersDir.resolve(file.getFileName()),
                                                 StandardCopyOption.REPLACE_EXISTING);
                                  } catch (IOException e) {
                                      failures.incrementAndGet();
                                      Logger.warn("Failed to backup user file: " + file.getFileName());
                                  }
                              });
                    }
                }

                // Copy all group files
                if (Files.exists(groupsDirectory)) {
                    try (var stream = Files.list(groupsDirectory)) {
                        stream.filter(p -> p.toString().endsWith(".json"))
                              .forEach(file -> {
                                  try {
                                      Files.copy(file, backupGroupsDir.resolve(file.getFileName()),
                                                 StandardCopyOption.REPLACE_EXISTING);
                                  } catch (IOException e) {
                                      failures.incrementAndGet();
                                      Logger.warn("Failed to backup group file: " + file.getFileName());
                                  }
                              });
                    }
                }

                // Copy all track files
                if (Files.exists(tracksDirectory)) {
                    try (var stream = Files.list(tracksDirectory)) {
                        stream.filter(p -> p.toString().endsWith(".json"))
                              .forEach(file -> {
                                  try {
                                      Files.copy(file, backupTracksDir.resolve(file.getFileName()),
                                                 StandardCopyOption.REPLACE_EXISTING);
                                  } catch (IOException e) {
                                      failures.incrementAndGet();
                                      Logger.warn("Failed to backup track file: " + file.getFileName());
                                  }
                              });
                    }
                }

                if (failures.get() > 0) {
                    throw new RuntimeException("Backup incomplete: " + failures.get() + " file(s) failed to copy");
                }

                Logger.info("Backup created: " + backupName);
                return backupName;

            } catch (IOException e) {
                Logger.severe("Failed to create backup: " + backupName, e);
                throw new RuntimeException("Backup failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> restoreBackup(@NotNull String name) {
        return executeAsync(() -> {
            Path backupDir = backupsDirectory.resolve(name);
            
            if (!Files.exists(backupDir)) {
                Logger.warn("Backup not found: " + name);
                return false;
            }

            try {
                // Create safety backup before restore
                createBackup("pre-restore-" + System.currentTimeMillis()).join();

                Path backupUsersDir = backupDir.resolve("users");
                Path backupGroupsDir = backupDir.resolve("groups");
                Path backupTracksDir = backupDir.resolve("tracks");

                // Restore users
                if (Files.exists(backupUsersDir)) {
                    try (var stream = Files.list(backupUsersDir)) {
                        stream.forEach(file -> {
                            try {
                                Files.copy(file, usersDirectory.resolve(file.getFileName()),
                                           StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                Logger.warn("Failed to restore user file: " + file.getFileName());
                            }
                        });
                    }
                }

                // Restore groups
                if (Files.exists(backupGroupsDir)) {
                    try (var stream = Files.list(backupGroupsDir)) {
                        stream.forEach(file -> {
                            try {
                                Files.copy(file, groupsDirectory.resolve(file.getFileName()),
                                           StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                Logger.warn("Failed to restore group file: " + file.getFileName());
                            }
                        });
                    }
                }

                // Restore tracks
                if (Files.exists(backupTracksDir)) {
                    try (var stream = Files.list(backupTracksDir)) {
                        stream.forEach(file -> {
                            try {
                                Files.copy(file, tracksDirectory.resolve(file.getFileName()),
                                           StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                Logger.warn("Failed to restore track file: " + file.getFileName());
                            }
                        });
                    }
                }

                Logger.info("Restored from backup: " + name);
                return true;

            } catch (Exception e) {
                Logger.severe("Failed to restore backup: " + name, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> listBackups() {
        return executeAsync(() -> {
            List<String> backups = new ArrayList<>();

            try {
                if (Files.exists(backupsDirectory)) {
                    try (var stream = Files.list(backupsDirectory)) {
                        stream.filter(Files::isDirectory)
                              .map(p -> p.getFileName().toString())
                              .sorted(Comparator.reverseOrder()) // Newest first
                              .forEach(backups::add);
                    }
                }
            } catch (IOException e) {
                Logger.severe("Failed to list backups", e);
            }

            return backups;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteBackup(@NotNull String name) {
        return executeAsync(() -> {
            Path backupDir = backupsDirectory.resolve(name);

            if (!Files.exists(backupDir)) {
                return false;
            }

            try {
                // Delete all files in backup directory recursively
                Files.walk(backupDir)
                     .sorted(Comparator.reverseOrder())
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             Logger.warn("Failed to delete: " + path);
                         }
                     });

                Logger.info("Deleted backup: " + name);
                return true;

            } catch (IOException e) {
                Logger.severe("Failed to delete backup: " + name, e);
                return false;
            }
        });
    }

    @Override
    @NotNull
    public String getType() {
        return "json";
    }

    // ==================== Type Adapters ====================

    private static class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toEpochMilli());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Instant.ofEpochMilli(json.getAsLong());
        }
    }

    private static class ContextSetAdapter implements JsonSerializer<ContextSet>, JsonDeserializer<ContextSet> {
        @Override
        public JsonElement serialize(ContextSet src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (Context ctx : src) {
                array.add(ctx.toString());
            }
            return array;
        }

        @Override
        public ContextSet deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonArray()) {
                return ContextSet.empty();
            }
            ContextSet.Builder builder = ContextSet.builder();
            for (JsonElement elem : json.getAsJsonArray()) {
                try {
                    builder.add(Context.parse(elem.getAsString()));
                } catch (IllegalArgumentException ignored) {}
            }
            return builder.build();
        }
    }

    private static class NodeAdapter implements JsonSerializer<Node>, JsonDeserializer<Node> {
        @Override
        public JsonElement serialize(Node src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("permission", src.getPermission());
            obj.addProperty("value", src.getValue());
            if (src.getExpiry() != null) {
                obj.add("expiry", context.serialize(src.getExpiry()));
            }
            if (!src.getContexts().isEmpty()) {
                obj.add("contexts", context.serialize(src.getContexts()));
            }
            return obj;
        }

        @Override
        public Node deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("permission") || obj.get("permission").isJsonNull()) {
                throw new JsonParseException("Node is missing required 'permission' field");
            }
            String permission = obj.get("permission").getAsString();
            boolean value = obj.has("value") ? obj.get("value").getAsBoolean() : true;
            Instant expiry = obj.has("expiry") ? context.deserialize(obj.get("expiry"), Instant.class) : null;
            ContextSet contexts = obj.has("contexts") ?
                    context.deserialize(obj.get("contexts"), ContextSet.class) : ContextSet.empty();
            return Node.builder(permission).value(value).expiry(expiry).contexts(contexts).build();
        }
    }

    private class UserAdapter implements JsonSerializer<User>, JsonDeserializer<User> {
        @Override
        public JsonElement serialize(User src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", src.getUuid().toString());
            if (src.getUsername() != null) {
                obj.addProperty("username", src.getUsername());
            }
            obj.addProperty("primaryGroup", src.getPrimaryGroup());
            if (src.getCustomPrefix() != null) {
                obj.addProperty("customPrefix", src.getCustomPrefix());
            }
            if (src.getCustomSuffix() != null) {
                obj.addProperty("customSuffix", src.getCustomSuffix());
            }
            JsonArray nodes = new JsonArray();
            for (Node node : src.getNodes()) {
                nodes.add(context.serialize(node));
            }
            obj.add("nodes", nodes);
            return obj;
        }

        @Override
        public User deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("uuid") || obj.get("uuid").isJsonNull()) {
                throw new JsonParseException("User is missing required 'uuid' field");
            }
            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
            String username = obj.has("username") ? obj.get("username").getAsString() : null;
            User user = new User(uuid, username);
            if (obj.has("primaryGroup")) {
                user.setPrimaryGroup(obj.get("primaryGroup").getAsString());
            }
            if (obj.has("customPrefix")) {
                user.setCustomPrefix(obj.get("customPrefix").getAsString());
            }
            if (obj.has("customSuffix")) {
                user.setCustomSuffix(obj.get("customSuffix").getAsString());
            }
            if (obj.has("nodes")) {
                for (JsonElement elem : obj.getAsJsonArray("nodes")) {
                    Node node = context.deserialize(elem, Node.class);
                    user.addNode(node);
                }
            }
            return user;
        }
    }

    private class GroupAdapter implements JsonSerializer<Group>, JsonDeserializer<Group> {
        @Override
        public JsonElement serialize(Group src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", src.getName());
            obj.addProperty("displayName", src.getDisplayName());
            obj.addProperty("weight", src.getWeight());
            if (src.getPrefix() != null) {
                obj.addProperty("prefix", src.getPrefix());
            }
            if (src.getSuffix() != null) {
                obj.addProperty("suffix", src.getSuffix());
            }
            if (src.getPrefixPriority() != 0) {
                obj.addProperty("prefixPriority", src.getPrefixPriority());
            }
            if (src.getSuffixPriority() != 0) {
                obj.addProperty("suffixPriority", src.getSuffixPriority());
            }
            JsonArray nodes = new JsonArray();
            for (Node node : src.getNodes()) {
                nodes.add(context.serialize(node));
            }
            obj.add("nodes", nodes);
            return obj;
        }

        @Override
        public Group deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String name = obj.get("name").getAsString();
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 0;
            Group group = new Group(name, weight);
            if (obj.has("displayName")) {
                group.setDisplayName(obj.get("displayName").getAsString());
            }
            if (obj.has("prefix")) {
                group.setPrefix(obj.get("prefix").getAsString());
            }
            if (obj.has("suffix")) {
                group.setSuffix(obj.get("suffix").getAsString());
            }
            if (obj.has("prefixPriority")) {
                group.setPrefixPriority(obj.get("prefixPriority").getAsInt());
            }
            if (obj.has("suffixPriority")) {
                group.setSuffixPriority(obj.get("suffixPriority").getAsInt());
            }
            if (obj.has("nodes")) {
                for (JsonElement elem : obj.getAsJsonArray("nodes")) {
                    Node node = context.deserialize(elem, Node.class);
                    group.addNode(node);
                }
            }
            return group;
        }
    }

    private static class TrackAdapter implements JsonSerializer<Track>, JsonDeserializer<Track> {
        @Override
        public JsonElement serialize(Track src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", src.getName());
            JsonArray groups = new JsonArray();
            for (String group : src.getGroups()) {
                groups.add(group);
            }
            obj.add("groups", groups);
            return obj;
        }

        @Override
        public Track deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String name = obj.get("name").getAsString();
            List<String> groups = new ArrayList<>();
            if (obj.has("groups")) {
                for (JsonElement elem : obj.getAsJsonArray("groups")) {
                    groups.add(elem.getAsString());
                }
            }
            return new Track(name, groups);
        }
    }
}
