package com.hyperperms.lifecycle;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates plugin startup and shutdown by running {@link Stage} objects
 * in order during initialization and in reverse during shutdown.
 */
public final class PluginLifecycle {

    private final ServiceContainer container;
    private final List<Stage> stages = new ArrayList<>();
    private final List<Stage> initializedStages = new ArrayList<>();

    public PluginLifecycle(@NotNull ServiceContainer container) {
        this.container = container;
    }

    /**
     * Add a stage to the lifecycle.
     */
    public void addStage(@NotNull Stage stage) {
        stages.add(stage);
    }

    /**
     * Initialize all stages in order. If any stage fails,
     * previously initialized stages are shut down in reverse.
     *
     * @throws Exception if a stage fails to initialize
     */
    public void initialize() throws Exception {
        stages.sort(Comparator.comparingInt(Stage::order));

        for (Stage stage : stages) {
            try {
                Logger.debug("[Lifecycle] Initializing stage: %s (order=%d)", stage.name(), stage.order());
                stage.initialize(container);
                initializedStages.add(stage);
            } catch (Exception e) {
                Logger.severe("Stage '%s' failed to initialize: %s", stage.name(), e.getMessage());
                // Shut down any stages that already initialized
                shutdownInitialized();
                throw e;
            }
        }
    }

    /**
     * Shutdown all initialized stages in reverse order.
     */
    public void shutdown() {
        shutdownInitialized();
        container.clear();
    }

    private void shutdownInitialized() {
        // Reverse order shutdown
        for (int i = initializedStages.size() - 1; i >= 0; i--) {
            Stage stage = initializedStages.get(i);
            try {
                Logger.debug("[Lifecycle] Shutting down stage: %s", stage.name());
                stage.shutdown(container);
            } catch (Exception e) {
                Logger.warn("Stage '%s' failed to shut down cleanly: %s", stage.name(), e.getMessage());
            }
        }
        initializedStages.clear();
    }
}
