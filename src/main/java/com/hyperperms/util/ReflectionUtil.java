package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection utilities for safe class and method access.
 * <p>
 * Consolidates the {@code Class.forName()} + try/catch pattern
 * duplicated across integration classes.
 */
public final class ReflectionUtil {

    private ReflectionUtil() {}

    /**
     * Checks if a class is available on the classpath.
     *
     * @param className the fully qualified class name
     * @return true if the class exists
     */
    public static boolean isClassAvailable(@NotNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets a method from a class without throwing.
     *
     * @param clazz      the class
     * @param methodName the method name
     * @param paramTypes the parameter types
     * @return the method, or empty if not found
     */
    @NotNull
    public static Optional<Method> getMethodSafe(@NotNull Class<?> clazz, @NotNull String methodName,
                                                  Class<?>... paramTypes) {
        try {
            return Optional.of(clazz.getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /**
     * Invokes a method without throwing.
     *
     * @param target the object to invoke on (null for static methods)
     * @param method the method to invoke
     * @param args   the method arguments
     * @return the result, or empty if invocation failed
     */
    @NotNull
    public static Optional<Object> invokeSafe(Object target, @NotNull Method method, Object... args) {
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Exception e) {
            Logger.debug("Reflection invoke failed: %s.%s - %s",
                    method.getDeclaringClass().getSimpleName(), method.getName(), e.getMessage());
            return Optional.empty();
        }
    }
}
