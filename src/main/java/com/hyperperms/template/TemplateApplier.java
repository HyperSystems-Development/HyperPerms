package com.hyperperms.template;

import com.hyperperms.HyperPerms;
import com.hyperperms.backup.BackupManager;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Applies permission templates to the server.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Merge</b>: Add new groups, update existing ones, keep user assignments</li>
 *   <li><b>Replace</b>: Backup first, remove non-template groups, reassign users to default</li>
 * </ul>
 */
public final class TemplateApplier {

    private static final DateTimeFormatter BACKUP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final HyperPerms hyperPerms;

    /**
     * Creates a new template applier.
     *
     * @param hyperPerms the HyperPerms instance
     */
    public TemplateApplier(@NotNull HyperPerms hyperPerms) {
        this.hyperPerms = Objects.requireNonNull(hyperPerms);
    }

    /**
     * Result of a template application.
     */
    public record ApplyResult(
            boolean success,
            @NotNull String message,
            int groupsCreated,
            int groupsUpdated,
            int groupsRemoved,
            int tracksCreated,
            int tracksUpdated,
            @org.jetbrains.annotations.Nullable String backupName
    ) {
        /**
         * Creates a successful result.
         */
        public static ApplyResult success(String message, int created, int updated, int removed, 
                                          int tracksCreated, int tracksUpdated, String backupName) {
            return new ApplyResult(true, message, created, updated, removed, 
                                   tracksCreated, tracksUpdated, backupName);
        }

        /**
         * Creates a failed result.
         */
        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message, 0, 0, 0, 0, 0, null);
        }
    }

    /**
     * Apply mode for templates.
     */
    public enum ApplyMode {
        /**
         * Merge mode: Add new groups, update existing ones, keep user assignments.
         */
        MERGE,

        /**
         * Replace mode: Backup first, remove non-template groups, reassign users to default.
         */
        REPLACE
    }

    /**
     * Applies a template to the server.
     *
     * @param template the template to apply
     * @param mode     the apply mode
     * @return a future containing the apply result
     */
    public CompletableFuture<ApplyResult> apply(@NotNull PermissionTemplate template, @NotNull ApplyMode mode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate template
                TemplateValidation.ValidationResult validation = TemplateValidation.validate(template);
                if (!validation.valid()) {
                    return ApplyResult.failure("Template validation failed: " + String.join(", ", validation.errors()));
                }

                if (!validation.warnings().isEmpty()) {
                    for (String warning : validation.warnings()) {
                        Logger.warn("[Template] Warning: %s", warning);
                    }
                }

                // Create backup
                String backupName = createBackup(template.getName());
                if (backupName == null) {
                    return ApplyResult.failure("Failed to create backup before applying template");
                }

                Logger.info("[Template] Created backup: %s", backupName);

                // Apply based on mode
                if (mode == ApplyMode.REPLACE) {
                    return applyReplace(template, backupName);
                } else {
                    return applyMerge(template, backupName);
                }

            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Logger.severe("[Template] Failed to apply template: %s", errorMsg);
                return ApplyResult.failure("Failed to apply template: " + errorMsg);
            }
        });
    }

    /**
     * Applies template in merge mode.
     */
    private ApplyResult applyMerge(PermissionTemplate template, String backupName) {
        GroupManagerImpl groupManager = (GroupManagerImpl) hyperPerms.getGroupManager();
        TrackManagerImpl trackManager = (TrackManagerImpl) hyperPerms.getTrackManager();

        int created = 0;
        int updated = 0;
        int tracksCreated = 0;
        int tracksUpdated = 0;

        // Apply groups
        for (TemplateGroup templateGroup : template.getGroups().values()) {
            Group existingGroup = groupManager.getGroup(templateGroup.getName());
            
            if (existingGroup == null) {
                // Create new group
                createGroup(groupManager, templateGroup);
                created++;
                Logger.debug("[Template] Created group: %s", templateGroup.getName());
            } else {
                // Update existing group
                updateGroup(existingGroup, templateGroup);
                groupManager.saveGroup(existingGroup).join();
                updated++;
                Logger.debug("[Template] Updated group: %s", templateGroup.getName());
            }
        }

        // Apply tracks
        for (TemplateTrack templateTrack : template.getTracks().values()) {
            Track existingTrack = trackManager.getTrack(templateTrack.name());
            
            if (existingTrack == null) {
                // Create new track
                Track newTrack = new Track(templateTrack.name(), new ArrayList<>(templateTrack.groups()));
                trackManager.saveTrack(newTrack).join();
                tracksCreated++;
                Logger.debug("[Template] Created track: %s", templateTrack.name());
            } else {
                // Update existing track
                existingTrack.setGroups(templateTrack.groups());
                trackManager.saveTrack(existingTrack).join();
                tracksUpdated++;
                Logger.debug("[Template] Updated track: %s", templateTrack.name());
            }
        }

        String message = String.format("Template '%s' applied successfully (merge mode)", template.getName());
        Logger.info("[Template] %s: %d created, %d updated", template.getName(), created, updated);

        return ApplyResult.success(message, created, updated, 0, tracksCreated, tracksUpdated, backupName);
    }

    /**
     * Applies template in replace mode.
     */
    private ApplyResult applyReplace(PermissionTemplate template, String backupName) {
        GroupManagerImpl groupManager = (GroupManagerImpl) hyperPerms.getGroupManager();
        TrackManagerImpl trackManager = (TrackManagerImpl) hyperPerms.getTrackManager();

        int created = 0;
        int updated = 0;
        int removed = 0;
        int tracksCreated = 0;
        int tracksUpdated = 0;

        Set<String> templateGroupNames = template.getGroups().keySet();
        Set<String> existingGroupNames = new HashSet<>();
        for (Group g : groupManager.getLoadedGroups()) {
            existingGroupNames.add(g.getName());
        }

        // Remove groups not in template
        for (String groupName : existingGroupNames) {
            if (!templateGroupNames.contains(groupName)) {
                groupManager.deleteGroup(groupName).join();
                removed++;
                Logger.debug("[Template] Removed group: %s", groupName);
            }
        }

        // Create/update groups from template
        for (TemplateGroup templateGroup : template.getGroups().values()) {
            Group existingGroup = groupManager.getGroup(templateGroup.getName());
            
            if (existingGroup == null) {
                createGroup(groupManager, templateGroup);
                created++;
                Logger.debug("[Template] Created group: %s", templateGroup.getName());
            } else {
                updateGroup(existingGroup, templateGroup);
                groupManager.saveGroup(existingGroup).join();
                updated++;
                Logger.debug("[Template] Updated group: %s", templateGroup.getName());
            }
        }

        // Remove tracks not in template
        Set<String> templateTrackNames = template.getTracks().keySet();
        for (Track track : trackManager.getLoadedTracks()) {
            if (!templateTrackNames.contains(track.getName())) {
                trackManager.deleteTrack(track.getName()).join();
                Logger.debug("[Template] Removed track: %s", track.getName());
            }
        }

        // Create/update tracks from template
        for (TemplateTrack templateTrack : template.getTracks().values()) {
            Track existingTrack = trackManager.getTrack(templateTrack.name());
            
            if (existingTrack == null) {
                Track newTrack = new Track(templateTrack.name(), new ArrayList<>(templateTrack.groups()));
                trackManager.saveTrack(newTrack).join();
                tracksCreated++;
                Logger.debug("[Template] Created track: %s", templateTrack.name());
            } else {
                existingTrack.setGroups(templateTrack.groups());
                trackManager.saveTrack(existingTrack).join();
                tracksUpdated++;
                Logger.debug("[Template] Updated track: %s", templateTrack.name());
            }
        }

        String message = String.format("Template '%s' applied successfully (replace mode)", template.getName());
        Logger.info("[Template] %s: %d created, %d updated, %d removed", 
                template.getName(), created, updated, removed);

        return ApplyResult.success(message, created, updated, removed, tracksCreated, tracksUpdated, backupName);
    }

    /**
     * Creates a new group from a template group.
     */
    private void createGroup(GroupManagerImpl groupManager, TemplateGroup templateGroup) {
        try {
            Group createdGroup = groupManager.createGroup(templateGroup.getName());
            updateGroup(createdGroup, templateGroup);
            groupManager.saveGroup(createdGroup).join();
        } catch (IllegalArgumentException e) {
            // Group already exists - fall back to update
            Logger.debug("[Template] Group '%s' already exists, updating instead", templateGroup.getName());
            Group existing = groupManager.getGroup(templateGroup.getName());
            if (existing != null) {
                updateGroup(existing, templateGroup);
                groupManager.saveGroup(existing).join();
            }
        }
    }

    /**
     * Updates an existing group with template values.
     */
    private void updateGroup(Group group, TemplateGroup templateGroup) {
        // Update basic properties
        group.setWeight(templateGroup.getWeight());
        group.setPrefix(templateGroup.getPrefix());
        group.setSuffix(templateGroup.getSuffix());
        if (templateGroup.getDisplayName() != null) {
            group.setDisplayName(templateGroup.getDisplayName());
        }

        // Clear existing permissions and re-add from template
        group.clearNodes();

        // Add permissions
        for (TemplatePermission perm : templateGroup.getPermissions()) {
            Node node = Node.builder(perm.node()).build();
            group.addNode(node);
        }

        // Add parent inheritance
        for (String parent : templateGroup.getParents()) {
            Node parentNode = Node.builder("group." + parent).build();
            group.addNode(parentNode);
        }
    }

    /**
     * Creates a backup before applying a template.
     */
    @org.jetbrains.annotations.Nullable
    private String createBackup(String templateName) {
        try {
            BackupManager backupManager = hyperPerms.getBackupManager();
            String backupName = "pre-template-" + templateName + "-" + BACKUP_FORMAT.format(Instant.now());
            String result = backupManager.createBackup(backupName).join();
            return result;
        } catch (Exception e) {
            Logger.warn("[Template] Failed to create backup: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Gets a preview of what applying a template would do.
     *
     * @param template the template
     * @param mode     the apply mode
     * @return a preview of the changes
     */
    @NotNull
    public ApplyPreview preview(@NotNull PermissionTemplate template, @NotNull ApplyMode mode) {
        GroupManagerImpl groupManager = (GroupManagerImpl) hyperPerms.getGroupManager();
        TrackManagerImpl trackManager = (TrackManagerImpl) hyperPerms.getTrackManager();

        Set<String> existingGroups = new HashSet<>();
        for (Group g : groupManager.getLoadedGroups()) {
            existingGroups.add(g.getName());
        }

        Set<String> existingTracks = new HashSet<>();
        for (Track t : trackManager.getLoadedTracks()) {
            existingTracks.add(t.getName());
        }

        Set<String> templateGroups = template.getGroups().keySet();
        Set<String> templateTracks = template.getTracks().keySet();

        // Groups to create
        Set<String> toCreate = new HashSet<>(templateGroups);
        toCreate.removeAll(existingGroups);

        // Groups to update
        Set<String> toUpdate = new HashSet<>(templateGroups);
        toUpdate.retainAll(existingGroups);

        // Groups to remove (only in replace mode)
        Set<String> toRemove = new HashSet<>();
        if (mode == ApplyMode.REPLACE) {
            toRemove.addAll(existingGroups);
            toRemove.removeAll(templateGroups);
        }

        // Tracks
        Set<String> tracksToCreate = new HashSet<>(templateTracks);
        tracksToCreate.removeAll(existingTracks);

        Set<String> tracksToUpdate = new HashSet<>(templateTracks);
        tracksToUpdate.retainAll(existingTracks);

        Set<String> tracksToRemove = new HashSet<>();
        if (mode == ApplyMode.REPLACE) {
            tracksToRemove.addAll(existingTracks);
            tracksToRemove.removeAll(templateTracks);
        }

        return new ApplyPreview(
                template.getName(), mode,
                toCreate, toUpdate, toRemove,
                tracksToCreate, tracksToUpdate, tracksToRemove
        );
    }

    /**
     * Preview of what applying a template would do.
     */
    public record ApplyPreview(
            @NotNull String templateName,
            @NotNull ApplyMode mode,
            @NotNull Set<String> groupsToCreate,
            @NotNull Set<String> groupsToUpdate,
            @NotNull Set<String> groupsToRemove,
            @NotNull Set<String> tracksToCreate,
            @NotNull Set<String> tracksToUpdate,
            @NotNull Set<String> tracksToRemove
    ) {
        /**
         * Gets the total number of group changes.
         */
        public int getTotalGroupChanges() {
            return groupsToCreate.size() + groupsToUpdate.size() + groupsToRemove.size();
        }

        /**
         * Gets the total number of track changes.
         */
        public int getTotalTrackChanges() {
            return tracksToCreate.size() + tracksToUpdate.size() + tracksToRemove.size();
        }
    }
}
