package com.hyperperms.migration.luckperms;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.migration.*;
import com.hyperperms.migration.luckperms.LuckPermsData.*;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.storage.StorageProvider;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Migrates permission data from LuckPerms to HyperPerms.
 * <p>
 * Supports migration from:
 * <ul>
 *   <li>YAML file storage</li>
 *   <li>JSON file storage</li>
 *   <li>H2 embedded database</li>
 *   <li>MySQL/MariaDB database</li>
 * </ul>
 */
public final class LuckPermsMigrator implements PermissionMigrator {
    
    // Permission validation
    private static final Pattern VALID_PERMISSION_PATTERN = 
        Pattern.compile("^[a-z0-9._-]+$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PERMISSION_LENGTH = 256;
    
    private final HyperPerms plugin;
    private final LuckPermsStorageDetector detector;
    private LuckPermsStorageReader reader;
    
    public LuckPermsMigrator(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
        Path modsDir = resolveModsDirectory(plugin);
        this.detector = modsDir != null ? new LuckPermsStorageDetector(modsDir) : null;
    }

    /**
     * Resolves the mods directory from the plugin data directory.
     * HyperPerms data dir is typically at /mods/HyperPerms/ so parent is /mods/
     *
     * @param plugin the HyperPerms plugin
     * @return the mods directory path, or null if it cannot be determined
     */
    @Nullable
    private static Path resolveModsDirectory(@NotNull HyperPerms plugin) {
        Path dataDir = plugin.getDataDirectory();
        if (dataDir == null) {
            Logger.warn("Cannot resolve mods directory: plugin data directory is null");
            return null;
        }

        Logger.debug("HyperPerms data directory: %s", dataDir.toAbsolutePath());

        // The data directory is typically /mods/HyperPerms/
        // So parent should be /mods/
        Path modsDir = dataDir.getParent();
        if (modsDir == null) {
            Logger.warn("Cannot resolve mods directory: data directory has no parent");
            return null;
        }

        Logger.debug("Resolved mods directory: %s", modsDir.toAbsolutePath());
        return modsDir;
    }
    
    @Override
    @NotNull
    public String getSourceName() {
        return "LuckPerms";
    }
    
    @Override
    public boolean canMigrate() {
        if (detector == null || !detector.isLuckPermsInstalled()) {
            return false;
        }
        reader = detector.createReader();
        return reader != null && reader.isAvailable();
    }
    
    @Override
    @NotNull
    public String getStorageDescription() {
        if (reader == null) {
            reader = detector.createReader();
        }
        return reader != null ? reader.getStorageDescription() : "Unknown";
    }
    
    @Override
    public CompletableFuture<MigrationPreview> preview(@NotNull MigrationOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generatePreview(options);
            } catch (Exception e) {
                Logger.severe("Failed to generate migration preview", e);
                throw new RuntimeException("Preview generation failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<MigrationResult> migrate(@NotNull MigrationOptions options,
                                                       @NotNull MigrationProgressCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            List<String> warnings = new ArrayList<>();
            String backupName = null;
            
            try {
                // Phase 1: Create backup
                callback.onPhaseStart("Creating backup", -1);
                backupName = createBackup();
                callback.onPhaseComplete("Creating backup", 1);
                
                // Phase 2: Read LuckPerms data
                callback.onPhaseStart("Reading LuckPerms data", -1);
                LPDataSet lpData = readLuckPermsData();
                callback.onPhaseComplete("Reading LuckPerms data", 
                    lpData.groups().size() + lpData.users().size() + lpData.tracks().size());
                
                // Phase 3: Detect conflicts
                callback.onPhaseStart("Analyzing conflicts", -1);
                Map<String, Group> existingGroups = loadExistingGroups();
                Map<UUID, User> existingUsers = loadExistingUsers();
                Map<String, Track> existingTracks = loadExistingTracks();
                callback.onPhaseComplete("Analyzing conflicts", 1);
                
                // Phase 4: Import groups
                callback.onPhaseStart("Importing groups", lpData.groups().size());
                ImportStats groupStats = importGroups(lpData.groups(), existingGroups, 
                    options, callback, warnings);
                callback.onPhaseComplete("Importing groups", groupStats.imported + groupStats.merged);
                
                // Phase 5: Import users
                callback.onPhaseStart("Importing users", lpData.users().size());
                ImportStats userStats = importUsers(lpData.users(), existingUsers,
                    options, callback, warnings);
                callback.onPhaseComplete("Importing users", userStats.imported + userStats.merged);
                
                // Phase 6: Import tracks
                callback.onPhaseStart("Importing tracks", lpData.tracks().size());
                int tracksImported = importTracks(lpData.tracks(), existingTracks,
                    options, callback, warnings);
                callback.onPhaseComplete("Importing tracks", tracksImported);
                
                // Phase 7: Save all data
                callback.onPhaseStart("Saving data", -1);
                saveAll();
                callback.onPhaseComplete("Saving data", 1);
                
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                
                return MigrationResult.success(
                    backupName,
                    groupStats.imported, groupStats.merged, groupStats.skipped,
                    userStats.imported, userStats.merged, userStats.skipped,
                    tracksImported, groupStats.permissions + userStats.permissions,
                    duration, warnings
                );
                
            } catch (Exception e) {
                Logger.severe("Migration failed", e);
                callback.onError(e.getMessage(), true);
                
                // Attempt rollback
                boolean rolledBack = false;
                if (backupName != null) {
                    try {
                        rollback(backupName);
                        rolledBack = true;
                    } catch (Exception rollbackEx) {
                        Logger.severe("Rollback failed", rollbackEx);
                        warnings.add("Rollback failed: " + rollbackEx.getMessage());
                    }
                }
                
                return MigrationResult.failure(backupName, e.getMessage(), rolledBack, warnings);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        });
    }
    
    // ==================== Preview Generation ====================
    
    private MigrationPreview generatePreview(MigrationOptions options) throws IOException {
        if (reader == null) {
            reader = detector.createReader();
        }
        if (reader == null) {
            throw new IOException("No LuckPerms storage reader available");
        }
        
        // Read all LuckPerms data
        LPDataSet lpData = reader.readAll();
        
        // Load existing HyperPerms data for conflict detection
        Map<String, Group> existingGroups = loadExistingGroups();
        Map<UUID, User> existingUsers = loadExistingUsers();
        Map<String, Track> existingTracks = loadExistingTracks();
        
        // Build preview
        List<MigrationPreview.GroupPreview> groupPreviews = new ArrayList<>();
        List<MigrationPreview.Conflict> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        int totalPermissions = 0;
        int grants = 0;
        int denials = 0;
        int temporary = 0;
        int contextual = 0;
        int expiredSkipped = 0;
        
        // Analyze groups
        for (LPGroup lpGroup : lpData.groups().values()) {
            boolean hasConflict = existingGroups.containsKey(lpGroup.name());
            String conflictDetails = null;
            
            if (hasConflict) {
                Group existing = existingGroups.get(lpGroup.name());
                conflictDetails = "conflicts with existing group";
                conflicts.add(new MigrationPreview.Conflict(
                    MigrationPreview.ConflictType.GROUP,
                    lpGroup.name(),
                    String.format("weight=%d, %d permissions", lpGroup.weight(), lpGroup.getPermissionCount()),
                    String.format("weight=%d, %d permissions", existing.getWeight(), existing.getNodes().size()),
                    "Use --merge, --skip, or --overwrite"
                ));
            }
            
            // Count permissions
            for (LPNode node : lpGroup.nodes()) {
                if (!node.isGroupNode()) {
                    if (options.skipExpired() && node.isExpired()) {
                        expiredSkipped++;
                        continue;
                    }
                    
                    // Validate permission
                    ValidationResult validation = validatePermission(node.permission(), options.maxNodeLength());
                    if (!validation.valid) {
                        warnings.add("Group '" + lpGroup.name() + "': " + validation.message);
                        continue;
                    }
                    
                    totalPermissions++;
                    if (node.value()) grants++; else denials++;
                    if (node.isTemporary()) temporary++;
                    if (node.hasContexts()) contextual++;
                }
            }
            
            groupPreviews.add(new MigrationPreview.GroupPreview(
                lpGroup.name(),
                lpGroup.weight(),
                lpGroup.getPermissionCount(),
                lpGroup.prefix(),
                lpGroup.suffix(),
                new ArrayList<>(lpGroup.parents()),
                hasConflict,
                conflictDetails
            ));
        }
        
        // Analyze users
        int usersWithCustomPermissions = 0;
        int usersWithGroupsOnly = 0;
        int skippedUsers = 0;
        
        for (LPUser lpUser : lpData.users().values()) {
            boolean hasCustomPerms = lpUser.hasCustomPermissions();
            
            if (hasCustomPerms) {
                usersWithCustomPermissions++;
            } else {
                usersWithGroupsOnly++;
            }
            
            // Check for conflicts
            if (existingUsers.containsKey(lpUser.uuid()) && 
                options.conflictResolution() == ConflictResolution.SKIP) {
                skippedUsers++;
            }
            
            // Count user permissions
            for (LPNode node : lpUser.nodes()) {
                if (!node.isGroupNode()) {
                    if (options.skipExpired() && node.isExpired()) {
                        expiredSkipped++;
                        continue;
                    }
                    
                    ValidationResult validation = validatePermission(node.permission(), options.maxNodeLength());
                    if (!validation.valid) {
                        continue;
                    }
                    
                    totalPermissions++;
                    if (node.value()) grants++; else denials++;
                    if (node.isTemporary()) temporary++;
                    if (node.hasContexts()) contextual++;
                }
            }
        }
        
        // Analyze tracks
        List<MigrationPreview.TrackPreview> trackPreviews = new ArrayList<>();
        for (LPTrack lpTrack : lpData.tracks().values()) {
            boolean hasConflict = existingTracks.containsKey(lpTrack.name());
            if (hasConflict) {
                conflicts.add(new MigrationPreview.Conflict(
                    MigrationPreview.ConflictType.TRACK,
                    lpTrack.name(),
                    "groups: " + String.join(" → ", lpTrack.groups()),
                    "groups: " + String.join(" → ", existingTracks.get(lpTrack.name()).getGroups()),
                    "Use --merge, --skip, or --overwrite"
                ));
            }
            trackPreviews.add(new MigrationPreview.TrackPreview(
                lpTrack.name(),
                lpTrack.groups(),
                hasConflict
            ));
        }
        
        // Check for circular inheritance
        Set<String> circularGroups = detectCircularInheritance(lpData.groups());
        if (!circularGroups.isEmpty()) {
            warnings.add("Circular inheritance detected in groups: " + String.join(", ", circularGroups));
        }
        
        // Build backup path
        String backupPath = plugin.getDataDirectory().resolve("backups")
            .resolve("pre-migration-" + System.currentTimeMillis() + ".zip").toString();
        
        return new MigrationPreview(
            getSourceName(),
            getStorageDescription(),
            groupPreviews,
            new MigrationPreview.UserStats(
                lpData.users().size(),
                usersWithCustomPermissions,
                usersWithGroupsOnly,
                skippedUsers
            ),
            new MigrationPreview.PermissionStats(
                totalPermissions, grants, denials, temporary, contextual, expiredSkipped
            ),
            trackPreviews,
            conflicts,
            warnings,
            backupPath
        );
    }
    
    // ==================== Data Import ====================
    
    private LPDataSet readLuckPermsData() throws IOException {
        if (reader == null) {
            reader = detector.createReader();
        }
        if (reader == null) {
            throw new IOException("No LuckPerms storage reader available");
        }
        return reader.readAll();
    }
    
    private ImportStats importGroups(Map<String, LPGroup> lpGroups, Map<String, Group> existing,
                                     MigrationOptions options, MigrationProgressCallback callback,
                                     List<String> warnings) {
        ImportStats stats = new ImportStats();
        int processed = 0;
        
        for (LPGroup lpGroup : lpGroups.values()) {
            processed++;
            callback.onProgress(processed, "Group: " + lpGroup.name());
            
            boolean exists = existing.containsKey(lpGroup.name());
            
            if (exists) {
                switch (options.conflictResolution()) {
                    case SKIP:
                        stats.skipped++;
                        continue;
                    case MERGE:
                        Group merged = mergeGroup(existing.get(lpGroup.name()), lpGroup, options, warnings);
                        saveGroup(merged);
                        stats.merged++;
                        stats.permissions += countValidPermissions(lpGroup, options);
                        continue;
                    case OVERWRITE:
                        // Fall through to create new
                        break;
                }
            }
            
            // Create new group
            Group hpGroup = transformGroup(lpGroup, options, warnings);
            saveGroup(hpGroup);
            stats.imported++;
            stats.permissions += countValidPermissions(lpGroup, options);
        }
        
        return stats;
    }
    
    private ImportStats importUsers(Map<UUID, LPUser> lpUsers, Map<UUID, User> existing,
                                    MigrationOptions options, MigrationProgressCallback callback,
                                    List<String> warnings) {
        ImportStats stats = new ImportStats();
        int processed = 0;
        
        for (LPUser lpUser : lpUsers.values()) {
            processed++;
            if (processed % 100 == 0) {
                callback.onProgress(processed, "Users: " + processed + "/" + lpUsers.size());
            }
            
            // Skip users with only default group and no permissions
            if (options.skipDefaultUsers() && !lpUser.hasCustomPermissions() &&
                lpUser.getGroups().isEmpty() && "default".equals(lpUser.primaryGroup())) {
                stats.skipped++;
                continue;
            }
            
            boolean exists = existing.containsKey(lpUser.uuid());
            
            if (exists) {
                switch (options.conflictResolution()) {
                    case SKIP:
                        stats.skipped++;
                        continue;
                    case MERGE:
                        User merged = mergeUser(existing.get(lpUser.uuid()), lpUser, options, warnings);
                        saveUser(merged);
                        stats.merged++;
                        stats.permissions += countValidUserPermissions(lpUser, options);
                        continue;
                    case OVERWRITE:
                        break;
                }
            }
            
            User hpUser = transformUser(lpUser, options, warnings);
            saveUser(hpUser);
            stats.imported++;
            stats.permissions += countValidUserPermissions(lpUser, options);
        }
        
        return stats;
    }
    
    private int importTracks(Map<String, LPTrack> lpTracks, Map<String, Track> existing,
                            MigrationOptions options, MigrationProgressCallback callback,
                            List<String> warnings) {
        int imported = 0;
        int processed = 0;
        
        for (LPTrack lpTrack : lpTracks.values()) {
            processed++;
            callback.onProgress(processed, "Track: " + lpTrack.name());
            
            boolean exists = existing.containsKey(lpTrack.name());
            
            if (exists && options.conflictResolution() == ConflictResolution.SKIP) {
                continue;
            }
            
            Track hpTrack = new Track(lpTrack.name(), new ArrayList<>(lpTrack.groups()));
            saveTrack(hpTrack);
            imported++;
        }
        
        return imported;
    }
    
    // ==================== Data Transformation ====================
    
    private Group transformGroup(LPGroup lpGroup, MigrationOptions options, List<String> warnings) {
        Group group = new Group(lpGroup.name(), lpGroup.weight());
        group.setPrefix(lpGroup.prefix());
        group.setSuffix(lpGroup.suffix());
        group.setPrefixPriority(lpGroup.prefixPriority());
        group.setSuffixPriority(lpGroup.suffixPriority());
        
        // Add permission nodes
        for (LPNode lpNode : lpGroup.nodes()) {
            Node hpNode = transformNode(lpNode, options, warnings, "group:" + lpGroup.name());
            if (hpNode != null) {
                group.addNode(hpNode);
            }
        }
        
        return group;
    }
    
    private Group mergeGroup(Group existing, LPGroup lpGroup, MigrationOptions options, List<String> warnings) {
        // Keep existing settings, add new permissions
        for (LPNode lpNode : lpGroup.nodes()) {
            Node hpNode = transformNode(lpNode, options, warnings, "group:" + lpGroup.name());
            if (hpNode != null) {
                // Only add if not already present
                boolean exists = existing.getNodes().stream()
                    .anyMatch(n -> n.equalsIgnoringExpiry(hpNode));
                if (!exists) {
                    existing.addNode(hpNode);
                }
            }
        }
        
        // Optionally update weight if LuckPerms weight is higher
        if (lpGroup.weight() > existing.getWeight()) {
            existing.setWeight(lpGroup.weight());
        }
        
        return existing;
    }
    
    private User transformUser(LPUser lpUser, MigrationOptions options, List<String> warnings) {
        User user = new User(lpUser.uuid(), lpUser.username());
        user.setPrimaryGroup(lpUser.primaryGroup());
        
        // Add permission nodes
        for (LPNode lpNode : lpUser.nodes()) {
            Node hpNode = transformNode(lpNode, options, warnings, "user:" + lpUser.uuid());
            if (hpNode != null) {
                user.addNode(hpNode);
            }
        }
        
        return user;
    }
    
    private User mergeUser(User existing, LPUser lpUser, MigrationOptions options, List<String> warnings) {
        // Add new permissions
        for (LPNode lpNode : lpUser.nodes()) {
            Node hpNode = transformNode(lpNode, options, warnings, "user:" + lpUser.uuid());
            if (hpNode != null) {
                boolean exists = existing.getNodes().stream()
                    .anyMatch(n -> n.equalsIgnoringExpiry(hpNode));
                if (!exists) {
                    existing.addNode(hpNode);
                }
            }
        }
        
        return existing;
    }
    
    @Nullable
    private Node transformNode(LPNode lpNode, MigrationOptions options, List<String> warnings, String context) {
        // Skip expired permissions
        if (options.skipExpired() && lpNode.isExpired()) {
            return null;
        }
        
        // Validate permission
        ValidationResult validation = validatePermission(lpNode.permission(), options.maxNodeLength());
        if (!validation.valid) {
            warnings.add(context + ": " + validation.message);
            if (validation.truncated != null) {
                // Use truncated version
                return createNode(validation.truncated, lpNode);
            }
            return null;
        }
        
        return createNode(lpNode.permission(), lpNode);
    }
    
    private Node createNode(String permission, LPNode lpNode) {
        // Build contexts
        ContextSet.Builder contextBuilder = ContextSet.builder();
        for (Map.Entry<String, String> entry : lpNode.contexts().entrySet()) {
            contextBuilder.add(entry.getKey(), entry.getValue());
        }
        
        // Build expiry
        Instant expiry = null;
        if (lpNode.expiry() > 0) {
            expiry = Instant.ofEpochSecond(lpNode.expiry());
        }
        
        return Node.builder(permission)
            .value(lpNode.value())
            .expiry(expiry)
            .contexts(contextBuilder.build())
            .build();
    }
    
    // ==================== Validation ====================
    
    private record ValidationResult(boolean valid, @Nullable String message, @Nullable String truncated) {
        static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }
        
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, null);
        }
        
        static ValidationResult truncate(String message, String truncated) {
            return new ValidationResult(false, message, truncated);
        }
    }
    
    private ValidationResult validatePermission(String permission, int maxLength) {
        if (permission == null || permission.isEmpty()) {
            return ValidationResult.invalid("Empty permission node");
        }
        
        // Check for unicode/non-ASCII characters
        if (!permission.chars().allMatch(c -> c < 128)) {
            return ValidationResult.invalid("Permission '" + permission + "' contains non-ASCII characters");
        }
        
        // Check pattern
        if (!VALID_PERMISSION_PATTERN.matcher(permission).matches()) {
            // Allow group.* permissions
            if (!permission.startsWith("group.")) {
                return ValidationResult.invalid("Permission '" + permission + "' contains invalid characters");
            }
        }
        
        // Check length
        if (permission.length() > maxLength) {
            String truncated = permission.substring(0, maxLength);
            return ValidationResult.truncate(
                "Permission '" + permission + "' exceeds max length, truncating",
                truncated
            );
        }
        
        return ValidationResult.ok();
    }
    
    private Set<String> detectCircularInheritance(Map<String, LPGroup> groups) {
        Set<String> circular = new HashSet<>();
        
        for (String groupName : groups.keySet()) {
            Set<String> visited = new HashSet<>();
            if (hasCircularInheritance(groupName, groups, visited)) {
                circular.add(groupName);
            }
        }
        
        return circular;
    }
    
    private boolean hasCircularInheritance(String groupName, Map<String, LPGroup> groups, Set<String> visited) {
        if (visited.contains(groupName)) {
            return true;
        }
        
        visited.add(groupName);
        LPGroup group = groups.get(groupName);
        if (group == null) {
            return false;
        }
        
        for (String parent : group.parents()) {
            if (hasCircularInheritance(parent, groups, new HashSet<>(visited))) {
                return true;
            }
        }
        
        return false;
    }
    
    private int countValidPermissions(LPGroup lpGroup, MigrationOptions options) {
        return (int) lpGroup.nodes().stream()
            .filter(n -> !n.isGroupNode())
            .filter(n -> !options.skipExpired() || !n.isExpired())
            .filter(n -> validatePermission(n.permission(), options.maxNodeLength()).valid)
            .count();
    }
    
    private int countValidUserPermissions(LPUser lpUser, MigrationOptions options) {
        return (int) lpUser.nodes().stream()
            .filter(n -> !n.isGroupNode())
            .filter(n -> !options.skipExpired() || !n.isExpired())
            .filter(n -> validatePermission(n.permission(), options.maxNodeLength()).valid)
            .count();
    }
    
    // ==================== Storage Operations ====================
    
    private String createBackup() {
        try {
            return plugin.getBackupManager().createBackup("pre-migration").join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create backup", e);
        }
    }
    
    private void rollback(String backupName) {
        try {
            plugin.getBackupManager().restoreBackup(backupName).join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore backup", e);
        }
    }
    
    private Map<String, Group> loadExistingGroups() {
        try {
            return plugin.getStorage().loadAllGroups().join();
        } catch (Exception e) {
            Logger.warn("Failed to load existing groups: %s", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    private Map<UUID, User> loadExistingUsers() {
        try {
            return plugin.getStorage().loadAllUsers().join();
        } catch (Exception e) {
            Logger.warn("Failed to load existing users: %s", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    private Map<String, Track> loadExistingTracks() {
        try {
            return plugin.getStorage().loadAllTracks().join();
        } catch (Exception e) {
            Logger.warn("Failed to load existing tracks: %s", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    private void saveGroup(Group group) {
        try {
            plugin.getStorage().saveGroup(group).join();
            plugin.getGroupManager().loadGroup(group.getName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save group: " + group.getName(), e);
        }
    }
    
    private void saveUser(User user) {
        try {
            plugin.getStorage().saveUser(user).join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save user: " + user.getUuid(), e);
        }
    }
    
    private void saveTrack(Track track) {
        try {
            plugin.getStorage().saveTrack(track).join();
            plugin.getTrackManager().loadTrack(track.getName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save track: " + track.getName(), e);
        }
    }
    
    private void saveAll() {
        try {
            plugin.getStorage().saveAll().join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save all data", e);
        }
    }
    
    // ==================== Helper Classes ====================
    
    private static class ImportStats {
        int imported = 0;
        int merged = 0;
        int skipped = 0;
        int permissions = 0;
    }
}
