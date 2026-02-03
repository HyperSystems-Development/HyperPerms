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
 */
public record MigrationPreview(
    /**
     * Name of the source system (e.g., "LuckPerms").
     */
    @NotNull String sourceName,
    
    /**
     * Description of the storage being migrated from.
     */
    @NotNull String storageDescription,
    
    /**
     * Groups that would be imported.
     */
    @NotNull List<GroupPreview> groups,
    
    /**
     * Users that would be imported.
     */
    @NotNull UserStats userStats,
    
    /**
     * Permission statistics.
     */
    @NotNull PermissionStats permissionStats,
    
    /**
     * Tracks that would be imported.
     */
    @NotNull List<TrackPreview> tracks,
    
    /**
     * Detected conflicts with existing HyperPerms data.
     */
    @NotNull List<Conflict> conflicts,
    
    /**
     * Warnings about potential issues.
     */
    @NotNull List<String> warnings,
    
    /**
     * Path where backup will be created.
     */
    @NotNull String backupPath
) {
    
    /**
     * Preview information for a group.
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
     */
    public record UserStats(
        /**
         * Total number of users to import.
         */
        int totalUsers,
        
        /**
         * Users with custom permissions (beyond group membership).
         */
        int usersWithCustomPermissions,
        
        /**
         * Users with only group assignments.
         */
        int usersWithGroupsOnly,
        
        /**
         * Users that will be skipped (e.g., already exist with SKIP conflict resolution).
         */
        int skippedUsers
    ) {}
    
    /**
     * Statistics about permissions.
     */
    public record PermissionStats(
        /**
         * Total permission entries.
         */
        int totalPermissions,
        
        /**
         * Granted permissions (value = true).
         */
        int grants,
        
        /**
         * Denied permissions (value = false, negations).
         */
        int denials,
        
        /**
         * Temporary permissions with expiry.
         */
        int temporary,
        
        /**
         * Permissions with context restrictions.
         */
        int contextual,
        
        /**
         * Expired permissions that will be skipped.
         */
        int expiredSkipped
    ) {}
    
    /**
     * Preview information for a track.
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
