package com.hyperperms.query;

import com.hyperperms.api.query.GroupQueryBuilder;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the GroupQueryBuilder.
 */
public final class GroupQueryBuilderImpl implements GroupQueryBuilder {

    private final GroupManagerImpl groupManager;
    private final Supplier<Set<Track>> trackSupplier;
    private final List<java.util.function.Predicate<Group>> filters = new ArrayList<>();

    public GroupQueryBuilderImpl(@NotNull GroupManagerImpl groupManager,
                                  @NotNull Supplier<Set<Track>> trackSupplier) {
        this.groupManager = groupManager;
        this.trackSupplier = trackSupplier;
    }

    @Override
    @NotNull
    public GroupQueryBuilder withPermission(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        filters.add(group -> group.getNodes().stream()
                .anyMatch(node -> node.getPermission().equals(lowerPerm) && node.getValue() && !node.isExpired()));
        return this;
    }

    @Override
    @NotNull
    public GroupQueryBuilder withWeightBetween(int min, int max) {
        filters.add(group -> group.getWeight() >= min && group.getWeight() <= max);
        return this;
    }

    @Override
    @NotNull
    public GroupQueryBuilder inheritsFrom(@NotNull String parentGroup) {
        String lowerParent = parentGroup.toLowerCase();
        filters.add(group -> group.getInheritedGroups().contains(lowerParent));
        return this;
    }

    @Override
    @NotNull
    public GroupQueryBuilder withPrefix() {
        filters.add(group -> group.getPrefix() != null && !group.getPrefix().isEmpty());
        return this;
    }

    @Override
    @NotNull
    public GroupQueryBuilder withSuffix() {
        filters.add(group -> group.getSuffix() != null && !group.getSuffix().isEmpty());
        return this;
    }

    @Override
    @NotNull
    public GroupQueryBuilder onTrack(@NotNull String trackName) {
        String lowerTrack = trackName.toLowerCase();
        filters.add(group -> {
            for (Track track : trackSupplier.get()) {
                if (track.getName().equalsIgnoreCase(lowerTrack)) {
                    return track.getGroups().contains(group.getName());
                }
            }
            return false;
        });
        return this;
    }

    @Override
    @NotNull
    public Set<String> execute() {
        Stream<Group> stream = groupManager.getLoadedGroups().stream();

        // Apply all filters
        for (var filter : filters) {
            stream = stream.filter(filter);
        }

        return stream.map(Group::getName).collect(Collectors.toSet());
    }

    @Override
    @NotNull
    public Set<Group> executeAndLoad() {
        Stream<Group> stream = groupManager.getLoadedGroups().stream();

        // Apply all filters
        for (var filter : filters) {
            stream = stream.filter(filter);
        }

        return stream.collect(Collectors.toSet());
    }

    @Override
    public int count() {
        Stream<Group> stream = groupManager.getLoadedGroups().stream();

        // Apply all filters
        for (var filter : filters) {
            stream = stream.filter(filter);
        }

        return (int) stream.count();
    }
}
