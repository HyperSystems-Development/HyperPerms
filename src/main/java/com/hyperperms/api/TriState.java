package com.hyperperms.api;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a tri-state value for permission checks.
 * <p>
 * Unlike a boolean which can only be true or false, TriState has three possible values:
 * <ul>
 *     <li>{@link #TRUE} - The permission is explicitly granted</li>
 *     <li>{@link #FALSE} - The permission is explicitly denied</li>
 *     <li>{@link #UNDEFINED} - The permission is not set (neither granted nor denied)</li>
 * </ul>
 * <p>
 * This is useful for distinguishing between "permission denied" and "permission not set",
 * which allows for inheritance and default permission behavior.
 */
public enum TriState {

    /**
     * The permission is explicitly granted.
     */
    TRUE,

    /**
     * The permission is explicitly denied.
     */
    FALSE,

    /**
     * The permission is not set (undefined).
     */
    UNDEFINED;

    /**
     * Converts this TriState to a boolean value.
     * <p>
     * {@link #TRUE} returns true, everything else returns false.
     *
     * @return true if this is TRUE, false otherwise
     */
    public boolean asBoolean() {
        return this == TRUE;
    }

    /**
     * Converts this TriState to a boolean, using a default value for UNDEFINED.
     *
     * @param defaultValue the value to return if this is UNDEFINED
     * @return true if TRUE, false if FALSE, defaultValue if UNDEFINED
     */
    public boolean asBoolean(boolean defaultValue) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case UNDEFINED -> defaultValue;
        };
    }

    /**
     * Creates a TriState from a boolean value.
     *
     * @param value the boolean value
     * @return TRUE if value is true, FALSE if value is false
     */
    @NotNull
    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Creates a TriState from a nullable Boolean.
     *
     * @param value the Boolean value, or null
     * @return TRUE if true, FALSE if false, UNDEFINED if null
     */
    @NotNull
    public static TriState of(Boolean value) {
        if (value == null) {
            return UNDEFINED;
        }
        return value ? TRUE : FALSE;
    }

    /**
     * Converts from the internal WildcardMatcher.TriState to the public API TriState.
     *
     * @param internal the internal TriState
     * @return the equivalent public API TriState
     */
    @NotNull
    public static TriState fromInternal(@NotNull com.hyperperms.resolver.WildcardMatcher.TriState internal) {
        return switch (internal) {
            case TRUE -> TRUE;
            case FALSE -> FALSE;
            case UNDEFINED -> UNDEFINED;
        };
    }

    /**
     * Converts to the internal WildcardMatcher.TriState.
     *
     * @return the equivalent internal TriState
     */
    @NotNull
    public com.hyperperms.resolver.WildcardMatcher.TriState toInternal() {
        return switch (this) {
            case TRUE -> com.hyperperms.resolver.WildcardMatcher.TriState.TRUE;
            case FALSE -> com.hyperperms.resolver.WildcardMatcher.TriState.FALSE;
            case UNDEFINED -> com.hyperperms.resolver.WildcardMatcher.TriState.UNDEFINED;
        };
    }

    /**
     * Checks if this state is defined (not UNDEFINED).
     *
     * @return true if this is TRUE or FALSE
     */
    public boolean isDefined() {
        return this != UNDEFINED;
    }

    /**
     * Checks if this state is undefined.
     *
     * @return true if this is UNDEFINED
     */
    public boolean isUndefined() {
        return this == UNDEFINED;
    }
}
