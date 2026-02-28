package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a subcommand within a {@link CommandGroup}.
 * Method must return {@code CompletableFuture<Void>} and accept
 * {@code CommandContext} as the first parameter.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    /** Subcommand name (e.g. "create" for /hp group create). */
    String name();
    /** Description shown in help. */
    String description() default "";
    /** Aliases for this subcommand. */
    String[] aliases() default {};
}
