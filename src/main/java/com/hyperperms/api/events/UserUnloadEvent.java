package com.hyperperms.api.events;

import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a user is unloaded from the cache.
 * <p>
 * This event is fired when a user's data is removed from memory,
 * typically when they disconnect or when the cache is cleared.
 */
public final class UserUnloadEvent implements HyperPermsEvent {

    /**
     * The reason for the user unload.
     */
    public enum UnloadReason {
        /**
         * User was unloaded because they disconnected.
         */
        DISCONNECT,

        /**
         * User was unloaded via an API call.
         */
        API,

        /**
         * User was unloaded during cache cleanup.
         */
        CACHE_CLEANUP,

        /**
         * User was unloaded during plugin disable.
         */
        SHUTDOWN
    }

    private final UUID uuid;
    private final User user;
    private final UnloadReason reason;

    /**
     * Creates a new user unload event.
     *
     * @param uuid   the UUID of the user
     * @param user   the user being unloaded (may be null if already removed)
     * @param reason the reason for unloading
     */
    public UserUnloadEvent(@NotNull UUID uuid, @Nullable User user, @NotNull UnloadReason reason) {
        this.uuid = uuid;
        this.user = user;
        this.reason = reason;
    }

    @Override
    public EventType getType() {
        return EventType.USER_UNLOAD;
    }

    /**
     * Gets the UUID of the unloaded user.
     *
     * @return the UUID
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the user that was unloaded.
     * <p>
     * This may be null if the user was already removed from the cache
     * before the event was fired.
     *
     * @return the user, or null
     */
    @Nullable
    public User getUser() {
        return user;
    }

    /**
     * Gets the reason for the unload.
     *
     * @return the unload reason
     */
    @NotNull
    public UnloadReason getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "UserUnloadEvent{uuid=" + uuid + ", reason=" + reason + "}";
    }
}
