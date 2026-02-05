package com.hyperperms.api;

import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Fluent builder for asynchronous permission checks.
 * <p>
 * Example usage:
 * <pre>
 * api.checkAsync(uuid)
 *    .permission("build.place")
 *    .inWorld("nether")
 *    .withGamemode("survival")
 *    .result()
 *    .thenAccept(canBuild -> {
 *        // Handle result asynchronously
 *    });
 * </pre>
 * <p>
 * All operations return CompletableFutures that complete asynchronously.
 * Use {@link #result(Consumer)} for callback-style handling.
 */
public final class AsyncPermissionCheckBuilder {

    private final HyperPermsAPI api;
    private final UUID uuid;
    private final Set<Context> contexts = new HashSet<>();
    private String permission;

    /**
     * Creates a new async permission check builder.
     *
     * @param api  the API instance
     * @param uuid the user UUID
     */
    public AsyncPermissionCheckBuilder(@NotNull HyperPermsAPI api, @NotNull UUID uuid) {
        this.api = api;
        this.uuid = uuid;
    }

    /**
     * Sets the permission to check.
     *
     * @param permission the permission string
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder permission(@NotNull String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Adds a context to the check.
     *
     * @param key   the context key
     * @param value the context value
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder with(@NotNull String key, @NotNull String value) {
        this.contexts.add(new Context(key, value));
        return this;
    }

    /**
     * Adds a world context.
     *
     * @param world the world name
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder inWorld(@NotNull String world) {
        return with("world", world);
    }

    /**
     * Adds a server context.
     *
     * @param server the server name
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder onServer(@NotNull String server) {
        return with("server", server);
    }

    /**
     * Adds a gamemode context.
     *
     * @param gamemode the gamemode
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder withGamemode(@NotNull String gamemode) {
        return with("gamemode", gamemode);
    }

    /**
     * Adds a biome context.
     *
     * @param biome the biome name
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder inBiome(@NotNull String biome) {
        return with("biome", biome);
    }

    /**
     * Adds a region context.
     *
     * @param region the region name
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder inRegion(@NotNull String region) {
        return with("region", region);
    }

    /**
     * Adds multiple contexts from an existing ContextSet.
     *
     * @param contextSet the contexts to add
     * @return this builder
     */
    @NotNull
    public AsyncPermissionCheckBuilder withContexts(@NotNull ContextSet contextSet) {
        this.contexts.addAll(contextSet.toSet());
        return this;
    }

    /**
     * Builds the ContextSet from the accumulated contexts.
     *
     * @return the context set
     */
    @NotNull
    private ContextSet buildContexts() {
        return contexts.isEmpty() ? ContextSet.empty() : ContextSet.of(contexts);
    }

    /**
     * Asynchronously checks the permission and returns the boolean result.
     *
     * @return a future that completes with true if the user has the permission
     * @throws IllegalStateException if permission was not set
     */
    @NotNull
    public CompletableFuture<Boolean> result() {
        if (permission == null) {
            throw new IllegalStateException("Permission must be set before calling result()");
        }
        return api.hasPermissionAsync(uuid, permission, buildContexts());
    }

    /**
     * Asynchronously checks the permission and invokes a callback with the result.
     *
     * @param callback the callback to invoke with the result
     * @throws IllegalStateException if permission was not set
     */
    public void result(@NotNull Consumer<Boolean> callback) {
        result().thenAcceptAsync(callback, api.getSyncExecutor());
    }

    /**
     * Asynchronously checks the permission and returns the TriState result.
     * <p>
     * This allows distinguishing between "denied" and "not set".
     *
     * @return a future that completes with the TriState value
     * @throws IllegalStateException if permission was not set
     */
    @NotNull
    public CompletableFuture<TriState> tristate() {
        if (permission == null) {
            throw new IllegalStateException("Permission must be set before calling tristate()");
        }
        return api.getPermissionValueAsync(uuid, permission, buildContexts());
    }

    /**
     * Asynchronously checks the permission and invokes a callback with the TriState result.
     *
     * @param callback the callback to invoke with the result
     * @throws IllegalStateException if permission was not set
     */
    public void tristate(@NotNull Consumer<TriState> callback) {
        tristate().thenAcceptAsync(callback, api.getSyncExecutor());
    }

    /**
     * Asynchronously checks if the user has ANY of the specified permissions.
     *
     * @param permissions the permissions to check
     * @return a future that completes with true if the user has any of the permissions
     */
    @NotNull
    public CompletableFuture<Boolean> hasAny(@NotNull String... permissions) {
        if (permissions.length == 0) {
            return CompletableFuture.completedFuture(false);
        }
        ContextSet ctx = buildContexts();
        CompletableFuture<Boolean>[] futures = Arrays.stream(permissions)
                .map(perm -> api.hasPermissionAsync(uuid, perm, ctx))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).thenApply(v -> {
            for (CompletableFuture<Boolean> future : futures) {
                if (future.join()) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Asynchronously checks if the user has ALL of the specified permissions.
     *
     * @param permissions the permissions to check
     * @return a future that completes with true if the user has all of the permissions
     */
    @NotNull
    public CompletableFuture<Boolean> hasAll(@NotNull String... permissions) {
        if (permissions.length == 0) {
            return CompletableFuture.completedFuture(true);
        }
        ContextSet ctx = buildContexts();
        CompletableFuture<Boolean>[] futures = Arrays.stream(permissions)
                .map(perm -> api.hasPermissionAsync(uuid, perm, ctx))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).thenApply(v -> {
            for (CompletableFuture<Boolean> future : futures) {
                if (!future.join()) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Asynchronously checks if the user has ANY of the specified permissions
     * and invokes a callback with the result.
     *
     * @param callback    the callback to invoke
     * @param permissions the permissions to check
     */
    public void hasAny(@NotNull Consumer<Boolean> callback, @NotNull String... permissions) {
        hasAny(permissions).thenAcceptAsync(callback, api.getSyncExecutor());
    }

    /**
     * Asynchronously checks if the user has ALL of the specified permissions
     * and invokes a callback with the result.
     *
     * @param callback    the callback to invoke
     * @param permissions the permissions to check
     */
    public void hasAll(@NotNull Consumer<Boolean> callback, @NotNull String... permissions) {
        hasAll(permissions).thenAcceptAsync(callback, api.getSyncExecutor());
    }
}
