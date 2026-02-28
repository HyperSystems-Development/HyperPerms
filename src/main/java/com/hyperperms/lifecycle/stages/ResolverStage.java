package com.hyperperms.lifecycle.stages;

import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.context.ContextManager;
import com.hyperperms.context.PlayerContextProvider;
import com.hyperperms.context.calculators.*;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.resolver.PermissionResolver;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the permission resolver and context system.
 */
public final class ResolverStage implements Stage {

    @Override
    public @NotNull String name() {
        return "Resolver";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);

        // Initialize resolver
        PermissionResolver resolver = new PermissionResolver(groupManager::getGroup);
        container.register(PermissionResolver.class, resolver);

        // Initialize context system
        ContextManager contextManager = new ContextManager();
        container.register(ContextManager.class, contextManager);

        PlayerContextProvider playerContextProvider = PlayerContextProvider.EMPTY;
        container.register(PlayerContextProvider.class, playerContextProvider);

        // Register default context calculators
        contextManager.registerCalculator(new WorldContextCalculator(playerContextProvider));
        contextManager.registerCalculator(new GameModeContextCalculator(playerContextProvider));
        contextManager.registerCalculator(new TimeContextCalculator(playerContextProvider));
        contextManager.registerCalculator(new BiomeContextCalculator(playerContextProvider));
        contextManager.registerCalculator(new RegionContextCalculator(playerContextProvider));

        // Server context (only if configured)
        String serverName = config.getServerName();
        if (!serverName.isEmpty()) {
            contextManager.registerCalculator(new ServerContextCalculator(serverName));
        }

        Logger.debug("Registered %d context calculators", contextManager.getCalculatorCount());
    }
}
