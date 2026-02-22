package com.hyperperms.storage.sqlite;

import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.storage.AbstractStorageProvider;
import com.hyperperms.util.Logger;
import com.hyperperms.util.SQLiteDriverLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite-based storage provider.
 * <p>
 * This implementation requires the SQLite JDBC driver to be available at runtime.
 * The driver is NOT bundled with the plugin to keep the file size small (~1.5MB vs ~14MB).
 * <p>
 * To use SQLite storage, server operators must:
 * <ol>
 *   <li>Download sqlite-jdbc from Maven Central</li>
 *   <li>Place it in the server's plugins/HyperPerms/lib/ directory</li>
 *   <li>Set storage.type to "sqlite" in config.json</li>
 * </ol>
 * <p>
 * <b>IMPORTANT - Thread Safety:</b> All database operations MUST execute on the
 * single-threaded executor to ensure transaction isolation. The current implementation
 * shares a single Connection and uses manual transaction management (setAutoCommit/commit).
 * Do not change the executor to multi-threaded without implementing connection pooling,
 * as concurrent access to the shared connection would cause transaction interference and
 * potential data corruption.
 */
public final class SQLiteStorageProvider extends AbstractStorageProvider {

    private final Path databaseFile;
    private final Path backupsDirectory;
    private Connection connection;

    public SQLiteStorageProvider(@NotNull Path databaseFile) {
        super("HyperPerms-SQLiteStorage");
        this.databaseFile = databaseFile;
        this.backupsDirectory = databaseFile.getParent().resolve("backups");
    }

    /**
     * Checks if the SQLite JDBC driver is available.
     * <p>
     * The driver must be downloaded separately and placed in the lib directory.
     * See {@link SQLiteDriverLoader#getDownloadInstructions()} for details.
     *
     * @return true if SQLite can be used
     */
    public static boolean isDriverAvailable() {
        return SQLiteDriverLoader.isAvailable();
    }

    @Override
    @NotNull
    public String getName() {
        return "SQLite";
    }

    @Override
    @NotNull
    public String getType() {
        return "sqlite";
    }

    @Override
    public CompletableFuture<Void> init() {
        return runAsync(() -> {
            Logger.debugStorage("Initializing SQLite storage at: %s", databaseFile);
            try {
                // Check if driver is available
                if (!isDriverAvailable()) {
                    throw new RuntimeException(SQLiteDriverLoader.getDownloadInstructions());
                }

                // Create directories
                Files.createDirectories(databaseFile.getParent());
                Files.createDirectories(backupsDirectory);

                // Open connection using the dynamically loaded driver
                String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
                connection = SQLiteDriverLoader.getConnection(url);

                // Enable WAL mode for better concurrent access
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA foreign_keys=ON");
                }

                // Create tables
                createTables();

                setHealthy(true);
                Logger.info("SQLite storage initialized at: " + databaseFile);

            } catch (Exception e) {
                setHealthy(false);
                throw new RuntimeException("Failed to initialize SQLite storage", e);
            }
        });
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    uuid TEXT PRIMARY KEY,
                    username TEXT,
                    primary_group TEXT NOT NULL DEFAULT 'default',
                    custom_prefix TEXT,
                    custom_suffix TEXT
                )
            """);

            // Groups table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS groups (
                    name TEXT PRIMARY KEY,
                    display_name TEXT,
                    weight INTEGER NOT NULL DEFAULT 0,
                    prefix TEXT,
                    suffix TEXT,
                    prefix_priority INTEGER NOT NULL DEFAULT 0,
                    suffix_priority INTEGER NOT NULL DEFAULT 0
                )
            """);

            // Tracks table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tracks (
                    name TEXT PRIMARY KEY,
                    groups_json TEXT NOT NULL DEFAULT '[]'
                )
            """);

            // User nodes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_nodes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_uuid TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    value INTEGER NOT NULL DEFAULT 1,
                    expiry INTEGER,
                    contexts_json TEXT NOT NULL DEFAULT '[]',
                    FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
                    UNIQUE(user_uuid, permission, contexts_json)
                )
            """);

            // Group nodes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS group_nodes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_name TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    value INTEGER NOT NULL DEFAULT 1,
                    expiry INTEGER,
                    contexts_json TEXT NOT NULL DEFAULT '[]',
                    FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE,
                    UNIQUE(group_name, permission, contexts_json)
                )
            """);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_nodes_uuid ON user_nodes(user_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_group_nodes_name ON group_nodes(group_name)");
        }
    }

    @Override
    protected void closeResources() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            Logger.debugStorage("SQLite connection closed");
        }
    }

    // ==================== User Operations ====================

    @Override
    public CompletableFuture<Optional<User>> loadUser(@NotNull UUID uuid) {
        return executeAsync(() -> {
            Logger.debugStorage("Loading user from SQLite: %s", uuid);
            try {
                String sql = "SELECT * FROM users WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    if (!rs.next()) {
                        Logger.debugStorage("User not found in SQLite: %s", uuid);
                        return Optional.empty();
                    }

                    String username = rs.getString("username");
                    User user = new User(uuid, username);
                    user.setPrimaryGroup(rs.getString("primary_group"));
                    user.setCustomPrefix(rs.getString("custom_prefix"));
                    user.setCustomSuffix(rs.getString("custom_suffix"));

                    // Load nodes
                    loadUserNodes(user);

                    Logger.debugStorage("Loaded user from SQLite: %s (%d nodes)", uuid, user.getNodes().size());
                    return Optional.of(user);
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load user: " + uuid, e);
                return Optional.empty();
            }
        });
    }

    private void loadUserNodes(User user) throws SQLException {
        String sql = "SELECT * FROM user_nodes WHERE user_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getUuid().toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String permission = rs.getString("permission");
                boolean value = rs.getInt("value") == 1;
                Long expiryMs = rs.getObject("expiry") != null ? rs.getLong("expiry") : null;
                Instant expiry = expiryMs != null ? Instant.ofEpochMilli(expiryMs) : null;
                ContextSet contexts = deserializeContexts(rs.getString("contexts_json"));

                Node node = Node.builder(permission)
                    .value(value)
                    .expiry(expiry)
                    .contexts(contexts)
                    .build();
                user.addNode(node);
            }
        }
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull User user) {
        return runAsync(() -> {
            Logger.debugStorage("Saving user to SQLite: %s", user.getUuid());
            try {
                connection.setAutoCommit(false);

                // Insert/update user
                String sql = """
                    INSERT INTO users (uuid, username, primary_group, custom_prefix, custom_suffix)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username = excluded.username,
                        primary_group = excluded.primary_group,
                        custom_prefix = excluded.custom_prefix,
                        custom_suffix = excluded.custom_suffix
                """;
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, user.getUuid().toString());
                    stmt.setString(2, user.getUsername());
                    stmt.setString(3, user.getPrimaryGroup());
                    stmt.setString(4, user.getCustomPrefix());
                    stmt.setString(5, user.getCustomSuffix());
                    stmt.executeUpdate();
                }

                // Clear existing nodes
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM user_nodes WHERE user_uuid = ?")) {
                    stmt.setString(1, user.getUuid().toString());
                    stmt.executeUpdate();
                }

                // Insert nodes
                String nodeSql = """
                    INSERT INTO user_nodes (user_uuid, permission, value, expiry, contexts_json)
                    VALUES (?, ?, ?, ?, ?)
                """;
                try (PreparedStatement stmt = connection.prepareStatement(nodeSql)) {
                    for (Node node : user.getNodes()) {
                        stmt.setString(1, user.getUuid().toString());
                        stmt.setString(2, node.getPermission());
                        stmt.setInt(3, node.getValue() ? 1 : 0);
                        stmt.setObject(4, node.getExpiry() != null ? node.getExpiry().toEpochMilli() : null);
                        stmt.setString(5, serializeContexts(node.getContexts()));
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                connection.commit();
                Logger.debugStorage("Saved user to SQLite: %s (%d nodes)", user.getUuid(), user.getNodes().size());
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    Logger.warn("Failed to rollback transaction");
                }
                Logger.severe("Failed to save user: " + user.getUuid(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    Logger.warn("Failed to reset auto-commit");
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteUser(@NotNull UUID uuid) {
        return runAsync(() -> {
            Logger.debugStorage("Deleting user from SQLite: %s", uuid);
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM users WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
                Logger.debugStorage("Deleted user from SQLite: %s", uuid);
            } catch (SQLException e) {
                Logger.severe("Failed to delete user: " + uuid, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, User>> loadAllUsers() {
        return executeAsync(() -> {
            Logger.debugStorage("Loading all users from SQLite");
            Map<UUID, User> users = new HashMap<>();
            try {
                String sql = "SELECT * FROM users";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String username = rs.getString("username");
                        User user = new User(uuid, username);
                        user.setPrimaryGroup(rs.getString("primary_group"));
                        user.setCustomPrefix(rs.getString("custom_prefix"));
                        user.setCustomSuffix(rs.getString("custom_suffix"));
                        loadUserNodes(user);
                        users.put(uuid, user);
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load all users", e);
            }
            Logger.debugStorage("Loaded %d users from SQLite", users.size());
            return users;
        });
    }

    @Override
    public CompletableFuture<Set<UUID>> getUserUuids() {
        return executeAsync(() -> {
            Set<UUID> uuids = new HashSet<>();
            try {
                String sql = "SELECT uuid FROM users";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to get user UUIDs", e);
            }
            return uuids;
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> lookupUuid(@NotNull String username) {
        return executeAsync(() -> {
            try {
                String sql = "SELECT uuid FROM users WHERE LOWER(username) = LOWER(?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to lookup UUID for: " + username, e);
            }
            return Optional.empty();
        });
    }

    // ==================== Group Operations ====================

    @Override
    public CompletableFuture<Optional<Group>> loadGroup(@NotNull String name) {
        return executeAsync(() -> {
            Logger.debugStorage("Loading group from SQLite: %s", name);
            try {
                String sql = "SELECT * FROM groups WHERE name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, name.toLowerCase());
                    ResultSet rs = stmt.executeQuery();

                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    Group group = new Group(rs.getString("name"), rs.getInt("weight"));
                    group.setDisplayName(rs.getString("display_name"));
                    group.setPrefix(rs.getString("prefix"));
                    group.setSuffix(rs.getString("suffix"));
                    group.setPrefixPriority(rs.getInt("prefix_priority"));
                    group.setSuffixPriority(rs.getInt("suffix_priority"));

                    // Load nodes
                    loadGroupNodes(group);

                    return Optional.of(group);
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load group: " + name, e);
                return Optional.empty();
            }
        });
    }

    private void loadGroupNodes(Group group) throws SQLException {
        String sql = "SELECT * FROM group_nodes WHERE group_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, group.getName());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String permission = rs.getString("permission");
                boolean value = rs.getInt("value") == 1;
                Long expiryMs = rs.getObject("expiry") != null ? rs.getLong("expiry") : null;
                Instant expiry = expiryMs != null ? Instant.ofEpochMilli(expiryMs) : null;
                ContextSet contexts = deserializeContexts(rs.getString("contexts_json"));

                Node node = Node.builder(permission)
                    .value(value)
                    .expiry(expiry)
                    .contexts(contexts)
                    .build();
                group.addNode(node);
            }
        }
    }

    @Override
    public CompletableFuture<Void> saveGroup(@NotNull Group group) {
        return runAsync(() -> {
            Logger.debugStorage("Saving group to SQLite: %s", group.getName());
            try {
                connection.setAutoCommit(false);

                String sql = """
                    INSERT INTO groups (name, display_name, weight, prefix, suffix, prefix_priority, suffix_priority)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(name) DO UPDATE SET
                        display_name = excluded.display_name,
                        weight = excluded.weight,
                        prefix = excluded.prefix,
                        suffix = excluded.suffix,
                        prefix_priority = excluded.prefix_priority,
                        suffix_priority = excluded.suffix_priority
                """;
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, group.getName());
                    stmt.setString(2, group.getDisplayName());
                    stmt.setInt(3, group.getWeight());
                    stmt.setString(4, group.getPrefix());
                    stmt.setString(5, group.getSuffix());
                    stmt.setInt(6, group.getPrefixPriority());
                    stmt.setInt(7, group.getSuffixPriority());
                    stmt.executeUpdate();
                }

                // Clear existing nodes
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM group_nodes WHERE group_name = ?")) {
                    stmt.setString(1, group.getName());
                    stmt.executeUpdate();
                }

                // Insert nodes
                String nodeSql = """
                    INSERT INTO group_nodes (group_name, permission, value, expiry, contexts_json)
                    VALUES (?, ?, ?, ?, ?)
                """;
                try (PreparedStatement stmt = connection.prepareStatement(nodeSql)) {
                    for (Node node : group.getNodes()) {
                        stmt.setString(1, group.getName());
                        stmt.setString(2, node.getPermission());
                        stmt.setInt(3, node.getValue() ? 1 : 0);
                        stmt.setObject(4, node.getExpiry() != null ? node.getExpiry().toEpochMilli() : null);
                        stmt.setString(5, serializeContexts(node.getContexts()));
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    Logger.warn("Failed to rollback transaction");
                }
                Logger.severe("Failed to save group: " + group.getName(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    Logger.warn("Failed to reset auto-commit");
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteGroup(@NotNull String name) {
        return runAsync(() -> {
            Logger.debugStorage("Deleting group from SQLite: %s", name);
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM groups WHERE name = ?")) {
                stmt.setString(1, name.toLowerCase());
                stmt.executeUpdate();
            } catch (SQLException e) {
                Logger.severe("Failed to delete group: " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Group>> loadAllGroups() {
        return executeAsync(() -> {
            Logger.debugStorage("Loading all groups from SQLite");
            Map<String, Group> groups = new HashMap<>();
            try {
                String sql = "SELECT * FROM groups";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        Group group = new Group(name, rs.getInt("weight"));
                        group.setDisplayName(rs.getString("display_name"));
                        group.setPrefix(rs.getString("prefix"));
                        group.setSuffix(rs.getString("suffix"));
                        group.setPrefixPriority(rs.getInt("prefix_priority"));
                        group.setSuffixPriority(rs.getInt("suffix_priority"));
                        loadGroupNodes(group);
                        groups.put(name, group);
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load all groups", e);
            }
            Logger.debugStorage("Loaded %d groups from SQLite", groups.size());
            return groups;
        });
    }

    @Override
    public CompletableFuture<Set<String>> getGroupNames() {
        return executeAsync(() -> {
            Set<String> names = new HashSet<>();
            try {
                String sql = "SELECT name FROM groups";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        names.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to get group names", e);
            }
            return names;
        });
    }

    // ==================== Track Operations ====================

    @Override
    public CompletableFuture<Optional<Track>> loadTrack(@NotNull String name) {
        return executeAsync(() -> {
            try {
                String sql = "SELECT * FROM tracks WHERE name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, name.toLowerCase());
                    ResultSet rs = stmt.executeQuery();

                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    String groupsJson = rs.getString("groups_json");
                    List<String> groups = parseGroupsList(groupsJson);
                    
                    return Optional.of(new Track(rs.getString("name"), groups));
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load track: " + name, e);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveTrack(@NotNull Track track) {
        return runAsync(() -> {
            try {
                String sql = """
                    INSERT INTO tracks (name, groups_json) VALUES (?, ?)
                    ON CONFLICT(name) DO UPDATE SET groups_json = excluded.groups_json
                """;
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, track.getName());
                    stmt.setString(2, serializeGroupsList(track.getGroups()));
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                Logger.severe("Failed to save track: " + track.getName(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteTrack(@NotNull String name) {
        return runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM tracks WHERE name = ?")) {
                stmt.setString(1, name.toLowerCase());
                stmt.executeUpdate();
            } catch (SQLException e) {
                Logger.severe("Failed to delete track: " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Track>> loadAllTracks() {
        return executeAsync(() -> {
            Map<String, Track> tracks = new HashMap<>();
            try {
                String sql = "SELECT * FROM tracks";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String groupsJson = rs.getString("groups_json");
                        List<String> groups = parseGroupsList(groupsJson);
                        tracks.put(name, new Track(name, groups));
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load all tracks", e);
            }
            return tracks;
        });
    }

    @Override
    public CompletableFuture<Set<String>> getTrackNames() {
        return executeAsync(() -> {
            Set<String> names = new HashSet<>();
            try {
                String sql = "SELECT name FROM tracks";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        names.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to get track names", e);
            }
            return names;
        });
    }

    // ==================== Backup Operations ====================

    @Override
    public CompletableFuture<String> createBackup(@Nullable String name) {
        return executeAsync(() -> {
            String backupName = name != null ? name :
                "backup-" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            Path backupFile = resolveBackupPath(backupName + ".db");

            try {
                Files.createDirectories(backupsDirectory);
                Files.copy(databaseFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                Logger.info("SQLite backup created: " + backupName);
                return backupName;
            } catch (IOException e) {
                Logger.severe("Failed to create SQLite backup", e);
                throw new RuntimeException("Backup failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> restoreBackup(@NotNull String name) {
        return executeAsync(() -> {
            Path backupFile = resolveBackupPath(name + ".db");

            if (!Files.exists(backupFile)) {
                // Try without .db extension
                backupFile = resolveBackupPath(name);
                if (!Files.exists(backupFile)) {
                    Logger.warn("SQLite backup not found: " + name);
                    return false;
                }
            }

            try {
                // Create safety backup
                createBackup("pre-restore-" + System.currentTimeMillis()).join();

                // Close current connection
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }

                // Replace database file
                Files.copy(backupFile, databaseFile, StandardCopyOption.REPLACE_EXISTING);

                // Reopen connection using the dynamically loaded driver
                String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
                connection = SQLiteDriverLoader.getConnection(url);

                Logger.info("SQLite backup restored: " + name);
                return true;

            } catch (Exception e) {
                Logger.severe("Failed to restore SQLite backup", e);
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
                        stream.filter(p -> p.toString().endsWith(".db"))
                              .map(p -> {
                                  String name = p.getFileName().toString();
                                  return name.endsWith(".db") ? name.substring(0, name.length() - 3) : name;
                              })
                              .sorted(Comparator.reverseOrder())
                              .forEach(backups::add);
                    }
                }
            } catch (IOException e) {
                Logger.severe("Failed to list SQLite backups", e);
            }
            return backups;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteBackup(@NotNull String name) {
        return executeAsync(() -> {
            Path backupFile = resolveBackupPath(name + ".db");
            try {
                return Files.deleteIfExists(backupFile);
            } catch (IOException e) {
                Logger.severe("Failed to delete SQLite backup: " + name, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        // SQLite auto-commits, nothing to do
        return CompletableFuture.completedFuture(null);
    }

    private Path resolveBackupPath(String name) {
        Path resolved = backupsDirectory.resolve(name).normalize();
        if (!resolved.startsWith(backupsDirectory)) {
            throw new IllegalArgumentException("Invalid backup name: " + name);
        }
        return resolved;
    }

    // ==================== Helper Methods ====================

    private List<String> parseGroupsList(String json) {
        // Simple JSON array parsing without external dependency
        List<String> groups = new ArrayList<>();
        if (json != null && json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1).trim();
            if (!content.isEmpty()) {
                for (String item : content.split(",")) {
                    String trimmed = item.trim();
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        groups.add(trimmed.substring(1, trimmed.length() - 1));
                    }
                }
            }
        }
        return groups;
    }

    private String serializeGroupsList(List<String> groups) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(groups.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeContexts(ContextSet contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Context ctx : contexts) {
            if (!first) sb.append(",");
            sb.append("\"").append(ctx.key()).append("=").append(ctx.value()).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private ContextSet deserializeContexts(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) {
            return ContextSet.empty();
        }
        // Parse JSON array of "key=value" strings
        String content = json.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1).trim();
        }
        if (content.isEmpty()) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        for (String item : content.split(",")) {
            String trimmed = item.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.contains("=")) {
                try {
                    builder.add(Context.parse(trimmed));
                } catch (IllegalArgumentException e) {
                    Logger.warn("Skipping invalid context entry: " + trimmed);
                }
            }
        }
        return builder.build();
    }
}
