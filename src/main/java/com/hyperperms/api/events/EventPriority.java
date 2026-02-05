package com.hyperperms.api.events;

/**
 * Represents the priority at which an event handler will be called.
 * <p>
 * Handlers are called in order from LOWEST to HIGHEST, then MONITOR.
 * Lower priority handlers are called first, allowing higher priority handlers
 * to override or react to their changes.
 * <p>
 * The MONITOR priority is special - it should be used only for monitoring
 * the outcome of an event, and should never modify the event state.
 */
public enum EventPriority {

    /**
     * Called first. Use for initial processing.
     */
    LOWEST(0),

    /**
     * Called after LOWEST.
     */
    LOW(1),

    /**
     * Default priority. Called after LOW.
     */
    NORMAL(2),

    /**
     * Called after NORMAL.
     */
    HIGH(3),

    /**
     * Called after HIGH. Use for final processing.
     */
    HIGHEST(4),

    /**
     * Called last, after all other priorities.
     * <p>
     * MONITOR handlers should NEVER modify the event or cancel it.
     * Use this only for logging, analytics, or other passive monitoring.
     */
    MONITOR(5);

    private final int slot;

    EventPriority(int slot) {
        this.slot = slot;
    }

    /**
     * Gets the slot number for this priority.
     * Lower numbers are called first.
     *
     * @return the slot number
     */
    public int getSlot() {
        return slot;
    }
}
