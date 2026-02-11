package com.hyperperms.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/**
 * Preview of a migration operation (dry-run result).
 * <p>
 * Contains all information about what would be migrated without actually
 * making any changes.
 *
 * @param sourceName         name of the source system (e.g., "LuckPerms")
 * @param storageDescription description of the storage being migrated from
 * @param groups             groups that would be imported
 * @param userStats          user statistics for the migration
 * @param permissionStats    permission statistics
 * @param tracks             tracks that would be imported
 * @param conflicts          detected conflicts with existing HyperPerms data
 * @param warnings           warnings about potential issues
 * @param backupPath         path where backup will be created
 */
public record MigrationPreview(
    @NotNull String sourceName,
    @NotNull String storageDescription,
    @NotNull List<GroupPreview> groups,
    @NotNull UserStats userStats,
    @NotNull PermissionStats permissionStats,
    @NotNull List<TrackPreview> tracks,
    @NotNull List<Conflict> conflicts,
    @NotNull List<String> warnings,
    @NotNull String backupPath
) {

    /**
     * Preview information for a group.
     *
     * @param name            the group name
     * @param weight          the group weight
     * @param permissionCount number of permissions in this group
     * @param prefix          the group prefix, or null
     * @param suffix          the group suffix, or null
     * @param parents         parent groups this group inherits from
     * @param hasConflict     whether this group conflicts with existing data
     * @param conflictDetails details about the conflict, or null
     */
    public record GroupPreview(
        @NotNull String name,
        int weight,
        int permissionCount,
        @Nullable String prefix,
        @Nullable String suffix,
        @NotNull List<String> parents,
        boolean hasConflict,
        @Nullable String conflictDetails
    ) {
        /**
         * Creates a simple group preview without conflict.
         */
        public GroupPreview(@NotNull String name, int weight, int permissionCount,
                           @Nullable String prefix, @Nullable String suffix,
                           @NotNull List<String> parents) {
            this(name, weight, permissionCount, prefix, suffix, parents, false, null);
        }
    }

    /**
     * Statistics about users to be migrated.
     *
     * @param totalUsers                 total number of users to import
     * @param usersWithCustomPermissions users with custom permissions (beyond group membership)
     * @param usersWithGroupsOnly        users with only group assignments
     * @param skippedUsers               users that will be skipped (e.g., already exist with SKIP conflict resolution)
     */
    public record UserStats(
        int totalUsers,
        int usersWithCustomPermissions,
        int usersWithGroupsOnly,
        int skippedUsers
    ) {}

    /**
     * Statistics about permissions.
     *
     * @param totalPermissions total permission entries
     * @param grants           granted permissions (value = true)
     * @param denials          denied permissions (value = false, negations)
     * @param temporary        temporary permissions with expiry
     * @param contextual       permissions with context restrictions
     * @param expiredSkipped   expired permissions that will be skipped
     */
    public record PermissionStats(
        int totalPermissions,
        int grants,
        int denials,
        int temporary,
        int contextual,
        int expiredSkipped
    ) {}

    /**
     * Preview information for a track.
     *
     * @param name        the track name
     * @param groups      ordered list of groups in the track
     * @param hasConflict whether this track conflicts with existing data
     */
    public record TrackPreview(
        @NotNull String name,
        @NotNull List<String> groups,
        boolean hasConflict
    ) {
        public String getGroupsDisplay() {
            return String.join(" → ", groups);
        }
    }

    /**
     * Information about a detected conflict.
     *
     * @param type              the type of conflict
     * @param itemName          the name of the conflicting item
     * @param sourceDetails     details about the source item
     * @param existingDetails   details about the existing item
     * @param recommendedAction the recommended action to resolve the conflict
     */
    public record Conflict(
        @NotNull ConflictType type,
        @NotNull String itemName,
        @NotNull String sourceDetails,
        @NotNull String existingDetails,
        @NotNull String recommendedAction
    ) {}

    /**
     * Type of conflict.
     */
    public enum ConflictType {
        GROUP,
        USER,
        TRACK
    }

    /**
     * Generates a formatted preview string for display.
     */
    public String toDisplayString(boolean verbose) {
        StringBuilder sb = new StringBuilder();

        sb.append("§6=== ").append(sourceName).append(" Migration Preview ===§r\n");
        sb.append("§7Source: §f").append(storageDescription).append("\n\n");

        // Groups section
        sb.append("§eGroups to import: §f").append(groups.size()).append("\n");
        for (GroupPreview group : groups) {
            String status = group.hasConflict ? "§c⚠" : "§a✓";
            sb.append("  ").append(status).append(" §f").append(group.name);
            sb.append(" §7(weight: ").append(group.weight);
            sb.append(", ").append(group.permissionCount).append(" permissions");
            if (!group.parents.isEmpty()) {
                sb.append(", inherits: ").append(String.join(", ", group.parents));
            }
            sb.append(")");
            if (group.hasConflict) {
                sb.append(" - ").append(group.conflictDetails);
            }
            sb.append("\n");
        }
        sb.append("\n");

        // Users section
        sb.append("§eUsers to import: §f").append(userStats.totalUsers).append("\n");
        sb.append("  §7- ").append(userStats.usersWithCustomPermissions)
          .append(" with custom permissions\n");
        sb.append("  §7- ").append(userStats.usersWithGroupsOnly)
          .append(" with group assignments only\n");
        if (userStats.skippedUsers > 0) {
            sb.append("  §7- ").append(userStats.skippedUsers).append(" will be skipped\n");
        }
        sb.append("\n");

        // Permissions section
        sb.append("§ePermissions: §f").append(permissionStats.totalPermissions).append(" total\n");
        sb.append("  §7- §a").append(permissionStats.grants).append(" grants §7(value: true)\n");
        sb.append("  §7- §c").append(permissionStats.denials).append(" negations §7(value: false)\n");
        if (permissionStats.temporary > 0) {
            sb.append("  §7- ").append(permissionStats.temporary).append(" temporary\n");
        }
        if (permissionStats.contextual > 0) {
            sb.append("  §7- ").append(permissionStats.contextual).append(" with contexts\n");
        }
        if (permissionStats.expiredSkipped > 0) {
            sb.append("  §7- ").append(permissionStats.expiredSkipped)
              .append(" expired (will be skipped)\n");
        }
        sb.append("\n");

        // Tracks section
        if (!tracks.isEmpty()) {
            sb.append("§eTracks: §f").append(tracks.size()).append("\n");
            for (TrackPreview track : tracks) {
                String status = track.hasConflict ? "§c⚠" : "§a✓";
                sb.append("  ").append(status).append(" §f").append(track.name)
                  .append(": ").append(track.getGroupsDisplay()).append("\n");
            }
            sb.append("\n");
        }

        // Conflicts section
        if (!conflicts.isEmpty()) {
            sb.append("§cConflicts: §f").append(conflicts.size()).append("\n");
            for (Conflict conflict : conflicts) {
                sb.append("  §c⚠ ").append(conflict.type).append(" '")
                  .append(conflict.itemName).append("' already exists\n");
                if (verbose) {
                    sb.append("      §7Source: ").append(conflict.sourceDetails).append("\n");
                    sb.append("      §7Existing: ").append(conflict.existingDetails).append("\n");
                }
                sb.append("      §7Options: merge, skip, overwrite\n");
            }
            sb.append("\n");
        }

        // Warnings section
        if (!warnings.isEmpty()) {
            sb.append("§eWarnings: §f").append(warnings.size()).append("\n");
            for (String warning : warnings) {
                sb.append("  §e⚠ ").append(warning).append("\n");
            }
            sb.append("\n");
        }

        // Backup info
        sb.append("§7Backup will be created at: §f").append(backupPath).append("\n\n");

        // Instructions
        sb.append("§7Add §f-confirm§7 to apply migration (e.g., /hp migrate luckperms-confirm)\n");
        sb.append("§7Add §f-verbose§7 for full permission listing.\n");

        return sb.toString();
    }
}
