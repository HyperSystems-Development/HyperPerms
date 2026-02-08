package com.hyperperms.integration;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionIntegration {

    private final HyperPerms plugin;
    private final boolean factionPluginAvailable;
    private final FactionProvider provider;
    private final String detectedPlugin;

    // Cache for faction data to avoid repeated lookups
    private final Map<UUID, CachedFactionData> factionCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000; // 5 second cache

    // Config options
    private boolean enabled = true;
    private String noFactionDefault = "";
    private String noRankDefault = "";
    private String factionFormat = "%s"; // Just the name by default

    // Prefix config options
    private boolean prefixEnabled = true;
    private String prefixFormat = "&7[&b%s&7] ";           // %s = faction name
    private boolean showRank = false;
    private String prefixWithRankFormat = "&7[&b%s&7|&e%r&7] "; // %s = faction, %r = rank

    public FactionIntegration(@NotNull HyperPerms plugin) {
        this.plugin = plugin;

        // Check for HyperFactions first (preferred), then HyFactions (legacy)
        if (checkHyperFactionsAvailable()) {
            this.provider = createHyperFactionsProvider();
            this.factionPluginAvailable = provider != null;
            this.detectedPlugin = "HyperFactions";
            if (factionPluginAvailable) {
                Logger.info("HyperFactions integration enabled - faction placeholders available");
            }
        } else if (checkHyFactionsAvailable()) {
            this.provider = createHyFactionsProvider();
            this.factionPluginAvailable = provider != null;
            this.detectedPlugin = "HyFactions";
            if (factionPluginAvailable) {
                Logger.info("HyFactions integration enabled - faction placeholders available");
            }
        } else {
            this.provider = null;
            this.factionPluginAvailable = false;
            this.detectedPlugin = null;
        }
    }

    /**
     * Checks if HyperFactions plugin classes are available.
     */
    private boolean checkHyperFactionsAvailable() {
        try {
            Class.forName("com.hyperfactions.api.HyperFactionsAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if HyFactions plugin classes are available.
     */
    private boolean checkHyFactionsAvailable() {
        try {
            Class.forName("com.kaws.hyfaction.claim.ClaimManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Creates a reflection-based provider for HyperFactions.
     * Uses reflection to avoid compile-time dependency on HyperFactions.
     */
    @Nullable
    private FactionProvider createHyperFactionsProvider() {
        try {
            return new ReflectiveHyperFactionsProvider();
        } catch (Exception e) {
            Logger.warn("Failed to initialize HyperFactions reflection provider: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Creates a reflection-based provider for HyFactions (legacy).
     * Uses reflection to avoid compile-time dependency on HyFactions.
     */
    @Nullable
    private FactionProvider createHyFactionsProvider() {
        try {
            return new ReflectiveHyFactionsProvider();
        } catch (Exception e) {
            Logger.warn("Failed to initialize HyFactions reflection provider: %s", e.getMessage());
            return null;
        }
    }

    /**
     * @return true if a faction plugin is available and integration is enabled
     */
    public boolean isAvailable() {
        return factionPluginAvailable && enabled && provider != null;
    }

    /**
     * @return true if a faction plugin JAR is present (regardless of enabled state)
     */
    public boolean isHyFactionsInstalled() {
        return factionPluginAvailable;
    }

    /**
     * @return the name of the detected faction plugin, or null if none
     */
    @Nullable
    public String getDetectedPlugin() {
        return detectedPlugin;
    }

    // ==================== Configuration ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setNoFactionDefault(@NotNull String noFactionDefault) {
        this.noFactionDefault = noFactionDefault;
    }

    @NotNull
    public String getNoFactionDefault() {
        return noFactionDefault;
    }

    public void setNoRankDefault(@NotNull String noRankDefault) {
        this.noRankDefault = noRankDefault;
    }

    @NotNull
    public String getNoRankDefault() {
        return noRankDefault;
    }

    public void setFactionFormat(@NotNull String factionFormat) {
        this.factionFormat = factionFormat;
    }

    @NotNull
    public String getFactionFormat() {
        return factionFormat;
    }

    // ==================== Prefix Configuration ====================

    public void setPrefixEnabled(boolean prefixEnabled) {
        this.prefixEnabled = prefixEnabled;
    }

    public boolean isPrefixEnabled() {
        return prefixEnabled;
    }

    public void setPrefixFormat(@NotNull String prefixFormat) {
        this.prefixFormat = prefixFormat;
    }

    @NotNull
    public String getPrefixFormat() {
        return prefixFormat;
    }

    public void setShowRank(boolean showRank) {
        this.showRank = showRank;
    }

    public boolean isShowRank() {
        return showRank;
    }

    public void setPrefixWithRankFormat(@NotNull String prefixWithRankFormat) {
        this.prefixWithRankFormat = prefixWithRankFormat;
    }

    @NotNull
    public String getPrefixWithRankFormat() {
        return prefixWithRankFormat;
    }

    // ==================== Faction Data Access ====================

    /**
     * Gets the faction name for a player.
     *
     * @param playerUuid the player's UUID
     * @return the faction name, or the configured default if no faction
     */
    @NotNull
    public String getFactionName(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return noFactionDefault;
        }

        FactionData data = getFactionData(playerUuid);
        if (data == null || data.factionName() == null) {
            return noFactionDefault;
        }

        return String.format(factionFormat, data.factionName());
    }

    /**
     * Gets the player's rank within their faction.
     *
     * @param playerUuid the player's UUID
     * @return the rank name (Owner, Officer, Member), or default if no faction
     */
    @NotNull
    public String getFactionRank(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return noRankDefault;
        }

        FactionData data = getFactionData(playerUuid);
        if (data == null || data.rank() == null) {
            return noRankDefault;
        }

        return data.rank();
    }

    /**
     * Gets the faction tag (short identifier) for a player.
     *
     * @param playerUuid the player's UUID
     * @return the faction tag, or default if no faction
     */
    @NotNull
    public String getFactionTag(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return noFactionDefault;
        }

        FactionData data = getFactionData(playerUuid);
        if (data == null || data.tag() == null) {
            return noFactionDefault;
        }

        return data.tag();
    }

    /**
     * Gets the formatted faction prefix for a player.
     * <p>
     * This returns a fully formatted prefix like "[Warriors] " or "[Warriors|Owner] "
     * that can be prepended to the player's existing prefix in chat.
     * <p>
     * Example outputs:
     * <ul>
     *   <li>"&7[&bWarriors&7] " - Default format</li>
     *   <li>"&7[&bWarriors&7|&eOwner&7] " - With rank shown</li>
     *   <li>"" - Player has no faction</li>
     * </ul>
     *
     * @param playerUuid the player's UUID
     * @return the formatted faction prefix, or empty string if no faction
     */
    @NotNull
    public String getFormattedFactionPrefix(@NotNull UUID playerUuid) {
        if (!isAvailable() || !prefixEnabled) {
            return "";
        }

        FactionData data = getFactionData(playerUuid);
        if (data == null || data.factionName() == null) {
            // No faction — use HyperFactions' noFactionTag if available, otherwise HyperPerms' default
            if (useHyperFactionsConfig()) {
                String noTag = provider.getNoFactionTag();
                if (noTag.isEmpty()) {
                    return "";
                }
                String color = provider.getNoFactionTagColor();
                return "&#" + color.substring(1) + noTag + "&r";
            }
            return "";
        }

        // When HyperFactions is detected, use its chat.json config directly
        if (useHyperFactionsConfig()) {
            return formatWithHyperFactionsConfig(data);
        }

        // Legacy path for HyFactions or manual config
        return formatWithLocalConfig(data);
    }

    // Uses HyperFactions' chat.json tagDisplay + tagFormat settings
    @NotNull
    private String formatWithHyperFactionsConfig(@NotNull FactionData data) {
        String tagDisplayMode = provider.getTagDisplayMode();
        String displayText = switch (tagDisplayMode) {
            case "tag" -> {
                String tag = data.tag();
                if (tag != null && !tag.isEmpty()) {
                    yield tag;
                }
                String name = data.factionName();
                yield name.substring(0, Math.min(3, name.length())).toUpperCase();
            }
            case "none" -> null;
            default -> data.factionName();
        };

        if (displayText == null || displayText.isEmpty()) {
            return "";
        }

        // Apply HyperFactions' tagFormat (e.g. "[{tag}] ")
        String result = provider.getTagFormat()
                .replace("{tag}", displayText);

        // Support rank placeholder if present in the format
        String rank = data.rank() != null ? data.rank() : "Member";
        result = result.replace("{rank}", rank);

        // Apply faction color to the tag
        String colorHex = toHexColor(data.color());
        if (colorHex != null) {
            result = "&#" + colorHex.substring(1) + result + "&r";
        }

        return result;
    }

    // Converts a faction color to hex format.
    // Handles both legacy single-char codes ("4", "b") and hex strings ("#FF5555").
    @Nullable
    private static String toHexColor(@Nullable String color) {
        if (color == null || color.isEmpty()) {
            return null;
        }

        // Already hex format
        if (color.startsWith("#") && color.length() == 7) {
            return color;
        }

        // Legacy Minecraft color code (single char) -> hex
        if (color.length() == 1) {
            return switch (color.charAt(0)) {
                case '0' -> "#000000"; // Black
                case '1' -> "#0000AA"; // Dark Blue
                case '2' -> "#00AA00"; // Dark Green
                case '3' -> "#00AAAA"; // Dark Aqua
                case '4' -> "#AA0000"; // Dark Red
                case '5' -> "#AA00AA"; // Dark Purple
                case '6' -> "#FFAA00"; // Gold
                case '7' -> "#AAAAAA"; // Gray
                case '8' -> "#555555"; // Dark Gray
                case '9' -> "#5555FF"; // Blue
                case 'a' -> "#55FF55"; // Green
                case 'b' -> "#55FFFF"; // Aqua
                case 'c' -> "#FF5555"; // Red
                case 'd' -> "#FF55FF"; // Light Purple
                case 'e' -> "#FFFF55"; // Yellow
                case 'f' -> "#FFFFFF"; // White
                default -> null;
            };
        }

        return null;
    }

    // Uses HyperPerms' own factions config section
    @NotNull
    private String formatWithLocalConfig(@NotNull FactionData data) {
        String displayText = data.factionName();

        String rank = data.rank() != null ? data.rank() : "Member";

        if (showRank) {
            return prefixWithRankFormat
                    .replace("%s", displayText)
                    .replace("%r", rank);
        } else {
            return prefixFormat.replace("%s", displayText);
        }
    }

    // Returns true when HyperFactions is detected (not legacy HyFactions)
    private boolean useHyperFactionsConfig() {
        return "HyperFactions".equals(detectedPlugin) && provider != null;
    }

    /**
     * Checks if a player has a faction.
     *
     * @param playerUuid the player's UUID
     * @return true if the player is in a faction
     */
    public boolean hasFaction(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return false;
        }
        FactionData data = getFactionData(playerUuid);
        return data != null && data.factionName() != null;
    }

    /**
     * Gets the raw faction data for a player (for advanced use).
     *
     * @param playerUuid the player's UUID
     * @return the faction data, or null if player has no faction
     */
    @Nullable
    public FactionData getPlayerFactionData(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return null;
        }
        return getFactionData(playerUuid);
    }

    /**
     * Gets all faction data for a player with caching.
     */
    @Nullable
    private FactionData getFactionData(@NotNull UUID playerUuid) {
        // Check cache first
        CachedFactionData cached = factionCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        // Fetch from provider
        FactionData data = provider != null ? provider.getFactionData(playerUuid) : null;

        // Cache the result (including null for players without factions)
        factionCache.put(playerUuid, new CachedFactionData(data));

        return data;
    }

    /**
     * Invalidates the cache for a specific player.
     * Call this when a player joins/leaves a faction.
     */
    public void invalidateCache(@NotNull UUID playerUuid) {
        factionCache.remove(playerUuid);
    }

    /**
     * Clears all cached faction data.
     */
    public void invalidateAllCaches() {
        factionCache.clear();
    }

    // ==================== Data Classes ====================

    /**
     * Holds faction data for a player.
     */
    public record FactionData(
            @Nullable String factionName,
            @Nullable String tag,
            @Nullable String color,
            @Nullable String rank,
            boolean isOwner,
            boolean isOfficer
    ) {}

    private record CachedFactionData(FactionData data, long timestamp) {
        CachedFactionData(FactionData data) {
            this(data, System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    // ==================== Provider Interface ====================

    /**
     * Interface for faction data providers, allowing different implementations.
     */
    private interface FactionProvider {
        @Nullable
        FactionData getFactionData(@NotNull UUID playerUuid);

        /**
         * Gets the tag display mode from the faction plugin's config.
         * Returns "tag", "name", or "none". Defaults to "name" if not available.
         */
        @NotNull
        default String getTagDisplayMode() {
            return "name";
        }

        // Gets the tag format template (e.g. "[{tag}] "). Defaults to "[{tag}] ".
        @NotNull
        default String getTagFormat() {
            return "[{tag}] ";
        }

        // Gets the text shown for players without a faction. Defaults to "".
        @NotNull
        default String getNoFactionTag() {
            return "";
        }

        // Gets the color for the no-faction tag. Defaults to "#555555" (dark gray).
        @NotNull
        default String getNoFactionTagColor() {
            return "#555555";
        }
    }

    /**
     * Reflection-based HyperFactions implementation.
     * Uses pure reflection to avoid compile-time dependency on HyperFactions.
     */
    private static final class ReflectiveHyperFactionsProvider implements FactionProvider {

        private final Class<?> apiClass;
        private final Class<?> factionClass;
        private final Class<?> factionMemberClass;
        private final Class<?> factionRoleClass;
        private final Method isAvailableMethod;
        private final Method getPlayerFactionMethod;
        private final Method factionNameMethod;
        private final Method factionTagMethod;
        private final Method factionColorMethod;
        private final Method factionGetMemberMethod;
        private final Method memberRoleMethod;
        private final Method roleGetDisplayNameMethod;

        // ConfigManager reflection for chat config
        private final Method configManagerGetMethod;
        private final Method getChatTagDisplayMethod;
        private final Method getChatTagFormatMethod;
        private final Method getChatNoFactionTagMethod;
        private final Method getChatNoFactionTagColorMethod;

        ReflectiveHyperFactionsProvider() throws Exception {
            // Load HyperFactions classes via reflection
            apiClass = Class.forName("com.hyperfactions.api.HyperFactionsAPI");
            factionClass = Class.forName("com.hyperfactions.data.Faction");
            factionMemberClass = Class.forName("com.hyperfactions.data.FactionMember");
            factionRoleClass = Class.forName("com.hyperfactions.data.FactionRole");

            // Cache method references
            isAvailableMethod = apiClass.getMethod("isAvailable");
            getPlayerFactionMethod = apiClass.getMethod("getPlayerFaction", UUID.class);
            factionNameMethod = factionClass.getMethod("name");
            factionTagMethod = factionClass.getMethod("tag");
            factionColorMethod = factionClass.getMethod("color");
            factionGetMemberMethod = factionClass.getMethod("getMember", UUID.class);
            memberRoleMethod = factionMemberClass.getMethod("role");
            roleGetDisplayNameMethod = factionRoleClass.getMethod("getDisplayName");

            // ConfigManager.get() chat config methods
            Class<?> configManagerClass = Class.forName("com.hyperfactions.config.ConfigManager");
            configManagerGetMethod = configManagerClass.getMethod("get");
            getChatTagDisplayMethod = configManagerClass.getMethod("getChatTagDisplay");
            getChatTagFormatMethod = configManagerClass.getMethod("getChatTagFormat");
            getChatNoFactionTagMethod = configManagerClass.getMethod("getChatNoFactionTag");
            getChatNoFactionTagColorMethod = configManagerClass.getMethod("getChatNoFactionTagColor");
        }

        @Override
        @Nullable
        public FactionData getFactionData(@NotNull UUID playerUuid) {
            try {
                // Check if API is available
                Boolean available = (Boolean) isAvailableMethod.invoke(null);
                if (available == null || !available) {
                    return null;
                }

                // Get player's faction
                Object faction = getPlayerFactionMethod.invoke(null, playerUuid);
                if (faction == null) {
                    return null;
                }

                // Extract faction data using reflection
                String factionName = (String) factionNameMethod.invoke(faction);
                String tag = (String) factionTagMethod.invoke(faction);
                String color = (String) factionColorMethod.invoke(faction);

                // Get member info
                Object member = factionGetMemberMethod.invoke(faction, playerUuid);
                if (member == null) {
                    return null;
                }

                // Get role
                Object role = memberRoleMethod.invoke(member);
                String rankDisplayName = (String) roleGetDisplayNameMethod.invoke(role);
                String roleName = role.toString(); // enum name like "LEADER", "OFFICER", "MEMBER"

                boolean isOwner = "LEADER".equals(roleName);
                boolean isOfficer = "OFFICER".equals(roleName);

                return new FactionData(factionName, tag, color, rankDisplayName, isOwner, isOfficer);

            } catch (Exception e) {
                // Fail gracefully - return null if anything goes wrong
                return null;
            }
        }

        @Override
        @NotNull
        public String getTagDisplayMode() {
            return getConfigString(getChatTagDisplayMethod, "name");
        }

        @Override
        @NotNull
        public String getTagFormat() {
            return getConfigString(getChatTagFormatMethod, "[{tag}] ");
        }

        @Override
        @NotNull
        public String getNoFactionTag() {
            return getConfigString(getChatNoFactionTagMethod, "");
        }

        @Override
        @NotNull
        public String getNoFactionTagColor() {
            return getConfigString(getChatNoFactionTagColorMethod, "#555555");
        }

        @NotNull
        private String getConfigString(@NotNull Method method, @NotNull String fallback) {
            try {
                Object configManager = configManagerGetMethod.invoke(null);
                if (configManager != null) {
                    String value = (String) method.invoke(configManager);
                    if (value != null) {
                        return value;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to default
            }
            return fallback;
        }
    }

    /**
     * Reflection-based HyFactions implementation (legacy).
     * Uses pure reflection to avoid compile-time dependency on HyFactions.
     */
    private static final class ReflectiveHyFactionsProvider implements FactionProvider {

        private final Class<?> claimManagerClass;
        private final Method getInstanceMethod;
        private final Method getFactionFromPlayerMethod;
        private final Method getNameMethod;
        private final Method isOwnerMethod;
        private final Method isOfficerMethod;
        private final Method getMemberGradeMethod;

        ReflectiveHyFactionsProvider() throws Exception {
            // Load HyFactions classes via reflection
            claimManagerClass = Class.forName("com.kaws.hyfaction.claim.ClaimManager");
            Class<?> factionInfoClass = Class.forName("com.kaws.hyfaction.claim.faction.FactionInfo");

            // Cache method references
            getInstanceMethod = claimManagerClass.getMethod("getInstance");
            getFactionFromPlayerMethod = claimManagerClass.getMethod("getFactionFromPlayer", UUID.class);
            getNameMethod = factionInfoClass.getMethod("getName");
            isOwnerMethod = factionInfoClass.getMethod("isOwner", UUID.class);
            isOfficerMethod = factionInfoClass.getMethod("isOfficer", UUID.class);
            getMemberGradeMethod = factionInfoClass.getMethod("getMemberGrade", UUID.class);
        }

        @Override
        @Nullable
        public FactionData getFactionData(@NotNull UUID playerUuid) {
            try {
                // Get ClaimManager singleton
                Object claimManager = getInstanceMethod.invoke(null);
                if (claimManager == null) {
                    return null;
                }

                // Get player's faction
                Object factionInfo = getFactionFromPlayerMethod.invoke(claimManager, playerUuid);
                if (factionInfo == null) {
                    return null;
                }

                // Extract faction data using reflection
                String factionName = (String) getNameMethod.invoke(factionInfo);
                String tag = generateTag(factionName);
                boolean isOwner = (Boolean) isOwnerMethod.invoke(factionInfo, playerUuid);
                boolean isOfficer = (Boolean) isOfficerMethod.invoke(factionInfo, playerUuid);
                String rank = determineRank(factionInfo, playerUuid, isOwner, isOfficer);

                return new FactionData(factionName, tag, null, rank, isOwner, isOfficer);

            } catch (Exception e) {
                // Fail gracefully - return null if anything goes wrong
                return null;
            }
        }

        /**
         * Generates a short tag from the faction name.
         */
        private String generateTag(String factionName) {
            if (factionName == null || factionName.isEmpty()) {
                return null;
            }
            // Use first 3-4 characters as tag
            int tagLength = Math.min(4, factionName.length());
            return factionName.substring(0, tagLength).toUpperCase();
        }

        /**
         * Determines the player's rank in the faction.
         */
        private String determineRank(Object faction, UUID playerUuid, boolean isOwner, boolean isOfficer) {
            if (isOwner) {
                return "Owner";
            }
            if (isOfficer) {
                return "Officer";
            }

            // Check for custom grade
            try {
                String grade = (String) getMemberGradeMethod.invoke(faction, playerUuid);
                if (grade != null && !grade.isEmpty()) {
                    return grade;
                }
            } catch (Exception ignored) {
                // Fall back to Member
            }

            return "Member";
        }
    }
}
