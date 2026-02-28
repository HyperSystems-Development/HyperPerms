package com.hyperperms.lifecycle;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed singleton registry for plugin services.
 * Services are registered during initialization and retrieved by type.
 */
public final class ServiceContainer {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Register a service instance for the given type.
     *
     * @throws IllegalStateException if a service of this type is already registered
     */
    public <T> void register(@NotNull Class<T> type, @NotNull T instance) {
        Object existing = services.putIfAbsent(type, instance);
        if (existing != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    /**
     * Get a required service by type.
     *
     * @throws IllegalStateException if the service is not registered
     */
    @NotNull
    public <T> T get(@NotNull Class<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return type.cast(service);
    }

    /**
     * Get an optional service by type. Returns empty if not registered.
     */
    @NotNull
    public <T> Optional<T> getOptional(@NotNull Class<T> type) {
        Object service = services.get(type);
        return service != null ? Optional.of(type.cast(service)) : Optional.empty();
    }

    /**
     * Check if a service is registered.
     */
    public boolean has(@NotNull Class<?> type) {
        return services.containsKey(type);
    }

    /**
     * Remove all registered services.
     */
    public void clear() {
        services.clear();
    }
}
