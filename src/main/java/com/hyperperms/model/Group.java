package com.hyperperms.model;

import com.hyperperms.api.PermissionHolder;
import com.hyperperms.api.PermissionHolderListener;
import com.hyperperms.api.TemporaryPermissionInfo;
import com.hyperperms.api.context.ContextSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents a permission group.
 * <p>
 * Groups can contain permission nodes and inherit from other groups.
 * The weight determines priority when resolving conflicting permissions.
 */
public final class Group implements PermissionHolder {

    private final String name;
    private volatile String displayName;
    private volatile int weight;
    private volatile String prefix;
    private volatile String suffix;
    private volatile int prefixPriority;
    private volatile int suffixPriority;
    private final Set<Node> nodes = ConcurrentHashMap.newKeySet();
    private volatile PermissionHolderListener listener = PermissionHolderListener.EMPTY;

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

    /**
     * Gets the group's lowercase identifier name.
     *
     * @return the name
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Gets the group's display name.
     *
     * @return the display name
     */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the group's display name.
     *
     * @param displayName the display name
     */
    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName != null ? displayName : name;
    }

    /**
     * Gets the group's weight.
     * Higher weight = higher priority when resolving conflicts.
     *
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Sets the group's weight.
     *
     * @param weight the weight
     */
    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * Gets the group's chat prefix.
     *
     * @return the prefix, or null if not set
     */
    @Nullable
    public String getPrefix() {
        return prefix;
    }

    /**
     * Sets the group's chat prefix.
     * Supports color codes like &a, &c, etc.
     *
     * @param prefix the prefix, or null to clear
     */
    public void setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets the group's chat suffix.
     *
     * @return the suffix, or null if not set
     */
    @Nullable
    public String getSuffix() {
        return suffix;
    }

    /**
     * Sets the group's chat suffix.
     * Supports color codes like &a, &c, etc.
     *
     * @param suffix the suffix, or null to clear
     */
    public void setSuffix(@Nullable String suffix) {
        this.suffix = suffix;
    }

    /**
     * Gets the prefix priority.
     * When a user has multiple groups, the highest priority prefix is used.
     *
     * @return the prefix priority
     */
    public int getPrefixPriority() {
        return prefixPriority;
    }

    /**
     * Sets the prefix priority.
     *
     * @param prefixPriority the priority
     */
    public void setPrefixPriority(int prefixPriority) {
        this.prefixPriority = prefixPriority;
    }

    /**
     * Gets the suffix priority.
     * When a user has multiple groups, the highest priority suffix is used.
     *
     * @return the suffix priority
     */
    public int getSuffixPriority() {
        return suffixPriority;
    }

    /**
     * Sets the suffix priority.
     *
     * @param suffixPriority the priority
     */
    public void setSuffixPriority(int suffixPriority) {
        this.suffixPriority = suffixPriority;
    }

    /**
     * Sets the permission holder listener.
     * <p>
     * The listener will be notified of changes to this group's permissions and inheritance.
     * This is typically set by the GroupManager when the group is loaded.
     *
     * @param listener the listener, or null to remove
     */
    public void setListener(@Nullable PermissionHolderListener listener) {
        this.listener = listener != null ? listener : PermissionHolderListener.EMPTY;
    }

    /**
     * Gets the current permission holder listener.
     *
     * @return the listener, never null
     */
    @NotNull
    public PermissionHolderListener getListener() {
        return listener;
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

    @Override
    @NotNull
    public Set<Node> getNodes() {
        return Collections.unmodifiableSet(new HashSet<>(nodes));
    }

    @Override
    @NotNull
    public Set<Node> getNodes(@NotNull ContextSet contexts) {
        return nodes.stream()
                .filter(node -> node.appliesIn(contexts))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @NotNull
    public Set<Node> getNodes(boolean includeExpired) {
        if (includeExpired) {
            return getNodes();
        }
        return nodes.stream()
                .filter(node -> !node.isExpired())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean hasNode(@NotNull Node node) {
        return nodes.contains(node);
    }

    @Override
    @NotNull
    public DataMutateResult addNode(@NotNull Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (nodes.add(node)) {
            listener.onNodeAdded(this, node, DataMutateResult.SUCCESS);
            return DataMutateResult.SUCCESS;
        }
        listener.onNodeAdded(this, node, DataMutateResult.ALREADY_EXISTS);
        return DataMutateResult.ALREADY_EXISTS;
    }

    @Override
    @NotNull
    public DataMutateResult removeNode(@NotNull Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (nodes.remove(node)) {
            listener.onNodeRemoved(this, node, DataMutateResult.SUCCESS);
            return DataMutateResult.SUCCESS;
        }
        listener.onNodeRemoved(this, node, DataMutateResult.DOES_NOT_EXIST);
        return DataMutateResult.DOES_NOT_EXIST;
    }

    @Override
    @NotNull
    public DataMutateResult removeNode(@NotNull String permission) {
        Objects.requireNonNull(permission, "permission cannot be null");
        String lowerPerm = permission.toLowerCase();
        List<Node> toRemove = nodes.stream()
                .filter(node -> node.getPermission().equals(lowerPerm))
                .toList();
        if (toRemove.isEmpty()) {
            return DataMutateResult.DOES_NOT_EXIST;
        }
        for (Node node : toRemove) {
            nodes.remove(node);
            listener.onNodeRemoved(this, node, DataMutateResult.SUCCESS);
        }
        return DataMutateResult.SUCCESS;
    }

    @Override
    @NotNull
    public DataMutateResult setNode(@NotNull Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        nodes.removeIf(existing -> existing.equalsIgnoringExpiry(node));
        nodes.add(node);
        listener.onNodeSet(this, node, DataMutateResult.SUCCESS);
        return DataMutateResult.SUCCESS;
    }

    @Override
    public void clearNodes() {
        nodes.clear();
        listener.onNodesCleared(this, null);
    }

    @Override
    public void clearNodes(@NotNull ContextSet contexts) {
        nodes.removeIf(node -> node.getContexts().equals(contexts));
        listener.onNodesCleared(this, contexts);
    }

    @Override
    @NotNull
    public Set<String> getInheritedGroups() {
        return nodes.stream()
                .filter(Node::isGroupNode)
                .filter(node -> !node.isExpired())
                .map(Node::getGroupName)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @NotNull
    public Set<String> getInheritedGroups(@NotNull ContextSet contexts) {
        return nodes.stream()
                .filter(Node::isGroupNode)
                .filter(node -> !node.isExpired())
                .filter(node -> node.appliesIn(contexts))
                .map(Node::getGroupName)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @NotNull
    public DataMutateResult addGroup(@NotNull String groupName) {
        return addGroup(groupName, (Instant) null);
    }

    @Override
    @NotNull
    public DataMutateResult addGroup(@NotNull String groupName, @Nullable Instant expiry) {
        Node groupNode = Node.builder(Node.GROUP_PREFIX + groupName.toLowerCase())
                .value(true)
                .expiry(expiry)
                .build();
        // Remove existing group node (ignoring expiry) and add new one
        nodes.removeIf(existing -> existing.equalsIgnoringExpiry(groupNode));
        nodes.add(groupNode);
        listener.onGroupAdded(this, groupName, DataMutateResult.SUCCESS);
        return DataMutateResult.SUCCESS;
    }

    @Override
    @NotNull
    public DataMutateResult removeGroup(@NotNull String groupName) {
        String groupPerm = Node.GROUP_PREFIX + groupName.toLowerCase();
        List<Node> toRemove = nodes.stream()
                .filter(node -> node.getPermission().equals(groupPerm))
                .toList();
        if (toRemove.isEmpty()) {
            listener.onGroupRemoved(this, groupName, DataMutateResult.DOES_NOT_EXIST);
            return DataMutateResult.DOES_NOT_EXIST;
        }
        for (Node node : toRemove) {
            nodes.remove(node);
        }
        listener.onGroupRemoved(this, groupName, DataMutateResult.SUCCESS);
        return DataMutateResult.SUCCESS;
    }

    @Override
    @NotNull
    public DataMutateResult setPermission(@NotNull String permission, boolean value, @NotNull Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        return setPermission(permission, value, Instant.now().plus(duration));
    }

    @Override
    @NotNull
    public DataMutateResult setPermission(@NotNull String permission, boolean value, @NotNull Instant expiry) {
        Objects.requireNonNull(permission, "permission cannot be null");
        Objects.requireNonNull(expiry, "expiry cannot be null");
        Node node = Node.builder(permission)
                .value(value)
                .expiry(expiry)
                .build();
        return setNode(node);
    }

    @Override
    public boolean isTemporaryPermission(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        return nodes.stream()
                .filter(node -> node.getPermission().equals(lowerPerm))
                .filter(node -> !node.isExpired())
                .anyMatch(Node::isTemporary);
    }

    @Override
    @Nullable
    public Instant getPermissionExpiry(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        return nodes.stream()
                .filter(node -> node.getPermission().equals(lowerPerm))
                .filter(node -> !node.isExpired())
                .filter(Node::isTemporary)
                .map(Node::getExpiry)
                .findFirst()
                .orElse(null);
    }

    @Override
    @NotNull
    public DataMutateResult setPermissionExpiry(@NotNull String permission, @Nullable Instant expiry) {
        String lowerPerm = permission.toLowerCase();
        Optional<Node> existing = nodes.stream()
                .filter(node -> node.getPermission().equals(lowerPerm))
                .filter(node -> !node.isExpired())
                .findFirst();
        if (existing.isEmpty()) {
            return DataMutateResult.DOES_NOT_EXIST;
        }
        return setNode(existing.get().withExpiry(expiry));
    }

    @Override
    @NotNull
    public DataMutateResult adjustPermissionExpiry(@NotNull String permission, @NotNull Duration adjustment) {
        Objects.requireNonNull(adjustment, "adjustment cannot be null");
        String lowerPerm = permission.toLowerCase();
        Optional<Node> existing = nodes.stream()
                .filter(node -> node.getPermission().equals(lowerPerm))
                .filter(node -> !node.isExpired())
                .findFirst();
        if (existing.isEmpty()) {
            return DataMutateResult.DOES_NOT_EXIST;
        }
        Node node = existing.get();
        Instant newExpiry = node.isTemporary()
                ? node.getExpiry().plus(adjustment)
                : Instant.now().plus(adjustment);
        return setNode(node.withExpiry(newExpiry));
    }

    @Override
    @NotNull
    public Set<TemporaryPermissionInfo> getTemporaryPermissions() {
        return nodes.stream()
                .filter(Node::isTemporary)
                .filter(node -> !node.isExpired())
                .map(node -> new TemporaryPermissionInfo(
                        node.getPermission(),
                        node.getValue(),
                        node.getExpiry(),
                        node.getContexts()
                ))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int cleanupExpired() {
        int before = nodes.size();
        nodes.removeIf(Node::isExpired);
        return before - nodes.size();
    }

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
     * Gets all parent groups (aliases for getInheritedGroups for clarity).
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
