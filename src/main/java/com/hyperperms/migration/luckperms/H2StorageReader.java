package com.hyperperms.migration.luckperms;

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
 * Inherits all SQL read operations (groups, users, tracks) from
 * {@link AbstractSqlLuckPermsReader}. This class only provides H2-specific
 * connection management, driver loading, and locked-database handling.
 */
public final class H2StorageReader extends AbstractSqlLuckPermsReader {

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

        // Try to load H2 driver from LuckPerms libs
        if (loadH2Driver()) {
            return true;
        }

        Logger.warn("H2 driver not found. H2 migration requires LuckPerms to be installed with its H2 driver in the libs folder.");
        return false;
    }

    /**
     * H2 reserves VALUE as a keyword, so it must be quoted.
     */
    @Override
    @NotNull
    protected String getValueColumnName() {
        return "\"VALUE\"";
    }

    // ==================== Connection Management ====================

    @Override
    protected Connection getConnection() throws SQLException {
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

    // ==================== H2 Driver Loading ====================

    /**
     * Attempts to load the H2 driver from LuckPerms libs folder.
     * <p>
     * H2 migration requires LuckPerms to be installed with its H2 driver in the libs folder.
     * We dynamically load from LuckPerms to ensure version compatibility with their database format.
     * <p>
     * Uses an isolated classloader (parent = platform classloader) to prevent other plugins'
     * H2 versions from being loaded via parent-first delegation. This mirrors LuckPerms's own
     * {@code IsolatedClassLoader} approach.
     *
     * @return true if driver was loaded successfully
     */
    private boolean loadH2Driver() {
        if (luckPermsDir == null) {
            Logger.debug("LuckPerms directory not provided, cannot load H2 driver");
            return false;
        }

        Path libsDir = luckPermsDir.resolve("libs");
        if (!Files.isDirectory(libsDir)) {
            Logger.debug("LuckPerms libs directory not found: %s", libsDir);
            return false;
        }

        Path h2Jar = findH2JarInLibs(libsDir);
        if (h2Jar == null) {
            Logger.debug("H2 driver JAR not found in LuckPerms libs folder");
            return false;
        }

        try {
            Logger.info("Loading H2 driver from LuckPerms: %s", h2Jar.getFileName());
            // Use platform classloader as parent to isolate from other plugins' H2 versions.
            // Without this, parent-first delegation loads H2 from whichever plugin (e.g. RPGLeveling)
            // has it on the classpath, causing version mismatches with the LuckPerms database format.
            h2ClassLoader = new URLClassLoader(
                new URL[]{h2Jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader()
            );
            Class<?> driverClass = h2ClassLoader.loadClass(H2_DRIVER);
            h2Driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            Logger.debug("Successfully loaded H2 driver from LuckPerms libs");
            return true;
        } catch (Exception e) {
            Logger.debug("Failed to load H2 from LuckPerms libs: %s", e.getMessage());
            closeClassLoader();
            return false;
        }
    }

    /**
     * Finds the H2 driver JAR in the libs directory.
     * <p>
     * Prefers the modern H2 driver (h2-driver-*.jar) over the legacy one (h2-driver-legacy-*.jar)
     * since LuckPerms's current database format (luckperms-h2-v2.mv.db) is created with the modern
     * H2 2.1.214 driver. Falls back to legacy if modern is not found.
     */
    @Nullable
    private Path findH2JarInLibs(Path libsDir) {
        try (Stream<Path> files = Files.list(libsDir)) {
            List<Path> h2Jars = files
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.startsWith("h2") && name.endsWith(".jar");
                })
                .toList();

            if (h2Jars.isEmpty()) {
                return null;
            }

            // Prefer modern driver over legacy
            Path modern = null;
            Path legacy = null;
            for (Path jar : h2Jars) {
                String name = jar.getFileName().toString().toLowerCase();
                if (name.contains("legacy")) {
                    legacy = jar;
                } else {
                    modern = jar;
                }
            }
            return modern != null ? modern : legacy;
        } catch (IOException e) {
            Logger.debug("Error searching for H2 jar: %s", e.getMessage());
            return null;
        }
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
            // Check if this is a Windows file lock issue
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows && e.getMessage() != null &&
                (e.getMessage().contains("locked") || e.getMessage().contains("another process"))) {
                throw new SQLException(
                    "Cannot copy H2 database - file is locked by LuckPerms. " +
                    "On Windows, you must stop LuckPerms before migrating. " +
                    "Either: (1) Stop the server, run migration, restart, or " +
                    "(2) Temporarily unload LuckPerms with a plugin manager.", e);
            }
            throw new SQLException("Failed to create temporary database copy: " + e.getMessage(), e);
        }
    }

    // ==================== Cleanup ====================

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
