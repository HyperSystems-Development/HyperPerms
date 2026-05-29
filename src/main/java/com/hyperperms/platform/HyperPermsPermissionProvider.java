package com.hyperperms.platform;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.registry.PermissionAliases;
import com.hyperperms.resolver.PermissionResolver;
import com.hyperperms.util.CaseInsensitiveSet;
import com.hyperperms.util.HyperPermsPermissionSet;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * HyperPerms implementation of Hytale's PermissionProvider interface.
 * <p>
 * This bridges Hytale's permission system with HyperPerms, allowing
 * HyperPerms to handle all permission checks and data management.
 * <p>
 * Note: Hytale's PermissionProvider interface uses a simple flat permission
 * model. HyperPerms supports much more (contexts, inheritance, wildcards),
 * but this adapter provides compatibility with Hytale's built-in systems.
 */
public class HyperPermsPermissionProvider implements PermissionProvider {

    private static final String PROVIDER_NAME = "HyperPerms";

    private final HyperPerms hyperPerms;

    /**
     * Creates a new HyperPermsPermissionProvider.
     *
     * @param hyperPerms the HyperPerms instance
     */
    public HyperPermsPermissionProvider(@NotNull HyperPerms hyperPerms) {
        this.hyperPerms = hyperPerms;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public void addUserPermissions(UUID uuid, Set<String> permissions) {
        // IMPORTANT: Do NOT persist permissions added through the Hytale provider API.
        // Hytale and other plugins call this method to "grant" permissions to users,
        // but HyperPerms manages permissions through groups and explicit /hp commands.
        // Persisting these would create direct user nodes that override group negations.
        Logger.debug("Ignoring addUserPermissions from Hytale API for %s (%d permissions) - permissions are managed through HyperPerms groups",
                uuid, permissions.size());
    }

    @Override
    public void removeUserPermissions(UUID uuid, Set<String> permissions) {
        User user = hyperPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return;
        }
        for (String permission : permissions) {
            user.removeNode(permission);
        }
        hyperPerms.getUserManager().saveUser(user);
        hyperPerms.getCacheInvalidator().invalidate(uuid);
        Logger.debug("Removed %d permissions from user %s", permissions.size(), uuid);
    }

    @Override
    public Set<String> getUserPermissions(UUID uuid) {
        // Return HyperPermsPermissionSet which delegates contains() to hasPermission()
        // This ensures negations work regardless of which code path calls this method
        // (whether Hytale native commands or plugin commands like economy/balance)
        return new HyperPermsPermissionSet(hyperPerms, uuid);
    }

    // ==================== Group Permissions ====================

    @Override
    public void addGroupPermissions(String groupName, Set<String> permissions) {
        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            // Create the group if it doesn't exist
            group = hyperPerms.getGroupManager().createGroup(groupName);
        }
        for (String permission : permissions) {
            group.setNode(Node.of(permission));
        }
        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCacheInvalidator().invalidateAll();
        Logger.debug("Added %d permissions to group %s", permissions.size(), groupName);
    }

    @Override
    public void removeGroupPermissions(String groupName, Set<String> permissions) {
        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return;
        }
        for (String permission : permissions) {
            group.removeNode(permission);
        }
        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCacheInvalidator().invalidateAll();
        Logger.debug("Removed %d permissions from group %s", permissions.size(), groupName);
    }

    @Override
    public Set<String> getGroupPermissions(String groupName) {
        // Handle virtual user group - use HyperPermsPermissionSet which delegates
        // contains() checks to hasPermission(), properly handling negations
        if (groupName.startsWith("user:")) {
            UUID uuid = UUID.fromString(groupName.substring(5));
            return new HyperPermsPermissionSet(hyperPerms, uuid);
        }

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            Logger.debug("getGroupPermissions: group '%s' not found", groupName);
            return Collections.emptySet();
        }

        Set<String> expanded = hyperPerms.getResolver().resolveGroup(group, ContextSet.empty())
                .getExpandedPermissions(hyperPerms.getPermissionRegistry());

        Logger.debug("getGroupPermissions(%s) returning %d permissions", groupName, expanded.size());

        // Wrap in CaseInsensitiveSet for Hytale compatibility
        // Hytale may use different case (e.g., "gameMode" vs "gamemode")
        return new CaseInsensitiveSet(expanded);
    }

    @Override
    public String getGroupParent(String groupName) {
        // HyperPerms resolves inheritance internally (multi-parent, weighted) through its own
        // resolver and the single virtual user group. It deliberately does NOT expose a vanilla
        // single-parent chain: returning null makes PermissionsModule.checkParentChain a no-op,
        // so Hytale never double-walks a parent chain over HyperPerms groups (which would
        // bypass our negation/weight resolution). All effective inheritance is already baked
        // into the permissions returned by getGroupPermissions()/getEffectiveGroupPermissions().
        return null;
    }

    @Override
    public Set<String> getAllRegisteredGroups() {
        // Surface HyperPerms-managed groups to Hytale. Used for group enumeration and by
        // plugins like EssentialsPlus that read via getFirstPermissionProvider() (HyperPerms
        // is forced first). Never null.
        return hyperPerms.getGroupManager().getGroupNames();
    }

    @Override
    public Set<String> getEffectiveGroupPermissions(String groupName) {
        // HyperPerms' getGroupPermissions() already returns the FULLY inheritance-resolved and
        // wildcard/alias-expanded permission set (PermissionResolver.resolveGroup walks the
        // inheritance graph), so the "effective" set is identical. Delegating keeps a single
        // source of truth. Note: 0.5.2 has no internal caller for this method; it exists for
        // interface conformance and third-party/future consumers.
        return getGroupPermissions(groupName);
    }

    /**
     * Gets the direct permissions for a user (not inherited from groups).
     * <p>
     * <b>Note:</b> This method does NOT handle negations correctly. It only returns
     * permissions where {@code getValue() == true}, ignoring negated permissions.
     * Use {@link HyperPermsPermissionSet} instead, which delegates to
     * {@link HyperPerms#hasPermission(UUID, String)} for correct negation handling.
     *
     * @param uuid the user's UUID
     * @return set of direct user permissions (excluding negations)
     * @deprecated Use {@link HyperPermsPermissionSet} instead which properly handles negations.
     */
    @Deprecated
    private Set<String> getUserDirectPermissions(UUID uuid) {
        User user = hyperPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return Collections.emptySet();
        }

        ContextSet contexts = hyperPerms.getContexts(uuid);
        Set<String> directPermissions = new HashSet<>();

        // Get user's direct permission nodes (not group inheritance nodes)
        for (Node node : user.getNodes()) {
            if (!node.isExpired() && !node.isGroupNode() && node.appliesIn(contexts) && node.getValue()) {
                directPermissions.add(node.getPermission());
            }
        }

        // Expand aliases AND wildcards in user's direct permissions
        // Must match the expansion logic in PermissionResolver.getExpandedPermissions()
        Set<String> expanded = new HashSet<>(directPermissions);
        PermissionAliases aliases = PermissionAliases.getInstance();

        for (String perm : directPermissions) {
            // Expand aliases for this permission (simplified -> actual Hytale paths)
            Set<String> aliasExpanded = aliases.expand(perm);
            expanded.addAll(aliasExpanded);

            // Check if this is a wildcard permission
            if (perm.endsWith(".*") || perm.equals("*")) {
                // Expand the wildcard using the registry
                Set<String> matching = hyperPerms.getPermissionRegistry().getMatchingPermissions(perm);
                expanded.addAll(matching);

                // Also expand aliases for wildcard patterns
                Set<String> wildcardAliases = aliases.getActualPermissions(perm);
                for (String aliasedPerm : wildcardAliases) {
                    expanded.add(aliasedPerm);
                    if (aliasedPerm.endsWith(".*")) {
                        expanded.addAll(hyperPerms.getPermissionRegistry().getMatchingPermissions(aliasedPerm));
                    }
                }
            }
        }

        Logger.debug("getUserDirectPermissions(%s) returning %d permissions (case-insensitive)", uuid, expanded.size());
        
        // Wrap in CaseInsensitiveSet for Hytale compatibility
        return new CaseInsensitiveSet(expanded);
    }

    // ==================== User-Group Membership ====================

    @Override
    public void addUserToGroup(UUID uuid, String groupName) {
        // Ensure the group exists
        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            Logger.debug("Ignoring addUserToGroup for non-existent group '%s' (user %s) - likely a Hytale virtual group (e.g. gamemode)", groupName, uuid);
            return;
        }

        User user = hyperPerms.getUserManager().getOrCreateUser(uuid);
        user.addGroup(groupName);
        hyperPerms.getUserManager().saveUser(user);
        hyperPerms.getCacheInvalidator().invalidate(uuid);
        Logger.debug("Added user %s to group %s", uuid, groupName);
    }

    @Override
    public void removeUserFromGroup(UUID uuid, String groupName) {
        User user = hyperPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return;
        }
        user.removeGroup(groupName);
        hyperPerms.getUserManager().saveUser(user);
        hyperPerms.getCacheInvalidator().invalidate(uuid);
        Logger.debug("Removed user %s from group %s", uuid, groupName);
    }

    @Override
    public void setUserGroup(UUID uuid, String groupName) {
        // Vanilla semantics are "this user's group is now <groupName>". HyperPerms models a
        // user's main group as its primary group, so we set the primary group rather than
        // destructively clearing additive (secondary) group memberships, which would be
        // surprising for an admin who, e.g., ran vanilla /setgroup on a HyperPerms server.
        // Only act on groups HyperPerms actually manages; ignore unknown/vanilla group names
        // gracefully so vanilla /setgroup with a non-HyperPerms group is a clean no-op.
        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            Logger.debug("Ignoring setUserGroup for non-existent group '%s' (user %s) - not a HyperPerms group", groupName, uuid);
            return;
        }

        User user = hyperPerms.getUserManager().getOrCreateUser(uuid);
        user.setPrimaryGroup(groupName);
        hyperPerms.getUserManager().saveUser(user);
        hyperPerms.getCacheInvalidator().invalidate(uuid);
        Logger.debug("Set primary group of user %s to %s", uuid, groupName);
    }

    @Override
    public Set<String> getGroupsForUser(UUID uuid) {
        // Use getOrCreateUser to ensure new players get assigned to their default group
        hyperPerms.getUserManager().getOrCreateUser(uuid);

        // Return ONLY the virtual user group. This ensures ALL permission resolution goes
        // through HyperPermsPermissionSet, which properly handles:
        // - Group inheritance (resolved by PermissionResolver)
        // - Permission negations (groups can negate inherited permissions)
        // - Wildcards and aliases
        //
        // If we returned actual groups here, Hytale would query each group's permissions
        // separately and combine them, completely bypassing our negation logic.
        // For example: if Moderator grants "kick.use" and Admin negates it, Hytale would
        // see both groups and grant the permission since Moderator has it directly.
        //
        // By returning only the virtual user group, Hytale queries HyperPermsPermissionSet
        // which delegates to hasPermission() for proper resolution.
        //
        // DESIGN NOTE: This single-element return also intentionally sidesteps a
        // nondeterministic iteration issue in Hytale's vanilla resolver. In
        // PermissionsModule.hasPermission(), groups are iterated from a HashSet with
        // undefined order, so conflicting permissions across groups produce random results.
        // By returning exactly one virtual group, iteration order is irrelevant.
        //
        // MULTI-PROVIDER AGGREGATION NOTE: PermissionsModule.getGroupsForUser() aggregates
        // non-empty group sets from ALL registered providers, so HyperPerms users also appear
        // in vanilla's default group (harmless).
        //
        // OP RECOGNITION (Hytale 0.5.2): the engine determines operator status — and gates
        // several capabilities — by GROUP MEMBERSHIP, not by a permission check:
        //   OpSelfCommand/OpAddCommand/OpRemoveCommand: getGroupsForUser(uuid).contains("hytale:Admin")
        //   FlyCameraModule / WorldMapTracker also key off getGroupsForUser group names.
        // Because we funnel resolution through the single "user:<uuid>" virtual group, a
        // HyperPerms admin would never be seen in hytale:Admin and would show as "not OP" even
        // though their permission checks pass. To bridge this, advertise hytale:Admin for users
        // who effectively resolve the "*" (superuser) permission. This is additive and does not
        // change permission resolution (still funneled through user:<uuid>); it only makes
        // vanilla's group-membership-based OP/capability checks recognize HyperPerms admins.
        boolean isSuperuser;
        try {
            ContextSet contexts = hyperPerms.getContexts(uuid);
            isSuperuser = hyperPerms.hasPermission(uuid, "*", contexts);
        } catch (Exception e) {
            isSuperuser = false;
        }

        if (isSuperuser) {
            // "hytale:Admin" is the literal Hytale 0.5.2 OP group the engine checks for.
            return Set.of("user:" + uuid.toString(), "hytale:Admin");
        }
        return Set.of("user:" + uuid.toString());
    }

    /**
     * Recursively collects all groups including parent groups in the inheritance chain.
     */
    private void collectInheritedGroups(Set<String> groupNames, Set<String> result) {
        for (String groupName : groupNames) {
            if (result.contains(groupName)) {
                continue; // Already processed, avoid cycles
            }
            result.add(groupName);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group != null) {
                Set<String> parents = group.getInheritedGroups();
                if (!parents.isEmpty()) {
                    collectInheritedGroups(parents, result);
                }
            }
        }
    }

    // ==================== Extended HyperPerms Functionality ====================

    /**
     * Checks if a user has a permission with context awareness.
     * <p>
     * This method provides the full HyperPerms permission check, including:
     * <ul>
     *   <li>Wildcard matching</li>
     *   <li>Group inheritance</li>
     *   <li>Context-sensitive permissions</li>
     *   <li>Negation handling</li>
     * </ul>
     *
     * @param uuid       the user's UUID
     * @param permission the permission to check
     * @return true if the user has the permission
     */
    public boolean hasPermission(UUID uuid, String permission) {
        // Get current contexts for the player
        ContextSet contexts = hyperPerms.getContexts(uuid);
        return hyperPerms.hasPermission(uuid, permission, contexts);
    }

    /**
     * Checks if a user has a permission with specific contexts.
     *
     * @param uuid       the user's UUID
     * @param permission the permission to check
     * @param contexts   the contexts to check against
     * @return true if the user has the permission in the given contexts
     */
    public boolean hasPermission(UUID uuid, String permission, ContextSet contexts) {
        return hyperPerms.hasPermission(uuid, permission, contexts);
    }

}
