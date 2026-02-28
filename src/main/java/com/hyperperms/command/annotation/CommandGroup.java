package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a command group. All {@link Command} methods
 * in this class become subcommands under the group name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommandGroup {
    /** Subcommand name (e.g. "group" for /hp group). */
    String name();
    /** Description shown in help. */
    String description() default "";
    /** When true, each @Command method becomes a standalone subcommand under /hp. */
    boolean root() default false;
}
