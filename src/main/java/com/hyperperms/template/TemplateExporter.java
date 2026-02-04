package com.hyperperms.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hyperperms.HyperPerms;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.manager.TrackManagerImpl;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Exports the current permission setup as a template.
 */
public final class TemplateExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HyperPerms hyperPerms;
    private final Path templatesDir;

    /**
     * Creates a new template exporter.
     *
     * @param hyperPerms   the HyperPerms instance
     * @param templatesDir the directory to export templates to
     */
    public TemplateExporter(@NotNull HyperPerms hyperPerms, @NotNull Path templatesDir) {
        this.hyperPerms = Objects.requireNonNull(hyperPerms);
        this.templatesDir = Objects.requireNonNull(templatesDir);
    }

    /**
     * Result of a template export.
     */
    public record ExportResult(
            boolean success,
            @NotNull String message,
            @org.jetbrains.annotations.Nullable Path filePath
    ) {
        /**
         * Creates a successful result.
         */
        public static ExportResult success(String message, Path filePath) {
            return new ExportResult(true, message, filePath);
        }

        /**
         * Creates a failed result.
         */
        public static ExportResult failure(String message) {
            return new ExportResult(false, message, null);
        }
    }

    /**
     * Exports the current permission setup as a template.
     *
     * @param name        the template name
     * @param displayName the human-readable display name (optional)
     * @param description the template description (optional)
     * @param author      the template author (optional)
     * @return a future containing the export result
     */
    public CompletableFuture<ExportResult> export(
            @NotNull String name,
            @org.jetbrains.annotations.Nullable String displayName,
            @org.jetbrains.annotations.Nullable String description,
            @org.jetbrains.annotations.Nullable String author
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Sanitize name
                String safeName = name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                if (safeName.isEmpty()) {
                    return ExportResult.failure("Invalid template name");
                }

                // Build JSON
                JsonObject root = buildTemplateJson(safeName, displayName, description, author);

                // Ensure templates directory exists
                Files.createDirectories(templatesDir);

                // Write file
                Path filePath = templatesDir.resolve(safeName + ".json");
                Files.writeString(filePath, GSON.toJson(root));

                Logger.info("[Template] Exported template: %s", filePath);
                return ExportResult.success("Template exported successfully: " + safeName, filePath);

            } catch (IOException e) {
                Logger.warn("[Template] Failed to export template: %s", e.getMessage());
                return ExportResult.failure("Failed to export template: " + e.getMessage());
            }
        });
    }

    /**
     * Builds the template JSON from the current setup.
     */
    private JsonObject buildTemplateJson(
            String name,
            @org.jetbrains.annotations.Nullable String displayName,
            @org.jetbrains.annotations.Nullable String description,
            @org.jetbrains.annotations.Nullable String author
    ) {
        GroupManagerImpl groupManager = (GroupManagerImpl) hyperPerms.getGroupManager();
        TrackManagerImpl trackManager = (TrackManagerImpl) hyperPerms.getTrackManager();

        JsonObject root = new JsonObject();

        // Basic info
        root.addProperty("name", name);
        root.addProperty("displayName", displayName != null ? displayName : name);
        root.addProperty("description", description != null ? description : "Exported from HyperPerms");
        root.addProperty("version", "1.0.0");
        root.addProperty("author", author != null ? author : "Server Admin");

        // Groups
        JsonObject groupsObj = new JsonObject();
        List<Group> groups = new ArrayList<>(groupManager.getLoadedGroups());
        groups.sort(Comparator.comparingInt(Group::getWeight));

        for (Group group : groups) {
            JsonObject groupObj = buildGroupJson(group);
            groupsObj.add(group.getName(), groupObj);
        }
        root.add("groups", groupsObj);

        // Tracks
        JsonObject tracksObj = new JsonObject();
        for (Track track : trackManager.getLoadedTracks()) {
            JsonArray trackGroups = new JsonArray();
            for (String groupName : track.getGroups()) {
                trackGroups.add(groupName);
            }
            tracksObj.add(track.getName(), trackGroups);
        }
        if (!tracksObj.isEmpty()) {
            root.add("tracks", tracksObj);
        }

        // Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("notes", "Exported from server on " + java.time.LocalDate.now());
        JsonArray recommendedPlugins = new JsonArray();
        metadata.add("recommendedPlugins", recommendedPlugins);
        root.add("metadata", metadata);

        return root;
    }

    /**
     * Builds JSON for a single group.
     */
    private JsonObject buildGroupJson(Group group) {
        JsonObject obj = new JsonObject();

        // Permissions (with descriptions where available)
        JsonArray permsArray = new JsonArray();
        List<String> parentGroups = new ArrayList<>();

        for (Node node : group.getNodes()) {
            String permission = node.getPermission();
            
            // Separate parent inheritance from regular permissions
            if (permission.startsWith("group.")) {
                parentGroups.add(permission.substring("group.".length()));
            } else {
                // Create permission object with optional description
                JsonObject permObj = new JsonObject();
                permObj.addProperty("node", permission);
                // We don't have descriptions stored, but the format supports it
                permsArray.add(permObj);
            }
        }
        obj.add("permissions", permsArray);

        // Parents
        if (!parentGroups.isEmpty()) {
            JsonArray parentsArray = new JsonArray();
            for (String parent : parentGroups) {
                parentsArray.add(parent);
            }
            obj.add("parents", parentsArray);
        }

        // Properties
        obj.addProperty("weight", group.getWeight());
        obj.addProperty("prefix", group.getPrefix() != null ? group.getPrefix() : "");
        obj.addProperty("suffix", group.getSuffix() != null ? group.getSuffix() : "");

        if (group.getDisplayName() != null && !group.getDisplayName().isEmpty()) {
            obj.addProperty("displayName", group.getDisplayName());
        }

        return obj;
    }

    /**
     * Exports to a specific path (not the templates directory).
     *
     * @param name        the template name
     * @param displayName the human-readable display name
     * @param description the template description
     * @param author      the template author
     * @param outputPath  the output file path
     * @return a future containing the export result
     */
    public CompletableFuture<ExportResult> exportTo(
            @NotNull String name,
            @org.jetbrains.annotations.Nullable String displayName,
            @org.jetbrains.annotations.Nullable String description,
            @org.jetbrains.annotations.Nullable String author,
            @NotNull Path outputPath
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject root = buildTemplateJson(name, displayName, description, author);
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, GSON.toJson(root));
                Logger.info("[Template] Exported template to: %s", outputPath);
                return ExportResult.success("Template exported successfully", outputPath);
            } catch (IOException e) {
                Logger.warn("[Template] Failed to export template: %s", e.getMessage());
                return ExportResult.failure("Failed to export template: " + e.getMessage());
            }
        });
    }
}
