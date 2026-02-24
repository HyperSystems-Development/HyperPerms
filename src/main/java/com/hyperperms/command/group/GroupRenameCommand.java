package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

/**
 * Renames a group by copying all settings/nodes/parents to a new group and updating
 * all users and parent references that pointed to the old name.
 * <p>
 * Usage: /hp group rename &lt;oldname&gt; &lt;newname&gt;
 */
public class GroupRenameCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> oldNameArg;
    private final RequiredArg<String> newNameArg;

    public GroupRenameCommand(HyperPerms hyperPerms) {
        super("rename", "Rename a group");
        this.hyperPerms = hyperPerms;
        this.oldNameArg = describeArg("oldname", "Current group name", ArgTypes.STRING);
        this.newNameArg = describeArg("newname", "New group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String oldName = ctx.get(oldNameArg);
        String newName = ctx.get(newNameArg);

        Group group = hyperPerms.getGroupManager().getGroup(oldName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + oldName));
            return CompletableFuture.completedFuture(null);
        }

        // Check if new name already exists
        if (hyperPerms.getGroupManager().getGroup(newName) != null) {
            ctx.sender().sendMessage(Message.raw("A group with name " + newName + " already exists"));
            return CompletableFuture.completedFuture(null);
        }

        // Delete old group and create new one with same settings
        hyperPerms.getGroupManager().deleteGroup(oldName);

        // Create new group with new name but same properties
        Group newGroup = new Group(newName);
        newGroup.setDisplayName(group.getDisplayName());
        newGroup.setWeight(group.getWeight());
        newGroup.setPrefix(group.getPrefix());
        newGroup.setSuffix(group.getSuffix());
        newGroup.setPrefixPriority(group.getPrefixPriority());
        newGroup.setSuffixPriority(group.getSuffixPriority());

        // Copy nodes
        for (Node node : group.getNodes()) {
            newGroup.setNode(node);
        }

        // Copy parent groups
        for (String parent : group.getParentGroups()) {
            newGroup.addParent(parent);
        }

        hyperPerms.getGroupManager().createGroup(newName);
        hyperPerms.getGroupManager().saveGroup(newGroup);

        // Update all users that had the old group
        for (User user : hyperPerms.getUserManager().getLoadedUsers()) {
            if (user.getInheritedGroups().contains(oldName.toLowerCase())) {
                user.removeGroup(oldName);
                user.addGroup(newName);
                hyperPerms.getUserManager().saveUser(user);
            }
        }

        // Update all groups that had the old group as parent
        for (Group g : hyperPerms.getGroupManager().getLoadedGroups()) {
            if (g.getParentGroups().contains(oldName.toLowerCase())) {
                g.removeParent(oldName);
                g.addParent(newName);
                hyperPerms.getGroupManager().saveGroup(g);
            }
        }

        hyperPerms.getCacheInvalidator().invalidateAll();
        ctx.sender().sendMessage(Message.raw("Renamed group " + oldName + " to " + newName));
        return CompletableFuture.completedFuture(null);
    }
}
