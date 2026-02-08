package com.hyperperms.integration;

import com.hyperperms.HyperPerms;
import com.hyperperms.integration.papi.HyperPermsExpansion;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integration with PlaceholderAPI for Hytale.
 * <p>
 * Provides two-way integration:
 * <ol>
 *   <li>Exposes HyperPerms placeholders to other plugins (e.g., %hyperperms_prefix%)</li>
 *   <li>Parses external PAPI placeholders in HyperPerms chat format (e.g., %player_health%)</li>
 * </ol>
 * <p>
 * Uses reflection for external placeholder parsing to avoid hard dependency on PlaceholderAPI,
 * while using direct API calls for expansion registration when PAPI is available.
 */
public final class PlaceholderAPIIntegration {

    private final HyperPerms plugin;
    private final boolean papiAvailable;

    // Configuration
    private boolean enabled = true;
    private boolean parseExternal = true;

    // Reflection cache for setPlaceholders method
    @Nullable
    private Class<?> placeholderApiClass;
    @Nullable
    private Method setPlaceholdersMethod;
    @Nullable
    private Method getPlayerRefMethod;

    // Expansion instance (if registered)
    @Nullable
    private HyperPermsExpansion expansion;

    /**
     * Creates a new PlaceholderAPIIntegration.
     *
     * @param plugin the HyperPerms plugin instance
     */
    public PlaceholderAPIIntegration(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
        this.papiAvailable = checkAvailable();

        if (papiAvailable) {
            initReflection();
            registerExpansion();
        }
    }

    /**
     * Checks if PlaceholderAPI is available.
     */
    private boolean checkAvailable() {
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            Logger.debug("PlaceholderAPI classes found");
            return true;
        } catch (ClassNotFoundException e) {
            Logger.info("PlaceholderAPI not found - integration disabled");
            return false;
        }
    }

    /**
     * Initializes reflection for parsing external placeholders.
     */
    private void initReflection() {
        try {
            placeholderApiClass = Class.forName("at.helpch.placeholderapi.PlaceholderAPI");

            // Find the setPlaceholders method - PlaceholderAPI.setPlaceholders(PlayerRef, String)
            Class<?> playerRefClass = Class.forName("com.hypixel.hytale.server.core.universe.PlayerRef");
            setPlaceholdersMethod = placeholderApiClass.getMethod("setPlaceholders", playerRefClass, String.class);

            Logger.debug("PlaceholderAPI reflection initialized successfully");
        } catch (Exception e) {
            Logger.warn("Failed to initialize PlaceholderAPI reflection: %s", e.getMessage());
            setPlaceholdersMethod = null;
        }
    }

    /**
     * Registers the HyperPerms expansion with PlaceholderAPI.
     */
    private void registerExpansion() {
        try {
            expansion = new HyperPermsExpansion(plugin);
            expansion.register();
            Logger.info("Registered HyperPerms expansion with PlaceholderAPI");
        } catch (Exception e) {
            Logger.warn("Failed to register PlaceholderAPI expansion: %s", e.getMessage());
            expansion = null;
        }
    }

    /**
     * Checks if PlaceholderAPI is available.
     *
     * @return true if PlaceholderAPI is available
     */
    public boolean isAvailable() {
        return papiAvailable;
    }

    /**
     * Checks if the integration is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the integration is enabled.
     *
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks if external placeholder parsing is enabled.
     *
     * @return true if external parsing is enabled
     */
    public boolean isParseExternal() {
        return parseExternal;
    }

    /**
     * Sets whether external placeholder parsing is enabled.
     *
     * @param parseExternal true to enable external parsing
     */
    public void setParseExternal(boolean parseExternal) {
        this.parseExternal = parseExternal;
    }

    /**
     * Checks if external placeholders should be parsed.
     *
     * @return true if external placeholders should be parsed
     */
    public boolean shouldParseExternal() {
        return enabled && parseExternal && papiAvailable && setPlaceholdersMethod != null;
    }

    /**
     * Parses PlaceholderAPI placeholders in the given text for a player.
     * <p>
     * This method uses reflection to call PlaceholderAPI.setPlaceholders()
     * to parse placeholders from other plugins (e.g., %player_health%).
     *
     * @param uuid the player's UUID
     * @param text the text containing placeholders
     * @return the text with placeholders replaced, or the original text if parsing fails
     */
    @NotNull
    public String parsePlaceholders(@NotNull UUID uuid, @NotNull String text) {
        if (!shouldParseExternal() || text == null) {
            return text;
        }

        // Fast path: no placeholders to parse
        if (!text.contains("%")) {
            return text;
        }

        try {
            // Get PlayerRef from UUID using reflection
            Object playerRef = getPlayerRef(uuid);
            if (playerRef == null) {
                Logger.debug("Could not get PlayerRef for UUID %s - skipping PAPI parsing", uuid);
                return text;
            }

            // Call PlaceholderAPI.setPlaceholders(PlayerRef, String)
            Object result = setPlaceholdersMethod.invoke(null, playerRef, text);
            return result != null ? (String) result : text;
        } catch (Exception e) {
            Logger.debug("PAPI placeholder parsing failed: %s", e.getMessage());
            return text;
        }
    }

    /**
     * Gets a PlayerRef object for the given UUID.
     * Uses reflection to access the Hytale server API.
     *
     * @param uuid the player's UUID
     * @return the PlayerRef, or null if not found
     */
    @Nullable
    private Object getPlayerRef(@NotNull UUID uuid) {
        try {
            // Try to get the player from HytaleServer
            Class<?> hytaleServerClass = Class.forName("com.hypixel.hytale.server.HytaleServer");
            Method getMethod = hytaleServerClass.getMethod("get");
            Object server = getMethod.invoke(null);

            if (server == null) {
                return null;
            }

            // Get the player manager
            Method getPlayerManagerMethod = hytaleServerClass.getMethod("getPlayerManager");
            Object playerManager = getPlayerManagerMethod.invoke(server);

            if (playerManager == null) {
                return null;
            }

            // Get the player by UUID
            Class<?> playerManagerClass = playerManager.getClass();
            Method getPlayerMethod = playerManagerClass.getMethod("getPlayer", UUID.class);
            return getPlayerMethod.invoke(playerManager, uuid);
        } catch (Exception e) {
            Logger.debug("Failed to get PlayerRef for UUID %s: %s", uuid, e.getMessage());
            return null;
        }
    }

    /**
     * Unregisters the HyperPerms expansion from PlaceholderAPI.
     * Call this on plugin disable.
     */
    public void unregister() {
        if (expansion != null) {
            try {
                expansion.unregister();
                Logger.debug("Unregistered HyperPerms expansion from PlaceholderAPI");
            } catch (Exception e) {
                Logger.debug("Failed to unregister PlaceholderAPI expansion: %s", e.getMessage());
            }
            expansion = null;
        }
    }

    /**
     * Gets the registered expansion instance.
     *
     * @return the expansion, or null if not registered
     */
    @Nullable
    public HyperPermsExpansion getExpansion() {
        return expansion;
    }
}
