package com.hyperperms.api.events;

/**
 * Interface for events that can be cancelled.
 * <p>
 * When an event is cancelled, the action that triggered it will not be performed.
 * Not all events are cancellable - only those that implement this interface.
 */
public interface Cancellable {

    /**
     * Checks if this event has been cancelled.
     *
     * @return true if the event is cancelled
     */
    boolean isCancelled();

    /**
     * Sets the cancelled state of this event.
     *
     * @param cancelled true to cancel the event
     */
    void setCancelled(boolean cancelled);
}
