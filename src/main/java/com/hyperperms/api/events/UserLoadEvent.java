package com.hyperperms.api.events;

import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a user's data is loaded.
 * <p>
 * This event is fired when a user is loaded from storage into the cache,
 * typically when they first connect or when their data is explicitly loaded.
 */
public final class UserLoadEvent implements HyperPermsEvent {

    /**
     * The source that triggered the user load.
     */
    public enum LoadSource {
        /**
         * User data was loaded because the player joined.
         */
        JOIN,

        /**
         * User data was loaded via an API call.
         */
        API,

        /**
         * User data was loaded during data reload.
         */
        RELOAD,

        /**
         * User data was loaded from storage on demand.
         */
        STORAGE
    }

    private final User user;
    private final UUID uuid;
    private final LoadSource source;
    private final boolean newUser;

    /**
     * Creates a new user load event.
     *
     * @param user    the loaded user
     * @param source  the source of the load
     * @param newUser true if this is a newly created user
     */
    public UserLoadEvent(@NotNull User user, @NotNull LoadSource source, boolean newUser) {
        this.user = user;
        this.uuid = user.getUuid();
        this.source = source;
        this.newUser = newUser;
    }

    @Override
    public EventType getType() {
        return EventType.USER_LOAD;
    }

    /**
     * Gets the loaded user.
     *
     * @return the user
     */
    @NotNull
    public User getUser() {
        return user;
    }

    /**
     * Gets the UUID of the loaded user.
     *
     * @return the UUID
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the source of the load.
     *
     * @return the load source
     */
    @NotNull
    public LoadSource getSource() {
        return source;
    }

    /**
     * Checks if this is a newly created user.
     * <p>
     * A new user is one that did not exist in storage and was created
     * with default permissions.
     *
     * @return true if the user is new
     */
    public boolean isNewUser() {
        return newUser;
    }

    @Override
    public String toString() {
        return "UserLoadEvent{uuid=" + uuid + ", source=" + source + ", newUser=" + newUser + "}";
    }
}
