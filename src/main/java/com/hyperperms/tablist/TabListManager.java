package com.hyperperms.tablist;

import com.hyperperms.HyperPerms;
import com.hyperperms.chat.ChatFormatter;
import com.hyperperms.chat.ColorUtil;
import com.hyperperms.chat.PrefixSuffixResolver;
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

/**
 * Central manager for the HyperPerms tab list formatting system.
 *
 * <p>This class coordinates tab list name formatting using:
 * <ul>
 *   <li>{@link PrefixSuffixResolver} - Prefix/suffix resolution</li>
 *   <li>{@link ChatFormatter} - Placeholder system</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * TabListManager tabList = hyperPerms.getTabListManager();
 *
 * // Format a tab list name
 * tabList.formatTabListName(uuid, playerName)
 *     .thenAccept(formattedName -> {
 *         // Apply to player
 *     });
 * }</pre>
 */
public class TabListManager {

    private final HyperPerms plugin;
    private final PrefixSuffixResolver prefixSuffixResolver;
    private final ChatFormatter chatFormatter;

    // Configuration
    private volatile boolean enabled = true;
    private volatile String tabListFormat = "%prefix%%player%";
    private volatile boolean sortByWeight = true;
    private volatile int updateIntervalTicks = 20;

    // Cache for display names (short-lived, for performance)
    private final ConcurrentMap<UUID, CachedDisplayName> displayNameCache = new ConcurrentHashMap<>();
    private static final long DISPLAY_NAME_CACHE_TTL_MS = 5000; // 5 seconds

    // Callback for applying tab list names to players
    @Nullable
    private volatile TabListNameApplier nameApplier;

    /**
     * Functional interface for applying tab list names to players.
     */
    @FunctionalInterface
    public interface TabListNameApplier {
        /**
         * Applies the formatted name to a player's tab list entry.
         *
         * @param uuid the player's UUID
         * @param formattedName the formatted tab list name
         */
        void apply(UUID uuid, String formattedName);
    }

    /**
     * Creates a new TabListManager.
     *
     * @param plugin the HyperPerms plugin instance
     */
    public TabListManager(@NotNull HyperPerms plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.prefixSuffixResolver = plugin.getChatManager() != null
            ? plugin.getChatManager().getPrefixSuffixResolver()
            : new PrefixSuffixResolver(plugin);
        this.chatFormatter = new ChatFormatter();
    }

    /**
     * Formats the tab list name for a player.
     *
     * @param uuid the player's UUID
     * @param playerName the player's display name
     * @return a future containing the formatted tab list name
     */
    public CompletableFuture<String> formatTabListName(@NotNull UUID uuid, @NotNull String playerName) {
        debugChat("TabListManager.formatTabListName: player=%s, uuid=%s, enabled=%s", playerName, uuid, enabled);

        if (!enabled) {
            debugChat("TabListManager.formatTabListName: tab list formatting disabled");
            return CompletableFuture.completedFuture(playerName);
        }

        // Check cache first
        CachedDisplayName cached = displayNameCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            debugChat("TabListManager.formatTabListName: using cached name for %s", playerName);
            return CompletableFuture.completedFuture(cached.formattedName);
        }

        debugChat("TabListManager.formatTabListName: loading display data for %s (cache miss)", playerName);
        // Load display data and format
        return loadDisplayData(uuid).thenApply(displayData -> {
            String formatted = formatWithDisplayData(displayData, playerName, uuid);

            // Cache it
            displayNameCache.put(uuid, new CachedDisplayName(formatted));

            return formatted;
        });
    }

    /**
     * Formats a tab list name synchronously with pre-loaded display data.
     *
     * @param displayData the pre-loaded display data
     * @param playerName the player's display name
     * @param playerUuid the player's UUID (for custom placeholders)
     * @return the formatted tab list name
     */
    @NotNull
    public String formatWithDisplayData(
            @NotNull PrefixSuffixResolver.DisplayData displayData,
            @NotNull String playerName,
            @Nullable UUID playerUuid) {

        debugChat("TabListManager.formatWithDisplayData: player=%s, prefix='%s', suffix='%s', group=%s",
                playerName, displayData.getPrefix(), displayData.getSuffix(), displayData.getPrimaryGroupName());

        // Build context
        ChatFormatter.PlaceholderContext context = ChatFormatter.PlaceholderContext.builder()
            .playerName(playerName)
            .displayName(playerName)
            .uuid(playerUuid)
            .prefix(displayData.getPrefix())
            .suffix(displayData.getSuffix())
            .primaryGroup(displayData.getPrimaryGroupName())
            .extra("group_display_name", displayData.getPrimaryGroupDisplayName())
            .extra("rank", String.valueOf(displayData.getRank()))
            .extra("weight", String.valueOf(displayData.getRank()))
            .build();

        // Format the tab list name
        String formatted = ChatFormatter.format(tabListFormat, context);

        // Process colors
        String colorized = ColorUtil.colorize(formatted);
        debugChat("TabListManager.formatWithDisplayData: format='%s', result='%s'", tabListFormat, colorized);
        return colorized;
    }

    /**
     * Loads display data for a player.
     */
    private CompletableFuture<PrefixSuffixResolver.DisplayData> loadDisplayData(@NotNull UUID uuid) {
        return plugin.getUserManager().loadUser(uuid).thenCompose(optUser -> {
            User user = optUser.orElseGet(() -> plugin.getUserManager().getOrCreateUser(uuid));
            return prefixSuffixResolver.resolveDisplayData(user);
        });
    }

    /**
     * Updates a player's tab list name.
     * This will format the name and apply it via the registered applier.
     *
     * @param uuid the player's UUID
     * @param playerName the player's display name
     */
    public void updatePlayer(@NotNull UUID uuid, @NotNull String playerName) {
        if (!enabled || nameApplier == null) {
            return;
        }

        formatTabListName(uuid, playerName).thenAccept(formattedName -> {
            TabListNameApplier applier = this.nameApplier;
            if (applier != null) {
                applier.apply(uuid, formattedName);
            }
        }).exceptionally(e -> {
            Logger.warn("Failed to update tab list name for %s: %s", uuid, e.getMessage());
            return null;
        });
    }

    /**
     * Invalidates the display name cache for a player.
     *
     * @param uuid the player's UUID
     */
    public void invalidateCache(@NotNull UUID uuid) {
        displayNameCache.remove(uuid);
    }

    /**
     * Invalidates all display name caches.
     */
    public void invalidateAllCaches() {
        displayNameCache.clear();
    }

    /**
     * Cleans up expired cache entries.
     */
    public void cleanupExpiredCaches() {
        displayNameCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    // ========== Configuration Methods ==========

    /**
     * Checks if tab list formatting is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether tab list formatting is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the tab list format string.
     */
    @NotNull
    public String getTabListFormat() {
        return tabListFormat;
    }

    /**
     * Sets the tab list format string.
     * Supports all placeholders from {@link ChatFormatter}.
     *
     * @param format the format string (e.g., "%prefix%%player%")
     */
    public void setTabListFormat(@NotNull String format) {
        this.tabListFormat = Objects.requireNonNull(format, "format cannot be null");
    }

    /**
     * Checks if tab list should be sorted by group weight.
     */
    public boolean isSortByWeight() {
        return sortByWeight;
    }

    /**
     * Sets whether to sort tab list by group weight.
     */
    public void setSortByWeight(boolean sortByWeight) {
        this.sortByWeight = sortByWeight;
    }

    /**
     * Gets the update interval in ticks.
     */
    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    /**
     * Sets the update interval in ticks.
     */
    public void setUpdateIntervalTicks(int ticks) {
        this.updateIntervalTicks = ticks;
    }

    /**
     * Sets the tab list name applier callback.
     *
     * @param applier the callback to apply formatted names
     */
    public void setNameApplier(@Nullable TabListNameApplier applier) {
        this.nameApplier = applier;
    }

    /**
     * Loads configuration from the plugin config.
     */
    public void loadConfig() {
        var config = plugin.getConfig();
        if (config != null) {
            this.enabled = config.isTabListEnabled();
            this.tabListFormat = config.getTabListFormat();
            this.sortByWeight = config.isTabListSortByWeight();
            this.updateIntervalTicks = config.getTabListUpdateIntervalTicks();

            debugChat("TabListManager.loadConfig: enabled=%s, format='%s', sortByWeight=%s, updateInterval=%d",
                    enabled, tabListFormat, sortByWeight, updateIntervalTicks);
        }
    }

    /**
     * Gets the group weight for a player (for sorting).
     *
     * @param uuid the player's UUID
     * @return the weight, or 0 if not found
     */
    public int getWeight(@NotNull UUID uuid) {
        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            return 0;
        }

        String primaryGroup = user.getPrimaryGroup();
        if (primaryGroup == null || primaryGroup.isEmpty()) {
            return 0;
        }

        var group = plugin.getGroupManager().getGroup(primaryGroup);
        return group != null ? group.getWeight() : 0;
    }

    // ========== Inner Classes ==========

    /**
     * Cached display name with expiration.
     */
    private static class CachedDisplayName {
        final String formattedName;
        final long createdAt;

        CachedDisplayName(String formattedName) {
            this.formattedName = formattedName;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DISPLAY_NAME_CACHE_TTL_MS;
        }
    }
}
