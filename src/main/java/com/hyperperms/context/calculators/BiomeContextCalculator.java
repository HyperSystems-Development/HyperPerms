package com.hyperperms.context.calculators;

import com.hyperperms.context.PlayerContextProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Context calculator that adds the player's current biome as context.
 * <p>
 * Adds context: {@code biome=<biomename>}
 * <p>
 * Biomes represent the environmental type at the player's location, such as
 * forest, desert, ocean, mountains, etc. This enables biome-specific permissions.
 * <p>
 * Example usage in permissions:
 * <pre>
 * /hp user Player permission set build.place biome=desert
 * /hp group Miner permission set build.break.ore biome=mountains
 * /hp user Player permission set entity.spawn.fish biome=ocean
 * </pre>
 * <p>
 * Biome names are normalized to lowercase with spaces replaced by underscores
 * for consistent matching (e.g., "Deep Ocean" becomes "deep_ocean").
 */
public final class BiomeContextCalculator extends SimpleContextCalculator {

    /**
     * The context key for biome contexts.
     */
    public static final String KEY = "biome";

    /**
     * Creates a new biome context calculator.
     *
     * @param provider the player context provider
     */
    public BiomeContextCalculator(@NotNull PlayerContextProvider provider) {
        super(KEY, provider);
    }

    @Override
    @Nullable
    protected String computeValue(@NotNull UUID uuid) {
        return provider.getBiome(uuid);
    }

    @Override
    @NotNull
    protected String normalize(@NotNull String value) {
        // Normalize biome name: lowercase and replace spaces with underscores
        return value.toLowerCase().replace(' ', '_');
    }
}
