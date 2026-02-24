package com.hyperperms.task;

import com.hyperperms.api.PermissionHolder;
import com.hyperperms.api.events.EventBus;
import com.hyperperms.api.events.PermissionChangeEvent;
import com.hyperperms.api.events.PermissionChangeEvent.ChangeType;
import com.hyperperms.cache.CacheInvalidator;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.model.Node;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scheduled task that cleans up expired permissions and fires events.
 */
public final class ExpiryCleanupTask implements Runnable {

    private final UserManagerImpl userManager;
    private final GroupManagerImpl groupManager;
    @Nullable
    private final EventBus eventBus;
    @Nullable
    private final CacheInvalidator cacheInvalidator;

    /**
     * Creates a new expiry cleanup task.
     *
     * @param userManager  the user manager
     * @param groupManager the group manager
     */
    public ExpiryCleanupTask(@NotNull UserManagerImpl userManager, @NotNull GroupManagerImpl groupManager) {
        this(userManager, groupManager, null, null);
    }

    /**
     * Creates a new expiry cleanup task with event firing.
     *
     * @param userManager      the user manager
     * @param groupManager     the group manager
     * @param eventBus         the event bus for firing expiry events, or null to disable
     * @param cacheInvalidator the cache invalidator for triggering sync, or null to disable
     */
    public ExpiryCleanupTask(@NotNull UserManagerImpl userManager, @NotNull GroupManagerImpl groupManager,
                             @Nullable EventBus eventBus, @Nullable CacheInvalidator cacheInvalidator) {
        this.userManager = userManager;
        this.groupManager = groupManager;
        this.eventBus = eventBus;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Override
    public void run() {
        try {
            int userExpired = cleanupUsers();
            int groupExpired = cleanupGroups();

            if (userExpired > 0 || groupExpired > 0) {
                Logger.debug("Cleaned up %d user and %d group expired permissions",
                        userExpired, groupExpired);
            }
        } catch (Exception e) {
            Logger.warn("Error during expiry cleanup: " + e.getMessage());
        }
    }

    /**
     * Cleans up expired permissions for all loaded users.
     *
     * @return the number of permissions removed
     */
    private int cleanupUsers() {
        int total = 0;
        for (var user : userManager.getLoadedUsers()) {
            List<Node> expiredNodes = collectExpiredNodes(user.getNodes());
            if (!expiredNodes.isEmpty()) {
                for (Node node : expiredNodes) {
                    user.removeNode(node);
                    fireExpiryEvent(user, node);
                    total++;
                }
                userManager.saveUser(user);
                if (cacheInvalidator != null) {
                    cacheInvalidator.invalidate(user.getUuid());
                }
            }
        }
        return total;
    }

    /**
     * Cleans up expired permissions for all loaded groups.
     *
     * @return the number of permissions removed
     */
    private int cleanupGroups() {
        int total = 0;
        for (var group : groupManager.getLoadedGroups()) {
            List<Node> expiredNodes = collectExpiredNodes(group.getNodes());
            if (!expiredNodes.isEmpty()) {
                for (Node node : expiredNodes) {
                    group.removeNode(node);
                    fireExpiryEvent(group, node);
                    total++;
                }
                groupManager.saveGroup(group);
                if (cacheInvalidator != null) {
                    cacheInvalidator.invalidateGroup(group.getName());
                }
            }
        }
        return total;
    }

    /**
     * Collects expired nodes from a set of nodes.
     *
     * @param nodes the nodes to check
     * @return list of expired nodes
     */
    private List<Node> collectExpiredNodes(Set<Node> nodes) {
        List<Node> expired = new ArrayList<>();
        for (Node node : nodes) {
            if (node.isExpired()) {
                expired.add(node);
            }
        }
        return expired;
    }

    /**
     * Fires an expiry event for a removed node.
     *
     * @param holder the holder that had the node
     * @param node   the expired node
     */
    private void fireExpiryEvent(PermissionHolder holder, Node node) {
        if (eventBus != null) {
            eventBus.fire(new PermissionChangeEvent(holder, node, ChangeType.EXPIRE));
        }
    }
}
