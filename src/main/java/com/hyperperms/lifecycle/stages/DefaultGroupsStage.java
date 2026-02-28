package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Loads default groups on first run and ensures the configured default group exists.
 */
public final class DefaultGroupsStage implements Stage {

    private final HyperPerms plugin;

    public DefaultGroupsStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Default Groups";
    }

    @Override
    public int order() {
        return 350; // After CoreManagerStage (300), before ResolverStage (400)
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);

        // Load default groups on first run
        if (groupManager.getLoadedGroups().isEmpty()) {
            plugin.loadDefaultGroups();
        }

        // Ensure default group exists
        if (config.shouldCreateDefaultGroup()) {
            groupManager.ensureDefaultGroup(config.getDefaultGroup());
        }
    }
}
