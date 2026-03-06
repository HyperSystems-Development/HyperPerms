package com.hyperperms.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Core plugin configuration: storage, defaults, server identity, backup, tasks, verbose, console, templates, updates.
 * <p>
 * File: {@code config.json}
 */
public final class CoreConfig extends ConfigFile {

    // Storage
    private String storageType;
    private String jsonDirectory;
    private boolean jsonPrettyPrint;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolSize;

    // Defaults
    private String defaultGroup;
    private boolean createDefaultGroup;
    private String defaultPrefix;
    private String defaultSuffix;

    // Server
    private String serverName;

    // Backup
    private boolean autoBackup;
    private int maxBackups;
    private boolean backupOnSave;
    private int backupIntervalSeconds;

    // Tasks
    private int expiryCheckIntervalSeconds;
    private int autoSaveIntervalSeconds;

    // Verbose
    private boolean verboseEnabledByDefault;
    private boolean verboseLogToConsole;

    // Console
    private boolean clickableLinks;
    private boolean forceOsc8;

    // Templates
    private String templatesCustomDirectory;

    // Updates
    private boolean updateCheckEnabled;
    private String updateCheckUrl;
    private boolean updateShowChangelog;

    public CoreConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("config.json"));
    }

    @Override
    protected void createDefaults() {
        storageType = "json";
        jsonDirectory = "data";
        jsonPrettyPrint = true;
        sqliteFile = "hyperperms.db";
        mysqlHost = "localhost";
        mysqlPort = 3306;
        mysqlDatabase = "hyperperms";
        mysqlUsername = "root";
        mysqlPassword = "";
        mysqlPoolSize = 10;
        defaultGroup = "default";
        createDefaultGroup = true;
        defaultPrefix = "&7";
        defaultSuffix = "";
        serverName = "";
        autoBackup = true;
        maxBackups = 10;
        backupOnSave = false;
        backupIntervalSeconds = 3600;
        expiryCheckIntervalSeconds = 60;
        autoSaveIntervalSeconds = 300;
        verboseEnabledByDefault = false;
        verboseLogToConsole = true;
        clickableLinks = true;
        forceOsc8 = false;
        templatesCustomDirectory = "templates";
        updateCheckEnabled = true;
        updateCheckUrl = "https://api.github.com/repos/HyperSystems-Development/HyperPerms/releases/latest";
        updateShowChangelog = true;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        // Storage
        JsonObject storage = getSection(root, "storage");
        storageType = getString(storage, "type", "json");
        JsonObject json = getSection(storage, "json");
        jsonDirectory = getString(json, "directory", "data");
        jsonPrettyPrint = getBool(json, "prettyPrint", true);
        JsonObject sqlite = getSection(storage, "sqlite");
        sqliteFile = getString(sqlite, "file", "hyperperms.db");
        JsonObject mysql = getSection(storage, "mysql");
        mysqlHost = getString(mysql, "host", "localhost");
        mysqlPort = getInt(mysql, "port", 3306);
        mysqlDatabase = getString(mysql, "database", "hyperperms");
        mysqlUsername = getString(mysql, "username", "root");
        mysqlPassword = getString(mysql, "password", "");
        mysqlPoolSize = getInt(mysql, "poolSize", 10);

        // Defaults
        JsonObject defaults = getSection(root, "defaults");
        defaultGroup = getString(defaults, "group", "default");
        createDefaultGroup = getBool(defaults, "createDefaultGroup", true);
        defaultPrefix = getString(defaults, "prefix", "&7");
        defaultSuffix = getString(defaults, "suffix", "");

        // Server
        JsonObject server = getSection(root, "server");
        serverName = getString(server, "name", "");

        // Backup
        JsonObject backup = getSection(root, "backup");
        autoBackup = getBool(backup, "autoBackup", true);
        maxBackups = getInt(backup, "maxBackups", 10);
        backupOnSave = getBool(backup, "backupOnSave", false);
        backupIntervalSeconds = getInt(backup, "intervalSeconds", 3600);

        // Tasks
        JsonObject tasks = getSection(root, "tasks");
        expiryCheckIntervalSeconds = getInt(tasks, "expiryCheckIntervalSeconds", 60);
        autoSaveIntervalSeconds = getInt(tasks, "autoSaveIntervalSeconds", 300);

        // Verbose
        JsonObject verbose = getSection(root, "verbose");
        verboseEnabledByDefault = getBool(verbose, "enabledByDefault", false);
        verboseLogToConsole = getBool(verbose, "logToConsole", true);

        // Console
        JsonObject console = getSection(root, "console");
        clickableLinks = getBool(console, "clickableLinks", true);
        forceOsc8 = getBool(console, "forceOsc8", false);

        // Templates
        JsonObject templates = getSection(root, "templates");
        templatesCustomDirectory = getString(templates, "customDirectory", "templates");

        // Updates
        JsonObject updates = getSection(root, "updates");
        updateCheckEnabled = getBool(updates, "enabled", true);
        updateCheckUrl = getString(updates, "checkUrl",
                "https://api.github.com/repos/HyperSystems-Development/HyperPerms/releases/latest");
        updateShowChangelog = getBool(updates, "showChangelog", true);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();

        // Storage
        JsonObject storage = new JsonObject();
        storage.addProperty("type", storageType);
        JsonObject json = new JsonObject();
        json.addProperty("directory", jsonDirectory);
        json.addProperty("prettyPrint", jsonPrettyPrint);
        storage.add("json", json);
        JsonObject sqlite = new JsonObject();
        sqlite.addProperty("file", sqliteFile);
        storage.add("sqlite", sqlite);
        JsonObject mysql = new JsonObject();
        mysql.addProperty("host", mysqlHost);
        mysql.addProperty("port", mysqlPort);
        mysql.addProperty("database", mysqlDatabase);
        mysql.addProperty("username", mysqlUsername);
        mysql.addProperty("password", mysqlPassword);
        mysql.addProperty("poolSize", mysqlPoolSize);
        storage.add("mysql", mysql);
        root.add("storage", storage);

        // Defaults
        JsonObject defaults = new JsonObject();
        defaults.addProperty("group", defaultGroup);
        defaults.addProperty("createDefaultGroup", createDefaultGroup);
        defaults.addProperty("prefix", defaultPrefix);
        defaults.addProperty("suffix", defaultSuffix);
        root.add("defaults", defaults);

        // Server
        JsonObject server = new JsonObject();
        server.addProperty("name", serverName);
        root.add("server", server);

        // Backup
        JsonObject backup = new JsonObject();
        backup.addProperty("autoBackup", autoBackup);
        backup.addProperty("maxBackups", maxBackups);
        backup.addProperty("backupOnSave", backupOnSave);
        backup.addProperty("intervalSeconds", backupIntervalSeconds);
        root.add("backup", backup);

        // Tasks
        JsonObject tasks = new JsonObject();
        tasks.addProperty("expiryCheckIntervalSeconds", expiryCheckIntervalSeconds);
        tasks.addProperty("autoSaveIntervalSeconds", autoSaveIntervalSeconds);
        root.add("tasks", tasks);

        // Verbose
        JsonObject verbose = new JsonObject();
        verbose.addProperty("enabledByDefault", verboseEnabledByDefault);
        verbose.addProperty("logToConsole", verboseLogToConsole);
        root.add("verbose", verbose);

        // Console
        JsonObject console = new JsonObject();
        console.addProperty("clickableLinks", clickableLinks);
        console.addProperty("forceOsc8", forceOsc8);
        root.add("console", console);

        // Templates
        JsonObject templates = new JsonObject();
        templates.addProperty("customDirectory", templatesCustomDirectory);
        root.add("templates", templates);

        // Updates
        JsonObject updates = new JsonObject();
        updates.addProperty("enabled", updateCheckEnabled);
        updates.addProperty("checkUrl", updateCheckUrl);
        updates.addProperty("showChangelog", updateShowChangelog);
        root.add("updates", updates);

        return root;
    }

    // === Getters ===

    @NotNull public String getStorageType() { return storageType; }
    @NotNull public String getJsonDirectory() { return jsonDirectory; }
    public boolean isJsonPrettyPrint() { return jsonPrettyPrint; }
    @NotNull public String getSqliteFile() { return sqliteFile; }
    @NotNull public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    @NotNull public String getMysqlDatabase() { return mysqlDatabase; }
    @NotNull public String getMysqlUsername() { return mysqlUsername; }
    @NotNull public String getMysqlPassword() { return mysqlPassword; }
    public int getMysqlPoolSize() { return mysqlPoolSize; }
    @NotNull public String getDefaultGroup() { return defaultGroup; }
    public boolean shouldCreateDefaultGroup() { return createDefaultGroup; }
    @NotNull public String getDefaultPrefix() { return defaultPrefix; }
    @NotNull public String getDefaultSuffix() { return defaultSuffix; }
    @NotNull public String getServerName() { return serverName; }
    public boolean isAutoBackupEnabled() { return autoBackup; }
    public int getMaxBackups() { return maxBackups; }
    public boolean isBackupOnSave() { return backupOnSave; }
    public int getBackupIntervalSeconds() { return backupIntervalSeconds; }
    public int getExpiryCheckInterval() { return expiryCheckIntervalSeconds; }
    public int getAutoSaveInterval() { return autoSaveIntervalSeconds; }
    public boolean isVerboseEnabledByDefault() { return verboseEnabledByDefault; }
    public boolean shouldLogVerboseToConsole() { return verboseLogToConsole; }
    public boolean isClickableLinksEnabled() { return clickableLinks; }
    public boolean isForceOsc8() { return forceOsc8; }
    @NotNull public String getTemplatesCustomDirectory() { return templatesCustomDirectory; }
    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    @NotNull public String getUpdateCheckUrl() { return updateCheckUrl; }
    public boolean isUpdateShowChangelog() { return updateShowChangelog; }
}
