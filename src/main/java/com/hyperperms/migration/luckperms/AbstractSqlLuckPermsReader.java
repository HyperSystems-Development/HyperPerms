package com.hyperperms.migration.luckperms;

import com.hyperperms.migration.luckperms.LuckPermsData.*;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Base class for SQL-based LuckPerms storage readers.
 * <p>
 * Extracts shared logic for reading groups, users, and tracks from SQL databases
 * that use the standard LuckPerms schema. Concrete subclasses (H2, MySQL, PostgreSQL)
 * only need to provide connection management and table name configuration.
 * <p>
 * LuckPerms SQL schema:
 * <ul>
 *   <li>{@code {prefix}groups} - Group name definitions</li>
 *   <li>{@code {prefix}group_permissions} - Group permission nodes</li>
 *   <li>{@code {prefix}players} - User/player definitions</li>
 *   <li>{@code {prefix}user_permissions} - User permission nodes</li>
 *   <li>{@code {prefix}tracks} - Promotion tracks with JSON group lists</li>
 * </ul>
 */
public abstract class AbstractSqlLuckPermsReader implements LuckPermsStorageReader {

    /**
     * Gets an active database connection, creating one if necessary.
     *
     * @return the database connection
     * @throws SQLException if a connection cannot be obtained
     */
    protected abstract Connection getConnection() throws SQLException;

    /**
     * Gets the table prefix for LuckPerms tables.
     * <p>
     * Defaults to {@code "luckperms_"}. Subclasses may override to use a
     * configurable prefix (e.g., from LuckPerms config).
     *
     * @return the table prefix (e.g., "luckperms_")
     */
    @NotNull
    protected String getTablePrefix() {
        return "luckperms_";
    }

    /**
     * Gets the column name for the permission value column in SQL queries.
     * <p>
     * H2 reserves VALUE as a keyword, so it must be quoted in SQL statements.
     * Override this in subclasses that need different quoting (e.g., H2 uses {@code "VALUE"}).
     * <p>
     * <b>Note:</b> This is only for SQL query construction. For {@link ResultSet} column
     * access, use {@code "value"} directly — JDBC does not need SQL keyword quoting.
     *
     * @return the value column name for SQL (e.g., "value" or "\"VALUE\"")
     */
    @NotNull
    protected String getValueColumnName() {
        return "value";
    }

    // ==================== Groups ====================

    @Override
    @NotNull
    public Map<String, LPGroup> readGroups() throws IOException {
        Logger.debugMigration("Reading groups from %s", getStorageDescription());
        Map<String, LPGroup> groups = new LinkedHashMap<>();

        try {
            Connection conn = getConnection();
            String prefix = getTablePrefix();

            // Get all group names
            String groupQuery = "SELECT name FROM " + prefix + "groups";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(groupQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name").toLowerCase();
                    groups.put(name, null); // Placeholder
                }
            }

            // Build full group objects
            for (String groupName : new ArrayList<>(groups.keySet())) {
                LPGroup group = readGroup(conn, groupName);
                if (group != null) {
                    groups.put(groupName, group);
                } else {
                    groups.remove(groupName);
                }
            }

        } catch (SQLException e) {
            throw new IOException("Failed to read groups from " + getStorageDescription(), e);
        }

        Logger.debugMigration("Read %d groups from %s", groups.size(), getStorageDescription());
        return groups;
    }

    /**
     * Reads a single group and its permissions from the database.
     * <p>
     * Parses LuckPerms meta-permissions:
     * <ul>
     *   <li>{@code meta.weight.N} - group weight</li>
     *   <li>{@code prefix.priority.value} - chat prefix</li>
     *   <li>{@code suffix.priority.value} - chat suffix</li>
     *   <li>{@code group.name} - parent group inheritance</li>
     * </ul>
     */
    @Nullable
    private LPGroup readGroup(Connection conn, String groupName) throws SQLException {
        List<LPNode> nodes = new ArrayList<>();
        Set<String> parents = new LinkedHashSet<>();
        int weight = 0;
        String prefix = null;
        String suffix = null;
        int prefixPriority = 0;
        int suffixPriority = 0;

        String valueSqlCol = getValueColumnName();
        String permQuery = "SELECT permission, " + valueSqlCol + ", expiry, server, world FROM " +
                getTablePrefix() + "group_permissions WHERE name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(permQuery)) {
            stmt.setString(1, groupName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String permission = rs.getString("permission");
                    boolean value = rs.getBoolean("value");
                    long expiry = rs.getLong("expiry");
                    String server = rs.getString("server");
                    String world = rs.getString("world");

                    Map<String, String> contexts = buildContexts(server, world);

                    // Handle meta permissions
                    if (permission.startsWith("meta.weight.")) {
                        try {
                            weight = Integer.parseInt(permission.substring("meta.weight.".length()));
                        } catch (NumberFormatException ignored) {}
                        continue;
                    }

                    if (permission.startsWith("prefix.")) {
                        String[] parts = permission.split("\\.", 3);
                        if (parts.length >= 3) {
                            try {
                                int priority = Integer.parseInt(parts[1]);
                                if (priority > prefixPriority) {
                                    prefixPriority = priority;
                                    prefix = parts[2];
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        continue;
                    }

                    if (permission.startsWith("suffix.")) {
                        String[] parts = permission.split("\\.", 3);
                        if (parts.length >= 3) {
                            try {
                                int priority = Integer.parseInt(parts[1]);
                                if (priority > suffixPriority) {
                                    suffixPriority = priority;
                                    suffix = parts[2];
                                }
                            } catch (NumberFormatException ignored) {}
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

    // ==================== Users ====================

    @Override
    @NotNull
    public Map<UUID, LPUser> readUsers() throws IOException {
        Logger.debugMigration("Reading users from %s", getStorageDescription());
        Map<UUID, LPUser> users = new LinkedHashMap<>();

        try {
            Connection conn = getConnection();
            String prefix = getTablePrefix();

            String userQuery = "SELECT uuid, username, primary_group FROM " + prefix + "players";
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
                        Logger.warn("Invalid UUID in %s: %s", getStorageDescription(), uuidStr);
                    }
                }
            }

        } catch (SQLException e) {
            throw new IOException("Failed to read users from " + getStorageDescription(), e);
        }

        Logger.debugMigration("Read %d users from %s", users.size(), getStorageDescription());
        return users;
    }

    /**
     * Reads a single user's permissions from the database.
     */
    @Nullable
    private LPUser readUser(Connection conn, UUID uuid, String username, String primaryGroup)
            throws SQLException {
        List<LPNode> nodes = new ArrayList<>();

        String valueSqlCol = getValueColumnName();
        String permQuery = "SELECT permission, " + valueSqlCol + ", expiry, server, world FROM " +
                getTablePrefix() + "user_permissions WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(permQuery)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String permission = rs.getString("permission");
                    boolean value = rs.getBoolean("value");
                    long expiry = rs.getLong("expiry");
                    String server = rs.getString("server");
                    String world = rs.getString("world");

                    Map<String, String> contexts = buildContexts(server, world);
                    nodes.add(new LPNode(permission, value, expiry, contexts));
                }
            }
        }

        if (primaryGroup == null || primaryGroup.isEmpty()) {
            primaryGroup = "default";
        }

        return new LPUser(uuid, username, primaryGroup, nodes);
    }

    // ==================== Tracks ====================

    @Override
    @NotNull
    public Map<String, LPTrack> readTracks() throws IOException {
        Logger.debugMigration("Reading tracks from %s", getStorageDescription());
        Map<String, LPTrack> tracks = new LinkedHashMap<>();

        try {
            Connection conn = getConnection();
            String prefix = getTablePrefix();

            String trackQuery = "SELECT name, groups FROM " + prefix + "tracks";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(trackQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name").toLowerCase();
                    String groupsJson = rs.getString("groups");

                    List<String> groups = parseGroupsJson(groupsJson);
                    tracks.put(name, new LPTrack(name, groups));
                }
            }

        } catch (SQLException e) {
            throw new IOException("Failed to read tracks from " + getStorageDescription(), e);
        }

        Logger.debugMigration("Read %d tracks from %s", tracks.size(), getStorageDescription());
        return tracks;
    }

    // ==================== Estimation ====================

    @Override
    public int estimateUserCount() {
        try {
            Connection conn = getConnection();
            String countQuery = "SELECT COUNT(*) FROM " + getTablePrefix() + "players";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countQuery)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            Logger.debug("Failed to count users in %s: %s", getStorageDescription(), e.getMessage());
        }
        return -1;
    }

    // ==================== Helpers ====================

    /**
     * Builds a context map from LuckPerms server/world columns.
     * <p>
     * LuckPerms stores contexts as "global" when no restriction applies.
     * Non-global values are included as context entries.
     *
     * @param server the server context value (or "global")
     * @param world  the world context value (or "global")
     * @return the context map (may be empty)
     */
    @NotNull
    protected static Map<String, String> buildContexts(@Nullable String server, @Nullable String world) {
        Map<String, String> contexts = new LinkedHashMap<>();
        if (server != null && !server.equals("global")) {
            contexts.put("server", server);
        }
        if (world != null && !world.equals("global")) {
            contexts.put("world", world);
        }
        return contexts;
    }

    /**
     * Parses a JSON array string of group names.
     * <p>
     * Simple parser for format: {@code ["group1","group2","group3"]}
     *
     * @param json the JSON array string
     * @return the list of group names (lowercase)
     */
    @NotNull
    protected static List<String> parseGroupsJson(@Nullable String json) {
        List<String> groups = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return groups;
        }

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
}
