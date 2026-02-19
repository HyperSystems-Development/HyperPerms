package com.hyperperms.storage;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * Base implementation for {@link StorageProvider} that provides shared infrastructure.
 * <p>
 * Concrete subclasses inherit:
 * <ul>
 *   <li>Single-threaded executor management with daemon threads</li>
 *   <li>{@link #executeAsync(Callable)} helper for wrapping operations in CompletableFuture</li>
 *   <li>{@link #runAsync(Runnable)} helper for void async operations</li>
 *   <li>Health tracking via {@link #setHealthy(boolean)} / {@link #isHealthy()}</li>
 *   <li>Standard {@link #shutdown()} lifecycle with graceful termination</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> All operations submitted via {@link #executeAsync} or {@link #runAsync}
 * execute on a single-threaded executor, ensuring serial access to storage resources.
 * Do not change to a multi-threaded executor without implementing proper synchronization
 * in concrete subclasses.
 */
public abstract class AbstractStorageProvider implements StorageProvider {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    protected final ExecutorService executor;
    protected volatile boolean healthy = false;

    /**
     * Creates a new storage provider with a single-threaded daemon executor.
     *
     * @param threadName the name for the executor thread (e.g., "HyperPerms-JsonStorage")
     */
    protected AbstractStorageProvider(@NotNull String threadName) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Async Helpers ====================

    /**
     * Executes a callable asynchronously on the storage executor and returns a CompletableFuture.
     * <p>
     * This is the primary method for submitting work that returns a value. All storage
     * read operations should use this method to ensure serial execution.
     *
     * @param callable the operation to execute
     * @param <T>      the return type
     * @return a future that completes with the result
     */
    protected <T> CompletableFuture<T> executeAsync(@NotNull Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Executes a runnable asynchronously on the storage executor and returns a CompletableFuture.
     * <p>
     * This is the primary method for submitting work that returns no value. All storage
     * write operations should use this method to ensure serial execution.
     *
     * @param runnable the operation to execute
     * @return a future that completes when the operation finishes
     */
    protected CompletableFuture<Void> runAsync(@NotNull Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    // ==================== Health Tracking ====================

    /**
     * Sets the health state of this storage provider.
     *
     * @param healthy true if the provider is functioning correctly
     */
    protected void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    // ==================== Lifecycle ====================

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            Logger.debugStorage("Shutting down %s storage...", getName());
            setHealthy(false);

            // Hook for subclasses to close resources (e.g., database connections)
            try {
                closeResources();
            } catch (Exception e) {
                Logger.warn("Error closing %s storage resources: %s", getName(), e.getMessage());
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Logger.warn("%s storage executor did not terminate in %ds, forcing shutdown",
                            getName(), SHUTDOWN_TIMEOUT_SECONDS);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            Logger.info("%s storage shut down", getName());
        });
    }

    /**
     * Hook for subclasses to close backend-specific resources during shutdown.
     * <p>
     * Called before the executor is shut down. Override this to close database connections,
     * file handles, etc. The default implementation does nothing.
     *
     * @throws Exception if resource cleanup fails
     */
    protected void closeResources() throws Exception {
        // Default no-op; subclasses override as needed
    }
}
