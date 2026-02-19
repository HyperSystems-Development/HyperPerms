package com.hyperperms.command;

import com.hyperperms.HyperPerms;

/**
 * Container command for permission listing and search.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /hp perms list [category]} - List registered permissions</li>
 *   <li>{@code /hp perms search <query>} - Search permissions by name or description</li>
 * </ul>
 */
public class PermsCommand extends HpContainerCommand {

    @SuppressWarnings("this-escape")
    public PermsCommand(HyperPerms hyperPerms) {
        super("perms", "Permission listing and search");
        addSubCommand(new PermsListCommand(hyperPerms));
        addSubCommand(new PermsSearchCommand(hyperPerms));
    }
}
