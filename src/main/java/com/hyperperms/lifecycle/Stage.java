package com.hyperperms.lifecycle;

import org.jetbrains.annotations.NotNull;

/**
 * A discrete initialization stage in the plugin lifecycle.
 * Stages run in {@link #order()} sequence during startup
 * and in reverse during shutdown.
 */
public interface Stage {

    /**
     * Human-readable name for logging (e.g. "Storage", "Managers").
     */
    @NotNull
    String name();

    /**
     * Execution order. Lower values run first.
     * Use increments of 100 to leave room for future stages.
     */
    int order();

    /**
     * Initialize this stage. Called during plugin startup.
     * May register services in the container for later stages to consume.
     *
     * @param container the service container
     * @throws Exception if initialization fails (aborts startup)
     */
    void initialize(@NotNull ServiceContainer container) throws Exception;

    /**
     * Shutdown this stage. Called during plugin disable in reverse order.
     * Should clean up resources registered during {@link #initialize}.
     *
     * @param container the service container
     */
    default void shutdown(@NotNull ServiceContainer container) {
        // Default no-op — most stages don't need explicit shutdown
    }
}
