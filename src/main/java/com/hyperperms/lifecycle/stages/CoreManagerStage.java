package com.hyperperms.lifecycle.stages;

import com.hyperperms.api.events.EventBus;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Creates the EventBus and core managers (Group, Track, User), then loads all data.
 */
public final class CoreManagerStage implements Stage {

    @Override
    public @NotNull String name() {
        return "Core Managers";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        StorageProvider storage = container.get(StorageProvider.class);
        PermissionCache cache = container.get(PermissionCache.class);
        CacheInvalidator cacheInvalidator = container.get(CacheInvalidator.class);

        // Create event bus
        EventBus eventBus = new EventBus();
        container.register(EventBus.class, eventBus);

        // Create managers
        GroupManagerImpl groupManager = new GroupManagerImpl(storage, cacheInvalidator, eventBus);
        TrackManagerImpl trackManager = new TrackManagerImpl(storage);
        UserManagerImpl userManager = new UserManagerImpl(storage, cache, eventBus, config.getDefaultGroup(), config.getOwnerGroup());

        container.register(GroupManagerImpl.class, groupManager);
        container.register(TrackManagerImpl.class, trackManager);
        container.register(UserManagerImpl.class, userManager);

        // Load data
        groupManager.loadAll().join();
        trackManager.loadAll().join();
        userManager.loadAll().join();
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        // Save all user data
        container.getOptional(UserManagerImpl.class).ifPresent(userManager -> {
            try {
                userManager.saveAll().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.warn("Failed to save users on shutdown: %s", e.getMessage());
            }
        });

        // Clear event bus
        container.getOptional(EventBus.class).ifPresent(EventBus::clear);
    }
}
