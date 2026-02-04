package com.hyperperms.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a complete permission template that can be applied to a server.
 * <p>
 * Templates contain predefined groups, tracks, and metadata for specific server types
 * (e.g., factions, survival, creative, etc.).
 */
public final class PermissionTemplate {

    private final String name;
    private final String displayName;
    private final String description;
    private final String version;
    private final String author;
    private final Map<String, TemplateGroup> groups;
    private final Map<String, TemplateTrack> tracks;
    private final TemplateMetadata metadata;
    private final boolean bundled;

    /**
     * Creates a new permission template.
     *
     * @param name        the template identifier
     * @param displayName the human-readable name
     * @param description the template description
     * @param version     the template version
     * @param author      the template author
     * @param groups      the groups in this template
     * @param tracks      the tracks in this template
     * @param metadata    additional metadata
     * @param bundled     whether this is a bundled template
     */
    public PermissionTemplate(
            @NotNull String name,
            @Nullable String displayName,
            @Nullable String description,
            @Nullable String version,
            @Nullable String author,
            @NotNull Map<String, TemplateGroup> groups,
            @Nullable Map<String, TemplateTrack> tracks,
            @Nullable TemplateMetadata metadata,
            boolean bundled
    ) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.displayName = displayName != null ? displayName : name;
        this.description = description != null ? description : "";
        this.version = version != null ? version : "1.0.0";
        this.author = author != null ? author : "Unknown";
        this.groups = Map.copyOf(groups);
        this.tracks = tracks != null ? Map.copyOf(tracks) : Collections.emptyMap();
        this.metadata = metadata != null ? metadata : TemplateMetadata.empty();
        this.bundled = bundled;
    }

    /**
     * Gets the template identifier (file name without extension).
     *
     * @return the template name
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return the display name
     */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the template description.
     *
     * @return the description
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * Gets the template version.
     *
     * @return the version string
     */
    @NotNull
    public String getVersion() {
        return version;
    }

    /**
     * Gets the template author.
     *
     * @return the author name
     */
    @NotNull
    public String getAuthor() {
        return author;
    }

    /**
     * Gets all groups in this template.
     *
     * @return an immutable map of group name to group
     */
    @NotNull
    public Map<String, TemplateGroup> getGroups() {
        return groups;
    }

    /**
     * Gets a specific group by name.
     *
     * @param name the group name
     * @return the group, or null if not found
     */
    @Nullable
    public TemplateGroup getGroup(@NotNull String name) {
        return groups.get(name);
    }

    /**
     * Gets all tracks in this template.
     *
     * @return an immutable map of track name to track
     */
    @NotNull
    public Map<String, TemplateTrack> getTracks() {
        return tracks;
    }

    /**
     * Gets a specific track by name.
     *
     * @param name the track name
     * @return the track, or null if not found
     */
    @Nullable
    public TemplateTrack getTrack(@NotNull String name) {
        return tracks.get(name);
    }

    /**
     * Gets the template metadata.
     *
     * @return the metadata
     */
    @NotNull
    public TemplateMetadata getMetadata() {
        return metadata;
    }

    /**
     * Checks if this is a bundled (built-in) template.
     *
     * @return true if bundled
     */
    public boolean isBundled() {
        return bundled;
    }

    /**
     * Gets the number of groups in this template.
     *
     * @return the group count
     */
    public int getGroupCount() {
        return groups.size();
    }

    /**
     * Gets the number of tracks in this template.
     *
     * @return the track count
     */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * Gets the total number of unique permissions across all groups.
     *
     * @return the total permission count
     */
    public int getTotalPermissionCount() {
        return groups.values().stream()
                .mapToInt(TemplateGroup::getPermissionCount)
                .sum();
    }

    /**
     * Gets all group names in this template, sorted by weight.
     *
     * @return a list of group names sorted by weight (ascending)
     */
    @NotNull
    public List<String> getGroupNamesSortedByWeight() {
        return groups.values().stream()
                .sorted(Comparator.comparingInt(TemplateGroup::getWeight))
                .map(TemplateGroup::getName)
                .toList();
    }

    @Override
    public String toString() {
        return "PermissionTemplate{name='" + name + "', displayName='" + displayName + 
               "', groups=" + groups.size() + ", tracks=" + tracks.size() + 
               ", bundled=" + bundled + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionTemplate that = (PermissionTemplate) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Metadata for a permission template.
     */
    public record TemplateMetadata(
            @NotNull List<String> recommendedPlugins,
            @Nullable String notes,
            @Nullable String minHyperPermsVersion
    ) {
        /**
         * Creates template metadata.
         */
        public TemplateMetadata {
            recommendedPlugins = recommendedPlugins != null ? List.copyOf(recommendedPlugins) : Collections.emptyList();
        }

        /**
         * Creates empty metadata.
         *
         * @return empty metadata instance
         */
        public static TemplateMetadata empty() {
            return new TemplateMetadata(Collections.emptyList(), null, null);
        }

        /**
         * Checks if there are recommended plugins.
         *
         * @return true if there are recommendations
         */
        public boolean hasRecommendedPlugins() {
            return !recommendedPlugins.isEmpty();
        }
    }
}
