package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Permission required to execute a {@link Command}.
 * The sender is checked before the method is invoked.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Permission {
    /** The permission node (e.g. "hyperperms.group.create"). */
    String value();
}
