package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.analytics.AnalyticsManager;
import com.hyperperms.api.MetricsAPI;
import com.hyperperms.api.QueryAPI;
import com.hyperperms.cache.PermissionCache;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.metrics.MetricsAPIImpl;
import com.hyperperms.query.QueryAPIImpl;
import com.hyperperms.update.UpdateChecker;
import com.hyperperms.update.UpdateNotificationPreferences;
import com.hyperperms.util.ConsoleLinks;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Initializes analytics, update checking, console links, verbose mode, and API implementations.
 */
public final class AnalyticsStage implements Stage {

    private final HyperPerms plugin;
    private final Path dataDirectory;

    public AnalyticsStage(@NotNull HyperPerms plugin, @NotNull Path dataDirectory) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public @NotNull String name() {
        return "Analytics";
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        UserManagerImpl userManager = container.get(UserManagerImpl.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);
        TrackManagerImpl trackManager = container.get(TrackManagerImpl.class);
        PermissionCache cache = container.get(PermissionCache.class);

        // Console links
        ConsoleLinks.setEnabled(config.isConsoleClickableLinksEnabled());
        ConsoleLinks.setForceOsc8(config.isConsoleForceOsc8());
        Logger.debug("Console links: enabled=%s, forceOsc8=%s, osc8Supported=%s",
                config.isConsoleClickableLinksEnabled(),
                config.isConsoleForceOsc8(),
                ConsoleLinks.isOsc8Supported());

        // Analytics manager
        AnalyticsManager analyticsManager = new AnalyticsManager(plugin);
        analyticsManager.start();
        container.register(AnalyticsManager.class, analyticsManager);

        // Update checker
        if (config.isUpdateCheckEnabled()) {
            UpdateChecker updateChecker = new UpdateChecker(plugin, HyperPerms.VERSION, config.getUpdateCheckUrl());
            updateChecker.checkForUpdates().thenAccept(info -> {
                if (info != null) {
                    Logger.info("[Update] A new version is available: v%s (current: v%s)",
                            info.version(), HyperPerms.VERSION);
                    if (config.isUpdateChangelogEnabled() && info.changelog() != null && !info.changelog().isEmpty()) {
                        Logger.info("[Update] Changelog: %s", info.changelog());
                    }
                }
            });
            container.register(UpdateChecker.class, updateChecker);
        }

        // Update notification preferences
        UpdateNotificationPreferences notificationPrefs = new UpdateNotificationPreferences(dataDirectory);
        notificationPrefs.load();
        container.register(UpdateNotificationPreferences.class, notificationPrefs);

        // API implementations
        QueryAPI queryApi = new QueryAPIImpl(userManager, groupManager, () -> trackManager.getLoadedTracks());
        container.register(QueryAPI.class, queryApi);

        if (analyticsManager.isEnabled()) {
            MetricsAPI metricsApi = new MetricsAPIImpl(
                    analyticsManager,
                    () -> cache.getStatistics(),
                    () -> (int) cache.size()
            );
            container.register(MetricsAPI.class, metricsApi);
        }
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        container.getOptional(AnalyticsManager.class)
                .ifPresent(AnalyticsManager::stop);
    }
}
