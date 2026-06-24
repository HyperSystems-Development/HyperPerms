package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Reflection utilities for safe class and method access.
 * <p>
 * Consolidates the {@code Class.forName()} + try/catch pattern
 * duplicated across integration classes.
 */
public final class ReflectionUtil {

    // ==================== Hytale-core reflection targets ====================
    // Centralized here so a future Hytale package move is a one-line fix and the two
    // optional integrations (PlaceholderAPI, MysticNameTags) can't drift independently.

    /** {@code Universe} — server-wide entity/player lookup. */
    public static final String UNIVERSE_CLASS = "com.hypixel.hytale.server.core.universe.Universe";
    /** {@code PlayerRef} — a player entity reference. */
    public static final String PLAYER_REF_CLASS = "com.hypixel.hytale.server.core.universe.PlayerRef";
    /** {@code World} — note the {@code .world} sub-package (a common reflection-string mistake). */
    public static final String WORLD_CLASS = "com.hypixel.hytale.server.core.universe.world.World";

    private ReflectionUtil() {}

    /**
     * Resolves an online player's {@code PlayerRef} via {@code Universe.get().getPlayer(uuid)},
     * reflectively (so callers that intentionally avoid a hard Hytale-core dependency stay
     * reflection-only). This is the canonical lookup used by the server itself.
     *
     * @param uuid the player's UUID
     * @return the {@code PlayerRef} object, or {@code null} if offline / unavailable
     */
    @Nullable
    public static Object getOnlinePlayerRef(@NotNull UUID uuid) {
        try {
            Class<?> universeClass = Class.forName(UNIVERSE_CLASS);
            Object universe = universeClass.getMethod("get").invoke(null);
            if (universe == null) {
                return null;
            }
            return universeClass.getMethod("getPlayer", UUID.class).invoke(universe, uuid);
        } catch (Exception e) {
            Logger.debug("getOnlinePlayerRef(%s) failed: %s", uuid, e.getMessage());
            return null;
        }
    }

    /**
     * Startup self-check: verifies the Hytale-core reflection targets resolve on the current
     * server build, logging a clear warning for any that are missing. Surfaces latent
     * package-move drift instead of letting optional integrations fail silently per-call.
     */
    public static void verifyHytaleCoreTargets() {
        for (String className : new String[]{UNIVERSE_CLASS, PLAYER_REF_CLASS, WORLD_CLASS}) {
            if (!isClassAvailable(className)) {
                Logger.warn("Hytale-core reflection target missing: %s "
                        + "(reflection-based optional integrations may not work on this server build)", className);
            }
        }
    }

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
