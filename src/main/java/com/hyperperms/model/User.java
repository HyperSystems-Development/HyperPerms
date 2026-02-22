package com.hyperperms.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a player's permission data.
 * <p>
 * Users hold direct permission nodes and group memberships. Effective permissions
 * are calculated by the permission resolver considering inheritance.
 */
public final class User extends PermissionHolderBase {

    private final UUID uuid;
    private volatile String username;
    private volatile String primaryGroup;
    private volatile String customPrefix;
    private volatile String customSuffix;

    /**
     * Creates a new user.
     *
     * @param uuid     the player's UUID
     * @param username the player's username (can be updated)
     */
    public User(@NotNull UUID uuid, @Nullable String username) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.username = username;
        this.primaryGroup = "default";
        this.customPrefix = null;
        this.customSuffix = null;
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @NotNull
    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(@NotNull String primaryGroup) {
        this.primaryGroup = Objects.requireNonNull(primaryGroup, "primaryGroup cannot be null");
    }

    @Nullable
    public String getCustomPrefix() {
        return customPrefix;
    }

    public void setCustomPrefix(@Nullable String customPrefix) {
        this.customPrefix = customPrefix;
    }

    @Nullable
    public String getCustomSuffix() {
        return customSuffix;
    }

    public void setCustomSuffix(@Nullable String customSuffix) {
        this.customSuffix = customSuffix;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return uuid.toString();
    }

    @Override
    @NotNull
    public String getFriendlyName() {
        return username != null ? username : uuid.toString();
    }

    /**
     * Override to always include the primary group in inherited groups.
     */
    @Override
    @NotNull
    public Set<String> getInheritedGroups() {
        Set<String> groups = new HashSet<>(super.getInheritedGroups());
        if (primaryGroup != null && !primaryGroup.isEmpty()) {
            groups.add(primaryGroup);
        }
        return Collections.unmodifiableSet(groups);
    }

    /**
     * Override to always include the primary group (applies in all contexts).
     */
    @Override
    @NotNull
    public Set<String> getInheritedGroups(@NotNull com.hyperperms.api.context.ContextSet contexts) {
        Set<String> groups = new HashSet<>(super.getInheritedGroups(contexts));
        if (primaryGroup != null && !primaryGroup.isEmpty()) {
            groups.add(primaryGroup);
        }
        return Collections.unmodifiableSet(groups);
    }

    /**
     * Checks if this user has any meaningful data (non-default state).
     *
     * @return true if the user has permissions or non-default settings
     */
    public boolean hasData() {
        return !nodes.isEmpty()
            || !primaryGroup.equals("default")
            || customPrefix != null
            || customSuffix != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return uuid.equals(user.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "User{uuid=" + uuid + ", username='" + username + "', primaryGroup='" + primaryGroup + "'}";
    }
}
