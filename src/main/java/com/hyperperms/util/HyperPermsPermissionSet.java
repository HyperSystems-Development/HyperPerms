package com.hyperperms.util;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.discovery.RuntimePermissionDiscovery;
import com.hyperperms.model.User;
import com.hyperperms.resolver.PermissionResolver;
import com.hyperperms.resolver.WildcardMatcher.TriState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A Set implementation that delegates contains() checks to HyperPerms'
 * hasPermission() method, properly handling negations, wildcards, and inheritance.
 * <p>
 * This is used for the virtual user group mechanism. When Hytale checks if a user
 * has a permission via {@code set.contains("some.permission")}, this set delegates
 * to {@link HyperPerms#hasPermission(UUID, String)} which correctly handles:
 * <ul>
 *   <li>Negated permissions (e.g., {@code -kick.use})</li>
 *   <li>Wildcard matching (e.g., {@code admin.*})</li>
 *   <li>Group inheritance with proper weight ordering</li>
 *   <li>Context-sensitive permissions</li>
 * </ul>
 * <p>
 * For iteration purposes (e.g., listing permissions), this set returns the
 * granted permissions from {@link HyperPerms#getUserPermissions(UUID)}.
 */
public class HyperPermsPermissionSet extends AbstractSet<String> {

    private final HyperPerms hyperPerms;
    private final UUID uuid;
    private volatile Set<String> cachedGranted;

    /**
     * Creates a new HyperPermsPermissionSet.
     *
     * @param hyperPerms the HyperPerms instance
     * @param uuid       the user's UUID
     */
    public HyperPermsPermissionSet(@NotNull HyperPerms hyperPerms, @NotNull UUID uuid) {
        this.hyperPerms = hyperPerms;
        this.uuid = uuid;
    }

    /**
     * Checks if the user has the given permission.
     * <p>
     * This method handles two types of checks that Hytale's PermissionsModule performs:
     * <ol>
     *   <li>{@code set.contains("permission")} - checks if permission is GRANTED</li>
     *   <li>{@code set.contains("-permission")} - checks if permission is DENIED (negated)</li>
     * </ol>
     * <p>
     * When Hytale checks for negations, it prefixes the permission with "-" and checks
     * if that string exists in the set. We intercept this pattern and check our
     * negation logic instead.
     *
     * @param o the permission to check (must be a String)
     * @return true if the permission is granted (for normal checks) or denied (for negation checks)
     */
    @Override
    public boolean contains(Object o) {
        if (o instanceof String permission) {
            ContextSet contexts = hyperPerms.getContexts(uuid);

            // Handle Hytale's negation check syntax: "-permission"
            // When Hytale checks set.contains("-kick.use"), it's asking "is kick.use denied?"
            if (permission.startsWith("-")) {
                String actualPerm = permission.substring(1);
                recordPermission(actualPerm);
                TriState result = hyperPerms.checkPermission(uuid, actualPerm, contexts);
                return result == TriState.FALSE;
            }

            // Record the permission for discovery
            recordPermission(permission);

            // Normal permission check - use cached permission lookup
            TriState state = hyperPerms.checkPermission(uuid, permission, contexts);
            return state.asBoolean();
        }
        return false;
    }

    /**
     * Records a permission for runtime discovery.
     *
     * @param permission the permission string to record
     */
    private void recordPermission(String permission) {
        RuntimePermissionDiscovery discovery = hyperPerms.getRuntimeDiscovery();
        if (discovery != null) {
            discovery.record(permission);
        }
    }

    /**
     * Checks if the user has all the given permissions.
     *
     * @param c the collection of permissions to check
     * @return true if the user has all permissions
     */
    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @NotNull
    public Iterator<String> iterator() {
        return getGrantedPermissions().iterator();
    }

    @Override
    public int size() {
        return getGrantedPermissions().size();
    }

    @Override
    public boolean isEmpty() {
        return getGrantedPermissions().isEmpty();
    }

    @Override
    @NotNull
    public Object[] toArray() {
        return getGrantedPermissions().toArray();
    }

    @Override
    @NotNull
    public <T> T[] toArray(@NotNull T[] a) {
        return getGrantedPermissions().toArray(a);
    }

    /**
     * Gets the set of granted permissions for iteration purposes.
     * <p>
     * This is lazily computed and cached. The cached value is used for
     * iteration, size(), isEmpty(), and toArray() operations.
     * <p>
     * This replicates the logic from {@code HyperPermsPermissionProvider.getUserPermissions()}
     * to get the resolved and expanded permissions for the user.
     *
     * @return the set of granted permissions
     */
    private Set<String> getGrantedPermissions() {
        if (cachedGranted == null) {
            User user = hyperPerms.getUserManager().getOrCreateUser(uuid);
            ContextSet contexts = hyperPerms.getContexts(uuid);
            PermissionResolver.ResolvedPermissions resolved = hyperPerms.getResolver().resolve(user, contexts);
            cachedGranted = resolved.getExpandedPermissions(hyperPerms.getPermissionRegistry());
        }
        return cachedGranted;
    }
}
