package com.hyperperms.api.events;

import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a user's group membership changes.
 * <p>
 * This includes adding groups, removing groups, and changing the primary group.
 */
public final class UserGroupChangeEvent implements HyperPermsEvent {

    /**
     * The type of group change.
     */
    public enum ChangeType {
        /**
         * A group was added to the user.
         */
        ADD,

        /**
         * A group was removed from the user.
         */
        REMOVE,

        /**
         * The user's primary group was changed.
         */
        PRIMARY_CHANGE
    }

    private final User user;
    private final UUID uuid;
    private final ChangeType changeType;
    private final String groupName;
    private final String previousPrimaryGroup;

    /**
     * Creates a group add or remove event.
     *
     * @param user       the user
     * @param changeType the type of change (ADD or REMOVE)
     * @param groupName  the group being added or removed
     */
    public UserGroupChangeEvent(@NotNull User user, @NotNull ChangeType changeType, @NotNull String groupName) {
        this.user = user;
        this.uuid = user.getUuid();
        this.changeType = changeType;
        this.groupName = groupName;
        this.previousPrimaryGroup = null;
    }

    /**
     * Creates a primary group change event.
     *
     * @param user                 the user
     * @param previousPrimaryGroup the previous primary group name
     * @param newPrimaryGroup      the new primary group name
     */
    public UserGroupChangeEvent(@NotNull User user, @NotNull String previousPrimaryGroup, @NotNull String newPrimaryGroup) {
        this.user = user;
        this.uuid = user.getUuid();
        this.changeType = ChangeType.PRIMARY_CHANGE;
        this.groupName = newPrimaryGroup;
        this.previousPrimaryGroup = previousPrimaryGroup;
    }

    @Override
    public EventType getType() {
        return EventType.USER_GROUP_CHANGE;
    }

    /**
     * Gets the user whose group membership changed.
     *
     * @return the user
     */
    @NotNull
    public User getUser() {
        return user;
    }

    /**
     * Gets the UUID of the user.
     *
     * @return the UUID
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the type of group change.
     *
     * @return the change type
     */
    @NotNull
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Gets the group name involved in the change.
     * <p>
     * For ADD/REMOVE, this is the group being added or removed.
     * For PRIMARY_CHANGE, this is the new primary group.
     *
     * @return the group name
     */
    @NotNull
    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets the previous primary group name.
     * <p>
     * Only set for PRIMARY_CHANGE events.
     *
     * @return the previous primary group, or null if not a primary change
     */
    @Nullable
    public String getPreviousPrimaryGroup() {
        return previousPrimaryGroup;
    }

    @Override
    public String toString() {
        if (changeType == ChangeType.PRIMARY_CHANGE) {
            return "UserGroupChangeEvent{uuid=" + uuid + ", changeType=" + changeType +
                    ", previousGroup='" + previousPrimaryGroup + "', newGroup='" + groupName + "'}";
        }
        return "UserGroupChangeEvent{uuid=" + uuid + ", changeType=" + changeType + ", groupName='" + groupName + "'}";
    }
}
