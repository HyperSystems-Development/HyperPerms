package com.hyperperms.integration;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hyperperms.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration with the WerChat plugin for chat channel placeholders.
 * Uses pure reflection to avoid compile-time dependency on WerChat.
 */
public final class WerChatIntegration {

    private final HyperPerms plugin;
    private final boolean werchatAvailable;
    private final WerChatProvider provider;

    // Cache for chat data to avoid repeated lookups
    private final Map<UUID, CachedChatData> chatCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000; // 5 second cache

    // Config options
    private boolean enabled = true;
    private String noChannelDefault = "";
    private String channelFormat = "%s";

    public WerChatIntegration(@NotNull HyperPerms plugin) {
        this.plugin = plugin;

        Logger.debugIntegration("Checking for WerChat availability...");
        this.werchatAvailable = checkWerChatAvailable();
        this.provider = werchatAvailable ? createReflectiveProvider() : null;

        if (werchatAvailable && provider != null) {
            Logger.info("WerChat integration enabled - chat channel placeholders available");
        } else if (werchatAvailable) {
            Logger.debugIntegration("WerChat classes found but provider creation failed");
        } else {
            Logger.debugIntegration("WerChat not detected");
        }
    }

    /**
     * Checks if WerChat plugin classes are available.
     */
    private boolean checkWerChatAvailable() {
        return ReflectionUtil.isClassAvailable("com.werchat.WerchatPlugin");
    }

    /**
     * Creates a reflection-based provider for WerChat.
     */
    @Nullable
    private WerChatProvider createReflectiveProvider() {
        try {
            return new ReflectiveWerChatProvider();
        } catch (Exception e) {
            Logger.warn("Failed to initialize WerChat reflection provider: %s", e.getMessage());
            return null;
        }
    }

    /**
     * @return true if WerChat is available and integration is enabled
     */
    public boolean isAvailable() {
        return werchatAvailable && enabled && provider != null;
    }

    /**
     * @return true if WerChat JAR is present (regardless of enabled state)
     */
    public boolean isWerChatInstalled() {
        return werchatAvailable;
    }

    // ==================== Configuration ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setNoChannelDefault(@NotNull String noChannelDefault) {
        this.noChannelDefault = noChannelDefault;
    }

    @NotNull
    public String getNoChannelDefault() {
        return noChannelDefault;
    }

    public void setChannelFormat(@NotNull String channelFormat) {
        this.channelFormat = channelFormat;
    }

    @NotNull
    public String getChannelFormat() {
        return channelFormat;
    }

    // ==================== Chat Data Access ====================

    /**
     * Gets the current focused channel name for a player.
     *
     * @param playerUuid the player's UUID
     * @return the channel name, or the configured default if no channel
     */
    @NotNull
    public String getFocusedChannelName(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return noChannelDefault;
        }

        ChatData data = getChatData(playerUuid);
        if (data == null || data.focusedChannelName() == null) {
            return noChannelDefault;
        }

        return String.format(channelFormat, data.focusedChannelName());
    }

    /**
     * Gets the current focused channel's nickname (short name).
     *
     * @param playerUuid the player's UUID
     * @return the channel nick, or default if no channel
     */
    @NotNull
    public String getFocusedChannelNick(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return noChannelDefault;
        }

        ChatData data = getChatData(playerUuid);
        if (data == null || data.focusedChannelNick() == null) {
            return noChannelDefault;
        }

        return data.focusedChannelNick();
    }

    /**
     * Gets the current focused channel's color as hex.
     *
     * @param playerUuid the player's UUID
     * @return the channel color hex (e.g., "FF0000"), or empty if no channel
     */
    @NotNull
    public String getFocusedChannelColor(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return "";
        }

        ChatData data = getChatData(playerUuid);
        if (data == null || data.focusedChannelColor() == null) {
            return "";
        }

        return data.focusedChannelColor();
    }

    /**
     * Gets the number of channels a player is in.
     *
     * @param playerUuid the player's UUID
     * @return the channel count as a string
     */
    @NotNull
    public String getChannelCount(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return "0";
        }

        ChatData data = getChatData(playerUuid);
        return data != null ? String.valueOf(data.channelCount()) : "0";
    }

    /**
     * Checks if a player is muted in their current focused channel.
     *
     * @param playerUuid the player's UUID
     * @return "true" if muted, "false" otherwise
     */
    @NotNull
    public String isMutedInChannel(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return "false";
        }

        ChatData data = getChatData(playerUuid);
        return data != null && data.isMuted() ? "true" : "false";
    }

    /**
     * Checks if a player is a moderator in their current focused channel.
     *
     * @param playerUuid the player's UUID
     * @return "true" if moderator, "false" otherwise
     */
    @NotNull
    public String isModeratorInChannel(@NotNull UUID playerUuid) {
        if (!isAvailable()) {
            return "false";
        }

        ChatData data = getChatData(playerUuid);
        return data != null && data.isModerator() ? "true" : "false";
    }

    /**
     * Gets all chat data for a player with caching.
     */
    @Nullable
    private ChatData getChatData(@NotNull UUID playerUuid) {
        // Check cache first
        CachedChatData cached = chatCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        // Fetch from provider
        ChatData data = provider != null ? provider.getChatData(playerUuid) : null;

        // Cache the result (including null for players without data)
        chatCache.put(playerUuid, new CachedChatData(data));

        return data;
    }

    /**
     * Invalidates the cache for a specific player.
     * Call this when a player changes channels.
     */
    public void invalidateCache(@NotNull UUID playerUuid) {
        chatCache.remove(playerUuid);
    }

    /**
     * Clears all cached chat data.
     */
    public void invalidateAllCaches() {
        chatCache.clear();
    }

    // ==================== Data Classes ====================

    /**
     * Holds chat data for a player.
     */
    public record ChatData(
            @Nullable String focusedChannelName,
            @Nullable String focusedChannelNick,
            @Nullable String focusedChannelColor,
            int channelCount,
            boolean isMuted,
            boolean isModerator
    ) {}

    private record CachedChatData(ChatData data, long timestamp) {
        CachedChatData(ChatData data) {
            this(data, System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    // ==================== Provider Interface ====================

    /**
     * Interface for chat data providers, allowing different implementations.
     */
    private interface WerChatProvider {
        @Nullable
        ChatData getChatData(@NotNull UUID playerUuid);
    }

    /**
     * Reflection-based WerChat implementation.
     * Uses pure reflection to avoid compile-time dependency on WerChat.
     */
    private static final class ReflectiveWerChatProvider implements WerChatProvider {

        private final Class<?> werchatPluginClass;
        private final Method getInstanceMethod;
        private final Method getChannelManagerMethod;
        private final Method getPlayerDataManagerMethod;
        
        // ChannelManager methods
        private final Method getPlayerChannelsMethod;
        private final Method getChannelMethod;
        
        // PlayerDataManager methods
        private final Method getFocusedChannelMethod;
        
        // Channel methods
        private final Method getChannelNameMethod;
        private final Method getChannelNickMethod;
        private final Method getChannelColorHexMethod;
        private final Method isMutedMethod;
        private final Method isModeratorMethod;

        ReflectiveWerChatProvider() throws Exception {
            // Load WerChat classes via reflection
            werchatPluginClass = Class.forName("com.werchat.WerchatPlugin");
            Class<?> channelManagerClass = Class.forName("com.werchat.channels.ChannelManager");
            Class<?> playerDataManagerClass = Class.forName("com.werchat.storage.PlayerDataManager");
            Class<?> channelClass = Class.forName("com.werchat.channels.Channel");

            // Cache method references - WerchatPlugin
            getInstanceMethod = werchatPluginClass.getMethod("getInstance");
            getChannelManagerMethod = werchatPluginClass.getMethod("getChannelManager");
            getPlayerDataManagerMethod = werchatPluginClass.getMethod("getPlayerDataManager");
            
            // ChannelManager methods
            getPlayerChannelsMethod = channelManagerClass.getMethod("getPlayerChannels", UUID.class);
            getChannelMethod = channelManagerClass.getMethod("getChannel", String.class);
            
            // PlayerDataManager methods
            getFocusedChannelMethod = playerDataManagerClass.getMethod("getFocusedChannel", UUID.class);
            
            // Channel methods
            getChannelNameMethod = channelClass.getMethod("getName");
            getChannelNickMethod = channelClass.getMethod("getNick");
            getChannelColorHexMethod = channelClass.getMethod("getColorHex");
            isMutedMethod = channelClass.getMethod("isMuted", UUID.class);
            isModeratorMethod = channelClass.getMethod("isModerator", UUID.class);
        }

        @Override
        @Nullable
        public ChatData getChatData(@NotNull UUID playerUuid) {
            try {
                // Get WerchatPlugin singleton
                Object werchatPlugin = getInstanceMethod.invoke(null);
                if (werchatPlugin == null) {
                    return null;
                }

                // Get managers
                Object channelManager = getChannelManagerMethod.invoke(werchatPlugin);
                Object playerDataManager = getPlayerDataManagerMethod.invoke(werchatPlugin);
                
                if (channelManager == null || playerDataManager == null) {
                    return null;
                }

                // Get player's channels
                @SuppressWarnings("unchecked")
                List<?> playerChannels = (List<?>) getPlayerChannelsMethod.invoke(channelManager, playerUuid);
                int channelCount = playerChannels != null ? playerChannels.size() : 0;

                // Get focused channel name
                String focusedChannelName = (String) getFocusedChannelMethod.invoke(playerDataManager, playerUuid);
                
                // If no focused channel, try to return basic data
                if (focusedChannelName == null || focusedChannelName.isEmpty()) {
                    return new ChatData(null, null, null, channelCount, false, false);
                }

                // Get the focused channel object
                Object channel = getChannelMethod.invoke(channelManager, focusedChannelName);
                if (channel == null) {
                    return new ChatData(focusedChannelName, null, null, channelCount, false, false);
                }

                // Extract channel data
                String channelName = (String) getChannelNameMethod.invoke(channel);
                String channelNick = (String) getChannelNickMethod.invoke(channel);
                String channelColorHex = (String) getChannelColorHexMethod.invoke(channel);
                boolean isMuted = (Boolean) isMutedMethod.invoke(channel, playerUuid);
                boolean isModerator = (Boolean) isModeratorMethod.invoke(channel, playerUuid);

                return new ChatData(channelName, channelNick, channelColorHex, channelCount, isMuted, isModerator);

            } catch (Exception e) {
                // Fail gracefully - return null if anything goes wrong
                return null;
            }
        }
    }
}
