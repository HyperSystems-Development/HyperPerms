package com.hyperperms.analytics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hyperperms.util.Logger;
import com.hyperperms.util.SQLiteDriverLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQLite implementation of analytics storage.
 */
public final class SQLiteAnalyticsStorage implements AnalyticsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path databasePath;
    private final ExecutorService executor;
    private Connection connection;

    public SQLiteAnalyticsStorage(@NotNull Path databasePath) {
        this.databasePath = databasePath;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HyperPerms-Analytics");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Ensure parent directory exists
                Files.createDirectories(databasePath.getParent());

                // Check if SQLite driver is available (must be downloaded separately)
                if (!SQLiteDriverLoader.isAvailable()) {
                    throw new RuntimeException(SQLiteDriverLoader.getDownloadInstructions());
                }

                // Connect to database using the dynamically loaded driver
                connection = SQLiteDriverLoader.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());

                // Create tables
                try (Statement stmt = connection.createStatement()) {
                    // Permission check tracking table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analytics_permission_checks (
                            permission TEXT PRIMARY KEY,
                            check_count INTEGER NOT NULL DEFAULT 0,
                            grant_count INTEGER NOT NULL DEFAULT 0,
                            deny_count INTEGER NOT NULL DEFAULT 0,
                            last_checked INTEGER NOT NULL
                        )
                    """);

                    // Audit log table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS analytics_audit_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            timestamp INTEGER NOT NULL,
                            holder_type TEXT NOT NULL,
                            holder_id TEXT NOT NULL,
                            action TEXT NOT NULL,
                            permission TEXT,
                            old_value INTEGER,
                            new_value INTEGER,
                            executor TEXT,
                            source TEXT NOT NULL
                        )
                    """);

                    // Create indexes
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON analytics_audit_log(timestamp)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_holder ON analytics_audit_log(holder_type, holder_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_checks_count ON analytics_permission_checks(check_count DESC)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_checks_last ON analytics_permission_checks(last_checked)");
                }

                Logger.info("[Analytics] SQLite storage initialized: %s", databasePath.getFileName());
            } catch (Exception e) {
                Logger.severe("[Analytics] Failed to initialize SQLite storage", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                executor.shutdown();
                Logger.info("[Analytics] Storage shut down");
            } catch (SQLException e) {
                Logger.warn("[Analytics] Error closing connection: %s", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> recordPermissionCheck(@NotNull String permission, boolean granted) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = """
                    INSERT INTO analytics_permission_checks (permission, check_count, grant_count, deny_count, last_checked)
                    VALUES (?, 1, ?, ?, ?)
                    ON CONFLICT(permission) DO UPDATE SET
                        check_count = check_count + 1,
                        grant_count = grant_count + ?,
                        deny_count = deny_count + ?,
                        last_checked = ?
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    long now = System.currentTimeMillis();
                    int grantInc = granted ? 1 : 0;
                    int denyInc = granted ? 0 : 1;
                    ps.setString(1, permission);
                    ps.setInt(2, grantInc);
                    ps.setInt(3, denyInc);
                    ps.setLong(4, now);
                    ps.setInt(5, grantInc);
                    ps.setInt(6, denyInc);
                    ps.setLong(7, now);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to record permission check: %s", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> bulkUpdatePermissionChecks(@NotNull Map<String, long[]> checks) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                String sql = """
                    INSERT INTO analytics_permission_checks (permission, check_count, grant_count, deny_count, last_checked)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(permission) DO UPDATE SET
                        check_count = check_count + ?,
                        grant_count = grant_count + ?,
                        deny_count = deny_count + ?,
                        last_checked = ?
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, long[]> entry : checks.entrySet()) {
                        long[] counts = entry.getValue(); // [checkCount, grantCount, denyCount]
                        ps.setString(1, entry.getKey());
                        ps.setLong(2, counts[0]);
                        ps.setLong(3, counts[1]);
                        ps.setLong(4, counts[2]);
                        ps.setLong(5, now);
                        ps.setLong(6, counts[0]);
                        ps.setLong(7, counts[1]);
                        ps.setLong(8, counts[2]);
                        ps.setLong(9, now);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
                Logger.warn("[Analytics] Failed to bulk update permission checks: %s", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<AnalyticsSummary.PermissionStats> getPermissionStats(@NotNull String permission) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT * FROM analytics_permission_checks WHERE permission = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, permission);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return new AnalyticsSummary.PermissionStats(
                                    rs.getString("permission"),
                                    rs.getLong("check_count"),
                                    rs.getLong("grant_count"),
                                    rs.getLong("deny_count"),
                                    rs.getLong("last_checked")
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get permission stats: %s", e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<List<AnalyticsSummary.PermissionStats>> getHotspots(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AnalyticsSummary.PermissionStats> result = new ArrayList<>();
            try {
                String sql = "SELECT * FROM analytics_permission_checks ORDER BY check_count DESC LIMIT ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(new AnalyticsSummary.PermissionStats(
                                    rs.getString("permission"),
                                    rs.getLong("check_count"),
                                    rs.getLong("grant_count"),
                                    rs.getLong("deny_count"),
                                    rs.getLong("last_checked")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get hotspots: %s", e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<List<String>> getUnusedPermissions(int daysSinceLastCheck) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> result = new ArrayList<>();
            try {
                long cutoff = System.currentTimeMillis() - (daysSinceLastCheck * 24L * 60 * 60 * 1000);
                String sql = "SELECT permission FROM analytics_permission_checks WHERE last_checked < ? ORDER BY last_checked ASC";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, cutoff);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getString("permission"));
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get unused permissions: %s", e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<AnalyticsSummary> getSummary() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = """
                    SELECT
                        SUM(check_count) as total_checks,
                        SUM(grant_count) as total_grants,
                        SUM(deny_count) as total_denies,
                        COUNT(*) as unique_perms,
                        MIN(last_checked) as period_start,
                        MAX(last_checked) as period_end
                    FROM analytics_permission_checks
                """;
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        return new AnalyticsSummary(
                                rs.getLong("total_checks"),
                                rs.getLong("total_grants"),
                                rs.getLong("total_denies"),
                                rs.getInt("unique_perms"),
                                0, // Unique users not tracked in check table
                                rs.getLong("period_start"),
                                rs.getLong("period_end")
                        );
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get summary: %s", e.getMessage());
            }
            return new AnalyticsSummary(0, 0, 0, 0, 0, 0, 0);
        }, executor);
    }

    @Override
    public CompletableFuture<Void> recordAuditEntry(
            @NotNull String holderType,
            @NotNull String holderId,
            @NotNull String action,
            @Nullable String permission,
            @Nullable Boolean oldValue,
            @Nullable Boolean newValue,
            @Nullable String changedBy,
            @NotNull String source
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = """
                    INSERT INTO analytics_audit_log
                    (timestamp, holder_type, holder_id, action, permission, old_value, new_value, executor, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, holderType);
                    ps.setString(3, holderId);
                    ps.setString(4, action);
                    ps.setString(5, permission);
                    ps.setObject(6, oldValue != null ? (oldValue ? 1 : 0) : null);
                    ps.setObject(7, newValue != null ? (newValue ? 1 : 0) : null);
                    ps.setString(8, changedBy);
                    ps.setString(9, source);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to record audit entry: %s", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AnalyticsSummary.AuditEntry>> getAuditLog(
            @NotNull String holderType,
            @NotNull String holderId,
            int limit
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<AnalyticsSummary.AuditEntry> result = new ArrayList<>();
            try {
                String sql = """
                    SELECT * FROM analytics_audit_log
                    WHERE holder_type = ? AND holder_id = ?
                    ORDER BY timestamp DESC LIMIT ?
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, holderType);
                    ps.setString(2, holderId);
                    ps.setInt(3, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(readAuditEntry(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get audit log: %s", e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<List<AnalyticsSummary.AuditEntry>> getRecentAuditLog(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AnalyticsSummary.AuditEntry> result = new ArrayList<>();
            try {
                String sql = "SELECT * FROM analytics_audit_log ORDER BY timestamp DESC LIMIT ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(readAuditEntry(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get recent audit log: %s", e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<List<AnalyticsSummary.AuditEntry>> getAuditLogInRange(long startMs, long endMs, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AnalyticsSummary.AuditEntry> result = new ArrayList<>();
            try {
                String sql = """
                    SELECT * FROM analytics_audit_log
                    WHERE timestamp >= ? AND timestamp < ?
                    ORDER BY timestamp DESC LIMIT ?
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, startMs);
                    ps.setLong(2, endMs);
                    ps.setInt(3, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(readAuditEntry(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to get audit log in range: %s", e.getMessage());
            }
            return result;
        }, executor);
    }

    private AnalyticsSummary.AuditEntry readAuditEntry(ResultSet rs) throws SQLException {
        Integer oldVal = rs.getObject("old_value") != null ? rs.getInt("old_value") : null;
        Integer newVal = rs.getObject("new_value") != null ? rs.getInt("new_value") : null;
        return new AnalyticsSummary.AuditEntry(
                rs.getLong("id"),
                rs.getLong("timestamp"),
                rs.getString("holder_type"),
                rs.getString("holder_id"),
                rs.getString("action"),
                rs.getString("permission"),
                oldVal != null ? oldVal == 1 : null,
                newVal != null ? newVal == 1 : null,
                rs.getString("executor"),
                rs.getString("source")
        );
    }

    @Override
    public CompletableFuture<Integer> cleanupOldData(int retentionDays) {
        return CompletableFuture.supplyAsync(() -> {
            int deleted = 0;
            try {
                long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
                
                // Delete old audit entries
                String sql = "DELETE FROM analytics_audit_log WHERE timestamp < ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, cutoff);
                    deleted = ps.executeUpdate();
                }
                
                Logger.info("[Analytics] Cleaned up %d old audit entries", deleted);
            } catch (SQLException e) {
                Logger.warn("[Analytics] Failed to cleanup old data: %s", e.getMessage());
            }
            return deleted;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> export(@NotNull String format, @NotNull String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (format.equalsIgnoreCase("json")) {
                    return exportJson(filePath);
                } else if (format.equalsIgnoreCase("csv")) {
                    return exportCsv(filePath);
                } else {
                    Logger.warn("[Analytics] Unknown export format: %s", format);
                    return false;
                }
            } catch (Exception e) {
                Logger.warn("[Analytics] Failed to export: %s", e.getMessage());
                return false;
            }
        }, executor);
    }

    private boolean exportJson(String filePath) throws SQLException, IOException {
        // Ensure parent directories exist
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Map<String, Object> data = new HashMap<>();
        
        // Export permission checks
        List<Map<String, Object>> checks = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM analytics_permission_checks")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("permission", rs.getString("permission"));
                row.put("checkCount", rs.getLong("check_count"));
                row.put("grantCount", rs.getLong("grant_count"));
                row.put("denyCount", rs.getLong("deny_count"));
                row.put("lastChecked", rs.getLong("last_checked"));
                checks.add(row);
            }
        }
        data.put("permissionChecks", checks);
        
        // Export audit log
        List<Map<String, Object>> audits = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM analytics_audit_log ORDER BY timestamp DESC LIMIT 10000")) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("timestamp", rs.getLong("timestamp"));
                row.put("holderType", rs.getString("holder_type"));
                row.put("holderId", rs.getString("holder_id"));
                row.put("action", rs.getString("action"));
                row.put("permission", rs.getString("permission"));
                row.put("executor", rs.getString("executor"));
                row.put("source", rs.getString("source"));
                audits.add(row);
            }
        }
        data.put("auditLog", audits);
        
        data.put("exportedAt", System.currentTimeMillis());
        
        try (FileWriter writer = new FileWriter(filePath)) {
            GSON.toJson(data, writer);
        }
        
        Logger.info("[Analytics] Exported to JSON: %s", filePath);
        return true;
    }

    private boolean exportCsv(String filePath) throws SQLException, IOException {
        // Ensure parent directories exist
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Export permission checks as CSV
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("permission,check_count,grant_count,deny_count,last_checked\n");
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM analytics_permission_checks")) {
                while (rs.next()) {
                    writer.write(String.format("\"%s\",%d,%d,%d,%d\n",
                            rs.getString("permission").replace("\"", "\"\""),
                            rs.getLong("check_count"),
                            rs.getLong("grant_count"),
                            rs.getLong("deny_count"),
                            rs.getLong("last_checked")
                    ));
                }
            }
        }
        
        Logger.info("[Analytics] Exported to CSV: %s", filePath);
        return true;
    }
}
