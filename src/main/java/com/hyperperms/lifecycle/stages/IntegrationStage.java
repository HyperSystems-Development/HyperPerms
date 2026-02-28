package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.chat.ChatManager;
import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.integration.*;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes soft-dependency plugin integrations.
 */
public final class IntegrationStage implements Stage {

    private final HyperPerms plugin;

    public IntegrationStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Integrations";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        ChatManager chatManager = container.get(ChatManager.class);

        // Faction integration
        Logger.debugIntegration("Initializing faction integration...");
        FactionIntegration factionIntegration = new FactionIntegration(plugin);
        factionIntegration.setEnabled(config.isFactionIntegrationEnabled());
        factionIntegration.setNoFactionDefault(config.getFactionNoFactionDefault());
        factionIntegration.setNoRankDefault(config.getFactionNoRankDefault());
        factionIntegration.setFactionFormat(config.getFactionFormat());
        factionIntegration.setPrefixEnabled(config.isFactionPrefixEnabled());
        factionIntegration.setPrefixFormat(config.getFactionPrefixFormat());
        factionIntegration.setShowRank(config.isFactionShowRank());
        factionIntegration.setPrefixWithRankFormat(config.getFactionPrefixWithRankFormat());
        chatManager.setFactionIntegration(factionIntegration);
        container.register(FactionIntegration.class, factionIntegration);

        // WerChat integration
        Logger.debugIntegration("Initializing WerChat integration...");
        WerChatIntegration werchatIntegration = new WerChatIntegration(plugin);
        werchatIntegration.setEnabled(config.isWerChatIntegrationEnabled());
        werchatIntegration.setNoChannelDefault(config.getWerChatNoChannelDefault());
        werchatIntegration.setChannelFormat(config.getWerChatChannelFormat());
        chatManager.setWerChatIntegration(werchatIntegration);
        container.register(WerChatIntegration.class, werchatIntegration);

        // PlaceholderAPI integration
        Logger.debugIntegration("Initializing PlaceholderAPI integration...");
        PlaceholderAPIIntegration placeholderApi = new PlaceholderAPIIntegration(plugin);
        placeholderApi.setEnabled(config.isPlaceholderAPIEnabled());
        placeholderApi.setParseExternal(config.isPlaceholderAPIParseExternal());
        chatManager.setPlaceholderAPIIntegration(placeholderApi);
        if (placeholderApi.isAvailable()) {
            Logger.info("PlaceholderAPI integration enabled - placeholders available");
        }
        container.register(PlaceholderAPIIntegration.class, placeholderApi);

        // MysticNameTags integration
        Logger.debugIntegration("Initializing MysticNameTags integration...");
        MysticNameTagsIntegration mysticNameTags = new MysticNameTagsIntegration(plugin);
        mysticNameTags.setEnabled(config.isMysticNameTagsEnabled());
        mysticNameTags.setRefreshOnPermissionChange(config.isMysticNameTagsRefreshOnPermissionChange());
        mysticNameTags.setRefreshOnGroupChange(config.isMysticNameTagsRefreshOnGroupChange());
        mysticNameTags.setTagPermissionPrefix(config.getMysticNameTagsPermissionPrefix());
        if (mysticNameTags.isAvailable()) {
            Logger.info("MysticNameTags integration enabled - tag permission sync active");
        }
        container.register(MysticNameTagsIntegration.class, mysticNameTags);

        // VaultUnlocked integration
        if (config.isVaultIntegrationEnabled()) {
            Logger.debugIntegration("Initializing VaultUnlocked integration...");
            VaultUnlockedIntegration.init(plugin);
        } else {
            Logger.debugIntegration("VaultUnlocked integration disabled in config");
        }
    }

    @Override
    public void shutdown(@NotNull ServiceContainer container) {
        VaultUnlockedIntegration.shutdown();

        container.getOptional(MysticNameTagsIntegration.class)
                .ifPresent(MysticNameTagsIntegration::unregister);

        container.getOptional(PlaceholderAPIIntegration.class)
                .ifPresent(PlaceholderAPIIntegration::unregister);
    }
}
