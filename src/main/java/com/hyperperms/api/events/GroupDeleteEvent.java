package com.hyperperms.api.events;

import com.hyperperms.model.Group;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a group is deleted.
 * <p>
 * This event can be cancelled to prevent the group from being deleted.
 * The PRE state fires before deletion, the POST state fires after.
 */
public final class GroupDeleteEvent implements HyperPermsEvent, Cancellable {

    /**
     * The state of the group deletion event.
     */
    public enum State {
        /**
         * Before the group is deleted. The event can be cancelled at this point.
         */
        PRE,

        /**
         * After the group has been deleted. Cancellation has no effect.
         */
        POST
    }

    private final String groupName;
    private final Group group;
    private final State state;
    private boolean cancelled;

    /**
     * Creates a PRE event for group deletion.
     *
     * @param group the group being deleted
     */
    public GroupDeleteEvent(@NotNull Group group) {
        this.groupName = group.getName();
        this.group = group;
        this.state = State.PRE;
        this.cancelled = false;
    }

    /**
     * Creates a POST event for group deletion.
     *
     * @param groupName the name of the deleted group
     */
    public GroupDeleteEvent(@NotNull String groupName) {
        this.groupName = groupName;
        this.group = null;
        this.state = State.POST;
        this.cancelled = false;
    }

    @Override
    public EventType getType() {
        return EventType.GROUP_DELETE;
    }

    /**
     * Gets the name of the group being deleted.
     *
     * @return the group name
     */
    @NotNull
    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets the group being deleted.
     * <p>
     * This is only available in the PRE state. Returns null in POST state.
     *
     * @return the group, or null if POST state
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
        return "GroupDeleteEvent{groupName='" + groupName + "', state=" + state + ", cancelled=" + cancelled + "}";
    }
}
