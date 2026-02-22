package com.hyperperms.command.debug;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpContainerCommand;

/**
 * Container command for debug subcommands.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /hp debug perms} - Toggle verbose permission check logging</li>
 *   <li>{@code /hp debug tree <user>} - Show inheritance tree for a user</li>
 *   <li>{@code /hp debug resolve <user> <permission>} - Debug permission resolution step-by-step</li>
 *   <li>{@code /hp debug contexts <user>} - Show all current contexts for a user</li>
 *   <li>{@code /hp debug toggle <category|all> [on|off]} - Toggle debug categories</li>
 *   <li>{@code /hp debug status} - Show all debug category states</li>
 * </ul>
 */
public class DebugCommand extends HpContainerCommand {

    @SuppressWarnings("this-escape")
    public DebugCommand(HyperPerms hyperPerms) {
        super("debug", "Debug commands for troubleshooting");
        addSubCommand(new DebugPermsCommand());
        addSubCommand(new DebugTreeCommand(hyperPerms));
        addSubCommand(new DebugResolveCommand(hyperPerms));
        addSubCommand(new DebugContextsCommand(hyperPerms));
        addSubCommand(new DebugToggleCommand());
        addSubCommand(new DebugStatusCommand());
    }
}
