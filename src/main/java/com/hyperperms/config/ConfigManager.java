package com.hyperperms.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Central configuration manager for HyperPerms.
 * <p>
 * Manages loading, reloading, and saving of all modular config files.
 * Handles migration from the old monolithic {@code config.json} to the split format.
 * <p>
 * Access via singleton: {@code ConfigManager.get()}
 */
public final class ConfigManager {

    private static ConfigManager instance;

    private final Path dataDirectory;
    private final CoreConfig core;
    private final CacheConfig cache;
    private final ChatConfig chat;
    private final IntegrationConfig integration;
    private final WebEditorConfig webEditor;
    private final DebugConfig debug;

    public ConfigManager(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.core = new CoreConfig(dataDirectory);
        this.cache = new CacheConfig(dataDirectory);
        this.chat = new ChatConfig(dataDirectory);
        this.integration = new IntegrationConfig(dataDirectory);
        this.webEditor = new WebEditorConfig(dataDirectory);
        this.debug = new DebugConfig(dataDirectory);
        instance = this;
    }

    /**
     * Gets the singleton instance.
     *
     * @return the config manager
     * @throws IllegalStateException if not yet initialized
     */
    @NotNull
    public static ConfigManager get() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager not initialized");
        }
        return instance;
    }

    /**
     * Loads all configuration files.
     * Automatically migrates from old monolithic format if detected.
     */
    public void loadAll() {
        // Check for old monolithic config and migrate if needed
        migrateFromLegacy();

        core.load();
        cache.load();
        chat.load();
        integration.load();
        webEditor.load();
        debug.load();

        // Apply debug config to Logger
        debug.applyToLogger();

        Logger.info("Configuration loaded (%d modules)", 6);
    }

    /**
     * Reloads all configuration files from disk.
     */
    public void reloadAll() {
        core.reload();
        cache.reload();
        chat.reload();
        integration.reload();
        webEditor.reload();
        debug.reload();
        debug.applyToLogger();

        Logger.info("Configuration reloaded");
    }

    /**
     * Saves all configuration files.
     */
    public void saveAll() {
        core.save();
        cache.save();
        chat.save();
        integration.save();
        webEditor.save();
        debug.save();
    }

    /**
     * Validates all configs and logs issues.
     */
    public void validateAll() {
        core.validateAndLog();
        cache.validateAndLog();
        chat.validateAndLog();
        integration.validateAndLog();
        webEditor.validateAndLog();
    }

    // === Module Accessors ===

    @NotNull public CoreConfig core() { return core; }
    @NotNull public CacheConfig cache() { return cache; }
    @NotNull public ChatConfig chat() { return chat; }
    @NotNull public IntegrationConfig integration() { return integration; }
    @NotNull public WebEditorConfig webEditor() { return webEditor; }
    @NotNull public DebugConfig debug() { return debug; }

    // ==================== Legacy Migration ====================

    /**
     * Detects and migrates the old monolithic config.json to split format.
     * <p>
     * Detection: if config.json exists AND cache.json does NOT exist,
     * the old format is in use. We split it into the new modular files.
     */
    private void migrateFromLegacy() {
        Path oldConfig = dataDirectory.resolve("config.json");
        Path cacheFile = dataDirectory.resolve("cache.json");

        // Only migrate if old config exists and new files don't
        if (!Files.exists(oldConfig) || Files.exists(cacheFile)) {
            return;
        }

        Logger.info("[Config] Detected legacy monolithic config.json — migrating to split format...");

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = Files.readString(oldConfig);
            JsonObject old = JsonParser.parseString(json).getAsJsonObject();

            // The old config.json contained all settings in one file.
            // CoreConfig will naturally load from config.json and pick up its sections.
            // We need to extract other sections into their respective files.

            // Extract cache section
            if (old.has("cache") && old.get("cache").isJsonObject()) {
                JsonObject cacheObj = old.getAsJsonObject("cache");
                Files.writeString(cacheFile, gson.toJson(cacheObj));
                Logger.info("[Config] Migrated: cache.json");
            }

            // Extract chat + tabList sections
            Path chatFile = dataDirectory.resolve("chat.json");
            if (old.has("chat") && old.get("chat").isJsonObject()) {
                JsonObject chatObj = old.getAsJsonObject("chat");
                if (old.has("tabList") && old.get("tabList").isJsonObject()) {
                    chatObj.add("tabList", old.getAsJsonObject("tabList"));
                }
                Files.writeString(chatFile, gson.toJson(chatObj));
                Logger.info("[Config] Migrated: chat.json");
            }

            // Extract integration sections
            Path intFile = dataDirectory.resolve("integrations.json");
            JsonObject intObj = new JsonObject();
            copySection(old, intObj, "factions");
            copySection(old, intObj, "werchat");
            copySection(old, intObj, "vault");
            copySection(old, intObj, "placeholderapi");
            copySection(old, intObj, "mysticnametags");
            copySection(old, intObj, "analytics");
            if (intObj.size() > 0) {
                Files.writeString(intFile, gson.toJson(intObj));
                Logger.info("[Config] Migrated: integrations.json");
            }

            // Extract web editor section
            Path webFile = dataDirectory.resolve("webeditor.json");
            if (old.has("webEditor") && old.get("webEditor").isJsonObject()) {
                Files.writeString(webFile, gson.toJson(old.getAsJsonObject("webEditor")));
                Logger.info("[Config] Migrated: webeditor.json");
            }

            // Create empty debug config
            Path debugFile = dataDirectory.resolve("debug.json");
            Files.writeString(debugFile, "{}");

            // Backup old config
            Path backup = dataDirectory.resolve("config.json.v1.bak");
            if (!Files.exists(backup)) {
                Files.copy(oldConfig, backup);
                Logger.info("[Config] Old config backed up to config.json.v1.bak");
            }

            Logger.info("[Config] Migration complete! Old config.json is now CoreConfig only.");

        } catch (IOException e) {
            Logger.severe("[Config] Migration failed: %s", e.getMessage());
            Logger.severe("[Config] Falling back to default configuration");
        }
    }

    private static void copySection(@NotNull JsonObject source, @NotNull JsonObject target,
                                    @NotNull String key) {
        if (source.has(key) && source.get(key).isJsonObject()) {
            target.add(key, source.getAsJsonObject(key));
        }
    }

    // ==================== Backward-Compatible Convenience Getters ====================
    // These delegate to the appropriate config module, making it easier to migrate
    // existing code gradually.

    @NotNull public String getStorageType() { return core.getStorageType(); }
    @NotNull public String getJsonDirectory() { return core.getJsonDirectory(); }
    @NotNull public String getSqliteFile() { return core.getSqliteFile(); }
    @NotNull public String getMysqlHost() { return core.getMysqlHost(); }
    public int getMysqlPort() { return core.getMysqlPort(); }
    @NotNull public String getMysqlDatabase() { return core.getMysqlDatabase(); }
    @NotNull public String getMysqlUsername() { return core.getMysqlUsername(); }
    @NotNull public String getMysqlPassword() { return core.getMysqlPassword(); }
    public int getMysqlPoolSize() { return core.getMysqlPoolSize(); }
    public boolean isCacheEnabled() { return cache.isEnabled(); }
    public int getCacheExpirySeconds() { return cache.getExpirySeconds(); }
    public int getCacheMaxSize() { return cache.getMaxSize(); }
    @NotNull public String getDefaultGroup() { return core.getDefaultGroup(); }
    public boolean shouldCreateDefaultGroup() { return core.shouldCreateDefaultGroup(); }
    @NotNull public String getDefaultPrefix() { return core.getDefaultPrefix(); }
    @NotNull public String getDefaultSuffix() { return core.getDefaultSuffix(); }
    public boolean isChatEnabled() { return chat.isEnabled(); }
    @NotNull public String getChatFormat() { return chat.getFormat(); }
    public boolean isAllowPlayerColors() { return chat.isAllowPlayerColors(); }
    @NotNull public String getColorPermission() { return chat.getColorPermission(); }
    public boolean isAutoBackupEnabled() { return core.isAutoBackupEnabled(); }
    public int getMaxBackups() { return core.getMaxBackups(); }
    public boolean isBackupOnSave() { return core.isBackupOnSave(); }
    public int getAutoBackupIntervalSeconds() { return core.getBackupIntervalSeconds(); }
    public int getExpiryCheckInterval() { return core.getExpiryCheckInterval(); }
    public int getAutoSaveInterval() { return core.getAutoSaveInterval(); }
    public boolean isVerboseEnabledByDefault() { return core.isVerboseEnabledByDefault(); }
    public boolean shouldLogVerboseToConsole() { return core.shouldLogVerboseToConsole(); }
    @NotNull public String getServerName() { return core.getServerName(); }
    @NotNull public String getWebEditorUrl() { return webEditor.getUrl(); }
    @NotNull public String getWebEditorApiUrl() { return webEditor.getApiUrl(); }
    public int getWebEditorTimeoutSeconds() { return webEditor.getTimeoutSeconds(); }
    public boolean isFactionIntegrationEnabled() { return integration.isFactionIntegrationEnabled(); }
    public String getFactionNoFactionDefault() { return integration.getFactionNoFactionDefault(); }
    public String getFactionNoRankDefault() { return integration.getFactionNoRankDefault(); }
    public String getFactionFormat() { return integration.getFactionFormat(); }
    public boolean isFactionPrefixEnabled() { return integration.isFactionPrefixEnabled(); }
    public String getFactionPrefixFormat() { return integration.getFactionPrefixFormat(); }
    public boolean isFactionShowRank() { return integration.isFactionShowRank(); }
    public String getFactionPrefixWithRankFormat() { return integration.getFactionPrefixWithRankFormat(); }
    public boolean isWerChatIntegrationEnabled() { return integration.isWerChatIntegrationEnabled(); }
    public String getWerChatNoChannelDefault() { return integration.getWerChatNoChannelDefault(); }
    public String getWerChatChannelFormat() { return integration.getWerChatChannelFormat(); }
    public boolean isVaultIntegrationEnabled() { return integration.isVaultIntegrationEnabled(); }
    public boolean isUpdateCheckEnabled() { return core.isUpdateCheckEnabled(); }
    @NotNull public String getUpdateCheckUrl() { return core.getUpdateCheckUrl(); }
    public boolean isUpdateChangelogEnabled() { return core.isUpdateShowChangelog(); }
    public boolean isTabListEnabled() { return chat.isTabListEnabled(); }
    @NotNull public String getTabListFormat() { return chat.getTabListFormat(); }
    public boolean isTabListSortByWeight() { return chat.isTabListSortByWeight(); }
    public int getTabListUpdateIntervalTicks() { return chat.getTabListUpdateIntervalTicks(); }
    public boolean isConsoleClickableLinksEnabled() { return core.isClickableLinksEnabled(); }
    public boolean isConsoleForceOsc8() { return core.isForceOsc8(); }
    @NotNull public String getTemplatesCustomDirectory() { return core.getTemplatesCustomDirectory(); }
    public boolean isAnalyticsEnabled() { return integration.isAnalyticsEnabled(); }
    public boolean isAnalyticsTrackChecks() { return integration.isAnalyticsTrackChecks(); }
    public boolean isAnalyticsTrackChanges() { return integration.isAnalyticsTrackChanges(); }
    public int getAnalyticsFlushIntervalSeconds() { return integration.getAnalyticsFlushIntervalSeconds(); }
    public int getAnalyticsRetentionDays() { return integration.getAnalyticsRetentionDays(); }
    public boolean isPlaceholderAPIEnabled() { return integration.isPlaceholderAPIEnabled(); }
    public boolean isPlaceholderAPIParseExternal() { return integration.isPlaceholderAPIParseExternal(); }
    public boolean isMysticNameTagsEnabled() { return integration.isMysticNameTagsEnabled(); }
    public boolean isMysticNameTagsRefreshOnPermissionChange() { return integration.isMysticNameTagsRefreshOnPermissionChange(); }
    public boolean isMysticNameTagsRefreshOnGroupChange() { return integration.isMysticNameTagsRefreshOnGroupChange(); }
    @NotNull public String getMysticNameTagsPermissionPrefix() { return integration.getMysticNameTagsPermissionPrefix(); }
}
