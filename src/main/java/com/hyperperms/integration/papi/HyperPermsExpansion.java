package com.hyperperms.integration.papi;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hyperperms.HyperPerms;
import com.hyperperms.chat.PrefixSuffixResolver;
import com.hyperperms.model.Group;
import com.hyperperms.model.User;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * PlaceholderAPI expansion for HyperPerms.
 * <p>
 * Exposes HyperPerms data as PAPI placeholders:
 * <ul>
 *   <li>%hyperperms_prefix% - Player's effective prefix</li>
 *   <li>%hyperperms_suffix% - Player's effective suffix</li>
 *   <li>%hyperperms_group% - Primary group name</li>
 *   <li>%hyperperms_group_display% - Primary group display name</li>
 *   <li>%hyperperms_groups% - All groups (comma-separated)</li>
 *   <li>%hyperperms_group_count% - Number of groups</li>
 *   <li>%hyperperms_weight% - Primary group weight</li>
 *   <li>%hyperperms_has_&lt;permission&gt;% - Check permission (true/false)</li>
 *   <li>%hyperperms_in_group_&lt;name&gt;% - Check group membership (true/false)</li>
 * </ul>
 */
public class HyperPermsExpansion extends PlaceholderExpansion {

    private final HyperPerms plugin;

    /**
     * Creates a new HyperPermsExpansion.
     *
     * @param plugin the HyperPerms plugin instance
     */
    public HyperPermsExpansion(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hyperperms";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HyperSystemsDev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(@Nullable PlayerRef player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        UUID uuid = player.getUuid();
        if (uuid == null) {
            return null;
        }

        String lowerParams = params.toLowerCase();

        return switch (lowerParams) {
            case "prefix" -> getPrefix(uuid);
            case "suffix" -> getSuffix(uuid);
            case "group" -> getPrimaryGroup(uuid);
            case "group_display" -> getPrimaryGroupDisplay(uuid);
            case "groups" -> getAllGroups(uuid);
            case "group_count" -> getGroupCount(uuid);
            case "weight" -> getWeight(uuid);
            default -> handleDynamicPlaceholder(uuid, lowerParams);
        };
    }

    /**
     * Handles dynamic placeholders with parameters.
     */
    @Nullable
    private String handleDynamicPlaceholder(@NotNull UUID uuid, @NotNull String params) {
        // %hyperperms_has_<permission>%
        if (params.startsWith("has_")) {
            String permission = params.substring(4);
            return checkPermission(uuid, permission);
        }

        // %hyperperms_in_group_<name>%
        if (params.startsWith("in_group_")) {
            String groupName = params.substring(9);
            return checkInGroup(uuid, groupName);
        }

        // Unknown placeholder
        return null;
    }

    /**
     * Gets the player's effective prefix.
     */
    @NotNull
    private String getPrefix(@NotNull UUID uuid) {
        try {
            PrefixSuffixResolver.ResolveResult result = plugin.getChatManager()
                    .getPrefixSuffixResolver()
                    .resolve(uuid)
                    .join();
            return result.getPrefix();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Gets the player's effective suffix.
     */
    @NotNull
    private String getSuffix(@NotNull UUID uuid) {
        try {
            PrefixSuffixResolver.ResolveResult result = plugin.getChatManager()
                    .getPrefixSuffixResolver()
                    .resolve(uuid)
                    .join();
            return result.getSuffix();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Gets the player's primary group name.
     */
    @NotNull
    private String getPrimaryGroup(@NotNull UUID uuid) {
        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            return plugin.getConfig().getDefaultGroup();
        }

        String primaryGroup = user.getPrimaryGroup();
        if (primaryGroup != null && !primaryGroup.isEmpty()) {
            return primaryGroup;
        }

        // Fallback to highest weight group
        Set<String> groups = user.getInheritedGroups();
        if (groups.isEmpty()) {
            return plugin.getConfig().getDefaultGroup();
        }

        return groups.stream()
                .map(name -> plugin.getGroupManager().getGroup(name))
                .filter(g -> g != null)
                .max((a, b) -> Integer.compare(a.getWeight(), b.getWeight()))
                .map(Group::getName)
                .orElse(plugin.getConfig().getDefaultGroup());
    }

    /**
     * Gets the player's primary group display name.
     */
    @NotNull
    private String getPrimaryGroupDisplay(@NotNull UUID uuid) {
        String groupName = getPrimaryGroup(uuid);
        Group group = plugin.getGroupManager().getGroup(groupName);

        if (group == null) {
            return groupName;
        }

        String displayName = group.getDisplayName();
        return displayName != null && !displayName.isEmpty() ? displayName : group.getName();
    }

    /**
     * Gets all groups the player is in (comma-separated).
     */
    @NotNull
    private String getAllGroups(@NotNull UUID uuid) {
        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            return plugin.getConfig().getDefaultGroup();
        }

        Set<String> groups = user.getInheritedGroups();
        if (groups.isEmpty()) {
            return plugin.getConfig().getDefaultGroup();
        }

        return String.join(", ", groups);
    }

    /**
     * Gets the number of groups the player is in.
     */
    @NotNull
    private String getGroupCount(@NotNull UUID uuid) {
        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            return "1"; // Default group
        }

        Set<String> groups = user.getInheritedGroups();
        return String.valueOf(Math.max(1, groups.size()));
    }

    /**
     * Gets the weight of the player's primary group.
     */
    @NotNull
    private String getWeight(@NotNull UUID uuid) {
        String groupName = getPrimaryGroup(uuid);
        Group group = plugin.getGroupManager().getGroup(groupName);
        return group != null ? String.valueOf(group.getWeight()) : "0";
    }

    /**
     * Checks if the player has a permission.
     */
    @NotNull
    private String checkPermission(@NotNull UUID uuid, @NotNull String permission) {
        // Convert underscores back to dots for permission checks
        String actualPermission = permission.replace('_', '.');
        boolean hasPermission = plugin.hasPermission(uuid, actualPermission);
        return String.valueOf(hasPermission);
    }

    /**
     * Checks if the player is in a specific group.
     */
    @NotNull
    private String checkInGroup(@NotNull UUID uuid, @NotNull String groupName) {
        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            return String.valueOf(groupName.equalsIgnoreCase(plugin.getConfig().getDefaultGroup()));
        }

        Set<String> groups = user.getInheritedGroups();
        boolean inGroup = groups.stream()
                .anyMatch(g -> g.equalsIgnoreCase(groupName));
        return String.valueOf(inGroup);
    }
}
