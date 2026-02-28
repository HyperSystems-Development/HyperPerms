package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Declares an optional argument on a {@link Command} method parameter.
 * The parameter value will be {@code null} if not provided.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptionalArg {
    /** Argument name shown in usage. */
    String name();
    /** Description shown in help. */
    String description() default "";
}
