package com.hyperperms.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Integration settings for external plugins (factions, werchat, vault, PAPI, nametags).
 * <p>
 * File: {@code integrations.json}
 */
public final class IntegrationConfig extends ConfigFile {

    // Factions
    private boolean factionsEnabled;
    private String factionsNoFactionDefault;
    private String factionsNoRankDefault;
    private String factionsFormat;
    private boolean factionsPrefixEnabled;
    private String factionsPrefixFormat;
    private boolean factionsShowRank;
    private String factionsPrefixWithRankFormat;

    // WerChat
    private boolean werchatEnabled;
    private String werchatNoChannelDefault;
    private String werchatChannelFormat;

    // VaultUnlocked
    private boolean vaultEnabled;

    // PlaceholderAPI
    private boolean papiEnabled;
    private boolean papiParseExternal;

    // MysticNameTags
    private boolean mysticNameTagsEnabled;
    private boolean mysticRefreshOnPermChange;
    private boolean mysticRefreshOnGroupChange;
    private String mysticTagPermissionPrefix;

    // Analytics
    private boolean analyticsEnabled;
    private boolean analyticsTrackChecks;
    private boolean analyticsTrackChanges;
    private int analyticsFlushIntervalSeconds;
    private int analyticsRetentionDays;

    public IntegrationConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("integrations.json"));
    }

    @Override
    protected void createDefaults() {
        factionsEnabled = true;
        factionsNoFactionDefault = "";
        factionsNoRankDefault = "";
        factionsFormat = "%s";
        factionsPrefixEnabled = true;
        factionsPrefixFormat = "&7[&b%s&7] ";
        factionsShowRank = false;
        factionsPrefixWithRankFormat = "&7[&b%s&7|&e%r&7] ";
        werchatEnabled = true;
        werchatNoChannelDefault = "";
        werchatChannelFormat = "%s";
        vaultEnabled = true;
        papiEnabled = true;
        papiParseExternal = true;
        mysticNameTagsEnabled = true;
        mysticRefreshOnPermChange = true;
        mysticRefreshOnGroupChange = true;
        mysticTagPermissionPrefix = "mysticnametags.tag.";
        analyticsEnabled = false;
        analyticsTrackChecks = true;
        analyticsTrackChanges = true;
        analyticsFlushIntervalSeconds = 60;
        analyticsRetentionDays = 90;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        // Factions
        JsonObject factions = getSection(root, "factions");
        factionsEnabled = getBool(factions, "enabled", true);
        factionsNoFactionDefault = getString(factions, "noFactionDefault", "");
        factionsNoRankDefault = getString(factions, "noRankDefault", "");
        factionsFormat = getString(factions, "format", "%s");
        factionsPrefixEnabled = getBool(factions, "prefixEnabled", true);
        factionsPrefixFormat = getString(factions, "prefixFormat", "&7[&b%s&7] ");
        factionsShowRank = getBool(factions, "showRank", false);
        factionsPrefixWithRankFormat = getString(factions, "prefixWithRankFormat", "&7[&b%s&7|&e%r&7] ");

        // WerChat
        JsonObject werchat = getSection(root, "werchat");
        werchatEnabled = getBool(werchat, "enabled", true);
        werchatNoChannelDefault = getString(werchat, "noChannelDefault", "");
        werchatChannelFormat = getString(werchat, "channelFormat", "%s");

        // Vault
        JsonObject vault = getSection(root, "vault");
        vaultEnabled = getBool(vault, "enabled", true);

        // PlaceholderAPI
        JsonObject papi = getSection(root, "placeholderapi");
        papiEnabled = getBool(papi, "enabled", true);
        papiParseExternal = getBool(papi, "parseExternal", true);

        // MysticNameTags
        JsonObject mystic = getSection(root, "mysticnametags");
        mysticNameTagsEnabled = getBool(mystic, "enabled", true);
        mysticRefreshOnPermChange = getBool(mystic, "refreshOnPermissionChange", true);
        mysticRefreshOnGroupChange = getBool(mystic, "refreshOnGroupChange", true);
        mysticTagPermissionPrefix = getString(mystic, "tagPermissionPrefix", "mysticnametags.tag.");

        // Analytics
        JsonObject analytics = getSection(root, "analytics");
        analyticsEnabled = getBool(analytics, "enabled", false);
        analyticsTrackChecks = getBool(analytics, "trackChecks", true);
        analyticsTrackChanges = getBool(analytics, "trackChanges", true);
        analyticsFlushIntervalSeconds = getInt(analytics, "flushIntervalSeconds", 60);
        analyticsRetentionDays = getInt(analytics, "retentionDays", 90);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();

        JsonObject factions = new JsonObject();
        factions.addProperty("enabled", factionsEnabled);
        factions.addProperty("noFactionDefault", factionsNoFactionDefault);
        factions.addProperty("noRankDefault", factionsNoRankDefault);
        factions.addProperty("format", factionsFormat);
        factions.addProperty("prefixEnabled", factionsPrefixEnabled);
        factions.addProperty("prefixFormat", factionsPrefixFormat);
        factions.addProperty("showRank", factionsShowRank);
        factions.addProperty("prefixWithRankFormat", factionsPrefixWithRankFormat);
        root.add("factions", factions);

        JsonObject werchat = new JsonObject();
        werchat.addProperty("enabled", werchatEnabled);
        werchat.addProperty("noChannelDefault", werchatNoChannelDefault);
        werchat.addProperty("channelFormat", werchatChannelFormat);
        root.add("werchat", werchat);

        JsonObject vault = new JsonObject();
        vault.addProperty("enabled", vaultEnabled);
        root.add("vault", vault);

        JsonObject papi = new JsonObject();
        papi.addProperty("enabled", papiEnabled);
        papi.addProperty("parseExternal", papiParseExternal);
        root.add("placeholderapi", papi);

        JsonObject mystic = new JsonObject();
        mystic.addProperty("enabled", mysticNameTagsEnabled);
        mystic.addProperty("refreshOnPermissionChange", mysticRefreshOnPermChange);
        mystic.addProperty("refreshOnGroupChange", mysticRefreshOnGroupChange);
        mystic.addProperty("tagPermissionPrefix", mysticTagPermissionPrefix);
        root.add("mysticnametags", mystic);

        JsonObject analytics = new JsonObject();
        analytics.addProperty("enabled", analyticsEnabled);
        analytics.addProperty("trackChecks", analyticsTrackChecks);
        analytics.addProperty("trackChanges", analyticsTrackChanges);
        analytics.addProperty("flushIntervalSeconds", analyticsFlushIntervalSeconds);
        analytics.addProperty("retentionDays", analyticsRetentionDays);
        root.add("analytics", analytics);

        return root;
    }

    // === Factions Getters ===
    public boolean isFactionIntegrationEnabled() { return factionsEnabled; }
    @NotNull public String getFactionNoFactionDefault() { return factionsNoFactionDefault; }
    @NotNull public String getFactionNoRankDefault() { return factionsNoRankDefault; }
    @NotNull public String getFactionFormat() { return factionsFormat; }
    public boolean isFactionPrefixEnabled() { return factionsPrefixEnabled; }
    @NotNull public String getFactionPrefixFormat() { return factionsPrefixFormat; }
    public boolean isFactionShowRank() { return factionsShowRank; }
    @NotNull public String getFactionPrefixWithRankFormat() { return factionsPrefixWithRankFormat; }

    // === WerChat Getters ===
    public boolean isWerChatIntegrationEnabled() { return werchatEnabled; }
    @NotNull public String getWerChatNoChannelDefault() { return werchatNoChannelDefault; }
    @NotNull public String getWerChatChannelFormat() { return werchatChannelFormat; }

    // === Vault Getters ===
    public boolean isVaultIntegrationEnabled() { return vaultEnabled; }

    // === PlaceholderAPI Getters ===
    public boolean isPlaceholderAPIEnabled() { return papiEnabled; }
    public boolean isPlaceholderAPIParseExternal() { return papiParseExternal; }

    // === MysticNameTags Getters ===
    public boolean isMysticNameTagsEnabled() { return mysticNameTagsEnabled; }
    public boolean isMysticNameTagsRefreshOnPermissionChange() { return mysticRefreshOnPermChange; }
    public boolean isMysticNameTagsRefreshOnGroupChange() { return mysticRefreshOnGroupChange; }
    @NotNull public String getMysticNameTagsPermissionPrefix() { return mysticTagPermissionPrefix; }

    // === Analytics Getters ===
    public boolean isAnalyticsEnabled() { return analyticsEnabled; }
    public boolean isAnalyticsTrackChecks() { return analyticsTrackChecks; }
    public boolean isAnalyticsTrackChanges() { return analyticsTrackChanges; }
    public int getAnalyticsFlushIntervalSeconds() { return analyticsFlushIntervalSeconds; }
    public int getAnalyticsRetentionDays() { return analyticsRetentionDays; }
}
