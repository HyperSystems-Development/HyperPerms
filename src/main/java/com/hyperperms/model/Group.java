package com.hyperperms.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a permission group.
 * <p>
 * Groups can contain permission nodes and inherit from other groups.
 * The weight determines priority when resolving conflicting permissions.
 */
public final class Group extends PermissionHolderBase {

    private final String name;
    private volatile String displayName;
    private volatile int weight;
    private volatile String prefix;
    private volatile String suffix;
    private volatile int prefixPriority;
    private volatile int suffixPriority;

    /**
     * Creates a new group.
     *
     * @param name the group name (lowercase identifier)
     */
    public Group(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null").toLowerCase();
        this.displayName = this.name;
        this.weight = 0;
        this.prefix = null;
        this.suffix = null;
        this.prefixPriority = 0;
        this.suffixPriority = 0;
    }

    /**
     * Creates a new group with specified weight.
     *
     * @param name   the group name
     * @param weight the weight
     */
    public Group(@NotNull String name, int weight) {
        this(name);
        this.weight = weight;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName != null ? displayName : name;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    @Nullable
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
    }

    @Nullable
    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(@Nullable String suffix) {
        this.suffix = suffix;
    }

    public int getPrefixPriority() {
        return prefixPriority;
    }

    public void setPrefixPriority(int prefixPriority) {
        this.prefixPriority = prefixPriority;
    }

    public int getSuffixPriority() {
        return suffixPriority;
    }

    public void setSuffixPriority(int suffixPriority) {
        this.suffixPriority = suffixPriority;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return name;
    }

    @Override
    @NotNull
    public String getFriendlyName() {
        return displayName;
    }

    // ==================== Group-Specific Aliases ====================

    /**
     * Adds a parent group to inherit from.
     *
     * @param parentName the parent group name
     * @return the result
     */
    @NotNull
    public DataMutateResult addParent(@NotNull String parentName) {
        return addGroup(parentName, (Instant) null);
    }

    /**
     * Adds a parent group to inherit from with optional expiry.
     *
     * @param parentName the parent group name
     * @param expiry     the expiry time, or null for permanent
     * @return the result
     */
    @NotNull
    public DataMutateResult addParent(@NotNull String parentName, @Nullable Instant expiry) {
        return addGroup(parentName, expiry);
    }

    /**
     * Removes a parent group.
     *
     * @param parentName the parent group name
     * @return the result
     */
    @NotNull
    public DataMutateResult removeParent(@NotNull String parentName) {
        return removeGroup(parentName);
    }

    /**
     * Gets all parent groups (alias for getInheritedGroups for clarity).
     *
     * @return the parent group names
     */
    @NotNull
    public Set<String> getParentGroups() {
        return getInheritedGroups();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Group group)) return false;
        return name.equals(group.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Group{name='").append(name).append('\'');
        sb.append(", displayName='").append(displayName).append('\'');
        sb.append(", weight=").append(weight);
        if (prefix != null) {
            sb.append(", prefix='").append(prefix).append('\'');
        }
        if (suffix != null) {
            sb.append(", suffix='").append(suffix).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
