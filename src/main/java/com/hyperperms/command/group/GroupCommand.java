package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpContainerCommand;

/**
 * Container command for all group subcommands.
 * <p>
 * Registers: list, info, create, delete, setperm, unsetperm, setexpiry,
 * setweight, setprefix, setsuffix, setdisplayname, rename, parent.
 */
public class GroupCommand extends HpContainerCommand {

    public GroupCommand(HyperPerms hyperPerms) {
        super("group", "Manage groups");
        addSubCommand(new GroupListCommand(hyperPerms));
        addSubCommand(new GroupInfoCommand(hyperPerms));
        addSubCommand(new GroupCreateCommand(hyperPerms));
        addSubCommand(new GroupDeleteCommand(hyperPerms));
        addSubCommand(new GroupSetPermCommand(hyperPerms));
        addSubCommand(new GroupUnsetPermCommand(hyperPerms));
        addSubCommand(new GroupSetExpiryCommand(hyperPerms));
        addSubCommand(new GroupSetWeightCommand(hyperPerms));
        addSubCommand(new GroupSetPrefixCommand(hyperPerms));
        addSubCommand(new GroupSetSuffixCommand(hyperPerms));
        addSubCommand(new GroupSetDisplayNameCommand(hyperPerms));
        addSubCommand(new GroupRenameCommand(hyperPerms));
        addSubCommand(new GroupParentCommand(hyperPerms));
    }
}
