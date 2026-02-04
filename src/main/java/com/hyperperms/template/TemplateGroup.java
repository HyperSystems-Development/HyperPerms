package com.hyperperms.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a group definition within a permission template.
 */
public final class TemplateGroup {

    private final String name;
    private final List<TemplatePermission> permissions;
    private final List<String> parents;
    private final int weight;
    private final String prefix;
    private final String suffix;
    private final String displayName;

    /**
     * Creates a new template group.
     *
     * @param name        the group name
     * @param permissions the list of permissions
     * @param parents     the list of parent group names
     * @param weight      the group weight for inheritance priority
     * @param prefix      the chat prefix
     * @param suffix      the chat suffix
     * @param displayName the display name (optional)
     */
    public TemplateGroup(
            @NotNull String name,
            @Nullable List<TemplatePermission> permissions,
            @Nullable List<String> parents,
            int weight,
            @Nullable String prefix,
            @Nullable String suffix,
            @Nullable String displayName
    ) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.permissions = permissions != null ? List.copyOf(permissions) : Collections.emptyList();
        this.parents = parents != null ? List.copyOf(parents) : Collections.emptyList();
        this.weight = weight;
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.displayName = displayName;
    }

    /**
     * Gets the group name.
     *
     * @return the group name
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Gets the list of permissions.
     *
     * @return an immutable list of permissions
     */
    @NotNull
    public List<TemplatePermission> getPermissions() {
        return permissions;
    }

    /**
     * Gets the list of parent group names.
     *
     * @return an immutable list of parent names
     */
    @NotNull
    public List<String> getParents() {
        return parents;
    }

    /**
     * Gets the group weight.
     *
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Gets the chat prefix.
     *
     * @return the prefix
     */
    @NotNull
    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets the chat suffix.
     *
     * @return the suffix
     */
    @NotNull
    public String getSuffix() {
        return suffix;
    }

    /**
     * Gets the display name.
     *
     * @return the display name, or null if not set
     */
    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this group has any parent groups.
     *
     * @return true if this group has parents
     */
    public boolean hasParents() {
        return !parents.isEmpty();
    }

    /**
     * Gets the number of permissions in this group.
     *
     * @return the permission count
     */
    public int getPermissionCount() {
        return permissions.size();
    }

    @Override
    public String toString() {
        return "TemplateGroup{name='" + name + "', weight=" + weight + 
               ", permissions=" + permissions.size() + ", parents=" + parents + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateGroup that = (TemplateGroup) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
