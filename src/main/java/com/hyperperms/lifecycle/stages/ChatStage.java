package com.hyperperms.lifecycle.stages;

import com.hyperperms.HyperPerms;
import com.hyperperms.chat.ChatManager;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.tablist.TabListManager;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes the chat manager and tab list manager.
 */
public final class ChatStage implements Stage {

    private final HyperPerms plugin;

    public ChatStage(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "Chat";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        ChatManager chatManager = new ChatManager(plugin);
        chatManager.loadConfig();
        container.register(ChatManager.class, chatManager);

        TabListManager tabListManager = new TabListManager(plugin);
        tabListManager.loadConfig();
        container.register(TabListManager.class, tabListManager);
    }
}
