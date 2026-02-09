package com.hyperperms.web.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents a single change from the web editor.
 * Different change types have different fields populated.
 */
public final class Change {

    /**
     * Type of change operation.
     */
    public enum Type {
        PERMISSION_ADDED,
        PERMISSION_REMOVED,
        PERMISSION_MODIFIED,
        GROUP_CREATED,
        GROUP_DELETED,
        PARENT_ADDED,
        PARENT_REMOVED,
        META_CHANGED,
        WEIGHT_CHANGED,
        TRACK_CREATED,
        TRACK_DELETED,
        TRACK_MODIFIED
    }

    // Common fields
    private final Type type;
    private final String targetType; // "group" or "user"
    private final String target;     // group name or user uuid

    // Permission change fields
    private final String node;
    private final Boolean value;
    private final Boolean oldValue;
    private final Boolean newValue;
    private final Map<String, String> contexts;
    private final Long expiry; // epoch millis, null = permanent

    // Group creation/deletion fields
    private final GroupData group;
    private final String groupName;

    // Parent change fields
    private final String parent;

    // Meta change fields
    private final String key;
    private final String metaOldValue;
    private final String metaNewValue;

    // Weight change fields
    private final Integer oldWeight;
    private final Integer newWeight;

    // Track fields
    private final TrackData track;
    private final String trackName;
    private final List<String> oldGroups;
    private final List<String> newGroups;

    private Change(Builder builder) {
        this.type = builder.type;
        this.targetType = builder.targetType;
        this.target = builder.target;
        this.node = builder.node;
        this.value = builder.value;
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.contexts = builder.contexts;
        this.expiry = builder.expiry;
        this.group = builder.group;
        this.groupName = builder.groupName;
        this.parent = builder.parent;
        this.key = builder.key;
        this.metaOldValue = builder.metaOldValue;
        this.metaNewValue = builder.metaNewValue;
        this.oldWeight = builder.oldWeight;
        this.newWeight = builder.newWeight;
        this.track = builder.track;
        this.trackName = builder.trackName;
        this.oldGroups = builder.oldGroups;
        this.newGroups = builder.newGroups;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    @Nullable
    public String getTargetType() {
        return targetType;
    }

    @Nullable
    public String getTarget() {
        return target;
    }

    @Nullable
    public String getNode() {
        return node;
    }

    @Nullable
    public Boolean getValue() {
        return value;
    }

    @Nullable
    public Boolean getOldValue() {
        return oldValue;
    }

    @Nullable
    public Boolean getNewValue() {
        return newValue;
    }

    @Nullable
    public Map<String, String> getContexts() {
        return contexts;
    }

    @Nullable
    public Long getExpiry() {
        return expiry;
    }

    @Nullable
    public GroupData getGroup() {
        return group;
    }

    @Nullable
    public String getGroupName() {
        return groupName;
    }

    @Nullable
    public String getParent() {
        return parent;
    }

    @Nullable
    public String getKey() {
        return key;
    }

    @Nullable
    public String getMetaOldValue() {
        return metaOldValue;
    }

    @Nullable
    public String getMetaNewValue() {
        return metaNewValue;
    }

    @Nullable
    public Integer getOldWeight() {
        return oldWeight;
    }

    @Nullable
    public Integer getNewWeight() {
        return newWeight;
    }

    @Nullable
    public TrackData getTrack() {
        return track;
    }

    @Nullable
    public String getTrackName() {
        return trackName;
    }

    @Nullable
    public List<String> getOldGroups() {
        return oldGroups;
    }

    @Nullable
    public List<String> getNewGroups() {
        return newGroups;
    }

    public static Builder builder(@NotNull Type type) {
        return new Builder(type);
    }

    /**
     * Group data for group_created changes.
     */
    public static final class GroupData {
        private final String name;
        private final String displayName;
        private final int weight;
        private final String prefix;
        private final String suffix;
        private final int prefixPriority;
        private final int suffixPriority;
        private final List<PermissionNode> permissions;
        private final List<String> parents;

        public GroupData(
                @NotNull String name,
                @Nullable String displayName,
                int weight,
                @Nullable String prefix,
                @Nullable String suffix,
                int prefixPriority,
                int suffixPriority,
                @NotNull List<PermissionNode> permissions,
                @NotNull List<String> parents
        ) {
            this.name = name;
            this.displayName = displayName;
            this.weight = weight;
            this.prefix = prefix;
            this.suffix = suffix;
            this.prefixPriority = prefixPriority;
            this.suffixPriority = suffixPriority;
            this.permissions = permissions;
            this.parents = parents;
        }

        @NotNull
        public String getName() {
            return name;
        }

        @Nullable
        public String getDisplayName() {
            return displayName;
        }

        public int getWeight() {
            return weight;
        }

        @Nullable
        public String getPrefix() {
            return prefix;
        }

        @Nullable
        public String getSuffix() {
            return suffix;
        }

        public int getPrefixPriority() {
            return prefixPriority;
        }

        public int getSuffixPriority() {
            return suffixPriority;
        }

        @NotNull
        public List<PermissionNode> getPermissions() {
            return permissions;
        }

        @NotNull
        public List<String> getParents() {
            return parents;
        }
    }

    /**
     * Track data for track_created changes.
     */
    public static final class TrackData {
        private final String name;
        private final List<String> groups;

        public TrackData(@NotNull String name, @NotNull List<String> groups) {
            this.name = name;
            this.groups = groups;
        }

        @NotNull
        public String getName() {
            return name;
        }

        @NotNull
        public List<String> getGroups() {
            return groups;
        }
    }

    /**
     * Permission node with contexts and optional expiry.
     */
    public static final class PermissionNode {
        private final String node;
        private final boolean value;
        private final Map<String, String> contexts;
        private final Long expiry; // epoch millis, null = permanent

        public PermissionNode(@NotNull String node, boolean value, @NotNull Map<String, String> contexts, @Nullable Long expiry) {
            this.node = node;
            this.value = value;
            this.contexts = contexts;
            this.expiry = expiry;
        }

        public PermissionNode(@NotNull String node, boolean value, @NotNull Map<String, String> contexts) {
            this(node, value, contexts, null);
        }

        @NotNull
        public String getNode() {
            return node;
        }

        public boolean getValue() {
            return value;
        }

        @NotNull
        public Map<String, String> getContexts() {
            return contexts;
        }

        @Nullable
        public Long getExpiry() {
            return expiry;
        }
    }

    public static final class Builder {
        private final Type type;
        private String targetType;
        private String target;
        private String node;
        private Boolean value;
        private Boolean oldValue;
        private Boolean newValue;
        private Map<String, String> contexts;
        private Long expiry;
        private GroupData group;
        private String groupName;
        private String parent;
        private String key;
        private String metaOldValue;
        private String metaNewValue;
        private Integer oldWeight;
        private Integer newWeight;
        private TrackData track;
        private String trackName;
        private List<String> oldGroups;
        private List<String> newGroups;

        private Builder(Type type) {
            this.type = type;
        }

        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder node(String node) {
            this.node = node;
            return this;
        }

        public Builder value(Boolean value) {
            this.value = value;
            return this;
        }

        public Builder oldValue(Boolean oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(Boolean newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder contexts(Map<String, String> contexts) {
            this.contexts = contexts;
            return this;
        }

        public Builder expiry(Long expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder group(GroupData group) {
            this.group = group;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public Builder parent(String parent) {
            this.parent = parent;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder metaOldValue(String metaOldValue) {
            this.metaOldValue = metaOldValue;
            return this;
        }

        public Builder metaNewValue(String metaNewValue) {
            this.metaNewValue = metaNewValue;
            return this;
        }

        public Builder oldWeight(Integer oldWeight) {
            this.oldWeight = oldWeight;
            return this;
        }

        public Builder newWeight(Integer newWeight) {
            this.newWeight = newWeight;
            return this;
        }

        public Builder track(TrackData track) {
            this.track = track;
            return this;
        }

        public Builder trackName(String trackName) {
            this.trackName = trackName;
            return this;
        }

        public Builder oldGroups(List<String> oldGroups) {
            this.oldGroups = oldGroups;
            return this;
        }

        public Builder newGroups(List<String> newGroups) {
            this.newGroups = newGroups;
            return this;
        }

        public Change build() {
            return new Change(this);
        }
    }
}
