package com.hyperperms.lifecycle.stages;

import com.hyperperms.api.events.EventBus;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.task.ExpiryCleanupTask;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts scheduled tasks: expiry cleanup and discovery auto-save.
 */
public final class SchedulerStage implements Stage {

    private ScheduledExecutorService scheduler;

    @Override
    public @NotNull String name() {
        return "Scheduler";
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        UserManagerImpl userManager = container.get(UserManagerImpl.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);
        EventBus eventBus = container.get(EventBus.class);
        CacheInvalidator cacheInvalidator = container.get(CacheInvalidator.class);
        RuntimePermissionDiscovery runtimeDiscovery = container.get(RuntimePermissionDiscovery.class);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyperPerms-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                new ExpiryCleanupTask(userManager, groupManager, eventBus, cacheInvalidator),
                config.getExpiryCheckInterval(),
                config.getExpiryCheckInterval(),
                TimeUnit.SECONDS
        );

        // Discovery auto-save every 5 minutes
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        runtimeDiscovery.save();
                    } catch (Exception e) {
                        Logger.warn("Failed to auto-save discovered permissions: %s", e.getMessage());
                    }
                },
                300, 300, TimeUnit.SECONDS
        );

        container.register(ScheduledExecutorService.class, scheduler);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
