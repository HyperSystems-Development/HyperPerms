package com.hyperperms.context.calculators;

import com.hyperperms.context.PlayerContextProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Context calculator that adds the player's current time of day as context.
 * <p>
 * Adds context: {@code time=<period>}
 * <p>
 * Time periods based on the in-game day/night cycle:
 * <ul>
 *   <li>{@code dawn} - Early morning transition (~5:00-7:00)</li>
 *   <li>{@code day} - Full daylight (~7:00-17:00)</li>
 *   <li>{@code dusk} - Evening transition (~17:00-19:00)</li>
 *   <li>{@code night} - Full darkness (~19:00-5:00)</li>
 * </ul>
 * <p>
 * Example usage in permissions:
 * <pre>
 * /hp user Player permission set movement.fly time=night
 * /hp group Vampire permission set combat.damage.bonus time=night
 * </pre>
 */
public final class TimeContextCalculator extends SimpleContextCalculator {

    /**
     * The context key for time contexts.
     */
    public static final String KEY = "time";

    /**
     * Time period: Dawn (early morning transition).
     */
    public static final String DAWN = "dawn";

    /**
     * Time period: Day (full daylight).
     */
    public static final String DAY = "day";

    /**
     * Time period: Dusk (evening transition).
     */
    public static final String DUSK = "dusk";

    /**
     * Time period: Night (full darkness).
     */
    public static final String NIGHT = "night";

    /**
     * Creates a new time context calculator.
     *
     * @param provider the player context provider
     */
    public TimeContextCalculator(@NotNull PlayerContextProvider provider) {
        super(KEY, provider);
    }

    @Override
    @Nullable
    protected String computeValue(@NotNull UUID uuid) {
        return provider.getTimeOfDay(uuid);
    }
}
