package com.hyperperms.context.calculators;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.context.ContextCalculator;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Context calculator that adds the server name as context.
 * <p>
 * Adds context: {@code server=<servername>}
 * <p>
 * The server name is configured in config.json and is useful for
 * network setups where permissions should only apply on specific servers.
 * <p>
 * Example usage in permissions:
 * <pre>
 * /hp user Player permission set some.permission server=lobby
 * /hp user Player permission set some.permission server=survival
 * </pre>
 */
public final class ServerContextCalculator implements ContextCalculator {

    /**
     * The context key for server contexts.
     */
    public static final String KEY = "server";

    private final String serverName;

    /**
     * Creates a new server context calculator.
     *
     * @param serverName the name of this server (from config)
     */
    public ServerContextCalculator(@NotNull String serverName) {
        this.serverName = Objects.requireNonNull(serverName, "serverName cannot be null").toLowerCase();
    }

    @Override
    public void calculate(@NotNull UUID uuid, @NotNull ContextSet.Builder builder) {
        if (!serverName.isEmpty()) {
            builder.add(KEY, serverName);
            Logger.debugContext("Calculated context %s=%s for %s", KEY, serverName, uuid);
        }
    }

    /**
     * Gets the configured server name.
     *
     * @return the server name
     */
    @NotNull
    public String getServerName() {
        return serverName;
    }
}
