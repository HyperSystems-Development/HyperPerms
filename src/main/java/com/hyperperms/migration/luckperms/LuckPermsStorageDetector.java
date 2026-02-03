package com.hyperperms.migration.luckperms;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects which LuckPerms storage backend is in use.
 */
public final class LuckPermsStorageDetector {
    
    private static final Pattern STORAGE_METHOD_PATTERN = 
        Pattern.compile("storage-method:\\s*['\"]?([a-zA-Z0-9_-]+)['\"]?", Pattern.CASE_INSENSITIVE);
    
    private final Path modsDir;
    private Path luckPermsDir;

    /**
     * Creates a new storage detector.
     *
     * @param modsDir the mods directory (e.g., /server/mods/)
     */
    public LuckPermsStorageDetector(@NotNull Path modsDir) {
        this.modsDir = modsDir;
        this.luckPermsDir = findLuckPermsDirectory();
    }

    /**
     * Finds the LuckPerms directory by searching in the mods folder.
     * Looks for folders containing "LuckPerms" (case-insensitive).
     *
     * @return the LuckPerms directory path, or null if not found
     */
    @Nullable
    private Path findLuckPermsDirectory() {
        Logger.debug("Searching for LuckPerms in: %s", modsDir.toAbsolutePath());

        // Search directly in the mods directory
        Path found = searchForLuckPermsIn(modsDir);
        if (found != null) {
            Logger.info("Found LuckPerms directory: %s", found.toAbsolutePath());
            return found;
        }

        // Fallback: try plugins subfolder if it exists
        Path pluginsDir = modsDir.resolve("plugins");
        if (Files.isDirectory(pluginsDir)) {
            found = searchForLuckPermsIn(pluginsDir);
            if (found != null) {
                Logger.info("Found LuckPerms directory in plugins: %s", found.toAbsolutePath());
                return found;
            }
        }

        Logger.debug("LuckPerms directory not found");
        return null;
    }

    /**
     * Searches for a LuckPerms folder within the given directory.
     * Matches folders containing "luckperms" (case-insensitive).
     *
     * @param parentDir the directory to search in
     * @return the LuckPerms directory path, or null if not found
     */
    @Nullable
    private Path searchForLuckPermsIn(@NotNull Path parentDir) {
        if (!Files.isDirectory(parentDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(parentDir)) {
            return stream
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().toLowerCase().contains("luckperms"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            Logger.debug("Error searching for LuckPerms in %s: %s", parentDir, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if LuckPerms is installed.
     *
     * @return true if LuckPerms directory exists
     */
    public boolean isLuckPermsInstalled() {
        return luckPermsDir != null && Files.isDirectory(luckPermsDir);
    }
    
    /**
     * Gets the LuckPerms directory.
     *
     * @return the LuckPerms plugin directory, or null if not found
     */
    @Nullable
    public Path getLuckPermsDirectory() {
        return luckPermsDir;
    }
    
    /**
     * Detects the storage type by checking the config and existing data directories.
     *
     * @return the detected storage type
     */
    @NotNull
    public LuckPermsStorageType detectStorageType() {
        // First try to read from config.yml
        LuckPermsStorageType configType = readStorageTypeFromConfig();
        if (configType != null && configType != LuckPermsStorageType.UNKNOWN) {
            Logger.debug("LuckPerms storage type from config: %s", configType);
            return configType;
        }
        
        // Fall back to detecting from existing data
        return detectStorageTypeFromData();
    }
    
    /**
     * Reads the storage-method setting from LuckPerms config.yml.
     *
     * @return the configured storage type, or null if not found
     */
    @Nullable
    private LuckPermsStorageType readStorageTypeFromConfig() {
        if (luckPermsDir == null) {
            return null;
        }
        Path configFile = luckPermsDir.resolve("config.yml");
        if (!Files.exists(configFile)) {
            return null;
        }
        
        try {
            String content = Files.readString(configFile);
            Matcher matcher = STORAGE_METHOD_PATTERN.matcher(content);
            if (matcher.find()) {
                String method = matcher.group(1).toLowerCase();
                return parseStorageMethod(method);
            }
        } catch (IOException e) {
            Logger.debug("Could not read LuckPerms config.yml: %s", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Parses a storage method string to storage type.
     */
    @NotNull
    private LuckPermsStorageType parseStorageMethod(String method) {
        return switch (method) {
            case "yaml" -> LuckPermsStorageType.YAML;
            case "json" -> LuckPermsStorageType.JSON;
            case "hocon" -> LuckPermsStorageType.HOCON;
            case "toml" -> LuckPermsStorageType.TOML;
            case "h2" -> LuckPermsStorageType.H2;
            case "sqlite" -> LuckPermsStorageType.SQLITE;
            case "mysql", "mariadb" -> LuckPermsStorageType.MYSQL;
            case "postgresql" -> LuckPermsStorageType.POSTGRESQL;
            case "mongodb" -> LuckPermsStorageType.MONGODB;
            default -> LuckPermsStorageType.UNKNOWN;
        };
    }
    
    /**
     * Detects storage type by checking for existing data directories/files.
     *
     * @return the detected storage type
     */
    @NotNull
    private LuckPermsStorageType detectStorageTypeFromData() {
        if (luckPermsDir == null) {
            return LuckPermsStorageType.UNKNOWN;
        }

        // Check file-based storage directories
        if (Files.isDirectory(luckPermsDir.resolve("yaml-storage"))) {
            return LuckPermsStorageType.YAML;
        }
        if (Files.isDirectory(luckPermsDir.resolve("json-storage"))) {
            return LuckPermsStorageType.JSON;
        }
        if (Files.isDirectory(luckPermsDir.resolve("hocon-storage"))) {
            return LuckPermsStorageType.HOCON;
        }
        if (Files.isDirectory(luckPermsDir.resolve("toml-storage"))) {
            return LuckPermsStorageType.TOML;
        }

        // Check embedded database files (search for any luckperms database file)
        Path h2Db = findDatabaseFile(".mv.db");  // H2 uses .mv.db extension
        if (h2Db != null) {
            Logger.debug("Found H2 database: %s", h2Db.getFileName());
            return LuckPermsStorageType.H2;
        }

        Path sqliteDb = findDatabaseFile(".db");  // SQLite uses .db extension
        if (sqliteDb != null && !sqliteDb.toString().endsWith(".mv.db")) {
            Logger.debug("Found SQLite database: %s", sqliteDb.getFileName());
            return LuckPermsStorageType.SQLITE;
        }

        // Cannot detect remote databases without config
        return LuckPermsStorageType.UNKNOWN;
    }

    /**
     * Finds a LuckPerms database file with the given extension.
     * Searches for files starting with "luckperms" and ending with the extension.
     *
     * @param extension the file extension to search for (e.g., ".mv.db", ".db")
     * @return the database file path, or null if not found
     */
    @Nullable
    private Path findDatabaseFile(@NotNull String extension) {
        if (luckPermsDir == null) {
            return null;
        }

        try (Stream<Path> stream = Files.list(luckPermsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.startsWith("luckperms") && name.endsWith(extension.toLowerCase());
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            Logger.debug("Error searching for database files: %s", e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the storage path for file-based or embedded storage.
     *
     * @param type the storage type
     * @return the storage path, or null for remote databases or if LuckPerms not found
     */
    @Nullable
    public Path getStoragePath(@NotNull LuckPermsStorageType type) {
        if (luckPermsDir == null) {
            return null;
        }
        return switch (type) {
            case YAML -> luckPermsDir.resolve("yaml-storage");
            case JSON -> luckPermsDir.resolve("json-storage");
            case HOCON -> luckPermsDir.resolve("hocon-storage");
            case TOML -> luckPermsDir.resolve("toml-storage");
            case H2 -> findDatabaseFile(".mv.db");
            case SQLITE -> findDatabaseFile(".db");
            default -> null;
        };
    }
    
    /**
     * Reads MySQL/MariaDB connection details from config.yml.
     *
     * @return connection details map, or null if not found
     */
    @Nullable
    public Map<String, String> readMySqlConnectionDetails() {
        if (luckPermsDir == null) {
            return null;
        }
        Path configFile = luckPermsDir.resolve("config.yml");
        if (!Files.exists(configFile)) {
            return null;
        }
        
        try {
            String content = Files.readString(configFile);
            
            // Simple regex-based parsing for common config values
            String address = extractConfigValue(content, "address");
            String database = extractConfigValue(content, "database");
            String username = extractConfigValue(content, "username");
            String password = extractConfigValue(content, "password");
            String tablePrefix = extractConfigValue(content, "table-prefix");
            
            if (address != null && database != null) {
                return Map.of(
                    "address", address,
                    "database", database,
                    "username", username != null ? username : "root",
                    "password", password != null ? password : "",
                    "table_prefix", tablePrefix != null ? tablePrefix : "luckperms_"
                );
            }
        } catch (IOException e) {
            Logger.debug("Could not read MySQL config: %s", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extracts a simple config value (handles both quoted and unquoted).
     */
    @Nullable
    private String extractConfigValue(String content, String key) {
        Pattern pattern = Pattern.compile(
            key + ":\\s*['\"]?([^'\"\\n]+)['\"]?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * Creates a storage reader for the detected storage type.
     *
     * @return a storage reader, or null if storage cannot be read
     */
    @Nullable
    public LuckPermsStorageReader createReader() {
        LuckPermsStorageType type = detectStorageType();
        return createReader(type);
    }
    
    /**
     * Creates a storage reader for the specified storage type.
     *
     * @param type the storage type
     * @return a storage reader, or null if storage cannot be read
     */
    @Nullable
    public LuckPermsStorageReader createReader(@NotNull LuckPermsStorageType type) {
        Path storagePath = getStoragePath(type);
        
        return switch (type) {
            case YAML -> new YamlStorageReader(storagePath);
            case JSON -> new JsonStorageReader(storagePath);
            case H2 -> new H2StorageReader(storagePath, luckPermsDir);
            case MYSQL, POSTGRESQL -> {
                Map<String, String> connectionDetails = readMySqlConnectionDetails();
                if (connectionDetails != null) {
                    yield new SqlStorageReader(type, connectionDetails);
                }
                yield null;
            }
            default -> {
                Logger.warn("Unsupported LuckPerms storage type: %s", type);
                yield null;
            }
        };
    }
}
