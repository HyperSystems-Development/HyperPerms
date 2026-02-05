package com.hyperperms.query;

import com.hyperperms.api.query.UserQueryBuilder;
import com.hyperperms.manager.UserManagerImpl;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the UserQueryBuilder.
 */
public final class UserQueryBuilderImpl implements UserQueryBuilder {

    private final UserManagerImpl userManager;
    private final List<java.util.function.Predicate<User>> filters = new ArrayList<>();
    private int limit = -1;
    private int offset = 0;

    public UserQueryBuilderImpl(@NotNull UserManagerImpl userManager) {
        this.userManager = userManager;
    }

    @Override
    @NotNull
    public UserQueryBuilder withPermission(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        filters.add(user -> user.getNodes().stream()
                .anyMatch(node -> node.getPermission().equals(lowerPerm) && node.getValue() && !node.isExpired()));
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder withPermissionPattern(@NotNull String pattern) {
        Pattern regex = wildcardToRegex(pattern);
        filters.add(user -> user.getNodes().stream()
                .anyMatch(node -> regex.matcher(node.getPermission()).matches() && node.getValue() && !node.isExpired()));
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder inGroup(@NotNull String groupName) {
        String lowerGroup = groupName.toLowerCase();
        filters.add(user -> user.getInheritedGroups().contains(lowerGroup));
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder inAnyGroup(@NotNull String... groupNames) {
        Set<String> lowerGroups = Arrays.stream(groupNames)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        filters.add(user -> {
            for (String group : user.getInheritedGroups()) {
                if (lowerGroups.contains(group)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder withPrimaryGroup(@NotNull String groupName) {
        String lowerGroup = groupName.toLowerCase();
        filters.add(user -> user.getPrimaryGroup().equalsIgnoreCase(lowerGroup));
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder withContext(@NotNull String key, @NotNull String value) {
        filters.add(user -> user.getNodes().stream()
                .filter(node -> !node.isExpired())
                .anyMatch(node -> node.getContexts().has(key, value)));
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    @NotNull
    public UserQueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    @Override
    @NotNull
    public CompletableFuture<Set<UUID>> execute() {
        return CompletableFuture.supplyAsync(() -> {
            Stream<User> stream = userManager.getLoadedUsers().stream();

            // Apply all filters
            for (var filter : filters) {
                stream = stream.filter(filter);
            }

            // Apply offset
            if (offset > 0) {
                stream = stream.skip(offset);
            }

            // Apply limit
            if (limit > 0) {
                stream = stream.limit(limit);
            }

            return stream.map(User::getUuid).collect(Collectors.toSet());
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<User>> executeAndLoad() {
        return CompletableFuture.supplyAsync(() -> {
            Stream<User> stream = userManager.getLoadedUsers().stream();

            // Apply all filters
            for (var filter : filters) {
                stream = stream.filter(filter);
            }

            // Apply offset
            if (offset > 0) {
                stream = stream.skip(offset);
            }

            // Apply limit
            if (limit > 0) {
                stream = stream.limit(limit);
            }

            return stream.collect(Collectors.toList());
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> count() {
        return CompletableFuture.supplyAsync(() -> {
            Stream<User> stream = userManager.getLoadedUsers().stream();

            // Apply all filters
            for (var filter : filters) {
                stream = stream.filter(filter);
            }

            return (int) stream.count();
        });
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
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
