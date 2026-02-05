package com.hyperperms.api.events;

import com.hyperperms.model.Group;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a group's properties are modified.
 * <p>
 * This event is fired when properties like weight, display name, prefix, or suffix change.
 * For permission node changes, see {@link PermissionChangeEvent}.
 */
public final class GroupModifyEvent implements HyperPermsEvent {

    /**
     * The type of modification made to the group.
     */
    public enum ModifyType {
        /**
         * The group's display name was changed.
         */
        DISPLAY_NAME,

        /**
         * The group's weight was changed.
         */
        WEIGHT,

        /**
         * The group's prefix was changed.
         */
        PREFIX,

        /**
         * The group's suffix was changed.
         */
        SUFFIX,

        /**
         * The group's prefix priority was changed.
         */
        PREFIX_PRIORITY,

        /**
         * The group's suffix priority was changed.
         */
        SUFFIX_PRIORITY,

        /**
         * A parent group was added.
         */
        PARENT_ADD,

        /**
         * A parent group was removed.
         */
        PARENT_REMOVE
    }

    private final Group group;
    private final ModifyType modifyType;
    private final Object oldValue;
    private final Object newValue;

    /**
     * Creates a new group modify event.
     *
     * @param group      the group that was modified
     * @param modifyType the type of modification
     * @param oldValue   the old value (can be null)
     * @param newValue   the new value (can be null)
     */
    public GroupModifyEvent(@NotNull Group group, @NotNull ModifyType modifyType,
                            @Nullable Object oldValue, @Nullable Object newValue) {
        this.group = group;
        this.modifyType = modifyType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public EventType getType() {
        return EventType.GROUP_MODIFY;
    }

    /**
     * Gets the group that was modified.
     *
     * @return the group
     */
    @NotNull
    public Group getGroup() {
        return group;
    }

    /**
     * Gets the type of modification.
     *
     * @return the modification type
     */
    @NotNull
    public ModifyType getModifyType() {
        return modifyType;
    }

    /**
     * Gets the old value before the modification.
     *
     * @return the old value, or null
     */
    @Nullable
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * Gets the new value after the modification.
     *
     * @return the new value, or null
     */
    @Nullable
    public Object getNewValue() {
        return newValue;
    }

    /**
     * Gets the old value cast to the expected type.
     *
     * @param <T> the expected type
     * @return the old value, or null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getOldValueAs() {
        return (T) oldValue;
    }

    /**
     * Gets the new value cast to the expected type.
     *
     * @param <T> the expected type
     * @return the new value, or null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getNewValueAs() {
        return (T) newValue;
    }

    @Override
    public String toString() {
        return "GroupModifyEvent{group='" + group.getName() + "', modifyType=" + modifyType +
                ", oldValue=" + oldValue + ", newValue=" + newValue + "}";
    }
}
