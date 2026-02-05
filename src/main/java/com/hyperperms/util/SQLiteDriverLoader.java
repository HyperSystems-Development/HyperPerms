package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Dynamically loads the SQLite JDBC driver from an external JAR.
 * <p>
 * The SQLite JDBC driver is not bundled with HyperPerms to keep the JAR size small
 * (~4MB vs ~15MB). Users who want to use SQLite features (analytics, SQLite storage)
 * must download the driver manually and place it in the lib directory.
 * <p>
 * Usage:
 * <ol>
 *   <li>Download sqlite-jdbc from Maven Central</li>
 *   <li>Place it in plugins/HyperPerms/lib/</li>
 *   <li>Restart the server</li>
 * </ol>
 * <p>
 * The driver is loaded once on first use and cached for subsequent connections.
 * Thread context classloader switching is used to ensure native libraries load correctly.
 */
public final class SQLiteDriverLoader {

    private static final String DRIVER_CLASS = "org.sqlite.JDBC";
    private static final String JAR_PATTERN = "sqlite-jdbc";

    private static URLClassLoader classLoader;
    private static Driver driver;
    private static boolean loadAttempted = false;
    private static Path libDirectory;

    private SQLiteDriverLoader() {
        // Utility class
    }

    /**
     * Sets the lib directory where SQLite JDBC JAR should be located.
     * This must be called before any driver loading attempts.
     *
     * @param path the lib directory path
     */
    public static void setLibDirectory(@NotNull Path path) {
        libDirectory = path;
    }

    /**
     * Gets the lib directory.
     *
     * @return the lib directory, or null if not set
     */
    @Nullable
    public static Path getLibDirectory() {
        return libDirectory;
    }

    /**
     * Attempts to load the SQLite JDBC driver from the lib directory.
     * <p>
     * This method is thread-safe and will only attempt to load the driver once.
     * Subsequent calls will return the cached result.
     *
     * @return true if the driver was loaded successfully
     */
    public static synchronized boolean loadDriver() {
        if (loadAttempted) {
            return driver != null;
        }
        loadAttempted = true;

        if (libDirectory == null) {
            Logger.debug("[SQLite] Lib directory not set, cannot load driver");
            return false;
        }

        if (!Files.isDirectory(libDirectory)) {
            Logger.debug("[SQLite] Lib directory does not exist: %s", libDirectory);
            return false;
        }

        Path sqliteJar = findSqliteJar();
        if (sqliteJar == null) {
            Logger.debug("[SQLite] SQLite JDBC JAR not found in: %s", libDirectory);
            return false;
        }

        try {
            Logger.info("[SQLite] Loading SQLite JDBC from: %s", sqliteJar.getFileName());
            classLoader = new URLClassLoader(
                new URL[]{sqliteJar.toUri().toURL()},
                SQLiteDriverLoader.class.getClassLoader()
            );
            Class<?> driverClass = classLoader.loadClass(DRIVER_CLASS);
            driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            Logger.info("[SQLite] Successfully loaded SQLite JDBC driver");
            return true;
        } catch (Exception e) {
            Logger.warn("[SQLite] Failed to load SQLite driver: %s", e.getMessage());
            Logger.debug("[SQLite] Stack trace:", e);
            closeClassLoader();
            return false;
        }
    }

    /**
     * Finds the SQLite JDBC JAR in the lib directory.
     * Looks for files matching "sqlite-jdbc*.jar".
     *
     * @return the path to the SQLite JAR, or null if not found
     */
    @Nullable
    private static Path findSqliteJar() {
        if (libDirectory == null || !Files.isDirectory(libDirectory)) {
            return null;
        }

        try (Stream<Path> files = Files.list(libDirectory)) {
            return files
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.contains(JAR_PATTERN) && name.endsWith(".jar");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            Logger.debug("[SQLite] Error searching for SQLite JAR: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Opens a connection to the specified SQLite database URL.
     * <p>
     * This method uses the dynamically loaded driver and handles thread context
     * classloader switching to ensure native libraries load correctly.
     *
     * @param url the JDBC URL (e.g., "jdbc:sqlite:/path/to/database.db")
     * @return the database connection
     * @throws SQLException if the driver is not loaded or connection fails
     */
    @NotNull
    public static Connection getConnection(@NotNull String url) throws SQLException {
        if (driver == null) {
            throw new SQLException(
                "SQLite driver not loaded. Please download sqlite-jdbc JAR and place it in: " +
                (libDirectory != null ? libDirectory : "plugins/HyperPerms/lib/")
            );
        }

        // Set thread context classloader to ensure SQLite loads all native libraries correctly
        ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            Properties props = new Properties();
            Connection conn = driver.connect(url, props);
            if (conn == null) {
                throw new SQLException("Driver returned null connection for URL: " + url);
            }
            return conn;
        } finally {
            Thread.currentThread().setContextClassLoader(originalCL);
        }
    }

    /**
     * Checks if the SQLite JDBC driver is available.
     * <p>
     * This will attempt to load the driver if it hasn't been loaded yet.
     *
     * @return true if the driver is available and ready for use
     */
    public static boolean isAvailable() {
        if (driver != null) {
            return true;
        }
        return loadDriver();
    }

    /**
     * Checks if a load attempt has been made.
     * <p>
     * This can be used to check if the driver loading was already attempted
     * without triggering another load attempt.
     *
     * @return true if a load attempt has been made
     */
    public static boolean hasLoadBeenAttempted() {
        return loadAttempted;
    }

    /**
     * Gets the loaded driver instance.
     *
     * @return the driver, or null if not loaded
     */
    @Nullable
    public static Driver getDriver() {
        return driver;
    }

    /**
     * Closes the classloader and releases resources.
     * <p>
     * After calling this method, the driver will no longer be available
     * and {@link #isAvailable()} will return false until {@link #reset()}
     * is called and the driver is loaded again.
     */
    public static synchronized void closeClassLoader() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                Logger.debug("[SQLite] Error closing classloader: %s", e.getMessage());
            }
            classLoader = null;
            driver = null;
        }
    }

    /**
     * Resets the loader state, allowing the driver to be loaded again.
     * <p>
     * This is primarily useful for testing or hot-reloading scenarios.
     */
    public static synchronized void reset() {
        closeClassLoader();
        loadAttempted = false;
    }

    /**
     * Gets the download instructions for the SQLite JDBC driver.
     *
     * @return a user-friendly message explaining how to download and install the driver
     */
    @NotNull
    public static String getDownloadInstructions() {
        String libPath = libDirectory != null
            ? libDirectory.toString()
            : "mods/com.hyperperms_HyperPerms/lib/";

        return String.format(
            "SQLite JDBC driver not found.%n" +
            "To enable SQLite features (analytics, SQLite storage):%n" +
            "1. Download sqlite-jdbc from GitHub:%n" +
            "   https://github.com/xerial/sqlite-jdbc/releases/%n" +
            "2. Place the JAR in: %s%n" +
            "3. Restart your server",
            libPath
        );
    }
}
