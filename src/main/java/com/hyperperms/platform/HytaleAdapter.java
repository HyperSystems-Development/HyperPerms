package com.hyperperms.platform;

import com.hyperperms.HyperPerms;
import com.hyperperms.context.PlayerContextProvider;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that bridges HyperPerms with Hytale's player and world systems.
 * <p>
 * This class:
 * <ul>
 *   <li>Implements {@link PlayerContextProvider} for context calculators</li>
 *   <li>Tracks online players and their current state</li>
 *   <li>Provides access to player world and game mode information</li>
 * </ul>
 */
public class HytaleAdapter implements PlayerContextProvider {

    private final HyperPerms hyperPerms;
    private final HyperPermsPlugin plugin;

    // Track online players by UUID
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    /**
     * Creates a new HytaleAdapter.
     *
     * @param hyperPerms the HyperPerms instance
     * @param plugin     the HyperPerms plugin instance
     */
    public HytaleAdapter(@NotNull HyperPerms hyperPerms, @NotNull HyperPermsPlugin plugin) {
        this.hyperPerms = hyperPerms;
        this.plugin = plugin;
    }

    // ==================== PlayerContextProvider Implementation ====================

    @Override
    @Nullable
    public String getWorld(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            return data.worldName;
        }
        return null;
    }

    @Override
    @Nullable
    public String getGameMode(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            return data.gameMode;
        }
        return null;
    }

    @Override
    public boolean isOnline(@NotNull UUID uuid) {
        return playerData.containsKey(uuid);
    }

    @Override
    @Nullable
    public String getTimeOfDay(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            return getTimePeriod(data.dayProgress);
        }
        return null;
    }

    @Override
    @Nullable
    public String getBiome(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null && data.biome != null) {
            return data.biome;
        }
        return null;
    }

    @Override
    @Nullable
    public String getRegion(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null && data.region != null) {
            return data.region;
        }
        return null;
    }

    @Override
    @Nullable
    public UUID findOnlineUuidByName(@NotNull String name) {
        for (PlayerData data : playerData.values()) {
            if (data.playerRef != null) {
                String username = data.playerRef.getUsername();
                if (username != null && username.equalsIgnoreCase(name)) {
                    return data.playerRef.getUuid();
                }
            }
        }
        return null;
    }

    // ==================== Player Tracking ====================

    /**
     * Tracks a player when they connect.
     *
     * @param playerRef the player reference
     * @param worldName the initial world name
     */
    public void trackPlayer(@NotNull PlayerRef playerRef, @Nullable String worldName) {
        UUID uuid = playerRef.getUuid();
        PlayerData data = new PlayerData(playerRef, worldName);
        playerData.put(uuid, data);
        Logger.debug("Tracking player: %s in world: %s", playerRef.getUsername(), worldName);
    }

    /**
     * Untracks a player when they disconnect.
     *
     * @param uuid the player's UUID
     */
    public void untrackPlayer(@NotNull UUID uuid) {
        PlayerData removed = playerData.remove(uuid);
        if (removed != null) {
            Logger.debug("Untracked player: %s", uuid);
        }
    }

    /**
     * Updates a player's world when they change worlds.
     *
     * @param uuid      the player's UUID
     * @param worldName the new world name
     */
    public void updatePlayerWorld(@NotNull UUID uuid, @Nullable String worldName) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            String oldWorld = data.worldName;
            data.worldName = worldName;
            Logger.debug("Player %s world changed: %s -> %s", uuid, oldWorld, worldName);
        }
    }

    /**
     * Updates a player's game mode.
     *
     * @param uuid     the player's UUID
     * @param gameMode the new game mode name
     */
    public void updatePlayerGameMode(@NotNull UUID uuid, @NotNull String gameMode) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            data.gameMode = gameMode.toLowerCase();
            Logger.debug("Player %s game mode changed to: %s", uuid, gameMode);

            // Invalidate cache when game mode changes
            hyperPerms.getCacheInvalidator().invalidateContextCache(uuid);
        }
    }

    /**
     * Updates a player's biome.
     *
     * @param uuid  the player's UUID
     * @param biome the new biome name
     */
    public void updatePlayerBiome(@NotNull UUID uuid, @Nullable String biome) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            String oldBiome = data.biome;
            data.biome = biome;
            
            if (!java.util.Objects.equals(oldBiome, biome)) {
                Logger.debug("Player %s biome changed: %s -> %s", uuid, oldBiome, biome);
                // Invalidate cache when biome changes
                hyperPerms.getCacheInvalidator().invalidateContextCache(uuid);
            }
        }
    }

    /**
     * Updates a player's region.
     *
     * @param uuid   the player's UUID
     * @param region the new region name, or null if not in a region
     */
    public void updatePlayerRegion(@NotNull UUID uuid, @Nullable String region) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            String oldRegion = data.region;
            data.region = region;
            
            if (!java.util.Objects.equals(oldRegion, region)) {
                Logger.debug("Player %s region changed: %s -> %s", uuid, oldRegion, region);
                // Invalidate cache when region changes
                hyperPerms.getCacheInvalidator().invalidateContextCache(uuid);
            }
        }
    }

    /**
     * Updates the day progress for a player's world.
     * <p>
     * This is typically updated periodically by the world's time system.
     *
     * @param uuid        the player's UUID
     * @param dayProgress the day progress (0.0 to 1.0, where 0.0 is midnight)
     */
    public void updateDayProgress(@NotNull UUID uuid, float dayProgress) {
        PlayerData data = playerData.get(uuid);
        if (data != null) {
            // Only invalidate cache if the time period changed
            String oldPeriod = getTimePeriod(data.dayProgress);
            String newPeriod = getTimePeriod(dayProgress);
            
            data.dayProgress = dayProgress;
            
            if (!oldPeriod.equals(newPeriod)) {
                Logger.debug("Player %s time period changed: %s -> %s", uuid, oldPeriod, newPeriod);
                hyperPerms.getCacheInvalidator().invalidateContextCache(uuid);
            }
        }
    }

    /**
     * Converts day progress to a time period string.
     */
    private String getTimePeriod(float dayProgress) {
        if (dayProgress < 0.20f || dayProgress >= 0.80f) {
            return "night";
        } else if (dayProgress < 0.30f) {
            return "dawn";
        } else if (dayProgress < 0.70f) {
            return "day";
        } else {
            return "dusk";
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Gets a tracked player's data.
     *
     * @param uuid the player's UUID
     * @return the player data, or null if not tracked
     */
    @Nullable
    public PlayerData getPlayerData(@NotNull UUID uuid) {
        return playerData.get(uuid);
    }

    /**
     * Gets the PlayerRef for a tracked player.
     *
     * @param uuid the player's UUID
     * @return the PlayerRef, or null if not tracked
     */
    @Nullable
    public PlayerRef getPlayerRef(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        return data != null ? data.playerRef : null;
    }

    /**
     * Gets the username for a tracked player.
     *
     * @param uuid the player's UUID
     * @return the username, or null if not tracked
     */
    @Nullable
    public String getUsername(@NotNull UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data != null && data.playerRef != null) {
            return data.playerRef.getUsername();
        }
        return null;
    }

    /**
     * Gets all online player UUIDs.
     *
     * @return set of online player UUIDs
     */
    @NotNull
    public java.util.Set<UUID> getOnlinePlayers() {
        return java.util.Collections.unmodifiableSet(playerData.keySet());
    }

    /**
     * Gets the count of online players.
     *
     * @return the number of online players
     */
    public int getOnlinePlayerCount() {
        return playerData.size();
    }

    /**
     * Shuts down the adapter and clears all tracked data.
     */
    public void shutdown() {
        playerData.clear();
        Logger.debug("HytaleAdapter shutdown complete");
    }

    // ==================== Inner Classes ====================

    /**
     * Holds tracked data for an online player.
     */
    public static class PlayerData {
        final PlayerRef playerRef;
        volatile String worldName;
        volatile String gameMode;
        volatile String biome;
        volatile String region;
        volatile float dayProgress;

        PlayerData(PlayerRef playerRef, String worldName) {
            this.playerRef = playerRef;
            this.worldName = worldName;
            this.gameMode = "adventure"; // Default game mode
            this.biome = null;
            this.region = null;
            this.dayProgress = 0.5f; // Default to midday
        }
    }
}
