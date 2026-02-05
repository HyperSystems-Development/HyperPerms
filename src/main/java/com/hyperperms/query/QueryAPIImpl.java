package com.hyperperms.query;

import com.hyperperms.api.QueryAPI;
import com.hyperperms.api.query.GroupQueryBuilder;
import com.hyperperms.api.query.UserQueryBuilder;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.resolver.WildcardMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of the QueryAPI.
 */
public final class QueryAPIImpl implements QueryAPI {

    private final UserManagerImpl userManager;
    private final GroupManagerImpl groupManager;
    private final Supplier<Set<Track>> trackSupplier;

    public QueryAPIImpl(@NotNull UserManagerImpl userManager, @NotNull GroupManagerImpl groupManager,
                        @NotNull Supplier<Set<Track>> trackSupplier) {
        this.userManager = userManager;
        this.groupManager = groupManager;
        this.trackSupplier = trackSupplier;
    }

    @Override
    @NotNull
    public CompletableFuture<Set<UUID>> findUsersWithPermission(@NotNull String permission) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> result = new HashSet<>();
            String lowerPerm = permission.toLowerCase();

            for (User user : userManager.getLoadedUsers()) {
                for (Node node : user.getNodes()) {
                    if (node.getPermission().equals(lowerPerm) && node.getValue() && !node.isExpired()) {
                        result.add(user.getUuid());
                        break;
                    }
                }
            }
            return result;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Set<UUID>> findUsersWithPermissionPattern(@NotNull String pattern) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> result = new HashSet<>();
            Pattern regex = wildcardToRegex(pattern);

            for (User user : userManager.getLoadedUsers()) {
                for (Node node : user.getNodes()) {
                    if (regex.matcher(node.getPermission()).matches() && node.getValue() && !node.isExpired()) {
                        result.add(user.getUuid());
                        break;
                    }
                }
            }
            return result;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Set<UUID>> findUsersInGroup(@NotNull String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> result = new HashSet<>();
            String lowerGroup = groupName.toLowerCase();

            for (User user : userManager.getLoadedUsers()) {
                if (user.getInheritedGroups().contains(lowerGroup)) {
                    result.add(user.getUuid());
                }
            }
            return result;
        });
    }

    @Override
    @NotNull
    public UserQueryBuilder queryUsers() {
        return new UserQueryBuilderImpl(userManager);
    }

    @Override
    @NotNull
    public Set<String> findGroupsWithPermission(@NotNull String permission) {
        Set<String> result = new HashSet<>();
        String lowerPerm = permission.toLowerCase();

        for (Group group : groupManager.getLoadedGroups()) {
            for (Node node : group.getNodes()) {
                if (node.getPermission().equals(lowerPerm) && node.getValue() && !node.isExpired()) {
                    result.add(group.getName());
                    break;
                }
            }
        }
        return result;
    }

    @Override
    @NotNull
    public Set<String> findGroupsByWeight(int min, int max) {
        return groupManager.getLoadedGroups().stream()
                .filter(g -> g.getWeight() >= min && g.getWeight() <= max)
                .map(Group::getName)
                .collect(Collectors.toSet());
    }

    @Override
    @NotNull
    public GroupQueryBuilder queryGroups() {
        return new GroupQueryBuilderImpl(groupManager, trackSupplier);
    }

    @Override
    public boolean matchesPattern(@NotNull String permission, @NotNull String pattern) {
        return wildcardToRegex(pattern).matcher(permission.toLowerCase()).matches();
    }

    @Override
    @NotNull
    public Set<String> getMatchingPermissions(@NotNull String pattern) {
        Set<String> result = new HashSet<>();
        Pattern regex = wildcardToRegex(pattern);

        // From users
        for (User user : userManager.getLoadedUsers()) {
            for (Node node : user.getNodes()) {
                if (regex.matcher(node.getPermission()).matches()) {
                    result.add(node.getPermission());
                }
            }
        }

        // From groups
        for (Group group : groupManager.getLoadedGroups()) {
            for (Node node : group.getNodes()) {
                if (regex.matcher(node.getPermission()).matches()) {
                    result.add(node.getPermission());
                }
            }
        }

        return result;
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
     *
     * @param wildcard the wildcard pattern (e.g., "admin.*")
     * @return the compiled regex pattern
     */
    private static Pattern wildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");

        for (char c : wildcard.toLowerCase().toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                default -> regex.append(c);
            }
        }

        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
