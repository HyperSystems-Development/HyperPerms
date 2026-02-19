package com.hyperperms.context.calculators;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.context.ContextCalculator;
import com.hyperperms.context.PlayerContextProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for simple provider-based context calculators.
 * <p>
 * Most context calculators follow the same pattern: read a value from the
 * {@link PlayerContextProvider}, normalize it, and add it to the context set.
 * This base class eliminates that duplication.
 * <p>
 * Subclasses only need to implement {@link #computeValue(UUID)} to extract
 * the relevant value from the provider.
 */
public abstract class SimpleContextCalculator implements ContextCalculator {

    private final String key;
    protected final PlayerContextProvider provider;

    /**
     * Creates a new simple context calculator.
     *
     * @param key      the context key (e.g., "world", "gamemode")
     * @param provider the player context provider
     */
    protected SimpleContextCalculator(@NotNull String key, @NotNull PlayerContextProvider provider) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
    }

    /**
     * Computes the context value for a player.
     *
     * @param uuid the player's UUID
     * @return the raw context value, or null if unavailable
     */
    @Nullable
    protected abstract String computeValue(@NotNull UUID uuid);

    /**
     * Normalizes the computed value before adding it to the context set.
     * <p>
     * The default implementation lowercases the value. Subclasses can override
     * to apply additional normalization (e.g., replacing spaces with underscores).
     *
     * @param value the raw value from {@link #computeValue(UUID)}
     * @return the normalized value
     */
    @NotNull
    protected String normalize(@NotNull String value) {
        return value.toLowerCase();
    }

    @Override
    public void calculate(@NotNull UUID uuid, @NotNull ContextSet.Builder builder) {
        String value = computeValue(uuid);
        if (value != null && !value.isEmpty()) {
            String normalized = normalize(value);
            builder.add(key, normalized);
            Logger.debugContext("Calculated context %s=%s for %s", key, normalized, uuid);
        }
    }

    /**
     * Gets the context key for this calculator.
     *
     * @return the context key
     */
    @NotNull
    public String getKey() {
        return key;
    }
}
