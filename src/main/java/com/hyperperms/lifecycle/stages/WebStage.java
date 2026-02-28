package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.backup.BackupManager;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.web.WebEditorService;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the web editor service and backup manager.
 */
public final class WebStage implements Stage {

    private final HyperPerms plugin;

    public WebStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Web";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        WebEditorService webEditorService = new WebEditorService(plugin, config);
        container.register(WebEditorService.class, webEditorService);

        BackupManager backupManager = new BackupManager(plugin);
        backupManager.start();
        container.register(BackupManager.class, backupManager);
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(BackupManager.class)
                .ifPresent(BackupManager::shutdown);
    }
}
