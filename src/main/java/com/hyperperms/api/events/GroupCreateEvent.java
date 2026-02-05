package com.hyperperms.api.events;

import com.hyperperms.model.Group;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a group is created.
 * <p>
 * This event can be cancelled to prevent the group from being created.
 * The PRE state fires before creation, the POST state fires after.
 */
public final class GroupCreateEvent implements HyperPermsEvent, Cancellable {

    /**
     * The state of the group creation event.
     */
    public enum State {
        /**
         * Before the group is created. The event can be cancelled at this point.
         */
        PRE,

        /**
         * After the group has been created. Cancellation has no effect.
         */
        POST
    }

    private final String groupName;
    private final Group group;
    private final State state;
    private boolean cancelled;

    /**
     * Creates a PRE event for group creation.
     *
     * @param groupName the name of the group being created
     */
    public GroupCreateEvent(@NotNull String groupName) {
        this.groupName = groupName;
        this.group = null;
        this.state = State.PRE;
        this.cancelled = false;
    }

    /**
     * Creates a POST event for group creation.
     *
     * @param group the created group
     */
    public GroupCreateEvent(@NotNull Group group) {
        this.groupName = group.getName();
        this.group = group;
        this.state = State.POST;
        this.cancelled = false;
    }

    @Override
    public EventType getType() {
        return EventType.GROUP_CREATE;
    }

    /**
     * Gets the name of the group being created.
     *
     * @return the group name
     */
    @NotNull
    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets the created group.
     * <p>
     * This is only available in the POST state. Returns null in PRE state.
     *
     * @return the group, or null if PRE state
     */
    @Nullable
    public Group getGroup() {
        return group;
    }

    /**
     * Gets the state of this event.
     *
     * @return the state
     */
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        // Only PRE events can be cancelled
        if (state == State.PRE) {
            this.cancelled = cancelled;
        }
    }

    @Override
    public String toString() {
        return "GroupCreateEvent{groupName='" + groupName + "', state=" + state + ", cancelled=" + cancelled + "}";
    }
}
