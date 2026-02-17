package com.hyperperms.context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Provider interface for obtaining player contextual information.
 *
 * This interface abstracts platform-specific player data retrieval.
 * Implementations should be provided by the platform adapter (e.g., HytalePlatform).
 *
 * When the Hytale API becomes available, the implementation should query
 * the actual player state from the server.
 */
public interface PlayerContextProvider {

    /**
     * Gets the name of the world the player is currently in.
     *
     * @param uuid the player's UUID
     * @return the world name, or null if the player is not online or world is unknown
     */
    @Nullable
    String getWorld(@NotNull UUID uuid);

    /**
     * Gets the player's current game mode.
     *
     * @param uuid the player's UUID
     * @return the game mode name (e.g., "survival", "creative", "adventure"),
     *         or null if the player is not online
     */
    @Nullable
    String getGameMode(@NotNull UUID uuid);

    /**
     * Checks if the player is currently online.
     *
     * @param uuid the player's UUID
     * @return true if the player is online
     */
    boolean isOnline(@NotNull UUID uuid);

    /**
     * Gets the current time of day for the player's world.
     *
     * Time periods are based on the in-game day/night cycle:
     * - dawn: Early morning transition period
     * - day: Full daylight period
     * - dusk: Evening transition period
     * - night: Full darkness period
     *
     * @param uuid the player's UUID
     * @return the time of day (day, night, dawn, dusk), or null if unavailable
     */
    @Nullable
    default String getTimeOfDay(@NotNull UUID uuid) {
        return null;
    }

    /**
     * Gets the name of the biome at the player's current location.
     *
     * @param uuid the player's UUID
     * @return the biome name, or null if unavailable
     */
    @Nullable
    default String getBiome(@NotNull UUID uuid) {
        return null;
    }

    /**
     * Gets the name of the region/zone the player is currently in.
     *
     * Regions are typically defined by protection plugins or server configuration.
     *
     * @param uuid the player's UUID
     * @return the region name, or null if not in a region or unavailable
     */
    @Nullable
    default String getRegion(@NotNull UUID uuid) {
        return null;
    }

    /**
     * Finds the UUID of an online player by username (case-insensitive).
     *
     * This exists as a safety net for a race condition: when a player connects,
     * their User is loaded asynchronously via UserManager.loadUser(). If a
     * command runs before that async load completes, the user won't be in the
     * loaded-users map yet. This method checks the platform's online-player list
     * directly so that PlayerResolver can still find and load the user
     * synchronously.
     *
     * @param name the player username (case-insensitive)
     * @return the player's UUID, or null if no online player matches
     */
    @Nullable
    default UUID findOnlineUuidByName(@NotNull String name) {
        return null;
    }

    /**
     * A no-op implementation that returns null for all queries.
     * Used as a default when no platform is available.
     */
    PlayerContextProvider EMPTY = new PlayerContextProvider() {
        @Override
        public @Nullable String getWorld(@NotNull UUID uuid) {
            return null;
        }

        @Override
        public @Nullable String getGameMode(@NotNull UUID uuid) {
            return null;
        }

        @Override
        public boolean isOnline(@NotNull UUID uuid) {
            return false;
        }
    };
}
