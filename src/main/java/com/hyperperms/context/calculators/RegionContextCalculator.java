package com.hyperperms.context.calculators;

import com.hyperperms.context.PlayerContextProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Context calculator that adds the player's current region/zone as context.
 * <p>
 * Adds context: {@code region=<regionname>}
 * <p>
 * Regions are typically defined by protection plugins, server configuration,
 * or Hytale's built-in zone system (when available).
 * <p>
 * Example usage in permissions:
 * <pre>
 * /hp user Player permission set build.place region=spawn
 * /hp group Builder permission set build.* region=creative_area
 * /hp user Player permission set teleport.use region=hub
 * </pre>
 * <p>
 * This calculator integrates with any region/zone system that provides region data
 * through the {@link PlayerContextProvider}. If no region system is available or
 * the player is not in a defined region, no context is added.
 * <p>
 * Region names are normalized to lowercase with spaces replaced by underscores.
 */
public final class RegionContextCalculator extends SimpleContextCalculator {

    /**
     * The context key for region contexts.
     */
    public static final String KEY = "region";

    /**
     * Creates a new region context calculator.
     *
     * @param provider the player context provider
     */
    public RegionContextCalculator(@NotNull PlayerContextProvider provider) {
        super(KEY, provider);
    }

    @Override
    @Nullable
    protected String computeValue(@NotNull UUID uuid) {
        return provider.getRegion(uuid);
    }

    @Override
    @NotNull
    protected String normalize(@NotNull String value) {
        // Normalize region name: lowercase and replace spaces with underscores
        return value.toLowerCase().replace(' ', '_');
    }
}
