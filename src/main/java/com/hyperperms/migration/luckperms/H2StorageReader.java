package com.hyperperms.migration.luckperms;

import com.hyperperms.migration.luckperms.LuckPermsData.*;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Reads LuckPerms data from H2 embedded database.
 * <p>
 * Dynamically loads the H2 driver from LuckPerms's libs folder to ensure
 * version compatibility with the database file.
 * <p>
 * LuckPerms H2 database schema:
 * <ul>
 *   <li>luckperms_groups - Group definitions</li>
 *   <li>luckperms_group_permissions - Group permissions</li>
 *   <li>luckperms_players - User definitions</li>
 *   <li>luckperms_user_permissions - User permissions</li>
 *   <li>luckperms_tracks - Track definitions</li>
 * </ul>
 */
public final class H2StorageReader implements LuckPermsStorageReader {

    private static final String H2_DRIVER = "org.h2.Driver";

    private final Path databasePath;
    private final Path luckPermsDir;
    private Connection connection;
    private URLClassLoader h2ClassLoader;
    private Driver h2Driver;
    private Path tempDatabasePath;  // Temporary copy when original is locked

    public H2StorageReader(@NotNull Path databasePath, @Nullable Path luckPermsDir) {
        this.databasePath = databasePath;
        this.luckPermsDir = luckPermsDir;
    }

    /**
     * @deprecated Use {@link #H2StorageReader(Path, Path)} instead to enable dynamic H2 driver loading.
     */
    @Deprecated
    public H2StorageReader(@NotNull Path databasePath) {
        this(databasePath, null);
    }

    @Override
    @NotNull
    public LuckPermsStorageType getStorageType() {
        return LuckPermsStorageType.H2;
    }

    @Override
    @NotNull
    public String getStorageDescription() {
        return "H2 database (" + databasePath.getFileName() + ")";
    }

    @Override
    public boolean isAvailable() {
        if (!Files.exists(databasePath)) {
            Logger.debug("H2 database file not found: %s", databasePath);
            return false;
        }

        // Try to load H2 driver from LuckPerms libs first, then fall back to bundled
        if (loadH2Driver()) {
            return true;
        }

        Logger.warn("H2 driver not found. Migration from H2 database not available.");
        return false;
    }

    /**
     * Attempts to load the H2 driver, first from LuckPerms libs folder (for version compatibility),
     * then falls back to the bundled H2 driver.
     *
     * @return true if driver was loaded successfully
     */
    private boolean loadH2Driver() {
        // First, try to load from LuckPerms libs folder for version compatibility
        if (luckPermsDir != null) {
            Path libsDir = luckPermsDir.resolve("libs");
            if (Files.isDirectory(libsDir)) {
                Path h2Jar = findH2JarInLibs(libsDir);
                if (h2Jar != null) {
                    try {
                        Logger.info("Loading H2 driver from LuckPerms: %s", h2Jar.getFileName());
                        h2ClassLoader = new URLClassLoader(
                            new URL[]{h2Jar.toUri().toURL()},
                            getClass().getClassLoader()
                        );
                        Class<?> driverClass = h2ClassLoader.loadClass(H2_DRIVER);
                        h2Driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                        Logger.debug("Successfully loaded H2 driver from LuckPerms libs");
                        return true;
                    } catch (Exception e) {
                        Logger.debug("Failed to load H2 from LuckPerms libs: %s", e.getMessage());
                        closeClassLoader();
                    }
                }
            }
        }

        // Fall back to bundled H2 driver
        try {
            Class.forName(H2_DRIVER);
            Logger.debug("Using bundled H2 driver");
            return true;
        } catch (ClassNotFoundException e) {
            Logger.debug("Bundled H2 driver not found");
            return false;
        }
    }

    /**
     * Finds the H2 driver JAR in the libs directory.
     */
    @Nullable
    private Path findH2JarInLibs(Path libsDir) {
        try (Stream<Path> files = Files.list(libsDir)) {
            return files
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.startsWith("h2") && name.endsWith(".jar");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            Logger.debug("Error searching for H2 jar: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Opens a connection to the H2 database.
     * If the database is locked (e.g., by LuckPerms), creates a temporary copy.
     */
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // H2 database path without .mv.db extension - MUST use absolute path
            String dbPath = databasePath.toAbsolutePath().toString();
            if (dbPath.endsWith(".mv.db")) {
                dbPath = dbPath.substring(0, dbPath.length() - 6);
            }

            // Try to connect directly first
            String url = "jdbc:h2:" + dbPath;

            try {
                connection = tryConnect(url);
            } catch (SQLException e) {
                // If database is locked, try copying to a temp file
                if (e.getMessage() != null &&
                    (e.getMessage().contains("already in use") ||
                     e.getMessage().contains("file is locked"))) {
                    Logger.info("Database is locked by another process, creating temporary copy...");
                    connection = connectViaTempCopy();
                } else {
                    throw e;
                }
            }
        }
        return connection;
    }

    /**
     * Attempts to connect using the given URL.
     * Always uses the dynamically loaded driver if available for version compatibility.
     */
    private Connection tryConnect(String url) throws SQLException {
        // Ensure driver is loaded (may not have been loaded yet if isAvailable wasn't called)
        if (h2Driver == null && h2ClassLoader == null) {
            loadH2Driver();
        }

        if (h2Driver == null || h2ClassLoader == null) {
            throw new SQLException(
                "H2 driver not available. Could not find H2 driver in LuckPerms libs folder. " +
                "Ensure LuckPerms is installed and has been run at least once."
            );
        }

        Logger.debug("Using dynamically loaded H2 driver for connection");
        // Set thread context classloader to ensure H2 loads all its classes from the same jar
        ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(h2ClassLoader);
            Properties props = new Properties();
            props.setProperty("user", "");
            props.setProperty("password", "");
            return h2Driver.connect(url, props);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCL);
        }
    }

    /**
     * Creates a temporary copy of the database and connects to that.
     */
    private Connection connectViaTempCopy() throws SQLException {
        try {
            // Create temp directory if needed
            Path tempDir = databasePath.getParent().resolve(".hyperperms_temp");
            Files.createDirectories(tempDir);

            // Copy the .mv.db file
            String baseName = databasePath.getFileName().toString();
            Path tempDb = tempDir.resolve(baseName);

            Logger.debug("Copying database to: %s", tempDb);
            Files.copy(databasePath, tempDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Also copy .trace.db if it exists (optional, for debugging)
            String traceName = baseName.replace(".mv.db", ".trace.db");
            Path traceFile = databasePath.getParent().resolve(traceName);
            if (Files.exists(traceFile)) {
                Files.copy(traceFile, tempDir.resolve(traceName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Connect to the copy
            String tempPath = tempDb.toAbsolutePath().toString();
            if (tempPath.endsWith(".mv.db")) {
                tempPath = tempPath.substring(0, tempPath.length() - 6);
            }

            String url = "jdbc:h2:" + tempPath;
            Logger.info("Connecting to temporary database copy");

            // Mark for cleanup
            this.tempDatabasePath = tempDb;

            return tryConnect(url);
        } catch (IOException e) {
            throw new SQLException("Failed to create temporary database copy: " + e.getMessage(), e);
        }
    }

    private void closeClassLoader() {
        if (h2ClassLoader != null) {
            try {
                h2ClassLoader.close();
            } catch (IOException e) {
                Logger.debug("Error closing H2 classloader: %s", e.getMessage());
            }
            h2ClassLoader = null;
            h2Driver = null;
        }
    }

    /**
     * Cleans up the temporary database copy if one was created.
     */
    private void cleanupTempDatabase() {
        if (tempDatabasePath != null) {
            try {
                // Delete the temp .mv.db file
                Files.deleteIfExists(tempDatabasePath);

                // Delete associated files
                String baseName = tempDatabasePath.getFileName().toString();
                Path tempDir = tempDatabasePath.getParent();

                String traceName = baseName.replace(".mv.db", ".trace.db");
                Files.deleteIfExists(tempDir.resolve(traceName));

                // Try to delete temp directory if empty
                try (var entries = Files.list(tempDir)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.deleteIfExists(tempDir);
                    }
                }

                Logger.debug("Cleaned up temporary database files");
            } catch (IOException e) {
                Logger.debug("Error cleaning up temp database: %s", e.getMessage());
            }
            tempDatabasePath = null;
        }
    }

    @Override
    @NotNull
    public Map<String, LPGroup> readGroups() throws IOException {
        Map<String, LPGroup> groups = new LinkedHashMap<>();
        
        try {
            Connection conn = getConnection();
            
            // First, get all group names and metadata
            String groupQuery = "SELECT name FROM luckperms_groups";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(groupQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name").toLowerCase();
                    groups.put(name, null); // Placeholder
                }
            }
            
            // For each group, get permissions and build the full group object
            for (String groupName : new ArrayList<>(groups.keySet())) {
                LPGroup group = readGroup(conn, groupName);
                if (group != null) {
                    groups.put(groupName, group);
                } else {
                    groups.remove(groupName);
                }
            }
            
        } catch (SQLException e) {
            throw new IOException("Failed to read groups from H2 database", e);
        }
        
        return groups;
    }
    
    @Nullable
    private LPGroup readGroup(Connection conn, String groupName) throws SQLException {
        List<LPNode> nodes = new ArrayList<>();
        Set<String> parents = new LinkedHashSet<>();
        int weight = 0;
        String prefix = null;
        String suffix = null;
        int prefixPriority = 0;
        int suffixPriority = 0;
        
        // Read permissions
        String permQuery = """
            SELECT permission, "VALUE", expiry, server, world
            FROM luckperms_group_permissions
            WHERE name = ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(permQuery)) {
            stmt.setString(1, groupName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String permission = rs.getString("permission");
                    boolean value = rs.getBoolean("VALUE");
                    long expiry = rs.getLong("expiry");
                    String server = rs.getString("server");
                    String world = rs.getString("world");
                    
                    Map<String, String> contexts = new LinkedHashMap<>();
                    if (server != null && !server.equals("global")) {
                        contexts.put("server", server);
                    }
                    if (world != null && !world.equals("global")) {
                        contexts.put("world", world);
                    }
                    
                    // Check for meta permissions
                    if (permission.startsWith("meta.weight.")) {
                        try {
                            weight = Integer.parseInt(permission.substring("meta.weight.".length()));
                        } catch (NumberFormatException ignored) {}
                        continue;
                    }
                    
                    if (permission.startsWith("prefix.")) {
                        // Format: prefix.priority.value
                        String[] parts = permission.split("\\.", 3);
                        if (parts.length >= 3) {
                            int priority = Integer.parseInt(parts[1]);
                            if (priority > prefixPriority) {
                                prefixPriority = priority;
                                prefix = parts[2];
                            }
                        }
                        continue;
                    }
                    
                    if (permission.startsWith("suffix.")) {
                        String[] parts = permission.split("\\.", 3);
                        if (parts.length >= 3) {
                            int priority = Integer.parseInt(parts[1]);
                            if (priority > suffixPriority) {
                                suffixPriority = priority;
                                suffix = parts[2];
                            }
                        }
                        continue;
                    }
                    
                    // Check for parent groups
                    if (permission.startsWith("group.") && value) {
                        parents.add(permission.substring("group.".length()));
                    }
                    
                    nodes.add(new LPNode(permission, value, expiry, contexts));
                }
            }
        }
        
        return new LPGroup(groupName, weight, prefix, suffix, prefixPriority, suffixPriority, nodes, parents);
    }
    
    @Override
    @NotNull
    public Map<UUID, LPUser> readUsers() throws IOException {
        Map<UUID, LPUser> users = new LinkedHashMap<>();
        
        try {
            Connection conn = getConnection();
            
            // Get all users
            String userQuery = "SELECT uuid, username, primary_group FROM luckperms_players";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(userQuery)) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String username = rs.getString("username");
                    String primaryGroup = rs.getString("primary_group");
                    
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        LPUser user = readUser(conn, uuid, username, primaryGroup);
                        if (user != null) {
                            users.put(uuid, user);
                        }
                    } catch (IllegalArgumentException e) {
                        Logger.warn("Invalid UUID in H2 database: %s", uuidStr);
                    }
                }
            }
            
        } catch (SQLException e) {
            throw new IOException("Failed to read users from H2 database", e);
        }
        
        return users;
    }
    
    @Nullable
    private LPUser readUser(Connection conn, UUID uuid, String username, String primaryGroup) throws SQLException {
        List<LPNode> nodes = new ArrayList<>();
        
        // Read permissions
        String permQuery = """
            SELECT permission, "VALUE", expiry, server, world
            FROM luckperms_user_permissions
            WHERE uuid = ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(permQuery)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String permission = rs.getString("permission");
                    boolean value = rs.getBoolean("VALUE");
                    long expiry = rs.getLong("expiry");
                    String server = rs.getString("server");
                    String world = rs.getString("world");
                    
                    Map<String, String> contexts = new LinkedHashMap<>();
                    if (server != null && !server.equals("global")) {
                        contexts.put("server", server);
                    }
                    if (world != null && !world.equals("global")) {
                        contexts.put("world", world);
                    }
                    
                    nodes.add(new LPNode(permission, value, expiry, contexts));
                }
            }
        }
        
        if (primaryGroup == null || primaryGroup.isEmpty()) {
            primaryGroup = "default";
        }
        
        return new LPUser(uuid, username, primaryGroup, nodes);
    }
    
    @Override
    @NotNull
    public Map<String, LPTrack> readTracks() throws IOException {
        Map<String, LPTrack> tracks = new LinkedHashMap<>();
        
        try {
            Connection conn = getConnection();
            
            String trackQuery = "SELECT name, groups FROM luckperms_tracks";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(trackQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name").toLowerCase();
                    String groupsJson = rs.getString("groups");
                    
                    // Parse groups from JSON array string
                    List<String> groups = parseGroupsJson(groupsJson);
                    tracks.put(name, new LPTrack(name, groups));
                }
            }
            
        } catch (SQLException e) {
            throw new IOException("Failed to read tracks from H2 database", e);
        }
        
        return tracks;
    }
    
    /**
     * Parses a JSON array string of group names.
     */
    private List<String> parseGroupsJson(String json) {
        List<String> groups = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return groups;
        }
        
        // Simple parsing for JSON array: ["group1","group2"]
        json = json.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1);
            for (String part : json.split(",")) {
                String group = part.trim();
                if (group.startsWith("\"") && group.endsWith("\"")) {
                    group = group.substring(1, group.length() - 1);
                }
                if (!group.isEmpty()) {
                    groups.add(group.toLowerCase());
                }
            }
        }
        
        return groups;
    }
    
    @Override
    public int estimateUserCount() {
        try {
            Connection conn = getConnection();
            String countQuery = "SELECT COUNT(*) FROM luckperms_players";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countQuery)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            Logger.debug("Failed to count users: %s", e.getMessage());
        }
        return -1;
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                Logger.debug("Error closing H2 connection: %s", e.getMessage());
            }
            connection = null;
        }
        closeClassLoader();
        cleanupTempDatabase();
    }
}
