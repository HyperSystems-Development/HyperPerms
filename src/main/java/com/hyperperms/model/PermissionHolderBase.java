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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Abstract base class implementing the shared node management logic
 * between {@link User} and {@link Group}.
 * <p>
 * Both User and Group have identical implementations for:
 * <ul>
 *   <li>Node CRUD: addNode, removeNode, setNode, clearNodes, getNodes, hasNode</li>
 *   <li>Group inheritance: getInheritedGroups, addGroup, removeGroup</li>
 *   <li>Temporary permissions: setPermission, isTemporary, getExpiry, adjustExpiry</li>
 *   <li>Cleanup: cleanupExpired</li>
 *   <li>Listener management</li>
 * </ul>
 * This base class eliminates ~350 lines of duplication.
 */
public abstract class PermissionHolderBase implements PermissionHolder {

    protected final Set<Node> nodes = ConcurrentHashMap.newKeySet();
    protected volatile PermissionHolderListener listener = PermissionHolderListener.EMPTY;

    // ==================== Listener ====================

    public void setListener(@Nullable PermissionHolderListener listener) {
        this.listener = listener != null ? listener : PermissionHolderListener.EMPTY;
    }

    @NotNull
    public PermissionHolderListener getListener() {
        return listener;
    }

    // ==================== Node Access ====================

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

    // ==================== Node Mutation ====================

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
        List<Node> toRemove;
        synchronized (nodes) {
            toRemove = nodes.stream()
                    .filter(node -> node.getPermission().equals(lowerPerm))
                    .toList();
            if (toRemove.isEmpty()) {
                return DataMutateResult.DOES_NOT_EXIST;
            }
            for (Node node : toRemove) {
                nodes.remove(node);
            }
        }
        for (Node node : toRemove) {
            listener.onNodeRemoved(this, node, DataMutateResult.SUCCESS);
        }
        return DataMutateResult.SUCCESS;
    }

    @Override
    @NotNull
    public DataMutateResult setNode(@NotNull Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        synchronized (nodes) {
            nodes.removeIf(existing -> existing.equalsIgnoringExpiry(node));
            nodes.add(node);
        }
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

    // ==================== Group Inheritance ====================

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
        synchronized (nodes) {
            nodes.removeIf(existing -> existing.equalsIgnoringExpiry(groupNode));
            nodes.add(groupNode);
        }
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

    // ==================== Temporary Permissions ====================

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
}
