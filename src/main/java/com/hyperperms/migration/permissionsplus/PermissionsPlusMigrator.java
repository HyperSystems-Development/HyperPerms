package com.hyperperms.migration.permissionsplus;

import com.google.gson.*;
import com.hyperperms.HyperPerms;
import com.hyperperms.migration.*;
import com.hyperperms.migration.permissionsplus.PermissionsPlusData.*;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Migrates permission data from PermissionsPlus to HyperPerms.
 * <p>
 * PermissionsPlus stores all data in two JSON files:
 * <ul>
 *   <li>{@code permissions.json} - groups and user assignments</li>
 *   <li>{@code PermissionsPlus_PermissionsPlus/config.json} - configuration and username cache</li>
 * </ul>
 * <p>
 * Key differences from LuckPerms:
 * <ul>
 *   <li>No group inheritance, weights, or prefix/suffix</li>
 *   <li>No tracks or promotion ladders</li>
 *   <li>No contexts or temporary permissions</li>
 *   <li>Some permissions contain embedded descriptions after ": " which are stripped</li>
 *   <li>Some permissions start with "/" which are invalid and skipped</li>
 * </ul>
 */
public final class PermissionsPlusMigrator implements PermissionMigrator {

    private static final Pattern VALID_PERMISSION_PATTERN =
        Pattern.compile("^[a-z0-9._*-]+$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PERMISSION_LENGTH = 256;

    private static final String PP_DATA_DIR = "PermissionsPlus_PermissionsPlus";
    private static final String PERMISSIONS_FILE = "permissions.json";
    private static final String CONFIG_FILE = "config.json";

    private final HyperPerms plugin;
    private final Path modsDir;
    private final Gson gson;

    public PermissionsPlusMigrator(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
        this.modsDir = resolveModsDirectory(plugin);
        this.gson = new GsonBuilder().create();
    }

    @Nullable
    private static Path resolveModsDirectory(@NotNull HyperPerms plugin) {
        Path dataDir = plugin.getDataDirectory();
        if (dataDir == null) {
            Logger.warn("Cannot resolve mods directory: plugin data directory is null");
            return null;
        }
        Path modsDir = dataDir.getParent();
        if (modsDir == null) {
            Logger.warn("Cannot resolve mods directory: data directory has no parent");
            return null;
        }
        return modsDir;
    }

    @Override
    @NotNull
    public String getSourceName() {
        return "PermissionsPlus";
    }

    @Override
    public boolean canMigrate() {
        if (modsDir == null) {
            return false;
        }
        Path permissionsFile = modsDir.resolve(PERMISSIONS_FILE);
        Path configFile = modsDir.resolve(PP_DATA_DIR).resolve(CONFIG_FILE);
        return Files.isRegularFile(permissionsFile) && Files.isRegularFile(configFile);
    }

    @Override
    @NotNull
    public String getStorageDescription() {
        if (modsDir == null) {
            return "Unknown";
        }
        return "JSON storage (" + modsDir.resolve(PERMISSIONS_FILE) + ")";
    }

    // ==================== JSON Reading ====================

    PPDataSet readPermissionsPlusData() throws IOException {
        if (modsDir == null) {
            throw new IOException("Mods directory not available");
        }

        Path permissionsFile = modsDir.resolve(PERMISSIONS_FILE);
        Path configFile = modsDir.resolve(PP_DATA_DIR).resolve(CONFIG_FILE);

        PPConfig config = readConfig(configFile);

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(permissionsFile)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        Map<String, PPGroup> groups = readGroups(root);
        Map<UUID, PPUser> users = readUsers(root, config.userNames());

        Logger.debugMigration("Read %d groups, %d users from PermissionsPlus", groups.size(), users.size());
        return new PPDataSet(groups, users, config);
    }

    private PPConfig readConfig(Path configFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(configFile)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();

            String defaultGroup = "default";
            if (data.has("defaultGroup") && !data.get("defaultGroup").isJsonNull()) {
                defaultGroup = data.get("defaultGroup").getAsString();
            }

            Map<UUID, String> userNames = new LinkedHashMap<>();
            if (data.has("userNames") && data.get("userNames").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("userNames").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        userNames.put(uuid, entry.getValue().getAsString());
                    } catch (IllegalArgumentException e) {
                        Logger.warn("Invalid UUID in PermissionsPlus config userNames: %s", entry.getKey());
                    }
                }
            }

            return new PPConfig(defaultGroup, userNames);
        }
    }

    private Map<String, PPGroup> readGroups(JsonObject root) {
        Map<String, PPGroup> groups = new LinkedHashMap<>();

        if (!root.has("groups") || !root.get("groups").isJsonObject()) {
            return groups;
        }

        JsonObject groupsObj = root.getAsJsonObject("groups");
        for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet()) {
            String name = entry.getKey();
            List<String> permissions = new ArrayList<>();

            if (entry.getValue().isJsonArray()) {
                for (JsonElement perm : entry.getValue().getAsJsonArray()) {
                    if (perm.isJsonPrimitive()) {
                        permissions.add(perm.getAsString());
                    }
                }
            }

            groups.put(name, new PPGroup(name, permissions));
        }

        return groups;
    }

    private Map<UUID, PPUser> readUsers(JsonObject root, Map<UUID, String> userNames) {
        Map<UUID, PPUser> users = new LinkedHashMap<>();

        if (!root.has("users") || !root.get("users").isJsonObject()) {
            return users;
        }

        JsonObject usersObj = root.getAsJsonObject("users");
        for (Map.Entry<String, JsonElement> entry : usersObj.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                if (!entry.getValue().isJsonObject()) continue;

                JsonObject userData = entry.getValue().getAsJsonObject();

                List<String> userGroups = new ArrayList<>();
                if (userData.has("groups") && userData.get("groups").isJsonArray()) {
                    for (JsonElement g : userData.getAsJsonArray("groups")) {
                        if (g.isJsonPrimitive()) {
                            userGroups.add(g.getAsString());
                        }
                    }
                }

                List<String> permissions = new ArrayList<>();
                if (userData.has("permissions") && userData.get("permissions").isJsonArray()) {
                    for (JsonElement p : userData.getAsJsonArray("permissions")) {
                        if (p.isJsonPrimitive()) {
                            permissions.add(p.getAsString());
                        }
                    }
                }

                String username = userNames.get(uuid);
                users.put(uuid, new PPUser(uuid, username, userGroups, permissions));
            } catch (IllegalArgumentException e) {
                Logger.warn("Invalid UUID in PermissionsPlus users: %s", entry.getKey());
            }
        }

        return users;
    }

    // ==================== Permission Cleaning ====================

    @Nullable
    String cleanPermission(@NotNull String rawPermission, @NotNull List<String> warnings, @NotNull String context) {
        if (rawPermission.startsWith("/")) {
            warnings.add(context + ": Skipped command-style permission '" + rawPermission + "'");
            return null;
        }

        String permission = rawPermission;

        int descIndex = permission.indexOf(": ");
        if (descIndex > 0) {
            String cleaned = permission.substring(0, descIndex);
            warnings.add(context + ": Stripped description from '" + rawPermission + "' → '" + cleaned + "'");
            permission = cleaned;
        }

        permission = permission.trim();

        if (permission.isEmpty()) {
            return null;
        }

        return permission;
    }

    @Nullable
    String validatePermission(@NotNull String permission, @NotNull List<String> warnings, @NotNull String context) {
        if (!permission.chars().allMatch(c -> c < 128)) {
            warnings.add(context + ": Permission '" + permission + "' contains non-ASCII characters, skipping");
            return null;
        }

        if (!VALID_PERMISSION_PATTERN.matcher(permission).matches()) {
            warnings.add(context + ": Permission '" + permission + "' contains invalid characters, skipping");
            return null;
        }

        if (permission.length() > MAX_PERMISSION_LENGTH) {
            String truncated = permission.substring(0, MAX_PERMISSION_LENGTH);
            warnings.add(context + ": Permission '" + permission + "' exceeds max length, truncating");
            return truncated;
        }

        return permission;
    }

    // ==================== Migration Interface ====================

    @Override
    public CompletableFuture<MigrationPreview> preview(@NotNull MigrationOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generatePreview(options);
            } catch (Exception e) {
                Logger.severe("Failed to generate PermissionsPlus migration preview", e);
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
                callback.onPhaseStart("Creating backup", -1);
                backupName = createBackup();
                callback.onPhaseComplete("Creating backup", 1);

                callback.onPhaseStart("Reading PermissionsPlus data", -1);
                PPDataSet ppData = readPermissionsPlusData();
                callback.onPhaseComplete("Reading PermissionsPlus data",
                    ppData.groups().size() + ppData.users().size());

                callback.onPhaseStart("Analyzing conflicts", -1);
                Map<String, Group> existingGroups = loadExistingGroups();
                Map<UUID, User> existingUsers = loadExistingUsers();
                callback.onPhaseComplete("Analyzing conflicts", 1);

                callback.onPhaseStart("Importing groups", ppData.groups().size());
                ImportStats groupStats = importGroups(ppData.groups(), existingGroups,
                    options, callback, warnings);
                callback.onPhaseComplete("Importing groups", groupStats.imported + groupStats.merged);

                callback.onPhaseStart("Importing users", ppData.users().size());
                ImportStats userStats = importUsers(ppData.users(), existingUsers,
                    ppData.config(), options, callback, warnings);
                callback.onPhaseComplete("Importing users", userStats.imported + userStats.merged);

                callback.onPhaseStart("Saving data", -1);
                saveAll();
                callback.onPhaseComplete("Saving data", 1);

                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

                return MigrationResult.success(
                    backupName,
                    groupStats.imported, groupStats.merged, groupStats.skipped,
                    userStats.imported, userStats.merged, userStats.skipped,
                    0, groupStats.permissions + userStats.permissions,
                    duration, warnings
                );

            } catch (Exception e) {
                Logger.severe("PermissionsPlus migration failed", e);
                callback.onError(e.getMessage(), true);

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
            }
        });
    }

    // ==================== Preview Generation ====================

    private MigrationPreview generatePreview(MigrationOptions options) throws IOException {
        PPDataSet ppData = readPermissionsPlusData();

        Map<String, Group> existingGroups = loadExistingGroups();
        Map<UUID, User> existingUsers = loadExistingUsers();

        List<MigrationPreview.GroupPreview> groupPreviews = new ArrayList<>();
        List<MigrationPreview.Conflict> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int totalPermissions = 0;
        int grants = 0;

        for (PPGroup ppGroup : ppData.groups().values()) {
            boolean hasConflict = existingGroups.containsKey(ppGroup.name().toLowerCase());
            String conflictDetails = null;

            if (hasConflict) {
                Group existing = existingGroups.get(ppGroup.name().toLowerCase());
                conflictDetails = "conflicts with existing group";
                conflicts.add(new MigrationPreview.Conflict(
                    MigrationPreview.ConflictType.GROUP,
                    ppGroup.name(),
                    String.format("%d permissions", ppGroup.getPermissionCount()),
                    String.format("weight=%d, %d permissions", existing.getWeight(), existing.getNodes().size()),
                    "Use --merge, --skip, or --overwrite"
                ));
            }

            int validPerms = 0;
            for (String rawPerm : ppGroup.permissions()) {
                String cleaned = cleanPermission(rawPerm, warnings, "group:" + ppGroup.name());
                if (cleaned == null) continue;
                String validated = validatePermission(cleaned, warnings, "group:" + ppGroup.name());
                if (validated != null) {
                    validPerms++;
                    totalPermissions++;
                    grants++;
                }
            }

            groupPreviews.add(new MigrationPreview.GroupPreview(
                ppGroup.name(),
                0,
                validPerms,
                null,
                null,
                Collections.emptyList(),
                hasConflict,
                conflictDetails
            ));
        }

        int usersWithCustomPermissions = 0;
        int usersWithGroupsOnly = 0;
        int skippedUsers = 0;

        for (PPUser ppUser : ppData.users().values()) {
            if (ppUser.hasCustomPermissions()) {
                usersWithCustomPermissions++;

                for (String rawPerm : ppUser.permissions()) {
                    String cleaned = cleanPermission(rawPerm, warnings, "user:" + ppUser.uuid());
                    if (cleaned == null) continue;
                    String validated = validatePermission(cleaned, warnings, "user:" + ppUser.uuid());
                    if (validated != null) {
                        totalPermissions++;
                        grants++;
                    }
                }
            } else {
                usersWithGroupsOnly++;
            }

            if (existingUsers.containsKey(ppUser.uuid()) &&
                options.conflictResolution() == ConflictResolution.SKIP) {
                skippedUsers++;
            }
        }

        String backupPath = plugin.getDataDirectory().resolve("backups")
            .resolve("pre-migration-" + System.currentTimeMillis() + ".zip").toString();

        return new MigrationPreview(
            getSourceName(),
            getStorageDescription(),
            groupPreviews,
            new MigrationPreview.UserStats(
                ppData.users().size(),
                usersWithCustomPermissions,
                usersWithGroupsOnly,
                skippedUsers
            ),
            new MigrationPreview.PermissionStats(
                totalPermissions, grants, 0, 0, 0, 0
            ),
            Collections.emptyList(),
            conflicts,
            warnings,
            backupPath
        );
    }

    // ==================== Data Import ====================

    private ImportStats importGroups(Map<String, PPGroup> ppGroups, Map<String, Group> existing,
                                     MigrationOptions options, MigrationProgressCallback callback,
                                     List<String> warnings) {
        ImportStats stats = new ImportStats();
        int processed = 0;

        for (PPGroup ppGroup : ppGroups.values()) {
            processed++;
            callback.onProgress(processed, "Group: " + ppGroup.name());

            String lowerName = ppGroup.name().toLowerCase();
            boolean exists = existing.containsKey(lowerName);

            if (exists) {
                switch (options.conflictResolution()) {
                    case SKIP:
                        stats.skipped++;
                        continue;
                    case MERGE:
                        Group merged = mergeGroup(existing.get(lowerName), ppGroup, warnings);
                        saveGroup(merged);
                        stats.merged++;
                        stats.permissions += countValidPermissions(ppGroup, warnings);
                        continue;
                    case OVERWRITE:
                        break;
                }
            }

            Group hpGroup = transformGroup(ppGroup, warnings);
            saveGroup(hpGroup);
            stats.imported++;
            stats.permissions += countValidPermissions(ppGroup, warnings);
        }

        return stats;
    }

    private ImportStats importUsers(Map<UUID, PPUser> ppUsers, Map<UUID, User> existing,
                                    PPConfig config, MigrationOptions options,
                                    MigrationProgressCallback callback, List<String> warnings) {
        ImportStats stats = new ImportStats();
        int processed = 0;

        for (PPUser ppUser : ppUsers.values()) {
            processed++;
            if (processed % 100 == 0) {
                callback.onProgress(processed, "Users: " + processed + "/" + ppUsers.size());
            }

            if (options.skipDefaultUsers() && !ppUser.hasCustomPermissions() &&
                ppUser.groups().size() <= 1 &&
                (ppUser.groups().isEmpty() || ppUser.groups().get(0).equalsIgnoreCase(config.defaultGroup()))) {
                stats.skipped++;
                continue;
            }

            boolean exists = existing.containsKey(ppUser.uuid());

            if (exists) {
                switch (options.conflictResolution()) {
                    case SKIP:
                        stats.skipped++;
                        continue;
                    case MERGE:
                        User merged = mergeUser(existing.get(ppUser.uuid()), ppUser, config, warnings);
                        saveUser(merged);
                        stats.merged++;
                        stats.permissions += countValidUserPermissions(ppUser, warnings);
                        continue;
                    case OVERWRITE:
                        break;
                }
            }

            User hpUser = transformUser(ppUser, config, warnings);
            saveUser(hpUser);
            stats.imported++;
            stats.permissions += countValidUserPermissions(ppUser, warnings);
        }

        return stats;
    }

    // ==================== Data Transformation ====================

    private Group transformGroup(PPGroup ppGroup, List<String> warnings) {
        Group group = new Group(ppGroup.name().toLowerCase());

        for (String rawPerm : ppGroup.permissions()) {
            Node node = transformPermission(rawPerm, warnings, "group:" + ppGroup.name());
            if (node != null) {
                group.addNode(node);
            }
        }

        return group;
    }

    private Group mergeGroup(Group existing, PPGroup ppGroup, List<String> warnings) {
        for (String rawPerm : ppGroup.permissions()) {
            Node node = transformPermission(rawPerm, warnings, "group:" + ppGroup.name());
            if (node != null) {
                boolean nodeExists = existing.getNodes().stream()
                    .anyMatch(n -> n.equalsIgnoringExpiry(node));
                if (!nodeExists) {
                    existing.addNode(node);
                }
            }
        }
        return existing;
    }

    private User transformUser(PPUser ppUser, PPConfig config, List<String> warnings) {
        User user = new User(ppUser.uuid(), ppUser.username());

        if (!ppUser.groups().isEmpty()) {
            user.setPrimaryGroup(ppUser.groups().get(0).toLowerCase());
        } else {
            user.setPrimaryGroup(config.defaultGroup().toLowerCase());
        }

        for (String groupName : ppUser.groups()) {
            user.addNode(Node.group(groupName));
        }

        for (String rawPerm : ppUser.permissions()) {
            Node node = transformPermission(rawPerm, warnings, "user:" + ppUser.uuid());
            if (node != null) {
                user.addNode(node);
            }
        }

        return user;
    }

    private User mergeUser(User existing, PPUser ppUser, PPConfig config, List<String> warnings) {
        for (String groupName : ppUser.groups()) {
            Node groupNode = Node.group(groupName);
            boolean nodeExists = existing.getNodes().stream()
                .anyMatch(n -> n.equalsIgnoringExpiry(groupNode));
            if (!nodeExists) {
                existing.addNode(groupNode);
            }
        }

        for (String rawPerm : ppUser.permissions()) {
            Node node = transformPermission(rawPerm, warnings, "user:" + ppUser.uuid());
            if (node != null) {
                boolean nodeExists = existing.getNodes().stream()
                    .anyMatch(n -> n.equalsIgnoringExpiry(node));
                if (!nodeExists) {
                    existing.addNode(node);
                }
            }
        }

        return existing;
    }

    @Nullable
    private Node transformPermission(String rawPermission, List<String> warnings, String context) {
        String cleaned = cleanPermission(rawPermission, warnings, context);
        if (cleaned == null) {
            return null;
        }

        String validated = validatePermission(cleaned, warnings, context);
        if (validated == null) {
            return null;
        }

        return Node.of(validated);
    }

    private int countValidPermissions(PPGroup ppGroup, List<String> warnings) {
        List<String> throwaway = new ArrayList<>();
        return (int) ppGroup.permissions().stream()
            .map(p -> cleanPermission(p, throwaway, "count"))
            .filter(Objects::nonNull)
            .map(p -> validatePermission(p, throwaway, "count"))
            .filter(Objects::nonNull)
            .count();
    }

    private int countValidUserPermissions(PPUser ppUser, List<String> warnings) {
        List<String> throwaway = new ArrayList<>();
        return (int) ppUser.permissions().stream()
            .map(p -> cleanPermission(p, throwaway, "count"))
            .filter(Objects::nonNull)
            .map(p -> validatePermission(p, throwaway, "count"))
            .filter(Objects::nonNull)
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

    private void saveAll() {
        try {
            plugin.getStorage().saveAll().join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save all data", e);
        }
    }

    private static class ImportStats {
        int imported = 0;
        int merged = 0;
        int skipped = 0;
        int permissions = 0;
    }
}
