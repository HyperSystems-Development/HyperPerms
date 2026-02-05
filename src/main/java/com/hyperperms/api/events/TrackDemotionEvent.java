package com.hyperperms.api.events;

import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a user is demoted along a track.
 * <p>
 * This event can be cancelled to prevent the demotion.
 */
public final class TrackDemotionEvent implements HyperPermsEvent, Cancellable {

    private final User user;
    private final UUID uuid;
    private final Track track;
    private final String fromGroup;
    private final String toGroup;
    private boolean cancelled;

    /**
     * Creates a new track demotion event.
     *
     * @param user      the user being demoted
     * @param track     the track being followed
     * @param fromGroup the current group
     * @param toGroup   the group being demoted to (null if removed from track)
     */
    public TrackDemotionEvent(@NotNull User user, @NotNull Track track,
                              @NotNull String fromGroup, @Nullable String toGroup) {
        this.user = user;
        this.uuid = user.getUuid();
        this.track = track;
        this.fromGroup = fromGroup;
        this.toGroup = toGroup;
        this.cancelled = false;
    }

    @Override
    public EventType getType() {
        return EventType.TRACK_DEMOTION;
    }

    /**
     * Gets the user being demoted.
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
     * Gets the current group before demotion.
     *
     * @return the current group
     */
    @NotNull
    public String getFromGroup() {
        return fromGroup;
    }

    /**
     * Gets the group the user will be demoted to.
     * <p>
     * May be null if the user will be removed from the track entirely.
     *
     * @return the target group, or null if removed from track
     */
    @Nullable
    public String getToGroup() {
        return toGroup;
    }

    /**
     * Checks if the user will be removed from the track.
     * <p>
     * This happens when the user is at the first group on the track.
     *
     * @return true if the user will be removed from the track
     */
    public boolean willRemoveFromTrack() {
        return toGroup == null;
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
        return "TrackDemotionEvent{uuid=" + uuid + ", track='" + track.getName() +
                "', fromGroup='" + fromGroup + "', toGroup='" + toGroup + "', cancelled=" + cancelled + "}";
    }
}
