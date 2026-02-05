package com.hyperperms.api.events;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Event bus for HyperPerms events.
 * <p>
 * The event bus allows subscribing to and firing events with optional priorities.
 * Handlers are called in priority order from LOWEST to HIGHEST, then MONITOR.
 * <p>
 * Example usage:
 * <pre>
 * eventBus.subscribe(PermissionChangeEvent.class, event -> {
 *     System.out.println("Permission changed: " + event.getNode().getPermission());
 * });
 *
 * // With priority
 * eventBus.subscribe(PermissionChangeEvent.class, event -> {
 *     // Handle with high priority
 * }, EventPriority.HIGH);
 *
 * // Async handler
 * eventBus.subscribeAsync(PermissionChangeEvent.class, event -> {
 *     // Handle asynchronously
 * });
 * </pre>
 */
public final class EventBus {

    private final Map<Class<? extends HyperPermsEvent>, List<HandlerEntry<?>>> handlers;
    private final Executor asyncExecutor;

    /**
     * Creates a new event bus with the default async executor.
     */
    public EventBus() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Creates a new event bus with a custom async executor.
     *
     * @param asyncExecutor the executor for async handlers
     */
    public EventBus(@NotNull Executor asyncExecutor) {
        this.handlers = new ConcurrentHashMap<>();
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Subscribes to an event type with NORMAL priority.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param <T>        the event type
     * @return a subscription that can be used to unsubscribe
     */
    @NotNull
    public <T extends HyperPermsEvent> Subscription subscribe(@NotNull Class<T> eventClass,
                                                               @NotNull Consumer<T> handler) {
        return subscribe(eventClass, handler, EventPriority.NORMAL);
    }

    /**
     * Subscribes to an event type with a specific priority.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param priority   the handler priority
     * @param <T>        the event type
     * @return a subscription that can be used to unsubscribe
     */
    @NotNull
    public <T extends HyperPermsEvent> Subscription subscribe(@NotNull Class<T> eventClass,
                                                               @NotNull Consumer<T> handler,
                                                               @NotNull EventPriority priority) {
        HandlerEntry<T> entry = new HandlerEntry<>(handler, priority, false);
        handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(entry);
        sortHandlers(eventClass);
        return () -> unsubscribe(eventClass, entry);
    }

    /**
     * Subscribes to an event type with asynchronous handling.
     * <p>
     * Async handlers run on a separate thread and receive the event after
     * all synchronous handlers have completed.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param <T>        the event type
     * @return a subscription that can be used to unsubscribe
     */
    @NotNull
    public <T extends HyperPermsEvent> Subscription subscribeAsync(@NotNull Class<T> eventClass,
                                                                    @NotNull Consumer<T> handler) {
        return subscribeAsync(eventClass, handler, EventPriority.NORMAL);
    }

    /**
     * Subscribes to an event type with asynchronous handling and a specific priority.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param priority   the handler priority (relative to other async handlers)
     * @param <T>        the event type
     * @return a subscription that can be used to unsubscribe
     */
    @NotNull
    public <T extends HyperPermsEvent> Subscription subscribeAsync(@NotNull Class<T> eventClass,
                                                                    @NotNull Consumer<T> handler,
                                                                    @NotNull EventPriority priority) {
        HandlerEntry<T> entry = new HandlerEntry<>(handler, priority, true);
        handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(entry);
        sortHandlers(eventClass);
        return () -> unsubscribe(eventClass, entry);
    }

    /**
     * Unsubscribes a handler entry from an event type.
     *
     * @param eventClass the event class
     * @param entry      the handler entry
     * @param <T>        the event type
     */
    private <T extends HyperPermsEvent> void unsubscribe(@NotNull Class<T> eventClass,
                                                          @NotNull HandlerEntry<T> entry) {
        List<HandlerEntry<?>> list = handlers.get(eventClass);
        if (list != null) {
            list.remove(entry);
        }
    }

    /**
     * Unsubscribes a handler from an event type.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param <T>        the event type
     * @deprecated Use the Subscription returned from subscribe() instead
     */
    @Deprecated
    public <T extends HyperPermsEvent> void unsubscribe(@NotNull Class<T> eventClass,
                                                         @NotNull Consumer<T> handler) {
        List<HandlerEntry<?>> list = handlers.get(eventClass);
        if (list != null) {
            list.removeIf(entry -> entry.handler == handler);
        }
    }

    /**
     * Sorts handlers by priority.
     *
     * @param eventClass the event class
     */
    private void sortHandlers(Class<? extends HyperPermsEvent> eventClass) {
        List<HandlerEntry<?>> list = handlers.get(eventClass);
        if (list != null && list.size() > 1) {
            // CopyOnWriteArrayList doesn't support sort, so we need to work around it
            List<HandlerEntry<?>> sorted = new ArrayList<>(list);
            sorted.sort((a, b) -> Integer.compare(a.priority.getSlot(), b.priority.getSlot()));
            list.clear();
            list.addAll(sorted);
        }
    }

    /**
     * Fires an event to all subscribers synchronously.
     * <p>
     * Synchronous handlers are called in priority order. Async handlers
     * are executed after all sync handlers complete.
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @return the event (allows checking cancellation state)
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends HyperPermsEvent> T fire(@NotNull T event) {
        List<HandlerEntry<?>> list = handlers.get(event.getClass());
        if (list != null) {
            List<HandlerEntry<T>> asyncHandlers = null;

            for (HandlerEntry<?> entry : list) {
                HandlerEntry<T> typedEntry = (HandlerEntry<T>) entry;

                if (typedEntry.async) {
                    // Collect async handlers to run after sync handlers
                    if (asyncHandlers == null) {
                        asyncHandlers = new ArrayList<>();
                    }
                    asyncHandlers.add(typedEntry);
                } else {
                    // Execute sync handler immediately
                    try {
                        typedEntry.handler.accept(event);
                    } catch (Exception e) {
                        Logger.severe("Exception in event handler for " + event.getClass().getSimpleName(), e);
                    }
                }
            }

            // Fire async handlers after sync handlers complete
            if (asyncHandlers != null) {
                final List<HandlerEntry<T>> finalAsyncHandlers = asyncHandlers;
                asyncExecutor.execute(() -> {
                    for (HandlerEntry<T> entry : finalAsyncHandlers) {
                        try {
                            entry.handler.accept(event);
                        } catch (Exception e) {
                            Logger.severe("Exception in async event handler for " + event.getClass().getSimpleName(), e);
                        }
                    }
                });
            }
        }
        return event;
    }

    /**
     * Fires an event asynchronously.
     * <p>
     * All handlers (both sync and async registered) are called on the async executor.
     * Returns a future that completes when all handlers have finished.
     *
     * @param event the event to fire
     * @param <T>   the event type
     * @return a future that completes with the event when all handlers are done
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends HyperPermsEvent> CompletableFuture<T> fireAsync(@NotNull T event) {
        return CompletableFuture.supplyAsync(() -> {
            List<HandlerEntry<?>> list = handlers.get(event.getClass());
            if (list != null) {
                for (HandlerEntry<?> entry : list) {
                    try {
                        ((HandlerEntry<T>) entry).handler.accept(event);
                    } catch (Exception e) {
                        Logger.severe("Exception in event handler for " + event.getClass().getSimpleName(), e);
                    }
                }
            }
            return event;
        }, asyncExecutor);
    }

    /**
     * Gets the number of handlers registered for an event type.
     *
     * @param eventClass the event class
     * @return the number of handlers
     */
    public int getHandlerCount(@NotNull Class<? extends HyperPermsEvent> eventClass) {
        List<HandlerEntry<?>> list = handlers.get(eventClass);
        return list != null ? list.size() : 0;
    }

    /**
     * Gets the total number of handlers registered.
     *
     * @return the total handler count
     */
    public int getTotalHandlerCount() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Checks if any handlers are registered for an event type.
     *
     * @param eventClass the event class
     * @return true if handlers exist
     */
    public boolean hasHandlers(@NotNull Class<? extends HyperPermsEvent> eventClass) {
        List<HandlerEntry<?>> list = handlers.get(eventClass);
        return list != null && !list.isEmpty();
    }

    /**
     * Clears all subscriptions.
     */
    public void clear() {
        handlers.clear();
    }

    /**
     * Clears all subscriptions for a specific event type.
     *
     * @param eventClass the event class
     */
    public void clear(@NotNull Class<? extends HyperPermsEvent> eventClass) {
        handlers.remove(eventClass);
    }

    /**
     * Represents a subscription that can be cancelled.
     */
    @FunctionalInterface
    public interface Subscription {
        /**
         * Cancels this subscription.
         */
        void unsubscribe();
    }

    /**
     * Internal handler entry with priority and async flag.
     */
    private record HandlerEntry<T extends HyperPermsEvent>(
            Consumer<T> handler,
            EventPriority priority,
            boolean async
    ) {}
}
