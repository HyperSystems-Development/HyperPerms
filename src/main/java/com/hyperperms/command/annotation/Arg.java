package com.hyperperms.command.annotation;

import com.hyperperms.command.suggest.ArgKind;

import java.lang.annotation.*;

/**
 * Declares a required argument on a {@link Command} method parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Arg {
    /** Argument name shown in usage. */
    String name();
    /** Description shown in help. */
    String description() default "";
    /** Semantic kind, used to attach tab-completion suggestions. Defaults to no suggestions. */
    ArgKind kind() default ArgKind.STRING;
}
