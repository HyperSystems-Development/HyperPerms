package com.hyperperms.lifecycle.stages;

import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.storage.StorageFactory;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Initializes the storage provider and permission cache.
 */
public final class StorageStage implements Stage {

    private final Path dataDirectory;

    public StorageStage(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Storage";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);

        // Initialize storage
        StorageProvider storage = StorageFactory.createStorage(config, dataDirectory);
        storage.init().join();
        container.register(StorageProvider.class, storage);

        // Initialize cache
        PermissionCache cache = new PermissionCache(
                config.getCacheMaxSize(),
                config.getCacheExpirySeconds(),
                config.isCacheEnabled()
        );
        container.register(PermissionCache.class, cache);

        CacheInvalidator cacheInvalidator = new CacheInvalidator(cache);
        container.register(CacheInvalidator.class, cacheInvalidator);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(StorageProvider.class).ifPresent(storage -> {
            try {
                storage.shutdown().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.warn("Failed to shutdown storage cleanly: %s", e.getMessage());
            }
        });
    }
}
