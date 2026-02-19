package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpContainerCommand;

/**
 * Container command for all user subcommands.
 * Registers: info, setperm, unsetperm, setexpiry, addgroup, removegroup,
 * setprimarygroup, promote, demote, setprefix, setsuffix, clear, clone.
 */
public class UserCommand extends HpContainerCommand {

    @SuppressWarnings("this-escape")
    public UserCommand(HyperPerms hyperPerms) {
        super("user", "Manage users");
        addSubCommand(new UserInfoCommand(hyperPerms));
        addSubCommand(new UserSetPermCommand(hyperPerms));
        addSubCommand(new UserUnsetPermCommand(hyperPerms));
        addSubCommand(new UserSetExpiryCommand(hyperPerms));
        addSubCommand(new UserAddGroupCommand(hyperPerms));
        addSubCommand(new UserRemoveGroupCommand(hyperPerms));
        addSubCommand(new UserSetPrimaryGroupCommand(hyperPerms));
        addSubCommand(new UserPromoteCommand(hyperPerms));
        addSubCommand(new UserDemoteCommand(hyperPerms));
        addSubCommand(new UserSetPrefixCommand(hyperPerms));
        addSubCommand(new UserSetSuffixCommand(hyperPerms));
        addSubCommand(new UserClearCommand(hyperPerms));
        addSubCommand(new UserCloneCommand(hyperPerms));
    }
}
