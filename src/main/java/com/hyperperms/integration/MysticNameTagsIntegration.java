package com.hyperperms.integration;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.events.DataReloadEvent;
import com.hyperperms.api.events.EventBus;
import com.hyperperms.api.events.PermissionChangeEvent;
import com.hyperperms.api.events.UserGroupChangeEvent;
import com.hyperperms.model.Group;
import com.hyperperms.model.User;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration with MysticNameTags for tag permission sync and cache invalidation.
 * <p>
 * MysticNameTags caches {@code canUseTag} results in a {@code ConcurrentHashMap}.
 * When HyperPerms changes permissions or groups, this integration clears those caches
 * and refreshes nameplates via reflection, ensuring tag availability updates immediately.
 * <p>
 * The native Hytale permissions fallback in MysticNameTags already works with HyperPerms
 * (since HyperPerms registers via {@code PermissionsModule.addProvider()}). This integration
 * adds cache invalidation, PlaceholderAPI tag placeholders, and diagnostic logging.
 */
public final class MysticNameTagsIntegration {

    private final HyperPerms plugin;
    private final boolean pluginAvailable;
    private final boolean apiAvailable;
    private final boolean luckPermsDetected;

    // Reflection: MysticNameTagsAPI static methods
    @Nullable private final Method apiGetActiveTagDisplay;
    @Nullable private final Method apiGetActiveTagView;
    @Nullable private final Method apiGetAllTags;
    @Nullable private final Method apiOwnsTag;
    @Nullable private final Method apiRefreshNameplate;

    // Reflection: TagManager methods
    @Nullable private final Method tagManagerGet;
    @Nullable private final Method tagManagerClearCanUseCache;
    @Nullable private final Method tagManagerGetOnlinePlayer;
    @Nullable private final Method tagManagerGetOnlineWorld;

    // Reflection: TagView methods
    @Nullable private final Method tagViewGetId;
    @Nullable private final Method tagViewGetDisplayRaw;
    @Nullable private final Method tagViewGetDisplayColored;
    @Nullable private final Method tagViewGetPermission;

    // Tag data cache (5-second TTL, matches FactionIntegration pattern)
    private final Map<UUID, CachedTagData> tagCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000;

    // Config options
    private boolean enabled = true;
    private boolean refreshOnPermissionChange = true;
    private boolean refreshOnGroupChange = true;
    private String tagPermissionPrefix = "mysticnametags.tag.";

    // Event subscriptions
    @Nullable private EventBus.Subscription permissionChangeSub;
    @Nullable private EventBus.Subscription groupChangeSub;
    @Nullable private EventBus.Subscription dataReloadSub;

    public MysticNameTagsIntegration(@NotNull HyperPerms plugin) {
        this.plugin = plugin;

        // Detect LuckPerms (will take priority in MysticNameTags' IntegrationManager)
        this.luckPermsDetected = detectLuckPerms();

        // Detect MysticNameTags plugin
        boolean pluginFound = false;
        boolean apiFound = false;

        // Reflection targets (all nullable - will be null if class/method not found)
        Method tmpApiGetActiveTagDisplay = null;
        Method tmpApiGetActiveTagView = null;
        Method tmpApiGetAllTags = null;
        Method tmpApiOwnsTag = null;
        Method tmpApiRefreshNameplate = null;
        Method tmpTagManagerGet = null;
        Method tmpTagManagerClearCanUseCache = null;
        Method tmpTagManagerGetOnlinePlayer = null;
        Method tmpTagManagerGetOnlineWorld = null;
        Method tmpTagViewGetId = null;
        Method tmpTagViewGetDisplayRaw = null;
        Method tmpTagViewGetDisplayColored = null;
        Method tmpTagViewGetPermission = null;

        try {
            Class.forName("com.mystichorizons.mysticnametags.MysticNameTagsPlugin");
            pluginFound = true;
        } catch (ClassNotFoundException ignored) {
            // MysticNameTags not installed
        }

        if (pluginFound) {
            try {
                // MysticNameTagsAPI
                Class<?> apiClass = Class.forName("com.mystichorizons.mysticnametags.api.MysticNameTagsAPI");
                Class<?> playerRefClass = Class.forName("com.hypixel.hytale.server.core.universe.PlayerRef");
                Class<?> worldClass = Class.forName("com.hypixel.hytale.server.core.universe.World");

                tmpApiGetActiveTagDisplay = apiClass.getMethod("getActiveTagDisplay", UUID.class);
                tmpApiGetActiveTagView = apiClass.getMethod("getActiveTagView", UUID.class);
                tmpApiGetAllTags = apiClass.getMethod("getAllTags");
                tmpApiOwnsTag = apiClass.getMethod("ownsTag", UUID.class, String.class);
                tmpApiRefreshNameplate = apiClass.getMethod("refreshNameplate", playerRefClass, worldClass);

                // TagManager
                Class<?> tagManagerClass = Class.forName("com.mystichorizons.mysticnametags.tags.TagManager");
                tmpTagManagerGet = tagManagerClass.getMethod("get");
                tmpTagManagerClearCanUseCache = tagManagerClass.getMethod("clearCanUseCache", UUID.class);
                tmpTagManagerGetOnlinePlayer = tagManagerClass.getMethod("getOnlinePlayer", UUID.class);
                tmpTagManagerGetOnlineWorld = tagManagerClass.getMethod("getOnlineWorld", UUID.class);

                // TagView
                Class<?> tagViewClass = Class.forName("com.mystichorizons.mysticnametags.api.TagView");
                tmpTagViewGetId = tagViewClass.getMethod("getId");
                tmpTagViewGetDisplayRaw = tagViewClass.getMethod("getDisplayRaw");
                tmpTagViewGetDisplayColored = tagViewClass.getMethod("getDisplayColored");
                tmpTagViewGetPermission = tagViewClass.getMethod("getPermission");

                apiFound = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                Logger.warn("MysticNameTags detected but API classes are incompatible: %s", e.getMessage());
                Logger.warn("Tag cache invalidation may not work. Consider updating MysticNameTags.");
            }
        }

        this.pluginAvailable = pluginFound;
        this.apiAvailable = apiFound;
        this.apiGetActiveTagDisplay = tmpApiGetActiveTagDisplay;
        this.apiGetActiveTagView = tmpApiGetActiveTagView;
        this.apiGetAllTags = tmpApiGetAllTags;
        this.apiOwnsTag = tmpApiOwnsTag;
        this.apiRefreshNameplate = tmpApiRefreshNameplate;
        this.tagManagerGet = tmpTagManagerGet;
        this.tagManagerClearCanUseCache = tmpTagManagerClearCanUseCache;
        this.tagManagerGetOnlinePlayer = tmpTagManagerGetOnlinePlayer;
        this.tagManagerGetOnlineWorld = tmpTagManagerGetOnlineWorld;
        this.tagViewGetId = tmpTagViewGetId;
        this.tagViewGetDisplayRaw = tmpTagViewGetDisplayRaw;
        this.tagViewGetDisplayColored = tmpTagViewGetDisplayColored;
        this.tagViewGetPermission = tmpTagViewGetPermission;

        // Log diagnostics
        if (pluginAvailable && apiAvailable) {
            if (luckPermsDetected) {
                Logger.warn("[MysticNameTags] LuckPerms detected! MysticNameTags will prefer LuckPerms " +
                        "over HyperPerms for permission checks. If HyperPerms is your intended permissions " +
                        "backend, consider removing LuckPerms.");
            }
            registerEventListeners();
        }
    }

    // ==================== Detection ====================

    /**
     * Checks if LuckPerms is present on the server.
     */
    private boolean detectLuckPerms() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ==================== Event Listeners ====================

    /**
     * Registers event listeners on HyperPerms' EventBus for cache invalidation.
     */
    private void registerEventListeners() {
        EventBus eventBus = plugin.getEventBus();

        // Permission change → invalidate tag cache if relevant
        permissionChangeSub = eventBus.subscribe(PermissionChangeEvent.class, this::onPermissionChange);

        // Group membership change → invalidate tag cache for that user
        groupChangeSub = eventBus.subscribe(UserGroupChangeEvent.class, this::onGroupChange);

        // Data reload → invalidate all tag caches
        dataReloadSub = eventBus.subscribe(DataReloadEvent.class, this::onDataReload);

        Logger.debug("[MysticNameTags] Registered event listeners for cache invalidation");
    }

    /**
     * Handles permission changes. If the permission is tag-related or a wildcard,
     * invalidates tag caches accordingly.
     */
    private void onPermissionChange(@NotNull PermissionChangeEvent event) {
        if (!enabled || !refreshOnPermissionChange) return;

        String permission = event.getNode().getPermission();
        boolean isTagPermission = permission.startsWith(tagPermissionPrefix);
        boolean isWildcard = permission.equals("*") || permission.endsWith(".*");

        if (!isTagPermission && !isWildcard) return;

        // Determine if the holder is a User or Group
        if (event.getHolder() instanceof User user) {
            // Single user — invalidate just their cache
            UUID uuid = user.getUuid();
            invalidateCache(uuid);
            refreshPlayerTagsSilent(uuid);
            Logger.debug("[MysticNameTags] Invalidated tag cache for %s (permission: %s)", uuid, permission);
        } else if (event.getHolder() instanceof Group) {
            // Group change — invalidate all online players (they may inherit from this group)
            refreshAllOnlinePlayers();
            Logger.debug("[MysticNameTags] Invalidated all tag caches (group permission change: %s)", permission);
        }
    }

    /**
     * Handles group membership changes (add/remove/primary change).
     */
    private void onGroupChange(@NotNull UserGroupChangeEvent event) {
        if (!enabled || !refreshOnGroupChange) return;

        UUID uuid = event.getUuid();
        invalidateCache(uuid);
        refreshPlayerTagsSilent(uuid);
        Logger.debug("[MysticNameTags] Invalidated tag cache for %s (group change: %s %s)",
                uuid, event.getChangeType(), event.getGroupName());
    }

    /**
     * Handles data reloads — clears all caches.
     */
    private void onDataReload(@NotNull DataReloadEvent event) {
        if (!enabled) return;
        if (event.getState() != DataReloadEvent.State.POST) return;

        tagCache.clear();
        refreshAllOnlinePlayers();
        Logger.debug("[MysticNameTags] Cleared all tag caches (data reload)");
    }

    // ==================== Configuration ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRefreshOnPermissionChange(boolean refreshOnPermissionChange) {
        this.refreshOnPermissionChange = refreshOnPermissionChange;
    }

    public void setRefreshOnGroupChange(boolean refreshOnGroupChange) {
        this.refreshOnGroupChange = refreshOnGroupChange;
    }

    public void setTagPermissionPrefix(@NotNull String tagPermissionPrefix) {
        this.tagPermissionPrefix = tagPermissionPrefix;
    }

    @NotNull
    public String getTagPermissionPrefix() {
        return tagPermissionPrefix;
    }

    // ==================== Status ====================

    /**
     * @return true if MysticNameTags plugin classes were found on the classpath
     */
    public boolean isAvailable() {
        return pluginAvailable && apiAvailable && enabled;
    }

    /**
     * @return true if MysticNameTags is installed (regardless of enabled state)
     */
    public boolean isInstalled() {
        return pluginAvailable;
    }

    /**
     * @return true if LuckPerms was detected (MysticNameTags will prefer it)
     */
    public boolean isLuckPermsDetected() {
        return luckPermsDetected;
    }

    // ==================== Public API Methods ====================

    /**
     * Gets the active tag display string for a player.
     *
     * @param uuid the player's UUID
     * @return the active tag display text, or null if unavailable
     */
    @Nullable
    public String getActiveTagDisplay(@NotNull UUID uuid) {
        if (!isAvailable()) return null;

        CachedTagData cached = getCachedData(uuid);
        return cached != null ? cached.activeTagDisplay : null;
    }

    /**
     * Gets the active tag ID for a player.
     *
     * @param uuid the player's UUID
     * @return the active tag ID, or null if unavailable
     */
    @Nullable
    public String getActiveTagId(@NotNull UUID uuid) {
        if (!isAvailable()) return null;

        CachedTagData cached = getCachedData(uuid);
        return cached != null ? cached.activeTagId : null;
    }

    /**
     * Gets the number of tags available to a player.
     *
     * @param uuid the player's UUID
     * @return the count of available tags, or 0 if unavailable
     */
    public int getAvailableTagCount(@NotNull UUID uuid) {
        if (!isAvailable()) return 0;

        CachedTagData cached = getCachedData(uuid);
        return cached != null ? cached.availableTagCount : 0;
    }

    /**
     * Gets the IDs of all tags available to a player.
     *
     * @param uuid the player's UUID
     * @return the list of available tag IDs
     */
    @NotNull
    public List<String> getAvailableTagIds(@NotNull UUID uuid) {
        if (!isAvailable()) return Collections.emptyList();

        CachedTagData cached = getCachedData(uuid);
        return cached != null ? cached.availableTags : Collections.emptyList();
    }

    /**
     * Checks if a player has permission to use a specific tag.
     * Falls back to scanning HyperPerms resolved permissions if the MysticNameTags
     * API is not fully accessible.
     *
     * @param uuid  the player's UUID
     * @param tagId the tag ID
     * @return true if the player can use the tag
     */
    public boolean hasTagPermission(@NotNull UUID uuid, @NotNull String tagId) {
        // Try MysticNameTags API first
        if (isAvailable() && apiOwnsTag != null) {
            try {
                Object result = apiOwnsTag.invoke(null, uuid, tagId);
                if (result instanceof Boolean b) return b;
            } catch (Exception ignored) {
                // Fall through to HyperPerms check
            }
        }

        // Fallback: check via HyperPerms permission system
        return plugin.hasPermission(uuid, tagPermissionPrefix + tagId);
    }

    /**
     * Gets all tag permission nodes that are granted to a player.
     * Scans HyperPerms resolved permissions for nodes matching the tag prefix.
     *
     * @param uuid the player's UUID
     * @return the list of granted tag permission suffixes (tag IDs)
     */
    @NotNull
    public List<String> getGrantedTagPermissions(@NotNull UUID uuid) {
        List<String> granted = new ArrayList<>();

        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) return granted;

        // Scan all direct nodes for tag permissions
        for (var node : user.getNodes()) {
            String perm = node.getPermission();
            if (perm.startsWith(tagPermissionPrefix) && node.getValue()) {
                granted.add(perm.substring(tagPermissionPrefix.length()));
            }
        }

        return granted;
    }

    /**
     * Refreshes a player's tag cache and nameplate in MysticNameTags.
     *
     * @param uuid the player's UUID
     */
    public void refreshPlayerTags(@NotNull UUID uuid) {
        invalidateCache(uuid);
        clearMysticCache(uuid);
        refreshNameplate(uuid);
    }

    /**
     * Refreshes all online players' tag caches and nameplates.
     */
    public void refreshAllOnlinePlayers() {
        tagCache.clear();

        // Get all loaded users and refresh their MysticNameTags caches
        for (User user : plugin.getUserManager().getLoadedUsers()) {
            UUID uuid = user.getUuid();
            clearMysticCache(uuid);
            refreshNameplate(uuid);
        }

        Logger.debug("[MysticNameTags] Refreshed all online player tag caches");
    }

    /**
     * Invalidates the local tag data cache for a player.
     *
     * @param uuid the player's UUID
     */
    public void invalidateCache(@NotNull UUID uuid) {
        tagCache.remove(uuid);
    }

    /**
     * Cleans up event subscriptions. Call on plugin disable.
     */
    public void unregister() {
        if (permissionChangeSub != null) {
            permissionChangeSub.unsubscribe();
            permissionChangeSub = null;
        }
        if (groupChangeSub != null) {
            groupChangeSub.unsubscribe();
            groupChangeSub = null;
        }
        if (dataReloadSub != null) {
            dataReloadSub.unsubscribe();
            dataReloadSub = null;
        }
        tagCache.clear();
        Logger.debug("[MysticNameTags] Unregistered event listeners");
    }

    // ==================== Internal Methods ====================

    /**
     * Silently refreshes a player's tags (no exception propagation).
     */
    private void refreshPlayerTagsSilent(@NotNull UUID uuid) {
        try {
            clearMysticCache(uuid);
            refreshNameplate(uuid);
        } catch (Exception e) {
            Logger.debug("[MysticNameTags] Failed to refresh tags for %s: %s", uuid, e.getMessage());
        }
    }

    /**
     * Calls TagManager.clearCanUseCache(UUID) via reflection.
     */
    private void clearMysticCache(@NotNull UUID uuid) {
        if (tagManagerGet == null || tagManagerClearCanUseCache == null) return;

        try {
            Object tagManager = tagManagerGet.invoke(null);
            if (tagManager != null) {
                tagManagerClearCanUseCache.invoke(tagManager, uuid);
            }
        } catch (Exception e) {
            Logger.debug("[MysticNameTags] Failed to clear canUse cache for %s: %s", uuid, e.getMessage());
        }
    }

    /**
     * Calls MysticNameTagsAPI.refreshNameplate(PlayerRef, World) via reflection.
     * Gets the PlayerRef and World from TagManager.
     */
    private void refreshNameplate(@NotNull UUID uuid) {
        if (tagManagerGet == null || tagManagerGetOnlinePlayer == null
                || tagManagerGetOnlineWorld == null || apiRefreshNameplate == null) return;

        try {
            Object tagManager = tagManagerGet.invoke(null);
            if (tagManager == null) return;

            Object playerRef = tagManagerGetOnlinePlayer.invoke(tagManager, uuid);
            if (playerRef == null) return; // Player not online

            Object world = tagManagerGetOnlineWorld.invoke(tagManager, uuid);
            if (world == null) return;

            apiRefreshNameplate.invoke(null, playerRef, world);
        } catch (Exception e) {
            Logger.debug("[MysticNameTags] Failed to refresh nameplate for %s: %s", uuid, e.getMessage());
        }
    }

    /**
     * Gets cached tag data, fetching from MysticNameTags API if expired or missing.
     */
    @Nullable
    private CachedTagData getCachedData(@NotNull UUID uuid) {
        CachedTagData cached = tagCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // Fetch fresh data from MysticNameTags API
        CachedTagData fresh = fetchTagData(uuid);
        if (fresh != null) {
            tagCache.put(uuid, fresh);
        }
        return fresh;
    }

    /**
     * Fetches tag data from MysticNameTags API via reflection.
     */
    @Nullable
    private CachedTagData fetchTagData(@NotNull UUID uuid) {
        if (!apiAvailable) return null;

        try {
            // Get active tag display
            String activeDisplay = null;
            if (apiGetActiveTagDisplay != null) {
                Object result = apiGetActiveTagDisplay.invoke(null, uuid);
                if (result instanceof String s) activeDisplay = s;
            }

            // Get active tag view for ID
            String activeId = null;
            if (apiGetActiveTagView != null) {
                Object tagView = apiGetActiveTagView.invoke(null, uuid);
                if (tagView != null && tagViewGetId != null) {
                    Object id = tagViewGetId.invoke(tagView);
                    if (id instanceof String s) activeId = s;
                }
            }

            // Get all tags and filter for available ones
            List<String> availableTags = new ArrayList<>();
            if (apiGetAllTags != null) {
                Object allTags = apiGetAllTags.invoke(null);
                if (allTags instanceof List<?> tagList) {
                    for (Object tagView : tagList) {
                        if (tagView == null) continue;

                        // Check if player has permission for this tag
                        String tagPermission = null;
                        if (tagViewGetPermission != null) {
                            Object perm = tagViewGetPermission.invoke(tagView);
                            if (perm instanceof String s) tagPermission = s;
                        }

                        // If tag has a permission, check if player has it
                        if (tagPermission != null && !tagPermission.isEmpty()) {
                            if (plugin.hasPermission(uuid, tagPermission)) {
                                if (tagViewGetId != null) {
                                    Object id = tagViewGetId.invoke(tagView);
                                    if (id instanceof String s) availableTags.add(s);
                                }
                            }
                        } else {
                            // No permission required — tag is available to all
                            if (tagViewGetId != null) {
                                Object id = tagViewGetId.invoke(tagView);
                                if (id instanceof String s) availableTags.add(s);
                            }
                        }
                    }
                }
            }

            return new CachedTagData(
                    activeId,
                    activeDisplay,
                    availableTags.size(),
                    Collections.unmodifiableList(availableTags),
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            Logger.debug("[MysticNameTags] Failed to fetch tag data for %s: %s", uuid, e.getMessage());
            return null;
        }
    }

    // ==================== Data Classes ====================

    /**
     * Cached tag data for a player with TTL.
     */
    private record CachedTagData(
            @Nullable String activeTagId,
            @Nullable String activeTagDisplay,
            int availableTagCount,
            @NotNull List<String> availableTags,
            long timestamp
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}
