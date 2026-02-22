package com.hyperperms.chat;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hyperperms.model.User;
import static com.hyperperms.util.Logger.debugChat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Resolves the effective prefix and suffix for a user based on their groups.
 *
 * <p>Resolution Priority (highest to lowest):
 * <ol>
 *   <li>User's custom prefix/suffix (if set)</li>
 *   <li>Group with highest prefixPriority/suffixPriority (weight-based selection)</li>
 *   <li>Group with highest weight (if priorities are equal)</li>
 *   <li>Primary group's prefix/suffix (fallback if no group has a prefix/suffix)</li>
 *   <li>Default prefix/suffix from config</li>
 * </ol>
 * 
 * <p>This class supports both synchronous resolution (when groups are cached)
 * and asynchronous resolution (when groups need to be loaded).
 * 
 * <p>Example usage:
 * <pre>{@code
 * PrefixSuffixResolver resolver = new PrefixSuffixResolver(hyperPerms);
 * 
 * // Async resolution
 * resolver.resolve(user).thenAccept(result -> {
 *     String prefix = result.getPrefix();
 *     String suffix = result.getSuffix();
 * });
 * 
 * // Or with UUID
 * resolver.resolve(playerUuid).thenAccept(result -> {
 *     // Use result
 * });
 * }</pre>
 */
public class PrefixSuffixResolver {
    
    private final HyperPerms plugin;
    
    // Default values from config
    private volatile String defaultPrefix = "";
    private volatile String defaultSuffix = "";
    
    // Caching options
    private volatile boolean useInheritedGroups = true;
    private volatile boolean processColors = true;
    
    /**
     * Creates a new PrefixSuffixResolver.
     *
     * @param plugin the HyperPerms plugin instance
     */
    @SuppressWarnings("this-escape")
    public PrefixSuffixResolver(@NotNull HyperPerms plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        loadConfigDefaults();
    }
    
    /**
     * Loads default values from config.
     */
    public void loadConfigDefaults() {
        // These would come from HyperPermsConfig
        // For now using empty defaults - config will override
    }
    
    /**
     * Sets the default prefix used when no group prefix is found.
     *
     * @param defaultPrefix the default prefix
     */
    public void setDefaultPrefix(@Nullable String defaultPrefix) {
        this.defaultPrefix = defaultPrefix != null ? defaultPrefix : "";
    }
    
    /**
     * Sets the default suffix used when no group suffix is found.
     *
     * @param defaultSuffix the default suffix
     */
    public void setDefaultSuffix(@Nullable String defaultSuffix) {
        this.defaultSuffix = defaultSuffix != null ? defaultSuffix : "";
    }
    
    /**
     * Sets whether to include inherited groups in resolution.
     *
     * @param useInheritedGroups true to include inherited groups
     */
    public void setUseInheritedGroups(boolean useInheritedGroups) {
        this.useInheritedGroups = useInheritedGroups;
    }
    
    /**
     * Sets whether to process color codes in the resolved prefix/suffix.
     *
     * @param processColors true to process colors
     */
    public void setProcessColors(boolean processColors) {
        this.processColors = processColors;
    }
    
    /**
     * Resolves the prefix and suffix for a user by UUID.
     * <p>
     * Uses UserManager.loadUser() instead of direct storage access to ensure
     * we get the in-memory cached user (if loaded) which may have more recent
     * changes that haven't been persisted to storage yet.
     *
     * @param uuid the user's UUID
     * @return a future containing the resolution result
     */
    public CompletableFuture<ResolveResult> resolve(@NotNull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        debugChat("PrefixSuffixResolver.resolve: uuid=%s", uuid);

        return plugin.getUserManager().loadUser(uuid)
            .thenCompose(optUser -> {
                if (optUser.isEmpty()) {
                    // User not found - return defaults
                    debugChat("PrefixSuffixResolver.resolve: user not found, returning defaults");
                    return CompletableFuture.completedFuture(
                        new ResolveResult(defaultPrefix, defaultSuffix, null, null)
                    );
                }
                return resolve(optUser.get());
            });
    }
    
    /**
     * Resolves the prefix and suffix for a user.
     *
     * @param user the user
     * @return a future containing the resolution result
     */
    public CompletableFuture<ResolveResult> resolve(@NotNull User user) {
        Objects.requireNonNull(user, "user cannot be null");

        // Check for custom prefix/suffix first (highest priority)
        String customPrefix = user.getCustomPrefix();
        String customSuffix = user.getCustomSuffix();
        debugChat("PrefixSuffixResolver.resolve: user=%s, customPrefix=%s, customSuffix=%s",
                user.getUuid(), customPrefix != null ? "'" + customPrefix + "'" : "null",
                customSuffix != null ? "'" + customSuffix + "'" : "null");

        // If both custom values are set, no need to check groups
        if (customPrefix != null && customSuffix != null) {
            debugChat("PrefixSuffixResolver.resolve: both custom values set, skipping group resolution");
            return CompletableFuture.completedFuture(
                new ResolveResult(
                    processColors ? ColorUtil.colorize(customPrefix) : customPrefix,
                    processColors ? ColorUtil.colorize(customSuffix) : customSuffix,
                    null, // No source group for custom values
                    null
                )
            );
        }

        // Need to resolve from groups
        return resolveFromGroups(user, customPrefix, customSuffix);
    }
    
    /**
     * Resolves prefix/suffix from user's groups.
     * Uses prefix/suffix priority to determine which group's prefix/suffix to use.
     * Primary group is only used as a fallback if no group has a prefix/suffix set.
     */
    private CompletableFuture<ResolveResult> resolveFromGroups(
            @NotNull User user,
            @Nullable String customPrefix,
            @Nullable String customSuffix) {

        // Collect all group names including primary group (same as PermissionResolver)
        Set<String> groupNames = new HashSet<>(user.getInheritedGroups());
        String primaryGroupName = user.getPrimaryGroup();
        if (primaryGroupName != null && !primaryGroupName.isEmpty()) {
            groupNames.add(primaryGroupName);
        }
        debugChat("PrefixSuffixResolver.resolveFromGroups: groups=%s, primaryGroup=%s",
                groupNames, primaryGroupName);

        if (groupNames.isEmpty()) {
            // No groups - use defaults or custom values
            debugChat("PrefixSuffixResolver.resolveFromGroups: no groups, using defaults");
            String prefix = customPrefix != null ? customPrefix : defaultPrefix;
            String suffix = customSuffix != null ? customSuffix : defaultSuffix;
            return CompletableFuture.completedFuture(
                new ResolveResult(
                    processColors ? ColorUtil.colorize(prefix) : prefix,
                    processColors ? ColorUtil.colorize(suffix) : suffix,
                    null, null
                )
            );
        }

        // Load all groups from GroupManager (uses in-memory cache with latest changes)
        List<CompletableFuture<Optional<Group>>> groupFutures = groupNames.stream()
            .map(name -> plugin.getGroupManager().loadGroup(name))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(groupFutures.toArray(new CompletableFuture<?>[0]))
            .thenCompose(v -> {
                List<Group> groups = groupFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
                
                // Find the primary group object
                Group primaryGroup = null;
                if (primaryGroupName != null && !primaryGroupName.isEmpty()) {
                    primaryGroup = groups.stream()
                        .filter(g -> g.getName().equals(primaryGroupName))
                        .findFirst()
                        .orElse(null);
                }
                
                final Group finalPrimaryGroup = primaryGroup;
                
                if (useInheritedGroups) {
                    // Expand to include inherited groups
                    return expandInheritedGroups(groups).thenApply(allGroups -> 
                        resolveFromGroupList(allGroups, customPrefix, customSuffix, finalPrimaryGroup)
                    );
                } else {
                    return CompletableFuture.completedFuture(
                        resolveFromGroupList(groups, customPrefix, customSuffix, finalPrimaryGroup)
                    );
                }
            });
    }
    
    /**
     * Expands a list of groups to include all inherited groups.
     */
    private CompletableFuture<List<Group>> expandInheritedGroups(@NotNull List<Group> directGroups) {
        Set<String> visited = new HashSet<>();
        List<Group> allGroups = new ArrayList<>(directGroups);
        
        // Mark direct groups as visited
        for (Group group : directGroups) {
            visited.add(group.getName().toLowerCase());
        }
        
        return expandInheritedGroupsRecursive(directGroups, visited, allGroups);
    }
    
    /**
     * Recursively expands inherited groups.
     */
    private CompletableFuture<List<Group>> expandInheritedGroupsRecursive(
            @NotNull List<Group> currentLevel,
            @NotNull Set<String> visited,
            @NotNull List<Group> accumulated) {
        
        // Collect all parent group names from current level
        Set<String> parentNames = new HashSet<>();
        for (Group group : currentLevel) {
            for (String parent : group.getParentGroups()) {
                String lowerParent = parent.toLowerCase();
                if (!visited.contains(lowerParent)) {
                    parentNames.add(parent);
                    visited.add(lowerParent);
                }
            }
        }
        
        if (parentNames.isEmpty()) {
            return CompletableFuture.completedFuture(accumulated);
        }
        
        // Load parent groups from GroupManager (uses in-memory cache)
        List<CompletableFuture<Optional<Group>>> parentFutures = parentNames.stream()
            .map(name -> plugin.getGroupManager().loadGroup(name))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(parentFutures.toArray(new CompletableFuture<?>[0]))
            .thenCompose(v -> {
                List<Group> parentGroups = parentFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
                
                accumulated.addAll(parentGroups);
                
                // Recurse for next level of inheritance
                return expandInheritedGroupsRecursive(parentGroups, visited, accumulated);
            });
    }
    
    /**
     * Resolves prefix/suffix from a list of groups.
     * Always uses weight/priority-based selection first (highest prefixPriority/suffixPriority,
     * then highest group weight). Primary group is only used as a fallback if no group
     * has a prefix/suffix set.
     */
    private ResolveResult resolveFromGroupList(
            @NotNull List<Group> groups,
            @Nullable String customPrefix,
            @Nullable String customSuffix,
            @Nullable Group primaryGroup) {
        
        String resolvedPrefix;
        String resolvedSuffix;
        Group prefixSource = null;
        Group suffixSource = null;
        
        // Resolve prefix
        if (customPrefix != null) {
            resolvedPrefix = customPrefix;
            debugChat("resolveFromGroupList: using custom prefix '%s'", customPrefix);
        } else {
            // Always use weight/priority-based selection first
            GroupPrefixSuffix best = findBestPrefix(groups);
            if (best != null) {
                resolvedPrefix = best.value;
                prefixSource = best.group;
                debugChat("resolveFromGroupList: best prefix '%s' from group '%s' (priority=%d, weight=%d)",
                        best.value, best.group.getName(), getEffectivePrefixPriority(best.group), best.group.getWeight());
            } else if (primaryGroup != null && primaryGroup.getPrefix() != null && !primaryGroup.getPrefix().isEmpty()) {
                // Fall back to primary group only if no weighted prefix found
                resolvedPrefix = primaryGroup.getPrefix();
                prefixSource = primaryGroup;
                debugChat("resolveFromGroupList: fallback to primary group prefix '%s' from '%s'",
                        resolvedPrefix, primaryGroup.getName());
            } else {
                resolvedPrefix = defaultPrefix;
                debugChat("resolveFromGroupList: using default prefix '%s'", defaultPrefix);
            }
        }

        // Resolve suffix
        if (customSuffix != null) {
            resolvedSuffix = customSuffix;
            debugChat("resolveFromGroupList: using custom suffix '%s'", customSuffix);
        } else {
            // Always use weight/priority-based selection first
            GroupPrefixSuffix best = findBestSuffix(groups);
            if (best != null) {
                resolvedSuffix = best.value;
                suffixSource = best.group;
                debugChat("resolveFromGroupList: best suffix '%s' from group '%s' (priority=%d, weight=%d)",
                        best.value, best.group.getName(), getEffectiveSuffixPriority(best.group), best.group.getWeight());
            } else if (primaryGroup != null && primaryGroup.getSuffix() != null && !primaryGroup.getSuffix().isEmpty()) {
                // Fall back to primary group only if no weighted suffix found
                resolvedSuffix = primaryGroup.getSuffix();
                suffixSource = primaryGroup;
                debugChat("resolveFromGroupList: fallback to primary group suffix '%s' from '%s'",
                        resolvedSuffix, primaryGroup.getName());
            } else {
                resolvedSuffix = defaultSuffix;
                debugChat("resolveFromGroupList: using default suffix '%s'", defaultSuffix);
            }
        }

        // Process colors if enabled
        if (processColors) {
            resolvedPrefix = ColorUtil.colorize(resolvedPrefix);
            resolvedSuffix = ColorUtil.colorize(resolvedSuffix);
        }

        debugChat("resolveFromGroupList: final prefix='%s', suffix='%s', prefixSource=%s, suffixSource=%s",
                resolvedPrefix, resolvedSuffix,
                prefixSource != null ? prefixSource.getName() : "custom/default",
                suffixSource != null ? suffixSource.getName() : "custom/default");

        return new ResolveResult(resolvedPrefix, resolvedSuffix, prefixSource, suffixSource);
    }
    
    /**
     * Legacy overload for backward compatibility.
     */
    private ResolveResult resolveFromGroupList(
            @NotNull List<Group> groups,
            @Nullable String customPrefix,
            @Nullable String customSuffix) {
        return resolveFromGroupList(groups, customPrefix, customSuffix, null);
    }
    
    /**
     * Finds the best prefix from a list of groups.
     * Selection criteria:
     * 1. Highest effective prefix priority (prefixPriority if set, otherwise weight)
     * 2. If tie, highest group weight
     * 3. If still tie, alphabetical group name
     *
     * <p>Note: When prefixPriority is 0 (default/not set), the group's weight is used
     * as the effective priority. This allows weight to determine prefix ordering by
     * default, while still allowing explicit prefixPriority overrides.
     */
    @Nullable
    private GroupPrefixSuffix findBestPrefix(@NotNull List<Group> groups) {
        return groups.stream()
            .filter(g -> g.getPrefix() != null && !g.getPrefix().isEmpty())
            .max(Comparator
                .comparingInt((Group g) -> getEffectivePrefixPriority(g))
                .thenComparingInt(Group::getWeight)
                .thenComparing(Group::getName))
            .map(g -> new GroupPrefixSuffix(g, g.getPrefix()))
            .orElse(null);
    }

    /**
     * Finds the best suffix from a list of groups.
     * Selection criteria:
     * 1. Highest effective suffix priority (suffixPriority if set, otherwise weight)
     * 2. If tie, highest group weight
     * 3. If still tie, alphabetical group name
     *
     * <p>Note: When suffixPriority is 0 (default/not set), the group's weight is used
     * as the effective priority. This allows weight to determine suffix ordering by
     * default, while still allowing explicit suffixPriority overrides.
     */
    @Nullable
    private GroupPrefixSuffix findBestSuffix(@NotNull List<Group> groups) {
        return groups.stream()
            .filter(g -> g.getSuffix() != null && !g.getSuffix().isEmpty())
            .max(Comparator
                .comparingInt((Group g) -> getEffectiveSuffixPriority(g))
                .thenComparingInt(Group::getWeight)
                .thenComparing(Group::getName))
            .map(g -> new GroupPrefixSuffix(g, g.getSuffix()))
            .orElse(null);
    }

    /**
     * Gets the effective prefix priority for a group.
     * If prefixPriority is explicitly set (non-zero), use it.
     * Otherwise, fall back to the group's weight.
     */
    private int getEffectivePrefixPriority(@NotNull Group group) {
        int priority = group.getPrefixPriority();
        return priority != 0 ? priority : group.getWeight();
    }

    /**
     * Gets the effective suffix priority for a group.
     * If suffixPriority is explicitly set (non-zero), use it.
     * Otherwise, fall back to the group's weight.
     */
    private int getEffectiveSuffixPriority(@NotNull Group group) {
        int priority = group.getSuffixPriority();
        return priority != 0 ? priority : group.getWeight();
    }
    
    /**
     * Internal holder for group prefix/suffix pair.
     */
    private static class GroupPrefixSuffix {
        final Group group;
        final String value;
        
        GroupPrefixSuffix(Group group, String value) {
            this.group = group;
            this.value = value;
        }
    }
    
    /**
     * Resolves all display data for a user (prefix, suffix, primary group, etc.).
     * This is a convenience method for chat formatting.
     *
     * @param user the user
     * @return a future containing full display data
     */
    public CompletableFuture<DisplayData> resolveDisplayData(@NotNull User user) {
        Objects.requireNonNull(user, "user cannot be null");
        
        return resolve(user).thenCompose(result -> 
            resolvePrimaryGroup(user).thenApply(primaryGroup -> 
                new DisplayData(
                    result.getPrefix(),
                    result.getSuffix(),
                    primaryGroup,
                    result.getPrefixSourceGroup(),
                    result.getSuffixSourceGroup(),
                    user
                )
            )
        );
    }
    
    /**
     * Resolves the primary group for a user.
     * Uses the user's explicit primaryGroup setting if available,
     * otherwise falls back to highest weight group.
     */
    private CompletableFuture<Group> resolvePrimaryGroup(@NotNull User user) {
        // First, check if user has an explicit primary group set
        String explicitPrimary = user.getPrimaryGroup();
        if (explicitPrimary != null && !explicitPrimary.isEmpty()) {
            return plugin.getGroupManager().loadGroup(explicitPrimary)
                .thenApply(opt -> opt.orElse(null));
        }

        // Fallback: find highest weight group from inherited groups
        Set<String> groupNames = user.getInheritedGroups();
        if (groupNames.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Optional<Group>>> futures = groupNames.stream()
            .map(name -> plugin.getGroupManager().loadGroup(name))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(Group::getWeight))
                .orElse(null)
            );
    }
    
    /**
     * Result of prefix/suffix resolution.
     */
    public static class ResolveResult {
        private final String prefix;
        private final String suffix;
        private final Group prefixSourceGroup;
        private final Group suffixSourceGroup;
        
        public ResolveResult(
                @NotNull String prefix, 
                @NotNull String suffix,
                @Nullable Group prefixSourceGroup,
                @Nullable Group suffixSourceGroup) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.prefixSourceGroup = prefixSourceGroup;
            this.suffixSourceGroup = suffixSourceGroup;
        }
        
        /**
         * Gets the resolved prefix (with colors processed if enabled).
         */
        @NotNull
        public String getPrefix() {
            return prefix;
        }
        
        /**
         * Gets the resolved suffix (with colors processed if enabled).
         */
        @NotNull
        public String getSuffix() {
            return suffix;
        }
        
        /**
         * Gets the group that provided the prefix, or null if custom/default.
         */
        @Nullable
        public Group getPrefixSourceGroup() {
            return prefixSourceGroup;
        }
        
        /**
         * Gets the group that provided the suffix, or null if custom/default.
         */
        @Nullable
        public Group getSuffixSourceGroup() {
            return suffixSourceGroup;
        }
        
        /**
         * Checks if the prefix came from a custom user setting.
         */
        public boolean isPrefixCustom() {
            return prefixSourceGroup == null && !prefix.isEmpty();
        }
        
        /**
         * Checks if the suffix came from a custom user setting.
         */
        public boolean isSuffixCustom() {
            return suffixSourceGroup == null && !suffix.isEmpty();
        }
        
        @Override
        public String toString() {
            return "ResolveResult{" +
                "prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                ", prefixSource=" + (prefixSourceGroup != null ? prefixSourceGroup.getName() : "custom/default") +
                ", suffixSource=" + (suffixSourceGroup != null ? suffixSourceGroup.getName() : "custom/default") +
                '}';
        }
    }
    
    /**
     * Complete display data for a user, useful for chat formatting.
     */
    public static class DisplayData {
        private final String prefix;
        private final String suffix;
        private final Group primaryGroup;
        private final Group prefixSourceGroup;
        private final Group suffixSourceGroup;
        private final User user;
        
        public DisplayData(
                @NotNull String prefix,
                @NotNull String suffix,
                @Nullable Group primaryGroup,
                @Nullable Group prefixSourceGroup,
                @Nullable Group suffixSourceGroup,
                @NotNull User user) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.primaryGroup = primaryGroup;
            this.prefixSourceGroup = prefixSourceGroup;
            this.suffixSourceGroup = suffixSourceGroup;
            this.user = user;
        }
        
        @NotNull
        public String getPrefix() {
            return prefix;
        }
        
        @NotNull
        public String getSuffix() {
            return suffix;
        }
        
        @Nullable
        public Group getPrimaryGroup() {
            return primaryGroup;
        }
        
        /**
         * Gets the primary group name, or "default" if none.
         */
        @NotNull
        public String getPrimaryGroupName() {
            return primaryGroup != null ? primaryGroup.getName() : "default";
        }
        
        /**
         * Gets the primary group display name (with colors), or the group name if no display name.
         */
        @NotNull
        public String getPrimaryGroupDisplayName() {
            if (primaryGroup == null) {
                return "Default";
            }
            String display = primaryGroup.getDisplayName();
            return display != null ? ColorUtil.colorize(display) : primaryGroup.getName();
        }
        
        @Nullable
        public Group getPrefixSourceGroup() {
            return prefixSourceGroup;
        }
        
        @Nullable
        public Group getSuffixSourceGroup() {
            return suffixSourceGroup;
        }
        
        @NotNull
        public User getUser() {
            return user;
        }
        
        /**
         * Gets the group weight/rank of the primary group.
         */
        public int getRank() {
            return primaryGroup != null ? primaryGroup.getWeight() : 0;
        }
        
        /**
         * Builds a PlaceholderContext from this display data.
         *
         * @param playerName the player's display name
         * @return a PlaceholderContext builder with pre-filled values
         */
        public ChatFormatter.PlaceholderContext.Builder toPlaceholderContext(@NotNull String playerName) {
            return ChatFormatter.PlaceholderContext.builder()
                .playerName(playerName)
                .displayName(playerName)
                .prefix(prefix)
                .suffix(suffix)
                .primaryGroup(getPrimaryGroupName())
                .extra("group_display_name", getPrimaryGroupDisplayName())
                .extra("rank", String.valueOf(getRank()));
        }
        
        @Override
        public String toString() {
            return "DisplayData{" +
                "prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                ", primaryGroup=" + getPrimaryGroupName() +
                ", user=" + user.getUuid() +
                '}';
        }
    }
    
    /**
     * Creates a builder for more advanced resolution configuration.
     *
     * @return a new resolver builder
     */
    public ResolverBuilder newResolverBuilder() {
        return new ResolverBuilder(this);
    }
    
    /**
     * Builder for customized prefix/suffix resolution.
     */
    public static class ResolverBuilder {
        private final PrefixSuffixResolver resolver;
        private UUID uuid;
        private User user;
        private boolean includeInheritedGroups = true;
        private boolean processColors = true;
        private String defaultPrefix = "";
        private String defaultSuffix = "";
        private Set<String> excludeGroups = new HashSet<>();
        
        private ResolverBuilder(PrefixSuffixResolver resolver) {
            this.resolver = resolver;
        }
        
        /**
         * Sets the user UUID to resolve for.
         */
        public ResolverBuilder forUuid(@NotNull UUID uuid) {
            this.uuid = uuid;
            this.user = null;
            return this;
        }
        
        /**
         * Sets the user to resolve for.
         */
        public ResolverBuilder forUser(@NotNull User user) {
            this.user = user;
            this.uuid = null;
            return this;
        }
        
        /**
         * Sets whether to include inherited groups.
         */
        public ResolverBuilder includeInheritedGroups(boolean include) {
            this.includeInheritedGroups = include;
            return this;
        }
        
        /**
         * Sets whether to process color codes.
         */
        public ResolverBuilder processColors(boolean process) {
            this.processColors = process;
            return this;
        }
        
        /**
         * Sets the default prefix.
         */
        public ResolverBuilder defaultPrefix(@Nullable String prefix) {
            this.defaultPrefix = prefix != null ? prefix : "";
            return this;
        }
        
        /**
         * Sets the default suffix.
         */
        public ResolverBuilder defaultSuffix(@Nullable String suffix) {
            this.defaultSuffix = suffix != null ? suffix : "";
            return this;
        }
        
        /**
         * Excludes a group from consideration.
         */
        public ResolverBuilder excludeGroup(@NotNull String groupName) {
            this.excludeGroups.add(groupName.toLowerCase());
            return this;
        }
        
        /**
         * Excludes multiple groups from consideration.
         */
        public ResolverBuilder excludeGroups(@NotNull Collection<String> groupNames) {
            groupNames.forEach(g -> this.excludeGroups.add(g.toLowerCase()));
            return this;
        }
        
        /**
         * Executes the resolution.
         */
        public CompletableFuture<ResolveResult> resolve() {
            // Store original settings
            boolean origInherited = resolver.useInheritedGroups;
            boolean origColors = resolver.processColors;
            String origDefaultPrefix = resolver.defaultPrefix;
            String origDefaultSuffix = resolver.defaultSuffix;
            
            try {
                // Apply builder settings
                resolver.useInheritedGroups = this.includeInheritedGroups;
                resolver.processColors = this.processColors;
                resolver.defaultPrefix = this.defaultPrefix;
                resolver.defaultSuffix = this.defaultSuffix;
                
                // Resolve
                CompletableFuture<ResolveResult> result;
                if (user != null) {
                    result = resolver.resolve(user);
                } else if (uuid != null) {
                    result = resolver.resolve(uuid);
                } else {
                    throw new IllegalStateException("Must specify either user or uuid");
                }
                
                return result;
            } finally {
                // Restore original settings
                resolver.useInheritedGroups = origInherited;
                resolver.processColors = origColors;
                resolver.defaultPrefix = origDefaultPrefix;
                resolver.defaultSuffix = origDefaultSuffix;
            }
        }
    }
}
