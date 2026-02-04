package com.hyperperms.template;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Represents a track (promotion ladder) definition within a permission template.
 */
public record TemplateTrack(
        @NotNull String name,
        @NotNull List<String> groups
) {
    /**
     * Creates a new template track.
     *
     * @param name   the track name
     * @param groups the ordered list of group names in the track
     */
    public TemplateTrack {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(groups, "groups cannot be null");
        groups = List.copyOf(groups);
    }

    /**
     * Gets the number of groups in this track.
     *
     * @return the group count
     */
    public int size() {
        return groups.size();
    }

    /**
     * Checks if this track is empty.
     *
     * @return true if the track has no groups
     */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * Checks if the track contains a specific group.
     *
     * @param groupName the group name to check
     * @return true if the group is in this track
     */
    public boolean contains(@NotNull String groupName) {
        return groups.contains(groupName);
    }

    /**
     * Gets the position of a group in the track.
     *
     * @param groupName the group name
     * @return the index (0-based), or -1 if not found
     */
    public int indexOf(@NotNull String groupName) {
        return groups.indexOf(groupName);
    }

    /**
     * Gets the first group in the track (lowest rank).
     *
     * @return the first group name, or null if empty
     */
    @org.jetbrains.annotations.Nullable
    public String getFirst() {
        return groups.isEmpty() ? null : groups.get(0);
    }

    /**
     * Gets the last group in the track (highest rank).
     *
     * @return the last group name, or null if empty
     */
    @org.jetbrains.annotations.Nullable
    public String getLast() {
        return groups.isEmpty() ? null : groups.get(groups.size() - 1);
    }

    @Override
    public String toString() {
        return "TemplateTrack{name='" + name + "', groups=" + groups + "}";
    }
}
