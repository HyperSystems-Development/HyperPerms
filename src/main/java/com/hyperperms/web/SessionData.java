package com.hyperperms.web;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object that holds the complete permission state
 * for serialization to the web editor API.
 */
public final class SessionData {

    private final List<GroupDto> groups;
    private final List<UserDto> users;
    private final List<TrackDto> tracks;
    private final MetadataDto metadata;
    private final List<PluginPermissions> installedPlugins;

    private SessionData(
            @NotNull List<GroupDto> groups,
            @NotNull List<UserDto> users,
            @NotNull List<TrackDto> tracks,
            @NotNull MetadataDto metadata,
            @NotNull List<PluginPermissions> installedPlugins
    ) {
        this.groups = groups;
        this.users = users;
        this.tracks = tracks;
        this.metadata = metadata;
        this.installedPlugins = installedPlugins;
    }

    /**
     * Creates SessionData from the current HyperPerms state.
     */
    public static SessionData fromHyperPerms(@NotNull HyperPerms hyperPerms, int playerCount) {
        List<GroupDto> groups = new ArrayList<>();
        List<UserDto> users = new ArrayList<>();
        List<TrackDto> tracks = new ArrayList<>();

        // Serialize groups
        for (Group group : hyperPerms.getGroupManager().getLoadedGroups()) {
            groups.add(GroupDto.fromGroup(group));
        }

        // Serialize users
        for (User user : hyperPerms.getUserManager().getLoadedUsers()) {
            users.add(UserDto.fromUser(user));
        }

        // Serialize tracks
        for (Track track : hyperPerms.getTrackManager().getLoadedTracks()) {
            tracks.add(TrackDto.fromTrack(track));
        }

        // Create metadata
        MetadataDto metadata = new MetadataDto(
                hyperPerms.getConfig().getServerName(),
                "2.0.0", // Plugin version
                playerCount
        );

        // Scan installed plugin permissions
        PluginPermissionScanner scanner = new PluginPermissionScanner(hyperPerms);
        List<PluginPermissions> installedPlugins = scanner.scanInstalledPlugins();

        return new SessionData(groups, users, tracks, metadata, installedPlugins);
    }

    public List<GroupDto> getGroups() {
        return groups;
    }

    public List<UserDto> getUsers() {
        return users;
    }

    public List<TrackDto> getTracks() {
        return tracks;
    }

    public MetadataDto getMetadata() {
        return metadata;
    }

    public List<PluginPermissions> getInstalledPlugins() {
        return installedPlugins;
    }

    /**
     * Group DTO for serialization.
     */
    public static final class GroupDto {
        private final String name;
        private final String displayName;
        private final String prefix;
        private final String suffix;
        private final int weight;
        private final int prefixPriority;
        private final int suffixPriority;
        private final List<PermissionDto> permissions;
        private final List<String> parents;

        public GroupDto(
                @NotNull String name,
                String displayName,
                String prefix,
                String suffix,
                int weight,
                int prefixPriority,
                int suffixPriority,
                @NotNull List<PermissionDto> permissions,
                @NotNull List<String> parents
        ) {
            this.name = name;
            this.displayName = displayName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.weight = weight;
            this.prefixPriority = prefixPriority;
            this.suffixPriority = suffixPriority;
            this.permissions = permissions;
            this.parents = parents;
        }

        public static GroupDto fromGroup(@NotNull Group group) {
            List<PermissionDto> permissions = new ArrayList<>();
            for (Node node : group.getNodes()) {
                if (!node.isGroupNode()) {
                    permissions.add(PermissionDto.fromNode(node));
                }
            }

            return new GroupDto(
                    group.getName(),
                    group.getDisplayName(),
                    group.getPrefix(),
                    group.getSuffix(),
                    group.getWeight(),
                    group.getPrefixPriority(),
                    group.getSuffixPriority(),
                    permissions,
                    new ArrayList<>(group.getParentGroups())
            );
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public int getWeight() {
            return weight;
        }

        public int getPrefixPriority() {
            return prefixPriority;
        }

        public int getSuffixPriority() {
            return suffixPriority;
        }

        public List<PermissionDto> getPermissions() {
            return permissions;
        }

        public List<String> getParents() {
            return parents;
        }
    }

    /**
     * User DTO for serialization.
     */
    public static final class UserDto {
        private final String uuid;
        private final String username;
        private final String primaryGroup;
        private final List<PermissionDto> permissions;
        private final List<String> parents;
        private final List<String> groups;  // Same as parents, for web app compatibility
        private final String customPrefix;
        private final String customSuffix;

        public UserDto(
                @NotNull String uuid,
                String username,
                @NotNull String primaryGroup,
                @NotNull List<PermissionDto> permissions,
                @NotNull List<String> parents,
                String customPrefix,
                String customSuffix
        ) {
            this.uuid = uuid;
            this.username = username;
            this.primaryGroup = primaryGroup;
            this.permissions = permissions;
            this.parents = parents;
            this.groups = parents;  // Mirror parents to groups for web app compatibility
            this.customPrefix = customPrefix;
            this.customSuffix = customSuffix;
        }

        public static UserDto fromUser(@NotNull User user) {
            List<PermissionDto> permissions = new ArrayList<>();
            for (Node node : user.getNodes()) {
                if (!node.isGroupNode()) {
                    permissions.add(PermissionDto.fromNode(node));
                }
            }

            List<String> inheritedGroups = new ArrayList<>(user.getInheritedGroups());
            
            return new UserDto(
                    user.getUuid().toString(),
                    user.getUsername(),
                    user.getPrimaryGroup(),
                    permissions,
                    inheritedGroups,
                    user.getCustomPrefix(),
                    user.getCustomSuffix()
            );
        }

        public String getUuid() {
            return uuid;
        }

        public String getUsername() {
            return username;
        }

        public String getPrimaryGroup() {
            return primaryGroup;
        }

        public List<PermissionDto> getPermissions() {
            return permissions;
        }

        public List<String> getParents() {
            return parents;
        }

        public List<String> getGroups() {
            return groups;
        }

        public String getCustomPrefix() {
            return customPrefix;
        }

        public String getCustomSuffix() {
            return customSuffix;
        }
    }

    /**
     * Track DTO for serialization.
     */
    public static final class TrackDto {
        private final String name;
        private final List<String> groups;

        public TrackDto(@NotNull String name, @NotNull List<String> groups) {
            this.name = name;
            this.groups = groups;
        }

        public static TrackDto fromTrack(@NotNull Track track) {
            return new TrackDto(track.getName(), new ArrayList<>(track.getGroups()));
        }

        public String getName() {
            return name;
        }

        public List<String> getGroups() {
            return groups;
        }
    }

    /**
     * Permission DTO for serialization.
     */
    public static final class PermissionDto {
        private final String node;
        private final boolean value;
        private final Map<String, String> contexts;

        public PermissionDto(@NotNull String node, boolean value, @NotNull Map<String, String> contexts) {
            this.node = node;
            this.value = value;
            this.contexts = contexts;
        }

        public static PermissionDto fromNode(@NotNull Node node) {
            Map<String, String> contexts = new HashMap<>();
            for (var entry : node.getContexts().toSet()) {
                contexts.put(entry.key(), entry.value());
            }
            return new PermissionDto(node.getPermission(), node.getValue(), contexts);
        }

        public String getNode() {
            return node;
        }

        public boolean getValue() {
            return value;
        }

        public Map<String, String> getContexts() {
            return contexts;
        }
    }

    /**
     * Metadata DTO for serialization.
     */
    public static final class MetadataDto {
        private final String serverName;
        private final String pluginVersion;
        private final int playerCount;

        public MetadataDto(@NotNull String serverName, @NotNull String pluginVersion, int playerCount) {
            this.serverName = serverName;
            this.pluginVersion = pluginVersion;
            this.playerCount = playerCount;
        }

        public String getServerName() {
            return serverName;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public int getPlayerCount() {
            return playerCount;
        }
    }
}
