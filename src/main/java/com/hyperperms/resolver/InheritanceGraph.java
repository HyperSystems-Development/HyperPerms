package com.hyperperms.resolver;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * Handles group inheritance resolution with cycle detection.
 */
public final class InheritanceGraph {

    private final Function<String, Group> groupLoader;

    /**
     * Creates a new inheritance graph.
     *
     * @param groupLoader function to load groups by name
     */
    public InheritanceGraph(@NotNull Function<String, Group> groupLoader) {
        this.groupLoader = Objects.requireNonNull(groupLoader, "groupLoader cannot be null");
    }

    /**
     * Resolves all inherited groups for a starting set of group names.
     * <p>
     * Groups are returned in application order: parent groups first (lowest priority),
     * then child groups (highest priority). Within the same inheritance depth, groups
     * are sorted by weight ascending (higher weight = higher priority, applied later).
     * <p>
     * This ensures a group's own permissions always take precedence over permissions
     * inherited from its parents, regardless of weight values.
     *
     * @param startGroups the initial group names
     * @param contexts    the current context for filtering
     * @return ordered list of groups (parents first, children last)
     */
    @NotNull
    public List<Group> resolveInheritance(@NotNull Set<String> startGroups, @NotNull ContextSet contexts) {
        Set<String> visited = new HashSet<>();
        Map<String, Integer> depths = new HashMap<>();
        List<Group> result = new ArrayList<>();

        // BFS with depth tracking — start groups are depth 0, their parents are depth 1, etc.
        Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        for (String g : startGroups) {
            queue.add(Map.entry(g, 0));
        }

        while (!queue.isEmpty()) {
            Map.Entry<String, Integer> entry = queue.poll();
            String groupName = entry.getKey();
            int depth = entry.getValue();

            if (visited.contains(groupName)) {
                continue; // Skip cycles
            }
            visited.add(groupName);

            Group group = groupLoader.apply(groupName);
            if (group == null) {
                continue; // Group doesn't exist
            }

            result.add(group);
            depths.put(groupName, depth);

            // Add parent groups to the queue at the next depth level
            for (String parent : group.getInheritedGroups(contexts)) {
                if (!visited.contains(parent)) {
                    queue.add(Map.entry(parent, depth + 1));
                }
            }
        }

        // Sort by: depth descending (parents first), then weight ascending within same depth.
        // Parents (higher depth) are applied first and can be overridden by children (depth 0).
        // Within the same depth level, higher weight groups are applied later and win conflicts.
        result.sort(Comparator
            .comparingInt((Group g) -> depths.getOrDefault(g.getName(), 0))
            .reversed()
            .thenComparingInt(Group::getWeight)
        );

        return result;
    }

    /**
     * Collects all permission nodes from a list of groups.
     * Nodes are collected in order, with later groups overriding earlier ones.
     *
     * @param groups   the groups in inheritance order
     * @param contexts the current context for filtering
     * @return list of applicable nodes
     */
    @NotNull
    public List<Node> collectNodes(@NotNull List<Group> groups, @NotNull ContextSet contexts) {
        List<Node> nodes = new ArrayList<>();

        for (Group group : groups) {
            for (Node node : group.getNodes()) {
                if (!node.isExpired() && !node.isGroupNode() && node.appliesIn(contexts)) {
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }

    /**
     * Checks if adding a parent group would create a cycle.
     *
     * @param group      the group to add a parent to
     * @param parentName the proposed parent group name
     * @return true if adding the parent would create a cycle
     */
    public boolean wouldCreateCycle(@NotNull Group group, @NotNull String parentName) {
        // Check if parentName eventually inherits from group
        String targetName = group.getName().toLowerCase();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(parentName.toLowerCase());

        while (!queue.isEmpty()) {
            String current = queue.poll().toLowerCase();

            if (current.equals(targetName)) {
                return true; // Found a cycle
            }

            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            Group currentGroup = groupLoader.apply(current);
            if (currentGroup != null) {
                queue.addAll(currentGroup.getInheritedGroups());
            }
        }

        return false;
    }

    /**
     * Gets the inheritance chain for a group (for debugging).
     *
     * @param groupName the starting group
     * @return list of group names in inheritance order
     */
    @NotNull
    public List<String> getInheritanceChain(@NotNull String groupName) {
        List<Group> groups = resolveInheritance(Set.of(groupName), ContextSet.empty());
        return groups.stream().map(Group::getName).toList();
    }
}
