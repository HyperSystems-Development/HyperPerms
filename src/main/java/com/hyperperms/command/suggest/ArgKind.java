package com.hyperperms.command.suggest;

/**
 * The semantic kind of a {@code /hp} command argument, used to attach tab-completion
 * suggestions backed by live HyperPerms data.
 * <p>
 * Declared on {@link com.hyperperms.command.annotation.Arg} /
 * {@link com.hyperperms.command.annotation.OptionalArg}. {@link #STRING} (the default)
 * registers no suggestions, preserving the previous behaviour.
 */
public enum ArgKind {
    /** Plain string — no suggestions (default). */
    STRING,
    /** A HyperPerms group name. */
    GROUP,
    /** A HyperPerms track name. */
    TRACK,
    /** A permission node (registered + runtime-discovered). */
    NODE,
    /** An online player's username. */
    PLAYER
}
