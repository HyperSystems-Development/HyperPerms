package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Requires double-invocation confirmation before executing.
 * First call shows the warning message; second call within
 * the timeout actually executes the command.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Confirm {
    /** Warning message shown on first invocation. */
    String message() default "Are you sure? Run the command again to confirm.";
    /** Confirmation timeout in seconds. */
    int timeoutSeconds() default 60;
}
