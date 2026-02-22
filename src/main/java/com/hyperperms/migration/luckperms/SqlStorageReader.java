package com.hyperperms.migration.luckperms;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

/**
 * Reads LuckPerms data from MySQL/MariaDB or PostgreSQL databases.
 * <p>
 * Inherits all SQL read operations (groups, users, tracks) from
 * {@link AbstractSqlLuckPermsReader}. This class only provides remote
 * database connection management and driver loading.
 */
public final class SqlStorageReader extends AbstractSqlLuckPermsReader {

    private final LuckPermsStorageType storageType;
    private final Map<String, String> connectionDetails;
    private final String tablePrefix;
    private Connection connection;

    public SqlStorageReader(@NotNull LuckPermsStorageType storageType,
                           @NotNull Map<String, String> connectionDetails) {
        this.storageType = storageType;
        this.connectionDetails = connectionDetails;
        this.tablePrefix = connectionDetails.getOrDefault("table_prefix", "luckperms_");
    }

    @Override
    @NotNull
    public LuckPermsStorageType getStorageType() {
        return storageType;
    }

    @Override
    @NotNull
    public String getStorageDescription() {
        String address = connectionDetails.getOrDefault("address", "unknown");
        String database = connectionDetails.getOrDefault("database", "unknown");
        return storageType.getDisplayName() + " (" + address + "/" + database + ")";
    }

    @Override
    @NotNull
    protected String getTablePrefix() {
        return tablePrefix;
    }

    @Override
    public boolean isAvailable() {
        try {
            // Try to load the appropriate driver
            if (storageType == LuckPermsStorageType.MYSQL) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    Class.forName("com.mysql.jdbc.Driver");
                }
            } else if (storageType == LuckPermsStorageType.POSTGRESQL) {
                Class.forName("org.postgresql.Driver");
            }

            // Test connection
            try (Connection conn = createConnection()) {
                return conn != null && !conn.isClosed();
            }
        } catch (ClassNotFoundException e) {
            Logger.warn("Database driver not found: %s", e.getMessage());
            return false;
        } catch (SQLException e) {
            Logger.warn("Cannot connect to database: %s", e.getMessage());
            return false;
        }
    }

    // ==================== Connection Management ====================

    private Connection createConnection() throws SQLException {
        String address = connectionDetails.get("address");
        String database = connectionDetails.get("database");
        String username = connectionDetails.getOrDefault("username", "root");
        String password = connectionDetails.getOrDefault("password", "");

        String url;
        if (storageType == LuckPermsStorageType.MYSQL) {
            // Handle port if included in address
            if (!address.contains(":")) {
                address = address + ":3306";
            }
            url = "jdbc:mysql://" + address + "/" + database +
                  "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        } else {
            if (!address.contains(":")) {
                address = address + ":5432";
            }
            url = "jdbc:postgresql://" + address + "/" + database;
        }

        return DriverManager.getConnection(url, username, password);
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = createConnection();
        }
        return connection;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                Logger.debug("Error closing database connection: %s", e.getMessage());
            }
        }
    }
}
