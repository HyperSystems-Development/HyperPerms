package com.hyperperms.api.events;

import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a user is promoted along a track.
 * <p>
 * This event can be cancelled to prevent the promotion.
 */
public final class TrackPromotionEvent implements HyperPermsEvent, Cancellable {

    private final User user;
    private final UUID uuid;
    private final Track track;
    private final String fromGroup;
    private final String toGroup;
    private boolean cancelled;

    /**
     * Creates a new track promotion event.
     *
     * @param user      the user being promoted
     * @param track     the track being followed
     * @param fromGroup the current group (null if not on track)
     * @param toGroup   the group being promoted to
     */
    public TrackPromotionEvent(@NotNull User user, @NotNull Track track,
                               @Nullable String fromGroup, @NotNull String toGroup) {
        this.user = user;
        this.uuid = user.getUuid();
        this.track = track;
        this.fromGroup = fromGroup;
        this.toGroup = toGroup;
        this.cancelled = false;
    }

    @Override
    public EventType getType() {
        return EventType.TRACK_PROMOTION;
    }

    /**
     * Gets the user being promoted.
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
     * Gets the track being followed.
     *
     * @return the track
     */
    @NotNull
    public Track getTrack() {
        return track;
    }

    /**
     * Gets the track name.
     *
     * @return the track name
     */
    @NotNull
    public String getTrackName() {
        return track.getName();
    }

    /**
     * Gets the current group before promotion.
     * <p>
     * May be null if the user is not currently on the track.
     *
     * @return the current group, or null
     */
    @Nullable
    public String getFromGroup() {
        return fromGroup;
    }

    /**
     * Gets the group the user will be promoted to.
     *
     * @return the target group
     */
    @NotNull
    public String getToGroup() {
        return toGroup;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String toString() {
        return "TrackPromotionEvent{uuid=" + uuid + ", track='" + track.getName() +
                "', fromGroup='" + fromGroup + "', toGroup='" + toGroup + "', cancelled=" + cancelled + "}";
    }
}
