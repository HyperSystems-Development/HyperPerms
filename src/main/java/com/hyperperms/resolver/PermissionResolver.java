package com.hyperperms.resolver;

import com.hyperperms.api.context.ContextSet;
import com.hyperperms.registry.PermissionAliases;
import com.hyperperms.registry.PermissionRegistry;
import com.hyperperms.util.Logger;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.resolver.WildcardMatcher.MatchResult;
import com.hyperperms.resolver.WildcardMatcher.MatchType;
import com.hyperperms.resolver.WildcardMatcher.TriState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Resolves effective permissions for users by processing inheritance and wildcards.
 */
public final class PermissionResolver {

    private final Function<String, Group> groupLoader;
    private final InheritanceGraph inheritanceGraph;

    /**
     * Creates a new permission resolver.
     *
     * @param groupLoader function to load groups by name
     */
    public PermissionResolver(@NotNull Function<String, Group> groupLoader) {
        this.groupLoader = Objects.requireNonNull(groupLoader, "groupLoader cannot be null");
        this.inheritanceGraph = new InheritanceGraph(groupLoader);
    }

    /**
     * Resolves all effective permissions for a user in the given context.
     *
     * @param user     the user
     * @param contexts the current context
     * @return resolved permission map
     */
    @NotNull
    public ResolvedPermissions resolve(@NotNull User user, @NotNull ContextSet contexts) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        Map<String, String> permissionSources = new LinkedHashMap<>(); // tracks which group each perm came from

        // 1. Collect groups the user belongs to (includes primary group)
        Set<String> userGroups = new HashSet<>(user.getInheritedGroups(contexts));

        // 2. Resolve group inheritance (returns groups sorted by weight, lowest first)
        List<Group> inheritedGroups = inheritanceGraph.resolveInheritance(userGroups, contexts);

        // 3. Apply group permissions in order (lowest weight first = lowest priority)
        for (Group group : inheritedGroups) {
            for (Node node : group.getNodes()) {
                if (!node.isExpired() && !node.isGroupNode() && node.appliesIn(contexts)) {
                    applyNode(permissions, permissionSources, node, group.getName());
                }
            }
        }

        // 4. Apply user's direct permissions (highest priority)
        for (Node node : user.getNodes()) {
            if (!node.isExpired() && !node.isGroupNode() && node.appliesIn(contexts)) {
                applyNode(permissions, permissionSources, node, null); // null = from user
            }
        }

        return new ResolvedPermissions(permissions, permissionSources, contexts);
    }

    /**
     * Resolves permissions for a group (for debugging/display).
     *
     * @param group    the group
     * @param contexts the current context
     * @return resolved permission map
     */
    @NotNull
    public ResolvedPermissions resolveGroup(@NotNull Group group, @NotNull ContextSet contexts) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        Map<String, String> permissionSources = new LinkedHashMap<>();

        // Resolve inheritance
        List<Group> inheritedGroups = inheritanceGraph.resolveInheritance(Set.of(group.getName()), contexts);

        // Apply in order
        for (Group g : inheritedGroups) {
            for (Node node : g.getNodes()) {
                if (!node.isExpired() && !node.isGroupNode() && node.appliesIn(contexts)) {
                    applyNode(permissions, permissionSources, node, g.getName());
                }
            }
        }

        return new ResolvedPermissions(permissions, permissionSources, contexts);
    }

    /**
     * Checks a single permission for a user.
     *
     * @param user       the user
     * @param permission the permission to check
     * @param contexts   the current context
     * @return the result
     */
    @NotNull
    public TriState check(@NotNull User user, @NotNull String permission, @NotNull ContextSet contexts) {
        ResolvedPermissions resolved = resolve(user, contexts);
        return resolved.check(permission);
    }

    /**
     * Checks a single permission with full trace information.
     *
     * @param user       the user
     * @param permission the permission to check
     * @param contexts   the current context
     * @return detailed trace of the permission check
     */
    @NotNull
    public PermissionTrace checkWithTrace(@NotNull User user, @NotNull String permission, @NotNull ContextSet contexts) {
        ResolvedPermissions resolved = resolve(user, contexts);
        return resolved.checkWithTrace(permission);
    }

    private void applyNode(Map<String, Boolean> permissions, Map<String, String> sources,
                           Node node, @Nullable String groupName) {
        String perm = node.getPermission();
        boolean value = node.getValue();

        // Handle negation prefix
        if (perm.startsWith("-")) {
            perm = perm.substring(1);
            value = !value;
        }

        permissions.put(perm, value);
        sources.put(perm, groupName); // null means from user
    }

    /**
     * Represents resolved permissions with wildcard checking and tracing support.
     */
    public static final class ResolvedPermissions {

        private final Map<String, Boolean> permissions;
        private final Map<String, String> sources; // permission -> group name (null = user)
        private final ContextSet resolvedContexts;

        ResolvedPermissions(@NotNull Map<String, Boolean> permissions,
                           @NotNull Map<String, String> sources,
                           @NotNull ContextSet contexts) {
            this.permissions = Collections.unmodifiableMap(new LinkedHashMap<>(permissions));
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            this.resolvedContexts = contexts;
        }

        /**
         * Checks a permission against the resolved permissions.
         * <p>
         * This method considers both wildcard matching and permission aliases.
         * If the permission itself is not matched, it will check if any of its
         * aliased forms are granted.
         *
         * @param permission the permission to check
         * @return the result
         */
        @NotNull
        public TriState check(@NotNull String permission) {
            String lowerPerm = permission.toLowerCase();

            // First try direct wildcard matching
            TriState result = WildcardMatcher.check(lowerPerm, permissions);
            if (result != TriState.UNDEFINED) {
                return result;
            }

            // If not found, check aliases
            // This handles cases like checking "hytale.command.player.gamemode" when
            // user has "hytale.command.gamemode" (simplified alias)
            PermissionAliases aliases = PermissionAliases.getInstance();

            // Check if any simplified alias of this permission is granted
            Set<String> simplifiedAliases = aliases.getAliases(lowerPerm);
            for (String alias : simplifiedAliases) {
                result = WildcardMatcher.check(alias, permissions);
                if (result != TriState.UNDEFINED) {
                    return result;
                }
            }

            // Check if any actual permission this maps to is granted
            Set<String> actualPerms = aliases.getActualPermissions(lowerPerm);
            for (String actual : actualPerms) {
                result = WildcardMatcher.check(actual, permissions);
                if (result != TriState.UNDEFINED) {
                    return result;
                }
            }

            return TriState.UNDEFINED;
        }

        /**
         * Checks a permission with detailed trace information.
         * <p>
         * This method considers both wildcard matching and permission aliases.
         *
         * @param permission the permission to check
         * @return the trace with full details
         */
        @NotNull
        public PermissionTrace checkWithTrace(@NotNull String permission) {
            String lowerPerm = permission.toLowerCase();
            MatchResult matchResult = WildcardMatcher.checkWithTrace(lowerPerm, permissions);

            // If not matched, check aliases
            if (!matchResult.isMatched()) {
                PermissionAliases aliases = PermissionAliases.getInstance();

                // Check simplified aliases (actual -> simplified)
                Set<String> simplifiedAliases = aliases.getAliases(lowerPerm);
                for (String alias : simplifiedAliases) {
                    MatchResult aliasResult = WildcardMatcher.checkWithTrace(alias, permissions);
                    if (aliasResult.isMatched()) {
                        matchResult = aliasResult;
                        break;
                    }
                }

                // If still not matched, check actual permissions (simplified -> actual)
                if (!matchResult.isMatched()) {
                    Set<String> actualPerms = aliases.getActualPermissions(lowerPerm);
                    for (String actual : actualPerms) {
                        MatchResult actualResult = WildcardMatcher.checkWithTrace(actual, permissions);
                        if (actualResult.isMatched()) {
                            matchResult = actualResult;
                            break;
                        }
                    }
                }
            }

            if (!matchResult.isMatched()) {
                return PermissionTrace.notFound(permission, resolvedContexts);
            }

            // Determine the source
            String matchedNode = matchResult.matchedNode();
            String sourceGroup = matchedNode != null ? sources.get(matchedNode) : null;
            boolean fromUser = sourceGroup == null && matchedNode != null;

            if (fromUser) {
                return PermissionTrace.fromUser(
                        permission,
                        matchResult.result(),
                        matchedNode,
                        matchResult.matchType(),
                        resolvedContexts
                );
            } else if (sourceGroup != null) {
                return PermissionTrace.fromGroup(
                        permission,
                        matchResult.result(),
                        matchedNode,
                        matchResult.matchType(),
                        sourceGroup,
                        resolvedContexts
                );
            } else {
                return new PermissionTrace(
                        permission,
                        matchResult.result(),
                        matchedNode,
                        matchResult.matchType(),
                        null,
                        false,
                        resolvedContexts
                );
            }
        }

        /**
         * Checks if a permission is granted.
         *
         * @param permission the permission
         * @return true if granted
         */
        public boolean hasPermission(@NotNull String permission) {
            return check(permission).asBoolean();
        }

        /**
         * Gets all resolved permissions.
         *
         * @return unmodifiable map of permissions
         */
        @NotNull
        public Map<String, Boolean> getPermissions() {
            return permissions;
        }

        /**
         * Gets the source group for a permission.
         *
         * @param permission the permission
         * @return the group name, or null if from user or not found
         */
        @Nullable
        public String getSource(@NotNull String permission) {
            return sources.get(permission.toLowerCase());
        }

        /**
         * Gets all granted permissions.
         *
         * @return set of granted permissions
         */
        @NotNull
        public Set<String> getGrantedPermissions() {
            Set<String> granted = new HashSet<>();
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                if (entry.getValue()) {
                    granted.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(granted);
        }

        /**
         * Gets all granted permissions with wildcards expanded.
         * <p>
         * This method returns a set that includes both the raw permission nodes
         * and all concrete permissions that match any wildcard nodes. This is
         * necessary for systems like Hytale's PermissionProvider that use
         * {@code Set.contains()} for permission checks instead of supporting
         * wildcard matching.
         * <p>
         * Also handles aliasing for plugin permissions where Hytale uses
         * full package paths (e.g., hyperhomes.* also grants com.hyperhomes.*)
         * and for Hytale permissions where simplified paths need to map to
         * actual Hytale permission paths (e.g., hytale.command.gamemode ->
         * hytale.command.player.gamemode).
         *
         * @param registry the permission registry to use for wildcard expansion
         * @return set of all granted permissions including wildcard expansions
         */
        @NotNull
        public Set<String> getExpandedPermissions(@NotNull PermissionRegistry registry) {
            Set<String> granted = getGrantedPermissions();
            Set<String> expanded = new HashSet<>(granted);
            PermissionAliases aliases = PermissionAliases.getInstance();

            for (String perm : granted) {
                // Expand aliases for this permission (simplified -> actual Hytale paths)
                Set<String> aliasExpanded = aliases.expand(perm);
                expanded.addAll(aliasExpanded);

                // Check if this is a wildcard permission
                if (perm.endsWith(".*") || perm.equals("*")) {
                    // Expand the wildcard using the registry
                    Set<String> matching = registry.getMatchingPermissions(perm);
                    expanded.addAll(matching);

                    // Also expand aliases for wildcard patterns
                    // e.g., hytale.command.gamemode.* -> all hytale.command.player.gamemode.* perms
                    Set<String> wildcardAliases = aliases.getActualPermissions(perm);
                    for (String aliasedPerm : wildcardAliases) {
                        expanded.add(aliasedPerm);
                        // If the alias is also a pattern, expand it
                        if (aliasedPerm.endsWith(".*")) {
                            expanded.addAll(registry.getMatchingPermissions(aliasedPerm));
                        }
                    }
                }
            }

            return Collections.unmodifiableSet(expanded);
        }

        /**
         * Gets all denied permissions.
         *
         * @return set of denied permissions
         */
        @NotNull
        public Set<String> getDeniedPermissions() {
            Set<String> denied = new HashSet<>();
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                if (!entry.getValue()) {
                    denied.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(denied);
        }

        /**
         * Gets the contexts these permissions were resolved in.
         *
         * @return the contexts
         */
        @NotNull
        public ContextSet getResolvedContexts() {
            return resolvedContexts;
        }

        /**
         * Gets the number of permissions.
         *
         * @return the count
         */
        public int size() {
            return permissions.size();
        }

        /**
         * Checks if empty.
         *
         * @return true if no permissions
         */
        public boolean isEmpty() {
            return permissions.isEmpty();
        }
    }
}
