package com.hyperperms.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Result of a migration operation.
 *
 * @param success              whether the migration was successful
 * @param backupName           name of the backup created before migration, or null if migration failed before backup
 * @param groupsImported       number of groups imported
 * @param groupsMerged         number of groups merged (conflicts resolved with MERGE)
 * @param groupsSkipped        number of groups skipped
 * @param usersImported        number of users imported
 * @param usersMerged          number of users merged
 * @param usersSkipped         number of users skipped
 * @param tracksImported       number of tracks imported
 * @param permissionsImported  total permissions imported
 * @param duration             duration of the migration
 * @param errorMessage         error message if migration failed
 * @param warnings             warnings encountered during migration
 * @param rolledBack           whether a rollback was performed
 */
public record MigrationResult(
    boolean success,
    @Nullable String backupName,
    int groupsImported,
    int groupsMerged,
    int groupsSkipped,
    int usersImported,
    int usersMerged,
    int usersSkipped,
    int tracksImported,
    int permissionsImported,
    @NotNull Duration duration,
    @Nullable String errorMessage,
    @NotNull List<String> warnings,
    boolean rolledBack
) {

    /**
     * Creates a successful result.
     */
    public static MigrationResult success(
        String backupName,
        int groupsImported, int groupsMerged, int groupsSkipped,
        int usersImported, int usersMerged, int usersSkipped,
        int tracksImported, int permissionsImported,
        Duration duration, List<String> warnings
    ) {
        return new MigrationResult(
            true, backupName,
            groupsImported, groupsMerged, groupsSkipped,
            usersImported, usersMerged, usersSkipped,
            tracksImported, permissionsImported,
            duration, null, warnings, false
        );
    }

    /**
     * Creates a failed result.
     */
    public static MigrationResult failure(
        @Nullable String backupName,
        @NotNull String errorMessage,
        boolean rolledBack,
        @NotNull List<String> warnings
    ) {
        return new MigrationResult(
            false, backupName,
            0, 0, 0,
            0, 0, 0,
            0, 0,
            Duration.ZERO, errorMessage, warnings, rolledBack
        );
    }

    /**
     * Generates a formatted result string for display.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        if (success) {
            sb.append("§a=== Migration Completed Successfully ===§r\n\n");

            sb.append("§eGroups:§r\n");
            sb.append("  §a✓ ").append(groupsImported).append(" imported\n");
            if (groupsMerged > 0) {
                sb.append("  §e⚡ ").append(groupsMerged).append(" merged\n");
            }
            if (groupsSkipped > 0) {
                sb.append("  §7○ ").append(groupsSkipped).append(" skipped\n");
            }

            sb.append("\n§eUsers:§r\n");
            sb.append("  §a✓ ").append(usersImported).append(" imported\n");
            if (usersMerged > 0) {
                sb.append("  §e⚡ ").append(usersMerged).append(" merged\n");
            }
            if (usersSkipped > 0) {
                sb.append("  §7○ ").append(usersSkipped).append(" skipped\n");
            }

            if (tracksImported > 0) {
                sb.append("\n§eTracks:§r\n");
                sb.append("  §a✓ ").append(tracksImported).append(" imported\n");
            }

            sb.append("\n§ePermissions:§r\n");
            sb.append("  §a✓ ").append(permissionsImported).append(" total\n");

            sb.append("\n§7Duration: ").append(formatDuration(duration)).append("\n");

            if (backupName != null) {
                sb.append("§7Backup created: ").append(backupName).append("\n");
            }
        } else {
            sb.append("§c=== Migration Failed ===§r\n\n");
            sb.append("§cError: ").append(errorMessage).append("\n");

            if (rolledBack) {
                sb.append("\n§aRollback performed - no changes were made.§r\n");
                if (backupName != null) {
                    sb.append("§7Original backup: ").append(backupName).append("\n");
                }
            } else {
                sb.append("\n§c⚠ Rollback was not possible - manual recovery may be needed.§r\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("\n§eWarnings:§r\n");
            for (String warning : warnings) {
                sb.append("  §e⚠ ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }
}
