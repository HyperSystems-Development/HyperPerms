package com.hyperperms.api;

import com.hyperperms.api.context.ContextSet;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/**
 * A read-only snapshot of a temporary permission node.
 * <p>
 * This record provides a developer-friendly view of a temporary permission
 * without exposing internal {@link com.hyperperms.model.Node} details.
 *
 * @param permission the permission string
 * @param value      true if granted, false if denied
 * @param expiry     the instant when this permission expires
 * @param contexts   the context set this permission applies in
 */
public record TemporaryPermissionInfo(
        @NotNull String permission,
        boolean value,
        @NotNull Instant expiry,
        @NotNull ContextSet contexts
) {

    /**
     * Gets the remaining duration until this permission expires.
     * <p>
     * Returns {@link Duration#ZERO} if already expired.
     *
     * @return the remaining duration
     */
    @NotNull
    public Duration getRemaining() {
        Duration remaining = Duration.between(Instant.now(), expiry);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Checks if this permission has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
