package com.hyperperms.context.calculators;

import com.hyperperms.context.PlayerContextProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Context calculator that adds the player's current world as context.
 * <p>
 * Adds context: {@code world=<worldname>}
 * <p>
 * Example usage in permissions:
 * <pre>
 * /hp user Player permission set some.permission world=nether
 * </pre>
 */
public final class WorldContextCalculator extends SimpleContextCalculator {

    /**
     * The context key for world contexts.
     */
    public static final String KEY = "world";

    /**
     * Creates a new world context calculator.
     *
     * @param provider the player context provider
     */
    public WorldContextCalculator(@NotNull PlayerContextProvider provider) {
        super(KEY, provider);
    }

    @Override
    @Nullable
    protected String computeValue(@NotNull UUID uuid) {
        return provider.getWorld(uuid);
    }
}
