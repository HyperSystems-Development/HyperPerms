package com.hyperperms.chat;

import com.hyperperms.HyperPerms;
import com.hyperperms.integration.FactionIntegration;
import com.hyperperms.integration.PlaceholderAPIIntegration;
import com.hyperperms.integration.WerChatIntegration;
import com.hyperperms.model.User;
import com.hyperperms.util.Logger;
import static com.hyperperms.util.Logger.debugChat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

/**
 * Central manager for the HyperPerms chat formatting system.
 * 
 * <p>This class coordinates all chat-related components:
 * <ul>
 *   <li>{@link ColorUtil} - Color code processing</li>
 *   <li>{@link ChatFormatter} - Placeholder system</li>
 *   <li>{@link PrefixSuffixResolver} - Prefix/suffix resolution</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * ChatManager chat = hyperPerms.getChatManager();
 * 
 * // Format a chat message
 * chat.formatChatMessage(uuid, playerName, "Hello, world!")
 *     .thenAccept(formattedMessage -> {
 *         // Send to players
 *     });
 * 
 * // Quick format with cached data
 * String formatted = chat.formatChatMessageSync(displayData, "Hello!");
 * }</pre>
 */
public class ChatManager {
    
    private final HyperPerms plugin;
    private final PrefixSuffixResolver prefixSuffixResolver;
    private final ChatFormatter chatFormatter;
    
    // Configuration
    private volatile boolean enabled = true;
    private volatile String chatFormat = "%prefix%player%suffix: %message%";
    private volatile String defaultPrefix = "";
    private volatile String defaultSuffix = "";
    
    // Cache for display data (short-lived, for performance during chat spam)
    private final ConcurrentMap<UUID, CachedDisplayData> displayDataCache = new ConcurrentHashMap<>();
    private static final long DISPLAY_DATA_CACHE_TTL_MS = 5000; // 5 seconds
    
    // Custom placeholder providers
    private final ConcurrentMap<String, BiFunction<UUID, String, String>> customPlaceholders = new ConcurrentHashMap<>();
    
    // Faction integration (optional, set after construction)
    @Nullable
    private volatile FactionIntegration factionIntegration;
    
    // WerChat integration (optional, set after construction)
    @Nullable
    private volatile WerChatIntegration werchatIntegration;

    // PlaceholderAPI integration (optional, set after construction)
    @Nullable
    private volatile PlaceholderAPIIntegration placeholderApiIntegration;
    
    /**
     * Creates a new ChatManager.
     *
     * @param plugin the HyperPerms plugin instance
     */
    @SuppressWarnings("this-escape")
    public ChatManager(@NotNull HyperPerms plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.prefixSuffixResolver = new PrefixSuffixResolver(plugin);
        this.chatFormatter = new ChatFormatter();
        
        // Set up default custom placeholders
        setupDefaultCustomPlaceholders();
    }
    
    /**
     * Sets up default custom placeholder providers.
     */
    private void setupDefaultCustomPlaceholders() {
        // Player group count
        registerCustomPlaceholder("group_count", (uuid, playerName) -> {
            User user = plugin.getUserManager().getUser(uuid);
            return user != null ? String.valueOf(user.getInheritedGroups().size()) : "0";
        });
        
        // Online status (always true if they're chatting)
        registerCustomPlaceholder("online", (uuid, playerName) -> "true");
    }
    
    /**
     * Sets up faction integration for chat placeholders.
     * This enables %faction%, %faction_rank%, and %faction_tag% placeholders.
     *
     * @param factionIntegration the faction integration instance
     */
    public void setFactionIntegration(@Nullable FactionIntegration factionIntegration) {
        this.factionIntegration = factionIntegration;
        debugChat("setFactionIntegration: available=%s", factionIntegration != null && factionIntegration.isAvailable());

        if (factionIntegration != null && factionIntegration.isAvailable()) {
            registerFactionPlaceholders(factionIntegration);
            Logger.info("Faction placeholders registered: %faction%, %faction_rank%, %faction_tag%");
        }
    }
    
    /**
     * Gets the current faction integration.
     *
     * @return the faction integration, or null if not available
     */
    @Nullable
    public FactionIntegration getFactionIntegration() {
        return factionIntegration;
    }
    
    /**
     * Sets up WerChat integration for chat placeholders.
     * This enables %werchat_channel%, %werchat_channel_nick%, etc. placeholders.
     *
     * @param werchatIntegration the WerChat integration instance
     */
    public void setWerChatIntegration(@Nullable WerChatIntegration werchatIntegration) {
        this.werchatIntegration = werchatIntegration;
        debugChat("setWerChatIntegration: available=%s", werchatIntegration != null && werchatIntegration.isAvailable());

        if (werchatIntegration != null && werchatIntegration.isAvailable()) {
            registerWerChatPlaceholders(werchatIntegration);
            Logger.info("WerChat placeholders registered: %%werchat_channel%%, %%werchat_channel_nick%%, %%werchat_channel_color%%, %%werchat_channels%%, %%werchat_muted%%, %%werchat_moderator%%");
        }
    }
    
    /**
     * Gets the current WerChat integration.
     *
     * @return the WerChat integration, or null if not available
     */
    @Nullable
    public WerChatIntegration getWerChatIntegration() {
        return werchatIntegration;
    }

    /**
     * Sets up PlaceholderAPI integration for parsing external placeholders.
     * This enables parsing of PAPI placeholders from other plugins in chat messages.
     *
     * @param placeholderApiIntegration the PlaceholderAPI integration instance
     */
    public void setPlaceholderAPIIntegration(@Nullable PlaceholderAPIIntegration placeholderApiIntegration) {
        this.placeholderApiIntegration = placeholderApiIntegration;
        debugChat("setPlaceholderAPIIntegration: available=%s", placeholderApiIntegration != null && placeholderApiIntegration.isAvailable());

        if (placeholderApiIntegration != null && placeholderApiIntegration.isAvailable()) {
            Logger.info("PlaceholderAPI external placeholder parsing enabled for chat");
        }
    }

    /**
     * Gets the current PlaceholderAPI integration.
     *
     * @return the PlaceholderAPI integration, or null if not available
     */
    @Nullable
    public PlaceholderAPIIntegration getPlaceholderAPIIntegration() {
        return placeholderApiIntegration;
    }

    /**
     * Registers faction-related placeholders.
     */
    private void registerFactionPlaceholders(@NotNull FactionIntegration factions) {
        // %faction% - The player's faction name (formatted)
        registerCustomPlaceholder("faction", (uuid, playerName) -> 
            factions.getFactionName(uuid));
        
        // %faction_rank% - The player's rank in their faction
        registerCustomPlaceholder("faction_rank", (uuid, playerName) -> 
            factions.getFactionRank(uuid));
        
        // %faction_tag% - Short faction tag
        registerCustomPlaceholder("faction_tag", (uuid, playerName) -> 
            factions.getFactionTag(uuid));
    }
    
    /**
     * Registers WerChat-related placeholders.
     */
    private void registerWerChatPlaceholders(@NotNull WerChatIntegration werchat) {
        // %werchat_channel% - The player's focused channel name
        registerCustomPlaceholder("werchat_channel", (uuid, playerName) -> 
            werchat.getFocusedChannelName(uuid));
        
        // %werchat_channel_nick% - The channel's short nickname
        registerCustomPlaceholder("werchat_channel_nick", (uuid, playerName) -> 
            werchat.getFocusedChannelNick(uuid));
        
        // %werchat_channel_color% - The channel's color hex
        registerCustomPlaceholder("werchat_channel_color", (uuid, playerName) -> 
            werchat.getFocusedChannelColor(uuid));
        
        // %werchat_channels% - Number of channels the player is in
        registerCustomPlaceholder("werchat_channels", (uuid, playerName) -> 
            werchat.getChannelCount(uuid));
        
        // %werchat_muted% - Whether player is muted in their current channel
        registerCustomPlaceholder("werchat_muted", (uuid, playerName) -> 
            werchat.isMutedInChannel(uuid));
        
        // %werchat_moderator% - Whether player is a moderator in their current channel
        registerCustomPlaceholder("werchat_moderator", (uuid, playerName) -> 
            werchat.isModeratorInChannel(uuid));
    }
    
    /**
     * Registers a custom placeholder provider.
     *
     * @param placeholder the placeholder name (without %)
     * @param provider the provider function (uuid, playerName) -> value
     */
    public void registerCustomPlaceholder(@NotNull String placeholder,
                                          @NotNull BiFunction<UUID, String, String> provider) {
        customPlaceholders.put(placeholder.toLowerCase(), provider);
        ChatFormatter.registerPlaceholder(placeholder, ctx -> {
            UUID uuid = ctx.getUuid();
            if (uuid != null) {
                String result = provider.apply(uuid, ctx.getPlayerName());
                return result != null ? result : "";
            }
            return "";
        });
    }
    
    /**
     * Formats a chat message for a player.
     *
     * @param uuid the player's UUID
     * @param playerName the player's display name
     * @param message the raw message content
     * @return a future containing the formatted message
     */
    public CompletableFuture<FormattedChatMessage> formatChatMessage(
            @NotNull UUID uuid,
            @NotNull String playerName,
            @NotNull String message) {
        
        debugChat("formatChatMessage: player=%s, uuid=%s, enabled=%s", playerName, uuid, enabled);

        if (!enabled) {
            // Chat formatting disabled - return raw message
            debugChat("formatChatMessage: formatting disabled, returning raw message");
            return CompletableFuture.completedFuture(
                new FormattedChatMessage(message, message, null)
            );
        }

        // Check cache first
        CachedDisplayData cached = displayDataCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            debugChat("formatChatMessage: using cached display data for %s", playerName);
            return CompletableFuture.completedFuture(
                formatWithDisplayData(cached.displayData, playerName, message, uuid)
            );
        }

        debugChat("formatChatMessage: loading display data for %s (cache miss)", playerName);
        // Load display data
        return loadDisplayData(uuid).thenApply(displayData -> {
            // Cache it
            displayDataCache.put(uuid, new CachedDisplayData(displayData));
            return formatWithDisplayData(displayData, playerName, message, uuid);
        });
    }
    
    /**
     * Formats a chat message synchronously with pre-loaded display data.
     * Use this for best performance when display data is already available.
     *
     * @param displayData the pre-loaded display data
     * @param playerName the player's display name
     * @param message the raw message content
     * @return the formatted message
     */
    @NotNull
    public FormattedChatMessage formatWithDisplayData(
            @NotNull PrefixSuffixResolver.DisplayData displayData,
            @NotNull String playerName,
            @NotNull String message) {
        
        return formatWithDisplayData(displayData, playerName, message, null);
    }

    /**
     * Formats a chat message synchronously with pre-loaded display data and player UUID.
     * Use this for best performance when display data is already available.
     *
     * @param displayData the pre-loaded display data
     * @param playerName the player's display name
     * @param message the raw message content
     * @param playerUuid the player's UUID (for faction prefix lookup)
     * @return the formatted message
     */
    @NotNull
    public FormattedChatMessage formatWithDisplayData(
            @NotNull PrefixSuffixResolver.DisplayData displayData,
            @NotNull String playerName,
            @NotNull String message,
            @Nullable UUID playerUuid) {
        
        // Get the base prefix
        String prefix = displayData.getPrefix();
        debugChat("formatWithDisplayData: player=%s, basePrefix='%s', suffix='%s', group=%s",
                playerName, prefix, displayData.getSuffix(), displayData.getPrimaryGroupName());

        // Prepend faction prefix if available and enabled
        if (playerUuid != null && factionIntegration != null && factionIntegration.isPrefixEnabled()) {
            String factionPrefix = factionIntegration.getFormattedFactionPrefix(playerUuid);
            if (!factionPrefix.isEmpty()) {
                debugChat("formatWithDisplayData: prepending faction prefix '%s'", factionPrefix);
                prefix = factionPrefix + prefix;
            }
        }

        // Build context with potentially modified prefix
        ChatFormatter.PlaceholderContext context = ChatFormatter.PlaceholderContext.builder()
            .playerName(playerName)
            .displayName(playerName)
            .uuid(playerUuid)
            .prefix(prefix)
            .suffix(displayData.getSuffix())
            .primaryGroup(displayData.getPrimaryGroupName())
            .extra("group_display_name", displayData.getPrimaryGroupDisplayName())
            .extra("rank", String.valueOf(displayData.getRank()))
            .message(message)
            .build();

        // Format the message
        String formatted = ChatFormatter.format(chatFormat, context);
        debugChat("formatWithDisplayData: format='%s', afterPlaceholders='%s'", chatFormat, formatted);

        // Parse external PlaceholderAPI placeholders (e.g., %player_health%, %vault_balance%)
        if (playerUuid != null && placeholderApiIntegration != null && placeholderApiIntegration.shouldParseExternal()) {
            String beforePapi = formatted;
            formatted = placeholderApiIntegration.parsePlaceholders(playerUuid, formatted);
            debugChat("formatWithDisplayData: PlaceholderAPI parsing applied, changed=%s", !beforePapi.equals(formatted));
        }

        // Process colors
        String colorized = ColorUtil.colorize(formatted);

        return new FormattedChatMessage(colorized, message, displayData);
    }
    
    /**
     * Loads display data for a player.
     * <p>
     * Uses UserManager.loadUser() instead of direct storage access to ensure
     * we get the in-memory cached user (if loaded) which may have more recent
     * changes that haven't been persisted to storage yet.
     */
    private CompletableFuture<PrefixSuffixResolver.DisplayData> loadDisplayData(@NotNull UUID uuid) {
        return plugin.getUserManager().loadUser(uuid).thenCompose(optUser -> {
            User user = optUser.orElseGet(() -> plugin.getUserManager().getOrCreateUser(uuid));
            return prefixSuffixResolver.resolveDisplayData(user);
        });
    }
    
    /**
     * Gets the prefix for a player (resolved from groups or custom).
     *
     * @param uuid the player's UUID
     * @return a future containing the prefix
     */
    public CompletableFuture<String> getPrefix(@NotNull UUID uuid) {
        return prefixSuffixResolver.resolve(uuid)
            .thenApply(PrefixSuffixResolver.ResolveResult::getPrefix);
    }
    
    /**
     * Gets the suffix for a player (resolved from groups or custom).
     *
     * @param uuid the player's UUID
     * @return a future containing the suffix
     */
    public CompletableFuture<String> getSuffix(@NotNull UUID uuid) {
        return prefixSuffixResolver.resolve(uuid)
            .thenApply(PrefixSuffixResolver.ResolveResult::getSuffix);
    }
    
    /**
     * Gets the full display data for a player.
     *
     * @param uuid the player's UUID
     * @return a future containing the display data
     */
    public CompletableFuture<PrefixSuffixResolver.DisplayData> getDisplayData(@NotNull UUID uuid) {
        return loadDisplayData(uuid);
    }
    
    /**
     * Invalidates the display data cache for a player.
     * Call this when their prefix/suffix might have changed.
     *
     * @param uuid the player's UUID
     */
    public void invalidateCache(@NotNull UUID uuid) {
        displayDataCache.remove(uuid);
    }
    
    /**
     * Invalidates all display data caches.
     */
    public void invalidateAllCaches() {
        displayDataCache.clear();
    }
    
    /**
     * Cleans up expired cache entries.
     */
    public void cleanupExpiredCaches() {
        displayDataCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    // ========== Configuration Methods ==========
    
    /**
     * Checks if chat formatting is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets whether chat formatting is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Gets the chat format string.
     */
    @NotNull
    public String getChatFormat() {
        return chatFormat;
    }
    
    /**
     * Sets the chat format string.
     * Supports all placeholders from {@link ChatFormatter}.
     *
     * @param chatFormat the format string (e.g., "%prefix%player%suffix: %message%")
     */
    public void setChatFormat(@NotNull String chatFormat) {
        this.chatFormat = Objects.requireNonNull(chatFormat, "chatFormat cannot be null");
    }
    
    /**
     * Gets the default prefix.
     */
    @NotNull
    public String getDefaultPrefix() {
        return defaultPrefix;
    }
    
    /**
     * Sets the default prefix for users with no group prefix.
     */
    public void setDefaultPrefix(@Nullable String defaultPrefix) {
        this.defaultPrefix = defaultPrefix != null ? defaultPrefix : "";
        prefixSuffixResolver.setDefaultPrefix(this.defaultPrefix);
    }
    
    /**
     * Gets the default suffix.
     */
    @NotNull
    public String getDefaultSuffix() {
        return defaultSuffix;
    }
    
    /**
     * Sets the default suffix for users with no group suffix.
     */
    public void setDefaultSuffix(@Nullable String defaultSuffix) {
        this.defaultSuffix = defaultSuffix != null ? defaultSuffix : "";
        prefixSuffixResolver.setDefaultSuffix(this.defaultSuffix);
    }
    
    /**
     * Loads configuration from the plugin config.
     */
    public void loadConfig() {
        var config = plugin.getConfig();
        if (config != null) {
            this.enabled = config.isChatEnabled();
            this.chatFormat = config.getChatFormat();
            this.defaultPrefix = config.getDefaultPrefix();
            this.defaultSuffix = config.getDefaultSuffix();

            // Update resolver defaults
            prefixSuffixResolver.setDefaultPrefix(defaultPrefix);
            prefixSuffixResolver.setDefaultSuffix(defaultSuffix);

            debugChat("loadConfig: enabled=%s, format='%s', defaultPrefix='%s', defaultSuffix='%s'",
                    enabled, chatFormat, defaultPrefix, defaultSuffix);
        }
    }
    
    // ========== Accessor Methods ==========
    
    /**
     * Gets the prefix/suffix resolver.
     */
    @NotNull
    public PrefixSuffixResolver getPrefixSuffixResolver() {
        return prefixSuffixResolver;
    }
    
    /**
     * Gets the chat formatter.
     */
    @NotNull
    public ChatFormatter getChatFormatter() {
        return chatFormatter;
    }
    
    // ========== Static Utility Methods ==========
    
    /**
     * Colorizes a string (convenience method for {@link ColorUtil#colorize(String)}).
     */
    @NotNull
    public static String colorize(@NotNull String text) {
        return ColorUtil.colorize(text);
    }
    
    /**
     * Strips colors from a string (convenience method for {@link ColorUtil#stripColors(String)}).
     */
    @NotNull
    public static String stripColors(@NotNull String text) {
        return ColorUtil.stripColors(text);
    }
    
    /**
     * Creates a gradient text (convenience method for {@link ColorUtil#gradient(String, String, String)}).
     *
     * @param text the text to apply gradient to
     * @param startHex start color hex code (without #, e.g., "FF0000")
     * @param endHex end color hex code (without #, e.g., "0000FF")
     * @return gradient text
     */
    @NotNull
    public static String gradient(@NotNull String text, @NotNull String startHex, @NotNull String endHex) {
        return ColorUtil.gradient(text, startHex, endHex);
    }
    
    /**
     * Creates rainbow text (convenience method for {@link ColorUtil#rainbow(String)}).
     */
    @NotNull
    public static String rainbow(@NotNull String text) {
        return ColorUtil.rainbow(text);
    }
    
    // ========== Inner Classes ==========
    
    /**
     * Result of formatting a chat message.
     */
    public static class FormattedChatMessage {
        private final String formatted;
        private final String original;
        private final PrefixSuffixResolver.DisplayData displayData;
        
        public FormattedChatMessage(
                @NotNull String formatted,
                @NotNull String original,
                @Nullable PrefixSuffixResolver.DisplayData displayData) {
            this.formatted = formatted;
            this.original = original;
            this.displayData = displayData;
        }
        
        /**
         * Gets the formatted message with colors and placeholders processed.
         */
        @NotNull
        public String getFormatted() {
            return formatted;
        }
        
        /**
         * Gets the original unformatted message.
         */
        @NotNull
        public String getOriginal() {
            return original;
        }
        
        /**
         * Gets the display data used for formatting.
         */
        @Nullable
        public PrefixSuffixResolver.DisplayData getDisplayData() {
            return displayData;
        }
        
        /**
         * Gets the player's prefix.
         */
        @NotNull
        public String getPrefix() {
            return displayData != null ? displayData.getPrefix() : "";
        }
        
        /**
         * Gets the player's suffix.
         */
        @NotNull
        public String getSuffix() {
            return displayData != null ? displayData.getSuffix() : "";
        }
        
        @Override
        public String toString() {
            return formatted;
        }
    }
    
    /**
     * Cached display data with expiration.
     */
    private static class CachedDisplayData {
        final PrefixSuffixResolver.DisplayData displayData;
        final long createdAt;
        
        CachedDisplayData(PrefixSuffixResolver.DisplayData displayData) {
            this.displayData = displayData;
            this.createdAt = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DISPLAY_DATA_CACHE_TTL_MS;
        }
    }
}
