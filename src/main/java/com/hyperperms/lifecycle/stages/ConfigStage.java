package com.hyperperms.lifecycle.stages;

import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.util.Logger;
import com.hyperperms.util.SQLiteDriverLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads plugin configuration and prepares the lib directory.
 */
public final class ConfigStage implements Stage {

    private final Path dataDirectory;

    public ConfigStage(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Config";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        // Initialize lib directory for optional SQLite driver
        Path libDir = dataDirectory.resolve("lib");
        try {
            Files.createDirectories(libDir);
        } catch (IOException e) {
            Logger.warn("Failed to create lib directory: %s", e.getMessage());
        }
        SQLiteDriverLoader.setLibDirectory(libDir);
        Logger.debug("SQLite lib directory: %s", libDir);

        // Load configuration
        HyperPermsConfig config = new HyperPermsConfig(dataDirectory);
        config.load();
        container.register(HyperPermsConfig.class, config);
    }
}
