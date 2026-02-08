package com.hyperperms.platform;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main HyperPerms command for Hytale.
 * Provides /hp command with various subcommands.
 *
 * Note: "this-escape" warning suppressed in constructor because subcommand
 * registration via addSubCommand() only stores references to inner class instances
 * without calling any methods that depend on the fully initialized state of the
 * outer HyperPermsCommand instance. This pattern is safe.
 */
public class HyperPermsCommand extends AbstractCommand {

    private final HyperPerms hyperPerms;

    // Confirmation tracking for destructive operations
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000; // 60 seconds

    /**
     * Checks if a pending confirmation exists and is still valid.
     *
     * @param key the confirmation key (e.g., "group-delete:groupname" or "user-clear:uuid")
     * @return true if confirmation is pending and not expired
     */
    private static boolean hasPendingConfirmation(String key) {
        Long timestamp = pendingConfirmations.get(key);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Records a new pending confirmation.
     *
     * @param key the confirmation key
     */
    private static void setPendingConfirmation(String key) {
        pendingConfirmations.put(key, System.currentTimeMillis());
    }

    /**
     * Clears a pending confirmation after execution.
     *
     * @param key the confirmation key
     */
    private static void clearPendingConfirmation(String key) {
        pendingConfirmations.remove(key);
    }

    @SuppressWarnings("this-escape")
    public HyperPermsCommand(HyperPerms hyperPerms) {
        super("hp", "HyperPerms management command");
        this.hyperPerms = hyperPerms;

        // Add subcommands
        addSubCommand(new HelpSubCommand());
        addSubCommand(new GroupSubCommand(hyperPerms));
        addSubCommand(new UserSubCommand(hyperPerms));
        addSubCommand(new CheckSubCommand(hyperPerms));
        addSubCommand(new BackupSubCommand(hyperPerms));
        addSubCommand(new ExportSubCommand(hyperPerms));
        addSubCommand(new ImportSubCommand(hyperPerms));
        addSubCommand(new com.hyperperms.commands.MigrateSubCommand(hyperPerms));
        addSubCommand(new ReloadSubCommand(hyperPerms));
        addSubCommand(new ResetGroupsSubCommand(hyperPerms));

        // Debug commands
        addSubCommand(new DebugSubCommand(hyperPerms));

        // Permission listing commands
        addSubCommand(new PermsSubCommand(hyperPerms));

        // Web editor commands
        if (hyperPerms.getWebEditorService() != null) {
            addSubCommand(new com.hyperperms.commands.EditorSubCommand(hyperPerms, hyperPerms.getWebEditorService()));
            addSubCommand(new com.hyperperms.commands.ApplySubCommand(hyperPerms, hyperPerms.getWebEditorService()));
        }

        // Update commands
        addSubCommand(new com.hyperperms.commands.UpdateSubCommand(hyperPerms));
        addSubCommand(new com.hyperperms.commands.UpdatesSubCommand(hyperPerms));

        // Template commands
        addSubCommand(new com.hyperperms.commands.TemplateSubCommand(hyperPerms));

        // Analytics commands
        addSubCommand(new com.hyperperms.commands.AnalyticsSubCommand(hyperPerms));

        // Add aliases
        addAliases("hyperperms", "perms");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(buildHelpMessage());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Builds the styled help message shown for "/hp" and "/hp help".
     *
     * Example output:
     *
     * --- HyperPerms -------------------------
     *   HyperPerms management command
     *
     *   Commands:
     *     group - Manage groups
     *     user - Manage users
     *     check - Check permissions
     *     backup - Manage backups
     *     export - Export data to file
     *     import - Import data from file
     *     reload - Reload configuration
     *     debug - Debug tools
     *     perms - Permission listing
     *
     *   Use /hp <command> --help for details
     * ------------------------------------------
     */
    private Message buildHelpMessage() {
        java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
        java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
        java.awt.Color GRAY = java.awt.Color.GRAY;
        java.awt.Color WHITE = java.awt.Color.WHITE;

        List<Message> parts = new ArrayList<>();
        int width = 42;
        String label = "HyperPerms";
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));
        parts.add(Message.raw("  " + getDescription() + "\n\n").color(WHITE));
        parts.add(Message.raw("  Commands:\n").color(GOLD));

        for (var entry : getSubCommands().entrySet()) {
            String name = entry.getKey();
            if (name.equals("help")) continue;
            parts.add(Message.raw("    " + name).color(GREEN));
            parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
        }

        parts.add(Message.raw("\n  Use /hp <command> --help for details\n").color(GRAY));
        parts.add(Message.raw("-".repeat(width)).color(GRAY));

        return Message.join(parts.toArray(new Message[0]));
    }

    // ==================== Utility Methods ====================

    /**
     * Finds a user by name from loaded users.
     * Returns the user if found by exact (case-insensitive) match.
     */
    private static User findUserByName(HyperPerms hyperPerms, String name) {
        for (User user : hyperPerms.getUserManager().getLoadedUsers()) {
            if (name.equalsIgnoreCase(user.getUsername())) {
                return user;
            }
        }
        return null;
    }

    /**
     * Tries to parse a string as UUID, returns empty if not a valid UUID.
     */
    private static Optional<UUID> parseUuid(String input) {
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a player identifier (name or UUID string) to a User.
     * Loads from storage if necessary. Does not create new users.
     */
    private static User resolveUser(HyperPerms hyperPerms, String identifier) {
        return resolveUser(hyperPerms, identifier, false);
    }

    /**
     * Resolves a player identifier (name or UUID string) to a User.
     * Loads from storage if necessary.
     *
     * @param hyperPerms the HyperPerms instance
     * @param identifier player name or UUID string
     * @param createIfNotExists if true and a UUID is provided, creates the user if not found
     * @return the User, or null if not found (and createIfNotExists is false)
     */
    private static User resolveUser(HyperPerms hyperPerms, String identifier, boolean createIfNotExists) {
        // Try as UUID first
        Optional<UUID> uuidOpt = parseUuid(identifier);
        if (uuidOpt.isPresent()) {
            UUID uuid = uuidOpt.get();
            User user = hyperPerms.getUserManager().getUser(uuid);
            if (user != null) {
                return user;
            }
            // Try to load from storage
            Optional<User> loaded = hyperPerms.getUserManager().loadUser(uuid).join();
            if (loaded.isPresent()) {
                return loaded.get();
            }
            // Create if requested (useful for Tebex offline player support)
            if (createIfNotExists) {
                return hyperPerms.getUserManager().getOrCreateUser(uuid);
            }
            return null;
        }

        // Try as username
        return findUserByName(hyperPerms, identifier);
    }

    /**
     * Resolves a player identifier to a User, creating the user if a UUID is provided
     * and the user doesn't exist. This is useful for Tebex integration where players
     * may not have joined the server yet.
     */
    private static User resolveOrCreateUser(HyperPerms hyperPerms, String identifier) {
        return resolveUser(hyperPerms, identifier, true);
    }

    // ==================== HpCommand Base Class ====================

    /**
     * Base class for leaf commands that have arguments.
     * Overrides getUsageString() to format help text with Message.raw()
     * instead of Message.translation(), which requires registered translation keys.
     *
     * Example output of getUsageString() for "/hp user setprefix --help":
     *
     * --- hp user setprefix ----------------
     *   Set a user's custom prefix
     *
     *   Usage: /hp user setprefix <player> [--prefix=value]
     *
     *   Required:
     *     player (STRING) - Player name or UUID
     *
     *   Optional:
     *     --prefix (STRING) - Prefix text (omit to clear)
     * ------------------------------------------
     *
     * Example output of getUsageShort() on incorrect usage:
     *
     *   Usage: /hp user setprefix <player> [--prefix=value]
     *   Required:
     *     player (STRING) - Player name or UUID
     */
    private static abstract class HpCommand extends AbstractCommand {
        private final List<ArgDescriptor> argDescriptors = new ArrayList<>();

        HpCommand(String name, String description) {
            super(name, description);
        }

        protected <D> RequiredArg<D> describeArg(String name, String description,
                com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType<D> type) {
            argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, false));
            return withRequiredArg(name, description, type);
        }

        protected <D> RequiredArg<D> describeFlagArg(String name, String description,
                com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType<D> type) {
            argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, true));
            return withRequiredArg(name, description, type);
        }

        protected <D> OptionalArg<D> describeOptionalArg(String name, String description,
                com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType<D> type) {
            argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, false, true));
            return withOptionalArg(name, description, type);
        }

        private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
        private static final java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
        private static final java.awt.Color RED = new java.awt.Color(255, 85, 85);
        private static final java.awt.Color DARK_GRAY = new java.awt.Color(100, 100, 100);
        private static final java.awt.Color GRAY = java.awt.Color.GRAY;
        private static final java.awt.Color WHITE = java.awt.Color.WHITE;


        protected static String stripQuotes(String value) {
            if (value != null && value.length() >= 2
                    && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        @Override
        public Message getUsageString(com.hypixel.hytale.server.core.command.system.CommandSender sender) {
            List<Message> parts = new ArrayList<>();
            String name = getFullyQualifiedName();

            // Header bar
            int width = 42;
            int padding = width - name.length() - 2;
            int left = 3;
            int right = Math.max(3, padding - left);
            parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
            parts.add(Message.raw(name).color(GOLD));
            parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

            // Description
            parts.add(Message.raw("  ").color(WHITE));
            parts.add(Message.raw(getDescription() + "\n\n").color(WHITE));

            // Usage line
            parts.add(Message.raw("  Usage: ").color(GOLD));
            parts.add(Message.raw("/").color(GRAY));
            parts.add(Message.raw(name).color(GOLD));
            for (ArgDescriptor arg : argDescriptors) {
                if (arg.required) {
                    if (arg.isFlag) {
                        parts.add(Message.raw(" <--" + arg.name + "=value>").color(GREEN));
                    } else {
                        parts.add(Message.raw(" <" + arg.name + ">").color(GREEN));
                    }
                } else {
                    if (arg.isFlag) {
                        parts.add(Message.raw(" [--" + arg.name + "=value]").color(GRAY));
                    } else {
                        parts.add(Message.raw(" [" + arg.name + "]").color(GRAY));
                    }
                }
            }
            parts.add(Message.raw("\n"));

            // Required arguments
            boolean hasRequired = argDescriptors.stream().anyMatch(a -> a.required);
            if (hasRequired) {
                parts.add(Message.raw("\n  Required:\n").color(GOLD));
                for (ArgDescriptor arg : argDescriptors) {
                    if (arg.required) {
                        String argDisplay = arg.isFlag ? "--" + arg.name : arg.name;
                        parts.add(Message.raw("    " + argDisplay).color(GREEN));
                        parts.add(Message.raw(" (" + arg.typeName + ")").color(DARK_GRAY));
                        parts.add(Message.raw(" - " + arg.description + "\n").color(WHITE));
                    }
                }
            }

            // Optional arguments
            boolean hasOptional = argDescriptors.stream().anyMatch(a -> !a.required);
            if (hasOptional) {
                parts.add(Message.raw("\n  Optional:\n").color(GRAY));
                for (ArgDescriptor arg : argDescriptors) {
                    if (!arg.required) {
                        String argDisplay = arg.isFlag ? "--" + arg.name : arg.name;
                        parts.add(Message.raw("    " + argDisplay).color(GRAY));
                        parts.add(Message.raw(" (" + arg.typeName + ")").color(DARK_GRAY));
                        parts.add(Message.raw(" - " + arg.description + "\n").color(WHITE));
                    }
                }
            }

            // Footer bar
            parts.add(Message.raw("-".repeat(42)).color(GRAY));

            return Message.join(parts.toArray(new Message[0]));
        }

        @Override
        public Message getUsageShort(com.hypixel.hytale.server.core.command.system.CommandSender sender, boolean showAll) {
            List<Message> parts = new ArrayList<>();

            // Usage line with required args in RED to emphasize what's missing
            parts.add(Message.raw("  Usage: ").color(GOLD));
            parts.add(Message.raw("/").color(GRAY));
            parts.add(Message.raw(getFullyQualifiedName()).color(GOLD));
            for (ArgDescriptor arg : argDescriptors) {
                if (arg.required) {
                    if (arg.isFlag) {
                        parts.add(Message.raw(" <--" + arg.name + "=value>").color(RED));
                    } else {
                        parts.add(Message.raw(" <" + arg.name + ">").color(RED));
                    }
                } else {
                    if (arg.isFlag) {
                        parts.add(Message.raw(" [--" + arg.name + "=value]").color(GRAY));
                    } else {
                        parts.add(Message.raw(" [" + arg.name + "]").color(GRAY));
                    }
                }
            }

            if (showAll) {
                boolean hasRequired = argDescriptors.stream().anyMatch(a -> a.required);
                if (hasRequired) {
                    parts.add(Message.raw("\n  Required:\n").color(RED));
                    for (ArgDescriptor arg : argDescriptors) {
                        if (arg.required) {
                            String argDisplay = arg.isFlag ? "--" + arg.name : arg.name;
                            parts.add(Message.raw("    " + argDisplay).color(RED));
                            parts.add(Message.raw(" (" + arg.typeName + ")").color(DARK_GRAY));
                            parts.add(Message.raw(" - " + arg.description + "\n").color(WHITE));
                        }
                    }
                }
            }

            return Message.join(parts.toArray(new Message[0]));
        }

        private static String getTypeName(
                com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType<?> type) {
            if (type == ArgTypes.STRING) return "STRING";
            if (type == ArgTypes.INTEGER) return "INTEGER";
            if (type == ArgTypes.BOOLEAN) return "BOOLEAN";
            if (type == ArgTypes.FLOAT) return "FLOAT";
            if (type == ArgTypes.DOUBLE) return "DOUBLE";
            if (type == ArgTypes.UUID) return "UUID";
            return "VALUE";
        }

        private record ArgDescriptor(String name, String typeName, String description, boolean required, boolean isFlag) {}
    }

    /**
     * Base class for container commands that hold subcommands.
     * Overrides getUsageString() to list subcommands with colored formatting
     * and a hint to use --help on individual subcommands.
     *
     * Example output of getUsageString() for "/hp user --help":
     *
     * --- hp user ----------------------------
     *   HyperPerms user management
     *
     *   Subcommands:
     *     info - Show user info
     *     setperm - Set a user permission
     *     unsetperm - Remove a user permission
     *     addgroup - Add user to a group
     *     ...
     *
     *   Use /hp user <subcommand> --help for details
     * ------------------------------------------
     *
     * Example output of getUsageShort() on incorrect usage:
     *
     *   Usage: /hp user <info|setperm|unsetperm|...>
     *   Available:
     *     info - Show user info
     *     setperm - Set a user permission
     *     ...
     */
    private static abstract class HpContainerCommand extends AbstractCommand {
        private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
        private static final java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
        private static final java.awt.Color RED = new java.awt.Color(255, 85, 85);
        private static final java.awt.Color GRAY = java.awt.Color.GRAY;
        private static final java.awt.Color WHITE = java.awt.Color.WHITE;

        @SuppressWarnings("this-escape")
        HpContainerCommand(String name, String description) {
            super(name, description);
            addSubCommand(new AbstractCommand("help", "Show help") {
                @Override
                protected CompletableFuture<Void> execute(CommandContext ctx) {
                    ctx.sender().sendMessage(HpContainerCommand.this.getUsageString(ctx.sender()));
                    return CompletableFuture.completedFuture(null);
                }
            });
        }

        @Override
        public Message getUsageString(com.hypixel.hytale.server.core.command.system.CommandSender sender) {
            List<Message> parts = new ArrayList<>();
            String name = getFullyQualifiedName();
            int width = 42;
            int padding = width - name.length() - 2;
            int left = 3;
            int right = Math.max(3, padding - left);

            // Header bar
            parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
            parts.add(Message.raw(name).color(GOLD));
            parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

            // Description
            parts.add(Message.raw("  ").color(WHITE));
            parts.add(Message.raw(getDescription() + "\n\n").color(WHITE));

            // Subcommand list
            parts.add(Message.raw("  Subcommands:\n").color(GOLD));
            for (var entry : getSubCommands().entrySet()) {
                if (entry.getKey().equals("help")) continue;
                parts.add(Message.raw("    " + entry.getKey()).color(GREEN));
                parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
            }

            // Hint
            parts.add(Message.raw("\n  Use /" + name + " <subcommand> --help for details\n").color(GRAY));

            // Footer bar
            parts.add(Message.raw("-".repeat(width)).color(GRAY));

            return Message.join(parts.toArray(new Message[0]));
        }

        @Override
        public Message getUsageShort(com.hypixel.hytale.server.core.command.system.CommandSender sender, boolean showAll) {
            List<Message> parts = new ArrayList<>();

            // Usage line showing required subcommand choice in RED
            parts.add(Message.raw("  Usage: ").color(GOLD));
            parts.add(Message.raw("/").color(GRAY));
            parts.add(Message.raw(getFullyQualifiedName()).color(GOLD));
            String subNames = " <" + getSubCommands().keySet().stream()
                    .filter(k -> !k.equals("help"))
                    .collect(java.util.stream.Collectors.joining("|")) + ">";
            parts.add(Message.raw(subNames).color(RED));

            if (showAll) {
                parts.add(Message.raw("\n  Available:\n").color(RED));
                for (var entry : getSubCommands().entrySet()) {
                    if (entry.getKey().equals("help")) continue;
                    parts.add(Message.raw("    " + entry.getKey()).color(GREEN));
                    parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
                }
            }

            return Message.join(parts.toArray(new Message[0]));
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(getUsageString(ctx.sender()));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Help Subcommand ====================

    private class HelpSubCommand extends AbstractCommand {
        HelpSubCommand() {
            super("help", "Show HyperPerms help");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(buildHelpMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Group Subcommand ====================

    private static class GroupSubCommand extends HpContainerCommand {
        GroupSubCommand(HyperPerms hyperPerms) {
            super("group", "Manage groups");
            addSubCommand(new GroupListSubCommand(hyperPerms));
            addSubCommand(new GroupInfoSubCommand(hyperPerms));
            addSubCommand(new GroupCreateSubCommand(hyperPerms));
            addSubCommand(new GroupDeleteSubCommand(hyperPerms));
            addSubCommand(new GroupSetPermSubCommand(hyperPerms));
            addSubCommand(new GroupUnsetPermSubCommand(hyperPerms));
            addSubCommand(new GroupSetWeightSubCommand(hyperPerms));
            addSubCommand(new GroupSetPrefixSubCommand(hyperPerms));
            addSubCommand(new GroupSetSuffixSubCommand(hyperPerms));
            addSubCommand(new GroupSetDisplayNameSubCommand(hyperPerms));
            addSubCommand(new GroupRenameSubCommand(hyperPerms));
            addSubCommand(new GroupParentSubCommand(hyperPerms));
        }
    }

    private static class GroupListSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        GroupListSubCommand(HyperPerms hyperPerms) {
            super("list", "List all groups");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color WHITE = java.awt.Color.WHITE;

            var groups = hyperPerms.getGroupManager().getLoadedGroups();
            int width = 42;
            String label = "Groups (" + groups.size() + ")";
            int padding = width - label.length() - 2;
            int left = 3;
            int right = Math.max(3, padding - left);

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
            parts.add(Message.raw(label).color(GOLD));
            parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

            for (Group group : groups) {
                parts.add(Message.raw("  " + group.getName()).color(GREEN));
                parts.add(Message.raw(" (weight: ").color(GRAY));
                parts.add(Message.raw(String.valueOf(group.getWeight())).color(WHITE));
                parts.add(Message.raw(")\n").color(GRAY));
            }

            parts.add(Message.raw("-".repeat(width)).color(GRAY));
            ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupInfoSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> nameArg;

        GroupInfoSubCommand(HyperPerms hyperPerms) {
            super("info", "View group info");
            this.hyperPerms = hyperPerms;
            this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color WHITE = java.awt.Color.WHITE;

            String groupName = ctx.get(nameArg);
            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            int width = 42;
            String label = "Group: " + group.getName();
            int padding = width - label.length() - 2;
            int left = 3;
            int right = Math.max(3, padding - left);

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
            parts.add(Message.raw(label).color(GOLD));
            parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

            // Display name
            parts.add(Message.raw("  Display Name: ").color(GOLD));
            parts.add(Message.raw((group.getDisplayName() != null ? group.getDisplayName() : group.getName()) + "\n").color(WHITE));

            // Weight
            parts.add(Message.raw("  Weight: ").color(GOLD));
            parts.add(Message.raw(group.getWeight() + "\n").color(WHITE));

            // Prefix
            parts.add(Message.raw("  Prefix: ").color(GOLD));
            parts.add(Message.raw((group.getPrefix() != null ? "\"" + group.getPrefix() + "\"" : "(none)") + "\n").color(WHITE));

            // Suffix
            parts.add(Message.raw("  Suffix: ").color(GOLD));
            parts.add(Message.raw((group.getSuffix() != null ? "\"" + group.getSuffix() + "\"" : "(none)") + "\n").color(WHITE));

            // Priorities
            parts.add(Message.raw("  Prefix Priority: ").color(GRAY));
            parts.add(Message.raw(group.getPrefixPriority() + "\n").color(WHITE));
            parts.add(Message.raw("  Suffix Priority: ").color(GRAY));
            parts.add(Message.raw(group.getSuffixPriority() + "\n").color(WHITE));

            // Parents
            var parents = group.getParentGroups();
            parts.add(Message.raw("  Parents: ").color(GOLD));
            parts.add(Message.raw((!parents.isEmpty() ? String.join(", ", parents) : "(none)") + "\n").color(GREEN));

            // Permissions
            java.awt.Color RED = new java.awt.Color(255, 85, 85);
            long permCount = group.getNodes().stream().filter(n -> !n.isGroupNode()).count();
            parts.add(Message.raw("\n  Permissions (" + permCount + "):\n").color(GOLD));
            if (permCount == 0) {
                parts.add(Message.raw("    (none)\n").color(GRAY));
            } else {
                group.getNodes().stream()
                    .filter(n -> !n.isGroupNode())
                    .sorted(java.util.Comparator.comparing(Node::getBasePermission, String.CASE_INSENSITIVE_ORDER))
                    .forEach(node -> {
                        boolean granted = node.getValue() && !node.isNegated();
                        String prefix = granted ? "+" : "-";
                        String displayPerm = node.getBasePermission();
                        java.awt.Color permColor = granted ? GREEN : RED;
                        parts.add(Message.raw("    " + prefix + " " + displayPerm + "\n").color(permColor));
                    });
            }

            parts.add(Message.raw("-".repeat(width)).color(GRAY));
            ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupCreateSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> nameArg;

        GroupCreateSubCommand(HyperPerms hyperPerms) {
            super("create", "Create a new group");
            this.hyperPerms = hyperPerms;
            this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(nameArg);
            if (hyperPerms.getGroupManager().getGroup(groupName) != null) {
                ctx.sender().sendMessage(Message.raw("Group already exists: " + groupName));
                return CompletableFuture.completedFuture(null);
            }
            hyperPerms.getGroupManager().createGroup(groupName);
            ctx.sender().sendMessage(Message.raw("Created group: " + groupName));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupDeleteSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> nameArg;

        GroupDeleteSubCommand(HyperPerms hyperPerms) {
            super("delete", "Delete a group");
            this.hyperPerms = hyperPerms;
            this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(nameArg);
            Group group = hyperPerms.getGroupManager().getGroup(groupName);

            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            String confirmationKey = "group-delete:" + groupName.toLowerCase();

            // Check if this is a confirmation
            if (hasPendingConfirmation(confirmationKey)) {
                clearPendingConfirmation(confirmationKey);
                hyperPerms.getGroupManager().deleteGroup(groupName);
                ctx.sender().sendMessage(Message.raw("Deleted group: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            // First invocation - show warning and request confirmation
            setPendingConfirmation(confirmationKey);
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
            ctx.sender().sendMessage(Message.raw("You are about to DELETE group: " + groupName));
            ctx.sender().sendMessage(Message.raw("This will remove all permissions and settings for this group."));
            ctx.sender().sendMessage(Message.raw("Users in this group will lose inherited permissions."));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
            ctx.sender().sendMessage(Message.raw(""));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupSetPermSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> permArg;
        private final OptionalArg<String> valueArg;

        GroupSetPermSubCommand(HyperPerms hyperPerms) {
            super("setperm", "Set a permission on a group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
            this.valueArg = describeOptionalArg("value", "true or false (default: true)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String permission = ctx.get(permArg);
            String valueStr = ctx.get(valueArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            boolean value = valueStr == null || !valueStr.equalsIgnoreCase("false");

            // Normalize negation: -prefix always uses value=true
            // The - prefix already encodes "deny", value=false would double-negate
            if (permission.startsWith("-")) {
                value = true;
            }

            Node node = Node.builder(permission).value(value).build();
            group.setNode(node);
            hyperPerms.getGroupManager().saveGroup(group);

            // Invalidate caches for users in this group
            hyperPerms.getCache().invalidateAll();

            String displayPerm = node.getBasePermission();
            boolean granted = node.getValue() && !node.isNegated();
            if (granted) {
                ctx.sender().sendMessage(Message.raw("Granted " + displayPerm + " on group " + groupName));
            } else {
                ctx.sender().sendMessage(Message.raw("Denied " + displayPerm + " on group " + groupName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupUnsetPermSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> permArg;

        GroupUnsetPermSubCommand(HyperPerms hyperPerms) {
            super("unsetperm", "Remove a permission from a group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String permission = ctx.get(permArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            var result = group.removeNode(permission);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getGroupManager().saveGroup(group);
                hyperPerms.getCache().invalidateAll();
                ctx.sender().sendMessage(Message.raw("Removed " + permission + " from group " + groupName));
            } else {
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not have permission " + permission));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupSetWeightSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<Integer> weightArg;

        GroupSetWeightSubCommand(HyperPerms hyperPerms) {
            super("setweight", "Set a group's weight/priority");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.weightArg = describeArg("weight", "Weight value", ArgTypes.INTEGER);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            int weight = ctx.get(weightArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            group.setWeight(weight);
            hyperPerms.getGroupManager().saveGroup(group);
            hyperPerms.getCache().invalidateAll();

            ctx.sender().sendMessage(Message.raw("Set weight of group " + groupName + " to " + weight));
            return CompletableFuture.completedFuture(null);
        }
    }


    private static class GroupSetPrefixSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final OptionalArg<String> prefixArg;
        private final OptionalArg<Integer> priorityArg;

        GroupSetPrefixSubCommand(HyperPerms hyperPerms) {
            super("setprefix", "Set a group's chat prefix");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.prefixArg = describeOptionalArg("prefix", "Prefix text (omit to clear)", ArgTypes.STRING);
            this.priorityArg = describeOptionalArg("priority", "Priority for multi-group resolution", ArgTypes.INTEGER);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String prefix = stripQuotes(ctx.get(prefixArg));
            Integer priority = ctx.get(priorityArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            if (prefix == null || prefix.isEmpty()) {
                group.setPrefix(null);
                ctx.sender().sendMessage(Message.raw("Cleared prefix for group " + groupName));
            } else {
                group.setPrefix(prefix);
                ctx.sender().sendMessage(Message.raw("Set prefix of group " + groupName + " to \"" + prefix + "\""));
            }

            if (priority != null) {
                group.setPrefixPriority(priority);
                ctx.sender().sendMessage(Message.raw("Set prefix priority to " + priority));
            }

            hyperPerms.getGroupManager().saveGroup(group);
            hyperPerms.getCache().invalidateAll();

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupSetSuffixSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final OptionalArg<String> suffixArg;
        private final OptionalArg<Integer> priorityArg;

        GroupSetSuffixSubCommand(HyperPerms hyperPerms) {
            super("setsuffix", "Set a group's chat suffix");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.suffixArg = describeOptionalArg("suffix", "Suffix text (omit to clear)", ArgTypes.STRING);
            this.priorityArg = describeOptionalArg("priority", "Priority for multi-group resolution", ArgTypes.INTEGER);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String suffix = stripQuotes(ctx.get(suffixArg));
            Integer priority = ctx.get(priorityArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            if (suffix == null || suffix.isEmpty()) {
                group.setSuffix(null);
                ctx.sender().sendMessage(Message.raw("Cleared suffix for group " + groupName));
            } else {
                group.setSuffix(suffix);
                ctx.sender().sendMessage(Message.raw("Set suffix of group " + groupName + " to \"" + suffix + "\""));
            }

            if (priority != null) {
                group.setSuffixPriority(priority);
                ctx.sender().sendMessage(Message.raw("Set suffix priority to " + priority));
            }

            hyperPerms.getGroupManager().saveGroup(group);
            hyperPerms.getCache().invalidateAll();

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupSetDisplayNameSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final OptionalArg<String> displayNameArg;

        GroupSetDisplayNameSubCommand(HyperPerms hyperPerms) {
            super("setdisplayname", "Set a group's display name");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.displayNameArg = describeOptionalArg("displayname", "Display name (omit to clear)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String displayName = ctx.get(displayNameArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            if (displayName == null || displayName.isEmpty()) {
                group.setDisplayName(null);
                ctx.sender().sendMessage(Message.raw("Cleared display name for group " + groupName));
            } else {
                group.setDisplayName(displayName);
                ctx.sender().sendMessage(Message.raw("Set display name of group " + groupName + " to \"" + displayName + "\""));
            }

            hyperPerms.getGroupManager().saveGroup(group);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupRenameSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> oldNameArg;
        private final RequiredArg<String> newNameArg;

        GroupRenameSubCommand(HyperPerms hyperPerms) {
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

            hyperPerms.getCache().invalidateAll();
            ctx.sender().sendMessage(Message.raw("Renamed group " + oldName + " to " + newName));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupParentSubCommand extends HpContainerCommand {
        GroupParentSubCommand(HyperPerms hyperPerms) {
            super("parent", "Manage group parents (inheritance)");
            addSubCommand(new GroupParentAddSubCommand(hyperPerms));
            addSubCommand(new GroupParentRemoveSubCommand(hyperPerms));
        }
    }

    private static class GroupParentAddSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;

        GroupParentAddSubCommand(HyperPerms hyperPerms) {
            super("add", "Add a parent group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            Group parent = hyperPerms.getGroupManager().getGroup(parentName);
            if (parent == null) {
                ctx.sender().sendMessage(Message.raw("Parent group not found: " + parentName));
                return CompletableFuture.completedFuture(null);
            }

            if (groupName.equalsIgnoreCase(parentName)) {
                ctx.sender().sendMessage(Message.raw("A group cannot inherit from itself"));
                return CompletableFuture.completedFuture(null);
            }

            var result = group.addParent(parentName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getGroupManager().saveGroup(group);
                hyperPerms.getCache().invalidateAll();
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " now inherits from " + parentName));
            } else {
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " already inherits from " + parentName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class GroupParentRemoveSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;

        GroupParentRemoveSubCommand(HyperPerms hyperPerms) {
            super("remove", "Remove a parent group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            var result = group.removeParent(parentName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getGroupManager().saveGroup(group);
                hyperPerms.getCache().invalidateAll();
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " no longer inherits from " + parentName));
            } else {
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not inherit from " + parentName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== User Subcommand ====================

    private static class UserSubCommand extends HpContainerCommand {
        UserSubCommand(HyperPerms hyperPerms) {
            super("user", "Manage users");
            addSubCommand(new UserInfoSubCommand(hyperPerms));
            addSubCommand(new UserSetPermSubCommand(hyperPerms));
            addSubCommand(new UserUnsetPermSubCommand(hyperPerms));
            addSubCommand(new UserAddGroupSubCommand(hyperPerms));
            addSubCommand(new UserRemoveGroupSubCommand(hyperPerms));
            addSubCommand(new UserSetPrimaryGroupSubCommand(hyperPerms));
            addSubCommand(new UserPromoteSubCommand(hyperPerms));
            addSubCommand(new UserDemoteSubCommand(hyperPerms));
            addSubCommand(new UserSetPrefixSubCommand(hyperPerms));
            addSubCommand(new UserSetSuffixSubCommand(hyperPerms));
            addSubCommand(new UserClearSubCommand(hyperPerms));
            addSubCommand(new UserCloneSubCommand(hyperPerms));
        }
    }

    private static class UserInfoSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;

        UserInfoSubCommand(HyperPerms hyperPerms) {
            super("info", "Show user's groups and permissions");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = new java.awt.Color(255, 85, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color WHITE = java.awt.Color.WHITE;

            String identifier = ctx.get(playerArg);
            User user = resolveUser(hyperPerms, identifier);

            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                ctx.sender().sendMessage(Message.raw("Note: User must be online or have existing data"));
                return CompletableFuture.completedFuture(null);
            }

            int width = 42;
            String label = "User: " + user.getFriendlyName();
            int padding = width - label.length() - 2;
            int left = 3;
            int right = Math.max(3, padding - left);

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
            parts.add(Message.raw(label).color(GOLD));
            parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

            // UUID
            parts.add(Message.raw("  UUID: ").color(GRAY));
            parts.add(Message.raw(user.getUuid().toString() + "\n").color(WHITE));

            // Primary group
            parts.add(Message.raw("  Primary Group: ").color(GOLD));
            parts.add(Message.raw(user.getPrimaryGroup() + "\n").color(GREEN));

            // Custom prefix
            parts.add(Message.raw("  Custom Prefix: ").color(GOLD));
            parts.add(Message.raw((user.getCustomPrefix() != null ? "\"" + user.getCustomPrefix() + "\"" : "(none)") + "\n").color(WHITE));

            // Custom suffix
            parts.add(Message.raw("  Custom Suffix: ").color(GOLD));
            parts.add(Message.raw((user.getCustomSuffix() != null ? "\"" + user.getCustomSuffix() + "\"" : "(none)") + "\n").color(WHITE));

            // Groups
            var groups = user.getInheritedGroups();
            parts.add(Message.raw("  Groups: ").color(GOLD));
            parts.add(Message.raw((!groups.isEmpty() ? String.join(", ", groups) : "(none)") + "\n").color(GREEN));

            // Direct permissions
            long permCount = user.getNodes().stream().filter(n -> !n.isGroupNode()).count();
            parts.add(Message.raw("\n  Direct Permissions (" + permCount + "):\n").color(GOLD));
            if (permCount == 0) {
                parts.add(Message.raw("    (none)\n").color(GRAY));
            } else {
                user.getNodes().stream()
                    .filter(n -> !n.isGroupNode())
                    .sorted(java.util.Comparator.comparing(Node::getBasePermission, String.CASE_INSENSITIVE_ORDER))
                    .forEach(node -> {
                        boolean granted = node.getValue() && !node.isNegated();
                        String prefix = granted ? "+" : "-";
                        String displayPerm = node.getBasePermission();
                        java.awt.Color permColor = granted ? GREEN : RED;
                        parts.add(Message.raw("    " + prefix + " " + displayPerm + "\n").color(permColor));
                    });
            }

            parts.add(Message.raw("-".repeat(width)).color(GRAY));
            ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserSetPermSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> permArg;
        private final OptionalArg<String> valueArg;

        UserSetPermSubCommand(HyperPerms hyperPerms) {
            super("setperm", "Set a permission on a user");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
            this.valueArg = describeOptionalArg("value", "true or false (default: true)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String permission = ctx.get(permArg);
            String valueStr = ctx.get(valueArg);

            // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
            User user = resolveOrCreateUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
                return CompletableFuture.completedFuture(null);
            }

            boolean value = valueStr == null || !valueStr.equalsIgnoreCase("false");

            // Normalize negation: -prefix always uses value=true
            // The - prefix already encodes "deny", value=false would double-negate
            if (permission.startsWith("-")) {
                value = true;
            }

            Node node = Node.builder(permission).value(value).build();
            user.setNode(node);
            hyperPerms.getUserManager().saveUser(user).join();

            // Invalidate cache for this user
            hyperPerms.getCache().invalidate(user.getUuid());

            String displayPerm = node.getBasePermission();
            boolean granted = node.getValue() && !node.isNegated();
            if (granted) {
                ctx.sender().sendMessage(Message.raw("Granted " + displayPerm + " on user " + user.getFriendlyName()));
            } else {
                ctx.sender().sendMessage(Message.raw("Denied " + displayPerm + " on user " + user.getFriendlyName()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserUnsetPermSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> permArg;

        UserUnsetPermSubCommand(HyperPerms hyperPerms) {
            super("unsetperm", "Remove a permission from a user");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String permission = ctx.get(permArg);

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            var result = user.removeNode(permission);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getUserManager().saveUser(user).join();
                hyperPerms.getCache().invalidate(user.getUuid());
                ctx.sender().sendMessage(Message.raw("Removed " + permission + " from user " + user.getFriendlyName()));
            } else {
                ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " does not have permission " + permission));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserAddGroupSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> groupArg;

        UserAddGroupSubCommand(HyperPerms hyperPerms) {
            super("addgroup", "Add a user to a group");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String groupName = ctx.get(groupArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
            User user = resolveOrCreateUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
                return CompletableFuture.completedFuture(null);
            }

            var result = user.addGroup(groupName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getUserManager().saveUser(user).join();
                hyperPerms.getCache().invalidate(user.getUuid());
                ctx.sender().sendMessage(Message.raw("Added user " + user.getFriendlyName() + " to group " + groupName));
            } else {
                ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " is already in group " + groupName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserRemoveGroupSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> groupArg;

        UserRemoveGroupSubCommand(HyperPerms hyperPerms) {
            super("removegroup", "Remove a user from a group");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String groupName = ctx.get(groupArg);

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            var result = user.removeGroup(groupName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getUserManager().saveUser(user).join();
                hyperPerms.getCache().invalidate(user.getUuid());
                ctx.sender().sendMessage(Message.raw("Removed user " + user.getFriendlyName() + " from group " + groupName));
            } else {
                ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " is not in group " + groupName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserSetPrimaryGroupSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> groupArg;

        UserSetPrimaryGroupSubCommand(HyperPerms hyperPerms) {
            super("setprimarygroup", "Set a user's primary/display group");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String groupName = ctx.get(groupArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
            User user = resolveOrCreateUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
                return CompletableFuture.completedFuture(null);
            }

            // Ensure user is in the group
            if (!user.getInheritedGroups().contains(groupName.toLowerCase())) {
                // Add them to the group automatically
                user.addGroup(groupName);
            }

            user.setPrimaryGroup(groupName.toLowerCase());
            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            ctx.sender().sendMessage(Message.raw("Set primary group of " + user.getFriendlyName() + " to " + groupName));
            return CompletableFuture.completedFuture(null);
        }
    }


    private static class UserPromoteSubCommand extends HpCommand {
        private static final java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
        private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
        private static final java.awt.Color GRAY = java.awt.Color.GRAY;

        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> trackArg;

        UserPromoteSubCommand(HyperPerms hyperPerms) {
            super("promote", "Promote a user along a track");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.trackArg = describeArg("track", "Track name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String trackName = ctx.get(trackArg);

            // Find track
            Track track = hyperPerms.getTrackManager().getTrack(trackName);
            if (track == null) {
                ctx.sender().sendMessage(Message.raw("Track not found: " + trackName));
                return CompletableFuture.completedFuture(null);
            }

            // Check if track has groups
            if (track.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("Track '" + trackName + "' has no groups defined."));
                return CompletableFuture.completedFuture(null);
            }

            // Find user
            User user = resolveOrCreateUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
                return CompletableFuture.completedFuture(null);
            }

            // Find user's current group on this track
            String currentGroupOnTrack = null;
            for (String groupName : user.getInheritedGroups()) {
                if (track.containsGroup(groupName)) {
                    // Use the highest position group if user has multiple groups on track
                    if (currentGroupOnTrack == null || track.indexOf(groupName) > track.indexOf(currentGroupOnTrack)) {
                        currentGroupOnTrack = groupName;
                    }
                }
            }

            String targetGroup;
            String actionMessage;

            if (currentGroupOnTrack == null) {
                // User not on track - start at first group
                targetGroup = track.getFirstGroup();
                actionMessage = "Added to track at";
            } else {
                // User is on track - get next group
                String nextGroup = track.getNextGroup(currentGroupOnTrack);
                if (nextGroup == null) {
                    ctx.sender().sendMessage(
                        Message.raw(user.getFriendlyName() + " is already at the top of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
                    );
                    return CompletableFuture.completedFuture(null);
                }
                targetGroup = nextGroup;
                actionMessage = "Promoted from " + currentGroupOnTrack + " to";
            }

            // Verify target group exists
            Group group = hyperPerms.getGroupManager().getGroup(targetGroup);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Target group '" + targetGroup + "' does not exist. Please create the group first."));
                return CompletableFuture.completedFuture(null);
            }

            // Remove old group from track if present
            if (currentGroupOnTrack != null) {
                user.removeGroup(currentGroupOnTrack);
            }

            // Add new group
            user.addGroup(targetGroup);

            // Save changes
            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            ctx.sender().sendMessage(
                Message.raw(actionMessage + " ").color(GRAY)
                    .insert(Message.raw(targetGroup).color(GREEN))
                    .insert(Message.raw(" for ").color(GRAY))
                    .insert(Message.raw(user.getFriendlyName()).color(GREEN))
                    .insert(Message.raw(" on track ").color(GRAY))
                    .insert(Message.raw(trackName).color(GREEN))
            );

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserDemoteSubCommand extends HpCommand {
        private static final java.awt.Color RED = new java.awt.Color(255, 85, 85);
        private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
        private static final java.awt.Color GRAY = java.awt.Color.GRAY;

        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> trackArg;

        UserDemoteSubCommand(HyperPerms hyperPerms) {
            super("demote", "Demote a user along a track");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.trackArg = describeArg("track", "Track name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String trackName = ctx.get(trackArg);

            // Find track
            Track track = hyperPerms.getTrackManager().getTrack(trackName);
            if (track == null) {
                ctx.sender().sendMessage(Message.raw("Track not found: " + trackName));
                return CompletableFuture.completedFuture(null);
            }

            // Check if track has groups
            if (track.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("Track '" + trackName + "' has no groups defined."));
                return CompletableFuture.completedFuture(null);
            }

            // Find user
            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            // Find user's current group on this track
            String currentGroupOnTrack = null;
            for (String groupName : user.getInheritedGroups()) {
                if (track.containsGroup(groupName)) {
                    // Use the highest position group if user has multiple groups on track
                    if (currentGroupOnTrack == null || track.indexOf(groupName) > track.indexOf(currentGroupOnTrack)) {
                        currentGroupOnTrack = groupName;
                    }
                }
            }

            if (currentGroupOnTrack == null) {
                ctx.sender().sendMessage(
                    Message.raw(user.getFriendlyName() + " is not on track '" + trackName + "'.").color(RED)
                );
                return CompletableFuture.completedFuture(null);
            }

            // Get previous group
            String previousGroup = track.getPreviousGroup(currentGroupOnTrack);
            if (previousGroup == null) {
                ctx.sender().sendMessage(
                    Message.raw(user.getFriendlyName() + " is already at the bottom of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
                );
                return CompletableFuture.completedFuture(null);
            }

            // Verify target group exists
            Group group = hyperPerms.getGroupManager().getGroup(previousGroup);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Target group '" + previousGroup + "' does not exist. Please create the group first."));
                return CompletableFuture.completedFuture(null);
            }

            // Remove old group from track
            user.removeGroup(currentGroupOnTrack);

            // Add new group
            user.addGroup(previousGroup);

            // Save changes
            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            ctx.sender().sendMessage(
                Message.raw("Demoted from " + currentGroupOnTrack + " to ").color(GRAY)
                    .insert(Message.raw(previousGroup).color(RED))
                    .insert(Message.raw(" for ").color(GRAY))
                    .insert(Message.raw(user.getFriendlyName()).color(RED))
                    .insert(Message.raw(" on track ").color(GRAY))
                    .insert(Message.raw(trackName).color(RED))
            );

            return CompletableFuture.completedFuture(null);
        }
    }


    private static class UserSetPrefixSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final OptionalArg<String> prefixArg;

        UserSetPrefixSubCommand(HyperPerms hyperPerms) {
            super("setprefix", "Set a user's custom prefix");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.prefixArg = describeOptionalArg("prefix", "Prefix text (omit to clear)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String prefix = stripQuotes(ctx.get(prefixArg));

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            if (prefix == null || prefix.isEmpty()) {
                user.setCustomPrefix(null);
                ctx.sender().sendMessage(Message.raw("Cleared custom prefix for " + user.getFriendlyName()));
            } else {
                user.setCustomPrefix(prefix);
                ctx.sender().sendMessage(Message.raw("Set custom prefix of " + user.getFriendlyName() + " to \"" + prefix + "\""));
            }

            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserSetSuffixSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final OptionalArg<String> suffixArg;

        UserSetSuffixSubCommand(HyperPerms hyperPerms) {
            super("setsuffix", "Set a user's custom suffix");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.suffixArg = describeOptionalArg("suffix", "Suffix text (omit to clear)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);
            String suffix = stripQuotes(ctx.get(suffixArg));

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            if (suffix == null || suffix.isEmpty()) {
                user.setCustomSuffix(null);
                ctx.sender().sendMessage(Message.raw("Cleared custom suffix for " + user.getFriendlyName()));
            } else {
                user.setCustomSuffix(suffix);
                ctx.sender().sendMessage(Message.raw("Set custom suffix of " + user.getFriendlyName() + " to \"" + suffix + "\""));
            }

            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserClearSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;

        UserClearSubCommand(HyperPerms hyperPerms) {
            super("clear", "Clear all data for a user");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(playerArg);

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            String confirmationKey = "user-clear:" + user.getUuid();

            // Check if this is a confirmation
            if (hasPendingConfirmation(confirmationKey)) {
                clearPendingConfirmation(confirmationKey);

                // Clear all nodes (permissions and group memberships)
                user.clearNodes();
                user.setPrimaryGroup("default");

                // Clear custom prefix/suffix
                user.setCustomPrefix(null);
                user.setCustomSuffix(null);

                hyperPerms.getUserManager().saveUser(user).join();
                hyperPerms.getCache().invalidate(user.getUuid());

                ctx.sender().sendMessage(Message.raw("Cleared all data for " + user.getFriendlyName()));
                return CompletableFuture.completedFuture(null);
            }

            // First invocation - show warning and request confirmation
            setPendingConfirmation(confirmationKey);
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
            ctx.sender().sendMessage(Message.raw("You are about to CLEAR ALL DATA for user: " + user.getFriendlyName()));
            ctx.sender().sendMessage(Message.raw("This will remove:"));
            ctx.sender().sendMessage(Message.raw("  - All permissions"));
            ctx.sender().sendMessage(Message.raw("  - All group memberships (reset to 'default')"));
            ctx.sender().sendMessage(Message.raw("  - Custom prefix/suffix"));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
            ctx.sender().sendMessage(Message.raw(""));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class UserCloneSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> sourceArg;
        private final RequiredArg<String> targetArg;

        UserCloneSubCommand(HyperPerms hyperPerms) {
            super("clone", "Copy permissions from one user to another");
            this.hyperPerms = hyperPerms;
            this.sourceArg = describeArg("source", "Source player name or UUID", ArgTypes.STRING);
            this.targetArg = describeArg("target", "Target player name or UUID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String sourceId = ctx.get(sourceArg);
            String targetId = ctx.get(targetArg);

            User source = resolveUser(hyperPerms, sourceId);
            if (source == null) {
                ctx.sender().sendMessage(Message.raw("Source user not found: " + sourceId));
                return CompletableFuture.completedFuture(null);
            }

            User target = resolveUser(hyperPerms, targetId);
            if (target == null) {
                ctx.sender().sendMessage(Message.raw("Target user not found: " + targetId));
                return CompletableFuture.completedFuture(null);
            }

            // Clear target data first
            target.getNodes().clear();
            target.getInheritedGroups().clear();
            
            // Copy nodes
            for (Node node : source.getNodes()) {
                target.setNode(node);
            }
            
            // Copy groups
            for (String group : source.getInheritedGroups()) {
                target.addGroup(group);
            }
            
            // Copy primary group
            target.setPrimaryGroup(source.getPrimaryGroup());
            
            // Copy custom prefix/suffix
            target.setCustomPrefix(source.getCustomPrefix());
            target.setCustomSuffix(source.getCustomSuffix());

            hyperPerms.getUserManager().saveUser(target);
            hyperPerms.getCache().invalidate(target.getUuid());

            ctx.sender().sendMessage(Message.raw("Cloned permissions from " + source.getFriendlyName() + " to " + target.getFriendlyName()));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Check Subcommand ====================

    private static class CheckSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> permArg;

        CheckSubCommand(HyperPerms hyperPerms) {
            super("check", "Check if a player has a permission");
            this.hyperPerms = hyperPerms;
            this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission node to check", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = new java.awt.Color(255, 85, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);

            String identifier = ctx.get(playerArg);
            String permission = ctx.get(permArg);

            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("✗ User not found: " + identifier).color(RED));
                return CompletableFuture.completedFuture(null);
            }

            boolean hasPermission = hyperPerms.hasPermission(user.getUuid(), permission);

            List<Message> parts = new ArrayList<>();
            if (hasPermission) {
                parts.add(Message.raw("✓ ").color(GREEN));
                parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
                parts.add(Message.raw(" has permission ").color(GRAY));
                parts.add(Message.raw(permission).color(GREEN));
            } else {
                parts.add(Message.raw("✗ ").color(RED));
                parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
                parts.add(Message.raw(" does NOT have permission ").color(GRAY));
                parts.add(Message.raw(permission).color(RED));
            }
            ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));

            return CompletableFuture.completedFuture(null);
        }
    }


    // ==================== Backup Subcommand ====================

    private static class BackupSubCommand extends HpContainerCommand {
        BackupSubCommand(HyperPerms hyperPerms) {
            super("backup", "Manage backups");
            addSubCommand(new BackupCreateSubCommand(hyperPerms));
            addSubCommand(new BackupListSubCommand(hyperPerms));
            addSubCommand(new BackupRestoreSubCommand(hyperPerms));
        }
    }

    private static class BackupCreateSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final OptionalArg<String> nameArg;

        BackupCreateSubCommand(HyperPerms hyperPerms) {
            super("create", "Create a manual backup (use --name <prefix> for custom name)");
            this.hyperPerms = hyperPerms;
            this.nameArg = describeOptionalArg("name", "Backup name prefix (default: manual)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = java.awt.Color.RED;
            java.awt.Color GRAY = java.awt.Color.GRAY;

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            String namePrefix = ctx.get(nameArg);
            if (namePrefix == null || namePrefix.isEmpty()) {
                namePrefix = "manual";
            }

            ctx.sender().sendMessage(Message.raw("Creating backup...").color(GRAY));

            final String finalPrefix = namePrefix;
            return backupManager.createBackup(finalPrefix)
                .thenAccept(backupName -> {
                    if (backupName != null) {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("✓ Backup created: ").color(GREEN));
                        parts.add(Message.raw(backupName).color(java.awt.Color.WHITE));
                        ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    } else {
                        ctx.sender().sendMessage(Message.raw("✗ Failed to create backup").color(RED));
                    }
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✗ Error creating backup: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    return null;
                });
        }
    }

    private static class BackupListSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        BackupListSubCommand(HyperPerms hyperPerms) {
            super("list", "List available backups");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = java.awt.Color.RED;
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color WHITE = java.awt.Color.WHITE;

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            return backupManager.listBackups()
                .thenAccept(backups -> {
                    if (backups.isEmpty()) {
                        ctx.sender().sendMessage(Message.raw("No backups found").color(GRAY));
                        return;
                    }

                    ctx.sender().sendMessage(Message.raw(""));

                    List<Message> header = new ArrayList<>();
                    header.add(Message.raw("--- Backups (").color(GRAY));
                    header.add(Message.raw(String.valueOf(backups.size())).color(GOLD));
                    header.add(Message.raw(") ---").color(GRAY));
                    ctx.sender().sendMessage(Message.join(header.toArray(new Message[0])));

                    for (String backup : backups) {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("  • ").color(GREEN));
                        parts.add(Message.raw(backup).color(WHITE));
                        ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    }
                    ctx.sender().sendMessage(Message.raw(""));
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✗ Error listing backups: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    return null;
                });
        }
    }

    private static class BackupRestoreSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> backupArg;

        BackupRestoreSubCommand(HyperPerms hyperPerms) {
            super("restore", "Restore from a backup");
            this.hyperPerms = hyperPerms;
            this.backupArg = describeArg("backup", "Backup file name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = java.awt.Color.RED;
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color GOLD = new java.awt.Color(255, 170, 0);

            String backupName = ctx.get(backupArg);

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            String confirmationKey = "backup-restore:" + backupName;

            // Check if this is a confirmation
            if (hasPendingConfirmation(confirmationKey)) {
                clearPendingConfirmation(confirmationKey);

                List<Message> restoring = new ArrayList<>();
                restoring.add(Message.raw("Restoring from backup: ").color(GRAY));
                restoring.add(Message.raw(backupName).color(GOLD));
                ctx.sender().sendMessage(Message.join(restoring.toArray(new Message[0])));

                return backupManager.restoreBackup(backupName)
                    .thenAccept(success -> {
                        if (success) {
                            ctx.sender().sendMessage(Message.raw("✓ Backup restored successfully!").color(GREEN));

                            List<Message> reload = new ArrayList<>();
                            reload.add(Message.raw("Please run ").color(GRAY));
                            reload.add(Message.raw("/hp reload").color(GOLD));
                            reload.add(Message.raw(" to apply changes").color(GRAY));
                            ctx.sender().sendMessage(Message.join(reload.toArray(new Message[0])));
                        } else {
                            ctx.sender().sendMessage(Message.raw("✗ Failed to restore backup").color(RED));
                        }
                    })
                    .exceptionally(e -> {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("✗ Error restoring backup: ").color(RED));
                        parts.add(Message.raw(e.getMessage()).color(GRAY));
                        ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                        return null;
                    });
            }

            // First invocation - show warning and request confirmation
            setPendingConfirmation(confirmationKey);
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
            ctx.sender().sendMessage(Message.raw("You are about to RESTORE from backup: " + backupName));
            ctx.sender().sendMessage(Message.raw("This will OVERWRITE all current data including:"));
            ctx.sender().sendMessage(Message.raw("  - All users and their permissions"));
            ctx.sender().sendMessage(Message.raw("  - All groups and their settings"));
            ctx.sender().sendMessage(Message.raw("  - All tracks"));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Current data will be LOST unless you have a backup."));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
            ctx.sender().sendMessage(Message.raw(""));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Export/Import Subcommands ====================

    private static class ExportSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final OptionalArg<String> filenameArg;

        ExportSubCommand(HyperPerms hyperPerms) {
            super("export", "Export all data to a file (use --filename <name> for custom name)");
            this.hyperPerms = hyperPerms;
            this.filenameArg = describeOptionalArg("filename", "Export file name prefix (default: auto-generated)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color RED = java.awt.Color.RED;
            java.awt.Color GRAY = java.awt.Color.GRAY;
            java.awt.Color WHITE = java.awt.Color.WHITE;

            String filenamePrefix = ctx.get(filenameArg);
            if (filenamePrefix == null || filenamePrefix.isEmpty()) {
                filenamePrefix = "export";
            }

            ctx.sender().sendMessage(Message.raw("Creating export...").color(GRAY));

            // Export is essentially a backup with a custom name
            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup/export not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            final String finalPrefix = filenamePrefix;
            return backupManager.createBackup(finalPrefix)
                .thenAccept(backupName -> {
                    if (backupName != null) {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("✓ Data exported to: ").color(GREEN));
                        parts.add(Message.raw(backupName).color(WHITE));
                        ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    } else {
                        ctx.sender().sendMessage(Message.raw("✗ Failed to export data").color(RED));
                    }
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✗ Error exporting: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
                    return null;
                });
        }
    }

    private static class ImportSubCommand extends HpContainerCommand {
        ImportSubCommand(HyperPerms hyperPerms) {
            super("import", "Import data from file or create defaults");
            addSubCommand(new ImportDefaultsSubCommand(hyperPerms));
            addSubCommand(new ImportFileSubCommand(hyperPerms));
        }
    }

    private static class ImportDefaultsSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ImportDefaultsSubCommand(HyperPerms hyperPerms) {
            super("defaults", "Create default group hierarchy");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(Message.raw("Creating default groups..."));

            var groupManager = hyperPerms.getGroupManager();

            // Check if groups already exist
            int existingCount = 0;
            String[] defaultGroups = {"default", "member", "builder", "moderator", "admin", "owner"};
            for (String name : defaultGroups) {
                if (groupManager.getGroup(name) != null) {
                    existingCount++;
                }
            }

            if (existingCount > 0) {
                ctx.sender().sendMessage(Message.raw("WARNING: " + existingCount + " default groups already exist."));
                ctx.sender().sendMessage(Message.raw("Existing groups will be updated, not replaced."));
            }

            // Create or update default group
            Group defaultGroup = getOrCreateGroup("default");
            defaultGroup.setWeight(0);
            defaultGroup.setDisplayName("Default");
            defaultGroup.setNode(Node.builder("hyperperms.command.check.self").value(true).build());
            groupManager.saveGroup(defaultGroup);

            // Create member group
            Group memberGroup = getOrCreateGroup("member");
            memberGroup.setWeight(10);
            memberGroup.setDisplayName("Member");
            memberGroup.addParent("default");
            groupManager.saveGroup(memberGroup);

            // Create builder group
            Group builderGroup = getOrCreateGroup("builder");
            builderGroup.setWeight(20);
            builderGroup.setDisplayName("Builder");
            builderGroup.setPrefix("&2[Builder] ");
            builderGroup.addParent("member");
            builderGroup.setNode(Node.builder("hytale.editor.builderTools").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.brush.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.selection.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.prefab.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.history").value(true).build());
            builderGroup.setNode(Node.builder("hytale.camera.flycam").value(true).build());
            groupManager.saveGroup(builderGroup);

            // Create moderator group
            Group modGroup = getOrCreateGroup("moderator");
            modGroup.setWeight(50);
            modGroup.setDisplayName("Moderator");
            modGroup.setPrefix("&9[Mod] ");
            modGroup.addParent("builder");
            modGroup.setNode(Node.builder("hyperperms.command.user.info").value(true).build());
            modGroup.setNode(Node.builder("hyperperms.command.check.others").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.kick").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.ban").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.unban").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.tp.self").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.tp.others").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.inventory.see").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.who").value(true).build());
            groupManager.saveGroup(modGroup);

            // Create admin group
            Group adminGroup = getOrCreateGroup("admin");
            adminGroup.setWeight(90);
            adminGroup.setDisplayName("Admin");
            adminGroup.setPrefix("&c[Admin] ");
            adminGroup.addParent("moderator");
            adminGroup.setNode(Node.builder("hyperperms.command.*").value(true).build());
            adminGroup.setNode(Node.builder("hytale.command.*").value(true).build());
            adminGroup.setNode(Node.builder("hytale.editor.*").value(true).build());
            groupManager.saveGroup(adminGroup);

            // Create owner group
            Group ownerGroup = getOrCreateGroup("owner");
            ownerGroup.setWeight(100);
            ownerGroup.setDisplayName("Owner");
            ownerGroup.setPrefix("&4[Owner] ");
            ownerGroup.setNode(Node.builder("*").value(true).build());
            groupManager.saveGroup(ownerGroup);

            // Invalidate all caches
            hyperPerms.getCache().invalidateAll();

            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Default groups created:"));
            ctx.sender().sendMessage(Message.raw("  default (weight 0) - Base permissions"));
            ctx.sender().sendMessage(Message.raw("  member (weight 10) - Trusted players"));
            ctx.sender().sendMessage(Message.raw("  builder (weight 20) - Building/editor tools"));
            ctx.sender().sendMessage(Message.raw("  moderator (weight 50) - Player management"));
            ctx.sender().sendMessage(Message.raw("  admin (weight 90) - Full command access"));
            ctx.sender().sendMessage(Message.raw("  owner (weight 100) - Full server access (*)"));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Use /hp user <player> addgroup <group> to assign players"));

            return CompletableFuture.completedFuture(null);
        }

        private Group getOrCreateGroup(String name) {
            var groupManager = hyperPerms.getGroupManager();
            Group group = groupManager.getGroup(name);
            if (group == null) {
                groupManager.createGroup(name);
                group = groupManager.getGroup(name);
            }
            return group;
        }
    }

    private static class ImportFileSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> filenameArg;

        ImportFileSubCommand(HyperPerms hyperPerms) {
            super("file", "Import data from a backup file");
            this.hyperPerms = hyperPerms;
            this.filenameArg = describeArg("filename", "Import file name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String filename = ctx.get(filenameArg);

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("Backup/import not available"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("Importing data from: " + filename));
            ctx.sender().sendMessage(Message.raw("WARNING: This will merge with or overwrite current data!"));

            return backupManager.restoreBackup(filename)
                .thenAccept(success -> {
                    if (success) {
                        ctx.sender().sendMessage(Message.raw("Data imported successfully!"));
                        ctx.sender().sendMessage(Message.raw("Please run /hp reload to apply changes"));
                    } else {
                        ctx.sender().sendMessage(Message.raw("Failed to import data. Check if file exists."));
                    }
                })
                .exceptionally(e -> {
                    ctx.sender().sendMessage(Message.raw("Error importing: " + e.getMessage()));
                    return null;
                });
        }
    }

    // ==================== Reload Subcommand ====================

    private static class ReloadSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ReloadSubCommand(HyperPerms hyperPerms) {
            super("reload", "Reload HyperPerms configuration");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;

            ctx.sender().sendMessage(Message.raw("Reloading HyperPerms...").color(GRAY));
            hyperPerms.reload();
            ctx.sender().sendMessage(Message.raw("✓ HyperPerms reloaded successfully!").color(GREEN));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class ResetGroupsSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ResetGroupsSubCommand(HyperPerms hyperPerms) {
            super("resetgroups", "Reset all groups to default");
            this.hyperPerms = hyperPerms;
            addSubCommand(new ResetGroupsConfirmSubCommand(hyperPerms));
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
            ctx.sender().sendMessage(Message.raw("This will DELETE all existing groups and replace them with defaults."));
            ctx.sender().sendMessage(Message.raw("User group memberships will be preserved, but custom group settings will be lost."));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("To confirm, run: /hp resetgroups confirm"));
            ctx.sender().sendMessage(Message.raw(""));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class ResetGroupsConfirmSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ResetGroupsConfirmSubCommand(HyperPerms hyperPerms) {
            super("confirm", "Confirm resetting groups to default");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            java.awt.Color RED = java.awt.Color.RED;
            java.awt.Color GREEN = new java.awt.Color(85, 255, 85);
            java.awt.Color GRAY = java.awt.Color.GRAY;

            ctx.sender().sendMessage(Message.raw("Resetting groups to defaults...").color(GRAY));

            try (var inputStream = getClass().getClassLoader().getResourceAsStream("default-groups.json")) {
                if (inputStream == null) {
                    ctx.sender().sendMessage(Message.raw("✗ Error: default-groups.json not found in plugin resources").color(RED));
                    return CompletableFuture.completedFuture(null);
                }

                String json = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                com.google.gson.JsonObject groups = root.getAsJsonObject("groups");

                if (groups == null) {
                    ctx.sender().sendMessage(Message.raw("✗ Error: No 'groups' object found in default-groups.json").color(RED));
                    return CompletableFuture.completedFuture(null);
                }

                int updated = 0;
                for (var entry : groups.entrySet()) {
                    String groupName = entry.getKey();
                    com.google.gson.JsonObject groupData = entry.getValue().getAsJsonObject();

                    // Get or create the group
                    Group group = hyperPerms.getGroupManager().getGroup(groupName);
                    if (group == null) {
                        group = hyperPerms.getGroupManager().createGroup(groupName);
                    } else {
                        // Clear existing permissions and parents (both stored as nodes)
                        group.clearNodes();
                    }

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

                    // Add parent groups
                    if (groupData.has("parents")) {
                        for (var parent : groupData.getAsJsonArray("parents")) {
                            group.addParent(parent.getAsString());
                        }
                    }

                    // Save the group
                    hyperPerms.getGroupManager().saveGroup(group).join();
                    updated++;
                }

                // Invalidate all caches
                hyperPerms.getCacheInvalidator().invalidateAll();

                ctx.sender().sendMessage(Message.raw(""));

                List<Message> success = new ArrayList<>();
                success.add(Message.raw("✓ Success! Reset ").color(GREEN));
                success.add(Message.raw(String.valueOf(updated)).color(java.awt.Color.WHITE));
                success.add(Message.raw(" groups to defaults.").color(GREEN));
                ctx.sender().sendMessage(Message.join(success.toArray(new Message[0])));

                ctx.sender().sendMessage(Message.raw("All permission caches have been invalidated.").color(GRAY));
                ctx.sender().sendMessage(Message.raw(""));

            } catch (Exception e) {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("✗ Error resetting groups: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            }

            return CompletableFuture.completedFuture(null);
        }
    }


    // ==================== Debug Subcommands ====================

    private static class DebugSubCommand extends HpContainerCommand {
        DebugSubCommand(HyperPerms hyperPerms) {
            super("debug", "Debug commands for troubleshooting");
            addSubCommand(new DebugTreeSubCommand(hyperPerms));
            addSubCommand(new DebugResolveSubCommand(hyperPerms));
            addSubCommand(new DebugContextsSubCommand(hyperPerms));
            addSubCommand(new DebugPermsSubCommand());
        }
    }

    private static class DebugPermsSubCommand extends AbstractCommand {
        DebugPermsSubCommand() {
            super("perms", "Toggle verbose permission check logging");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean currentState = com.hyperperms.util.Logger.isPermissionDebugEnabled();
            com.hyperperms.util.Logger.setPermissionDebugEnabled(!currentState);

            if (!currentState) {
                ctx.sender().sendMessage(Message.raw("§aPermission debug logging ENABLED"));
                ctx.sender().sendMessage(Message.raw("§7All permission checks will now be logged to console with detailed info."));
                ctx.sender().sendMessage(Message.raw("§7This helps debug issues between plugins like HyperHomes."));
                ctx.sender().sendMessage(Message.raw("§7Run §e/hp debug perms§7 again to disable."));
            } else {
                ctx.sender().sendMessage(Message.raw("§cPermission debug logging DISABLED"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DebugTreeSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> userArg;

        DebugTreeSubCommand(HyperPerms hyperPerms) {
            super("tree", "Show inheritance tree for a user");
            this.hyperPerms = hyperPerms;
            this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(userArg);
            User user = resolveUser(hyperPerms, identifier);
            
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("=== Inheritance Tree for " + user.getFriendlyName() + " ==="));
            
            // Direct permissions
            ctx.sender().sendMessage(Message.raw("Direct Permissions:"));
            var directPerms = user.getNodes();
            if (directPerms.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("  (none)"));
            } else {
                for (var node : directPerms) {
                    boolean granted = node.getValue() && !node.isNegated();
                    String prefix = granted ? "  + " : "  - ";
                    ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
                }
            }

            // Groups
            ctx.sender().sendMessage(Message.raw("Groups:"));
            var groups = user.getInheritedGroups();
            if (groups.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("  (none)"));
            } else {
                for (String groupName : groups) {
                    printGroupTree(ctx, hyperPerms, groupName, "  ", new java.util.HashSet<>());
                }
            }

            return CompletableFuture.completedFuture(null);
        }

        private void printGroupTree(CommandContext ctx, HyperPerms hyperPerms, String groupName, 
                                    String indent, java.util.Set<String> visited) {
            if (visited.contains(groupName)) {
                ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (circular ref)"));
                return;
            }
            visited.add(groupName);

            var group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (not found)"));
                return;
            }

            ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (weight=" + group.getWeight() + ")"));
            
            // Show permissions
            for (var node : group.getNodes()) {
                boolean granted = node.getValue() && !node.isNegated();
                String prefix = granted ? indent + "  + " : indent + "  - ";
                ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
            }

            // Show parent groups
            for (String parent : group.getParentGroups()) {
                printGroupTree(ctx, hyperPerms, parent, indent + "  ", visited);
            }
        }

        private String formatContext(com.hyperperms.model.Node node) {
            var contexts = node.getContexts();
            if (contexts == null || contexts.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(" [");
            boolean first = true;
            for (var ctx : contexts.toSet()) {
                if (!first) sb.append(", ");
                sb.append(ctx.key()).append("=").append(ctx.value());
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
    }

    private static class DebugResolveSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> userArg;
        private final RequiredArg<String> permArg;

        DebugResolveSubCommand(HyperPerms hyperPerms) {
            super("resolve", "Debug permission resolution step-by-step");
            this.hyperPerms = hyperPerms;
            this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
            this.permArg = describeArg("permission", "Permission to resolve", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(userArg);
            String permission = ctx.get(permArg);
            
            User user = resolveUser(hyperPerms, identifier);
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("=== Resolving: " + permission + " for " + user.getFriendlyName() + " ==="));

            // Get current contexts
            var contexts = hyperPerms.getContexts(user.getUuid());
            ctx.sender().sendMessage(Message.raw("Current contexts: " + contexts));

            // Enable verbose mode temporarily to capture the trace
            boolean wasVerbose = hyperPerms.isVerboseMode();
            hyperPerms.setVerboseMode(true);

            boolean result = hyperPerms.hasPermission(user.getUuid(), permission, contexts);
            
            hyperPerms.setVerboseMode(wasVerbose);

            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Final result: " + (result ? "ALLOWED" : "DENIED")));
            ctx.sender().sendMessage(Message.raw("(Check console for detailed trace if verbose logging is enabled)"));

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DebugContextsSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> userArg;

        DebugContextsSubCommand(HyperPerms hyperPerms) {
            super("contexts", "Show all current contexts for a user");
            this.hyperPerms = hyperPerms;
            this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String identifier = ctx.get(userArg);
            User user = resolveUser(hyperPerms, identifier);
            
            if (user == null) {
                ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
                return CompletableFuture.completedFuture(null);
            }

            var contexts = hyperPerms.getContexts(user.getUuid());
            
            ctx.sender().sendMessage(Message.raw("=== Contexts for " + user.getFriendlyName() + " ==="));
            
            if (contexts.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("  (no contexts)"));
            } else {
                for (var context : contexts.toSet()) {
                    ctx.sender().sendMessage(Message.raw("  " + context.key() + " = " + context.value()));
                }
            }

            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Registered calculators: " + hyperPerms.getContextManager().getCalculatorCount()));

            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Permission List Subcommands ====================

    private static class PermsSubCommand extends HpContainerCommand {
        PermsSubCommand(HyperPerms hyperPerms) {
            super("perms", "Permission listing and search");
            addSubCommand(new PermsListSubCommand(hyperPerms));
            addSubCommand(new PermsSearchSubCommand(hyperPerms));
        }
    }

    private static class PermsListSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final OptionalArg<String> categoryArg;

        PermsListSubCommand(HyperPerms hyperPerms) {
            super("list", "List registered permissions");
            this.hyperPerms = hyperPerms;
            this.categoryArg = describeOptionalArg("category", "Filter by category", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            var registry = hyperPerms.getPermissionRegistry();
            String category = ctx.get(categoryArg);

            if (category != null) {
                // List by category
                var perms = registry.getByCategory(category);
                if (perms.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("No permissions found in category: " + category));
                    ctx.sender().sendMessage(Message.raw("Available categories: " + String.join(", ", registry.getCategories())));
                    return CompletableFuture.completedFuture(null);
                }

                ctx.sender().sendMessage(Message.raw("=== Permissions in category '" + category + "' (" + perms.size() + ") ==="));
                for (var perm : perms) {
                    ctx.sender().sendMessage(Message.raw("  " + perm.getPermission()));
                    ctx.sender().sendMessage(Message.raw("    " + perm.getDescription()));
                }
            } else {
                // List categories
                var categories = registry.getCategories();
                ctx.sender().sendMessage(Message.raw("=== Permission Categories (" + registry.size() + " total permissions) ==="));
                for (String cat : categories) {
                    int count = registry.getByCategory(cat).size();
                    ctx.sender().sendMessage(Message.raw("  " + cat + " (" + count + " permissions)"));
                }
                ctx.sender().sendMessage(Message.raw(""));
                ctx.sender().sendMessage(Message.raw("Use /hp perms list <category> to view permissions in a category"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    private static class PermsSearchSubCommand extends HpCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> queryArg;

        PermsSearchSubCommand(HyperPerms hyperPerms) {
            super("search", "Search for permissions by name or description");
            this.hyperPerms = hyperPerms;
            this.queryArg = describeArg("query", "Search query", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String query = ctx.get(queryArg);
            var registry = hyperPerms.getPermissionRegistry();

            var results = registry.search(query);
            
            if (results.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("No permissions found matching: " + query));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("=== Search results for '" + query + "' (" + results.size() + " found) ==="));
            
            int shown = 0;
            for (var perm : results) {
                if (shown >= 20) {
                    ctx.sender().sendMessage(Message.raw("... and " + (results.size() - 20) + " more"));
                    break;
                }
                ctx.sender().sendMessage(Message.raw("  " + perm.getPermission() + " [" + perm.getCategory() + "]"));
                ctx.sender().sendMessage(Message.raw("    " + perm.getDescription()));
                shown++;
            }

            return CompletableFuture.completedFuture(null);
        }
    }
}
