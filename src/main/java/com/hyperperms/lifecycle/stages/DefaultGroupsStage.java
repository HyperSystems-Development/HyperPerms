package com.hyperperms.lifecycle.stages;

import com.hyperperms.config.HyperPermsConfig;
import com.hyperperms.lifecycle.ServiceContainer;
import com.hyperperms.lifecycle.Stage;
import com.hyperperms.manager.GroupManagerImpl;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Loads default groups on first run and ensures the configured default group exists.
 */
public final class DefaultGroupsStage implements Stage {

    @Override
    public @NotNull String name() {
        return "Default Groups";
    }

    @Override
    public int order() {
        return 350; // After CoreManagerStage (300), before ResolverStage (400)
    }

    @Override
    public void initialize(@NotNull ServiceContainer container) throws Exception {
        HyperPermsConfig config = container.get(HyperPermsConfig.class);
        GroupManagerImpl groupManager = container.get(GroupManagerImpl.class);

        // Load default groups on first run
        if (groupManager.getLoadedGroups().isEmpty()) {
            loadDefaultGroups(groupManager);
        }

        // Ensure default group exists
        if (config.shouldCreateDefaultGroup()) {
            groupManager.ensureDefaultGroup(config.getDefaultGroup());
        }
    }

    /**
     * Loads default groups from the default-groups.json resource.
     * Called on first run when no groups exist in storage.
     */
    private void loadDefaultGroups(GroupManagerImpl groupManager) {
        Logger.info("No groups found, loading default groups...");

        try (var inputStream = getClass().getClassLoader().getResourceAsStream("default-groups.json")) {
            if (inputStream == null) {
                Logger.warn("default-groups.json not found in resources, skipping default group creation");
                return;
            }

            String json = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            com.google.gson.JsonObject groups = root.getAsJsonObject("groups");

            if (groups == null) {
                Logger.warn("No 'groups' object found in default-groups.json");
                return;
            }

            int created = 0;
            for (var entry : groups.entrySet()) {
                String groupName = entry.getKey();
                com.google.gson.JsonObject groupData = entry.getValue().getAsJsonObject();

                // Create the group
                Group group = groupManager.createGroup(groupName);

                // Set weight
                if (groupData.has("weight")) {
                    group.setWeight(groupData.get("weight").getAsInt());
                }

                // Set prefix
                if (groupData.has("prefix")) {
                    group.setPrefix(groupData.get("prefix").getAsString());
                }

                // Set suffix
                if (groupData.has("suffix")) {
                    group.setSuffix(groupData.get("suffix").getAsString());
                }

                // Add permissions
                if (groupData.has("permissions")) {
                    for (var perm : groupData.getAsJsonArray("permissions")) {
                        group.addNode(Node.builder(perm.getAsString()).build());
                    }
                }

                // Add parent groups (will be resolved after all groups are created)
                if (groupData.has("parents")) {
                    for (var parent : groupData.getAsJsonArray("parents")) {
                        group.addParent(parent.getAsString());
                    }
                }

                // Save the group
                groupManager.saveGroup(group).join();
                created++;
                Logger.debug("Created default group: %s (weight=%d)", groupName, group.getWeight());
            }

            Logger.info("Loaded %d default groups from default-groups.json", created);

        } catch (Exception e) {
            Logger.warn("Failed to load default groups: %s", e.getMessage());
            Logger.debug("Stack trace: ", e);
        }
    }
}
