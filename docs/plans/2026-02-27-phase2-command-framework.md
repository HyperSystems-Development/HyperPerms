# Phase 2: Annotation-Driven Command Framework — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace 48 individual command classes with an annotation-driven framework where commands are methods grouped by domain, eliminating boilerplate and reducing file count by ~70%.

**Architecture:** Annotations (`@CommandGroup`, `@Command`, `@Arg`, `@Permission`, `@Confirm`) define commands declaratively. A `CommandScanner` discovers annotated classes at startup and builds `AbstractCommand` wrappers that integrate with Hytale's command system. A `CommandDispatcher` handles argument extraction, permission checks, and confirmation tracking.

**Tech Stack:** Java 25, annotations, reflection (startup-only scanning)

**Worktree:** `.worktrees/architecture-rehaul` on branch `refactor/architecture-rehaul`

**Git Author:** `ZenithDevHQ <scrubc1ty4ever@gmail.com>`

**Base path:** `src/main/java/com/hyperperms/command/`

**Depends on:** Phase 1 (ServiceContainer access for dependency resolution in command groups)

---

### Task 1: Create Command Annotations

**Files:**
- Create: `src/main/java/com/hyperperms/command/annotation/CommandGroup.java`
- Create: `src/main/java/com/hyperperms/command/annotation/Command.java`
- Create: `src/main/java/com/hyperperms/command/annotation/Permission.java`
- Create: `src/main/java/com/hyperperms/command/annotation/Arg.java`
- Create: `src/main/java/com/hyperperms/command/annotation/OptionalArg.java`
- Create: `src/main/java/com/hyperperms/command/annotation/Confirm.java`

**Step 1: Create all annotation files**

```java
// CommandGroup.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a command group. All {@link Command} methods
 * in this class become subcommands under the group name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommandGroup {
    /** Subcommand name (e.g. "group" for /hp group). */
    String name();
    /** Description shown in help. */
    String description() default "";
}
```

```java
// Command.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a subcommand within a {@link CommandGroup}.
 * Method must return {@code CompletableFuture<Void>} and accept
 * {@code CommandContext} as the first parameter.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    /** Subcommand name (e.g. "create" for /hp group create). */
    String name();
    /** Description shown in help. */
    String description() default "";
    /** Aliases for this subcommand. */
    String[] aliases() default {};
}
```

```java
// Permission.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Permission required to execute a {@link Command}.
 * The sender is checked before the method is invoked.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Permission {
    /** The permission node (e.g. "hyperperms.group.create"). */
    String value();
}
```

```java
// Arg.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Declares a required argument on a {@link Command} method parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Arg {
    /** Argument name shown in usage. */
    String name();
    /** Description shown in help. */
    String description() default "";
}
```

```java
// OptionalArg.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Declares an optional argument on a {@link Command} method parameter.
 * The parameter value will be {@code null} if not provided.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptionalArg {
    /** Argument name shown in usage. */
    String name();
    /** Description shown in help. */
    String description() default "";
}
```

```java
// Confirm.java
package com.hyperperms.command.annotation;

import java.lang.annotation.*;

/**
 * Requires double-invocation confirmation before executing.
 * First call shows the warning message; second call within
 * the timeout actually executes the command.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Confirm {
    /** Warning message shown on first invocation. */
    String message() default "Are you sure? Run the command again to confirm.";
    /** Confirmation timeout in seconds. */
    int timeoutSeconds() default 60;
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/command/annotation/
git commit -m "feat(commands): add annotation types for declarative command framework"
```

---

### Task 2: Create CommandScanner and CommandDispatcher

**Files:**
- Create: `src/main/java/com/hyperperms/command/CommandScanner.java`
- Create: `src/main/java/com/hyperperms/command/CommandDispatcher.java`

**Step 1: Create CommandDispatcher**

The dispatcher handles confirmation tracking, permission checking, and argument extraction for annotated methods.

```java
package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.util.CommandUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles permission checks, confirmation tracking, and argument extraction
 * for annotated command methods.
 */
public final class CommandDispatcher {

    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();

    private CommandDispatcher() {}

    /**
     * Check if the sender has the required permission for this method.
     * Returns true if allowed, false if denied (and sends error message).
     */
    public static boolean checkPermission(@NotNull CommandContext ctx, @NotNull HyperPerms hp,
                                           @Nullable Permission permission) {
        if (permission == null) return true;
        return !CommandUtil.requirePermission(ctx, hp, permission.value());
    }

    /**
     * Handle confirmation flow. Returns true if the command should execute,
     * false if this was the first invocation (warning shown).
     */
    public static boolean handleConfirmation(@NotNull CommandContext ctx, @NotNull String commandKey,
                                              @NotNull Confirm confirm) {
        if (hasPendingConfirmation(commandKey)) {
            clearPendingConfirmation(commandKey);
            return true;
        }

        setPendingConfirmation(commandKey);
        ctx.sender().sendMessage(CommandUtil.msg(confirm.message(), CommandUtil.YELLOW));
        ctx.sender().sendMessage(CommandUtil.msg(
                "Run the command again within " + confirm.timeoutSeconds() + " seconds to confirm.",
                CommandUtil.GRAY));
        return false;
    }

    private static boolean hasPendingConfirmation(String key) {
        Long timestamp = pendingConfirmations.get(key);
        if (timestamp == null) return false;
        if (System.currentTimeMillis() - timestamp > 60_000) {
            pendingConfirmations.remove(key);
            return false;
        }
        return true;
    }

    private static void setPendingConfirmation(String key) {
        pendingConfirmations.put(key, System.currentTimeMillis());
    }

    private static void clearPendingConfirmation(String key) {
        pendingConfirmations.remove(key);
    }
}
```

**Step 2: Create CommandScanner**

The scanner discovers `@CommandGroup` classes, creates `HpContainerCommand` wrappers for each group, and creates `HpSubCommand` wrappers for each `@Command` method.

```java
package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.command.util.CommandUtil;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Discovers {@link CommandGroup} classes and builds the command tree
 * by wrapping annotated methods as {@link HpSubCommand} instances
 * within {@link HpContainerCommand} containers.
 */
public final class CommandScanner {

    private final HyperPerms plugin;

    public CommandScanner(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    /**
     * Scan a command group class and return a container command
     * with all annotated methods registered as subcommands.
     */
    @NotNull
    public HpContainerCommand scan(@NotNull Object commandGroupInstance) {
        Class<?> clazz = commandGroupInstance.getClass();
        CommandGroup group = clazz.getAnnotation(CommandGroup.class);
        if (group == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not annotated with @CommandGroup");
        }

        HpContainerCommand container = new HpContainerCommand(group.name(), group.description()) {};

        for (Method method : clazz.getDeclaredMethods()) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) continue;

            validateCommandMethod(method);

            Permission permission = method.getAnnotation(Permission.class);
            Confirm confirm = method.getAnnotation(Confirm.class);

            // Build argument descriptors from method parameters
            AbstractCommand subCommand = buildSubCommand(
                    commandGroupInstance, method, cmd, permission, confirm);

            container.addSubCommand(subCommand);

            // Register aliases
            for (String alias : cmd.aliases()) {
                // The framework handles aliases through addAliases on the command
            }

            Logger.debug("[CommandScanner] Registered: /%s %s %s",
                    group.name(), cmd.name(),
                    permission != null ? "(requires " + permission.value() + ")" : "");
        }

        return container;
    }

    private void validateCommandMethod(Method method) {
        if (!CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException(
                    "Command method " + method.getName() + " must return CompletableFuture<Void>");
        }

        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            throw new IllegalArgumentException(
                    "Command method " + method.getName() + " must have CommandContext as first parameter");
        }
    }

    @SuppressWarnings("unchecked")
    private AbstractCommand buildSubCommand(Object instance, Method method,
                                             Command cmd, Permission permission, Confirm confirm) {
        method.setAccessible(true);

        return new HpSubCommand(cmd.name(), cmd.description()) {
            // Arguments are registered in the constructor block
            {
                Parameter[] params = method.getParameters();
                for (int i = 1; i < params.length; i++) { // skip CommandContext
                    Parameter param = params[i];
                    Arg arg = param.getAnnotation(Arg.class);
                    OptionalArg optArg = param.getAnnotation(OptionalArg.class);

                    if (arg != null) {
                        describeArg(arg.name(), arg.description(), ArgTypes.STRING);
                    } else if (optArg != null) {
                        describeOptionalArg(optArg.name(), optArg.description(), ArgTypes.STRING);
                    }
                }
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                // Permission check
                if (!CommandDispatcher.checkPermission(ctx, plugin, permission)) {
                    return CompletableFuture.completedFuture(null);
                }

                // Confirmation check
                if (confirm != null) {
                    String confirmKey = cmd.name() + ":" + ctx.sender().getName();
                    if (!CommandDispatcher.handleConfirmation(ctx, confirmKey, confirm)) {
                        return CompletableFuture.completedFuture(null);
                    }
                }

                // Build argument array
                Parameter[] params = method.getParameters();
                Object[] args = new Object[params.length];
                args[0] = ctx;

                // Extract string arguments from remaining params
                // The HpSubCommand framework handles positional arg extraction
                // We map them by position after CommandContext
                int argIndex = 0;
                for (int i = 1; i < params.length; i++) {
                    Parameter param = params[i];
                    Arg argAnnotation = param.getAnnotation(Arg.class);
                    OptionalArg optAnnotation = param.getAnnotation(OptionalArg.class);

                    if (argAnnotation != null || optAnnotation != null) {
                        // Use the registered arg descriptors to extract values
                        var descriptors = getArgDescriptors();
                        if (argIndex < descriptors.size()) {
                            args[i] = ctx.get(descriptors.get(argIndex).arg());
                            argIndex++;
                        }
                    }
                }

                try {
                    return (CompletableFuture<Void>) method.invoke(instance, args);
                } catch (Exception e) {
                    Logger.warn("Error executing command %s: %s", cmd.name(), e.getMessage());
                    ctx.sender().sendMessage(CommandUtil.error("An error occurred. Check console."));
                    return CompletableFuture.completedFuture(null);
                }
            }
        };
    }
}
```

**Important implementation note:** The `buildSubCommand` method above is approximate. The implementer must verify how `HpSubCommand` exposes its arg descriptors (the `getArgDescriptors()` method may need to be added or accessed differently). Read `HpSubCommand.java` and adapt accordingly. The key contract is: arguments declared via annotations map 1:1 to positional args extracted from `CommandContext`.

**Step 3: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (may need adjustments based on HpSubCommand internals)

**Step 4: Commit**

```bash
git add src/main/java/com/hyperperms/command/CommandScanner.java src/main/java/com/hyperperms/command/CommandDispatcher.java
git commit -m "feat(commands): add CommandScanner and CommandDispatcher for annotation framework"
```

---

### Task 3: Create GroupCommands — first annotated command group

Convert the 13 group command classes into a single annotated class. Start with the 3 simplest commands (create, delete, list) to validate the pattern.

**Files:**
- Create: `src/main/java/com/hyperperms/command/groups/GroupCommands.java`

**Step 1: Create GroupCommands with create, delete, and list**

```java
package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.util.CommandUtil;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.util.PlayerResolver;
import com.hyperperms.util.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp group subcommands.
 */
@CommandGroup(name = "group", description = "Manage groups")
public class GroupCommands {

    private final HyperPerms plugin;

    public GroupCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    @Command(name = "create", description = "Create a new group")
    @Permission("hyperperms.group.create")
    public CompletableFuture<Void> create(
            CommandContext ctx,
            @Arg(name = "name", description = "Group name") String name) {

        if (plugin.getGroupManager().getGroup(name) != null) {
            ctx.sender().sendMessage(error("Group already exists: " + name));
            return CompletableFuture.completedFuture(null);
        }

        plugin.getGroupManager().createGroup(name);
        ctx.sender().sendMessage(success("Created group: " + name));
        return CompletableFuture.completedFuture(null);
    }

    @Command(name = "delete", description = "Delete a group")
    @Permission("hyperperms.group.delete")
    @Confirm(message = "This will permanently delete the group and remove all users from it.")
    public CompletableFuture<Void> delete(
            CommandContext ctx,
            @Arg(name = "name", description = "Group name") String name) {

        Group group = plugin.getGroupManager().getGroup(name);
        if (group == null) {
            ctx.sender().sendMessage(error("Group not found: " + name));
            return CompletableFuture.completedFuture(null);
        }

        plugin.getGroupManager().deleteGroup(name);
        plugin.getCache().invalidateAll();
        ctx.sender().sendMessage(success("Deleted group: " + name));
        return CompletableFuture.completedFuture(null);
    }

    @Command(name = "list", description = "List all groups")
    @Permission("hyperperms.group.list")
    public CompletableFuture<Void> list(CommandContext ctx) {
        Collection<Group> groups = plugin.getGroupManager().getLoadedGroups().values();

        ctx.sender().sendMessage(header("Groups (" + groups.size() + ")"));
        for (Group group : groups) {
            ctx.sender().sendMessage(listItem(group.getName(),
                    "weight=" + group.getWeight() + ", perms=" + group.getNodes().size()));
        }
        ctx.sender().sendMessage(footer());
        return CompletableFuture.completedFuture(null);
    }

    // --- Continue with remaining group commands ---
    // The implementer should port all 13 group commands here:
    // info, setperm, unsetperm, setexpiry, setweight, setprefix,
    // setsuffix, setdisplayname, rename, parent
    //
    // Each follows the same pattern as the existing command class
    // but without boilerplate (no constructor, no arg field declarations).
    // Copy the execute() body from each existing command class.

    @Command(name = "setperm", description = "Set a permission on a group")
    @Permission("hyperperms.group.setperm")
    public CompletableFuture<Void> setperm(
            CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @Arg(name = "permission", description = "Permission node") String permission,
            @OptionalArg(name = "value", description = "true or false (default: true)") String valueStr,
            @OptionalArg(name = "duration", description = "Duration (e.g. 1d2h30m)") String durationStr) {

        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(error("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        boolean value = valueStr == null || !valueStr.equalsIgnoreCase("false");
        if (permission.startsWith("-")) {
            value = true;
        }

        Instant expiry = null;
        if (durationStr != null && !durationStr.isBlank()) {
            Optional<Duration> duration = TimeUtil.parseDuration(durationStr);
            if (duration.isEmpty() && !durationStr.equalsIgnoreCase("permanent")
                    && !durationStr.equalsIgnoreCase("perm") && !durationStr.equalsIgnoreCase("forever")) {
                ctx.sender().sendMessage(error("Invalid duration: " + durationStr + ". Use formats like 1d, 2h30m, 1w"));
                return CompletableFuture.completedFuture(null);
            }
            if (duration.isPresent()) {
                expiry = TimeUtil.expiryFromDuration(duration.get());
            }
        }

        var builder = Node.builder(permission).value(value);
        if (expiry != null) builder.expiry(expiry);
        Node node = builder.build();
        group.setNode(node);
        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        boolean granted = node.getValue() && !node.isNegated();
        String action = granted ? "Granted" : "Denied";
        String expiryMsg = node.isTemporary() ? " (" + TimeUtil.formatExpiry(node.getExpiry()) + ")" : "";
        ctx.sender().sendMessage(success(action + " " + node.getBasePermission() + " on group " + groupName + expiryMsg));
        return CompletableFuture.completedFuture(null);
    }
}
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/command/groups/GroupCommands.java
git commit -m "feat(commands): add GroupCommands as first annotated command group"
```

---

### Task 4: Create UserCommands

Port all 13 user command classes into a single annotated class.

**Files:**
- Create: `src/main/java/com/hyperperms/command/groups/UserCommands.java`

**Step 1: Create UserCommands**

Follow the same pattern as GroupCommands. Port the execute() body from each existing user command:
- info, setperm, unsetperm, setexpiry, addgroup, removegroup,
  setprimarygroup, promote, demote, setprefix, setsuffix, clear, clone

Key differences from group commands:
- Use `PlayerResolver.resolveOrCreate(plugin, identifier)` for player lookup
- Cache invalidation: `plugin.getCache().invalidate(user.getUuid())` (not invalidateAll)
- Save is async: `plugin.getUserManager().saveUser(user).join()`
- Some commands sync to Hytale: check existing code for `syncPermissionsToHytale` calls

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/command/groups/UserCommands.java
git commit -m "feat(commands): add UserCommands annotated command group"
```

---

### Task 5: Create remaining command groups

**Files:**
- Create: `src/main/java/com/hyperperms/command/groups/TrackCommands.java`
- Create: `src/main/java/com/hyperperms/command/groups/DebugCommands.java`
- Create: `src/main/java/com/hyperperms/command/groups/BackupCommands.java`
- Create: `src/main/java/com/hyperperms/command/groups/PermsCommands.java`
- Create: `src/main/java/com/hyperperms/command/groups/UtilityCommands.java`
- Create: `src/main/java/com/hyperperms/command/groups/WebCommands.java`

**Step 1: Create each command group**

Port commands from existing classes. Each group follows the same annotation pattern.

- **TrackCommands**: promote, demote (from user commands that operate on tracks)
- **DebugCommands**: perms, tree, resolve, contexts, toggle, status
- **BackupCommands**: create, list, restore (restore uses `@Confirm`)
- **PermsCommands**: list, search
- **UtilityCommands**: check, reload, export, import, resetgroups, migrate, template, analytics
- **WebCommands**: editor, apply (conditional on web editor availability)

Note: The `commands/` package classes (EditorSubCommand, ApplySubCommand, MigrateSubCommand, TemplateSubCommand, AnalyticsSubCommand, UpdateSubCommand, UpdatesSubCommand) should be ported into the appropriate groups above.

**Step 2: Verify and commit each group**

```bash
git add src/main/java/com/hyperperms/command/groups/
git commit -m "feat(commands): add remaining annotated command groups

Port TrackCommands, DebugCommands, BackupCommands, PermsCommands,
UtilityCommands, and WebCommands from individual command classes."
```

---

### Task 6: Update CommandStage to use CommandScanner

**Files:**
- Modify: `src/main/java/com/hyperperms/lifecycle/stages/CommandStage.java` (created in Phase 1 but was a placeholder)

**Step 1: Update CommandStage to scan annotated groups**

```java
// In CommandStage.initialize():
CommandScanner scanner = new CommandScanner(plugin);

// Create command group instances
GroupCommands groupCommands = new GroupCommands(plugin);
UserCommands userCommands = new UserCommands(plugin);
// ... etc

// Root command
HyperPermsCommand rootCommand = new HyperPermsCommand();
rootCommand.addSubCommand(scanner.scan(groupCommands));
rootCommand.addSubCommand(scanner.scan(userCommands));
// ... etc

// Register with server
container.register(HyperPermsCommand.class, rootCommand);
```

Note: The exact registration mechanism depends on how `HyperPermsPlugin.java` registers commands with the Hytale server. Read that file and adapt.

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/lifecycle/stages/CommandStage.java
git commit -m "refactor(commands): wire CommandScanner into CommandStage lifecycle"
```

---

### Task 7: Delete old command classes

Only after verifying all commands work correctly via the new framework.

**Files:**
- Delete: All files in `src/main/java/com/hyperperms/command/group/` (13 files)
- Delete: All files in `src/main/java/com/hyperperms/command/user/` (13 files)
- Delete: All files in `src/main/java/com/hyperperms/command/debug/` (7 files)
- Delete: `src/main/java/com/hyperperms/command/BackupCommand.java`
- Delete: `src/main/java/com/hyperperms/command/CheckCommand.java`
- Delete: `src/main/java/com/hyperperms/command/ExportCommand.java`
- Delete: `src/main/java/com/hyperperms/command/ImportCommand.java`
- Delete: `src/main/java/com/hyperperms/command/ReloadCommand.java`
- Delete: `src/main/java/com/hyperperms/command/ResetGroupsCommand.java`
- Delete: `src/main/java/com/hyperperms/command/HelpCommand.java`
- Delete: `src/main/java/com/hyperperms/command/PermsCommand.java`
- Delete: `src/main/java/com/hyperperms/command/PermsListCommand.java`
- Delete: `src/main/java/com/hyperperms/command/PermsSearchCommand.java`
- Delete: All files in `src/main/java/com/hyperperms/commands/` (legacy package, 8 files)

**Keep:**
- `HpContainerCommand.java` — still used by CommandScanner
- `HpSubCommand.java` — still used by CommandScanner
- `util/CommandUtil.java` — still used by command groups

**Step 1: Delete old files**

```bash
rm -rf src/main/java/com/hyperperms/command/group/
rm -rf src/main/java/com/hyperperms/command/user/
rm -rf src/main/java/com/hyperperms/command/debug/
rm src/main/java/com/hyperperms/command/BackupCommand.java
# ... etc (delete each individually)
rm -rf src/main/java/com/hyperperms/commands/
```

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (fix any remaining references to deleted classes)

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor(commands): remove old individual command classes

48 command classes replaced by 8 annotated command groups.
Framework classes (HpContainerCommand, HpSubCommand, CommandUtil) retained."
```

---

## Verification Checklist

1. `./gradlew compileJava` — passes
2. `./gradlew shadowJar` — produces valid JAR
3. All commands produce identical output to before:
   - `/hp group create test` — creates group
   - `/hp group setperm test example.perm` — sets permission
   - `/hp user setperm PlayerName example.perm` — sets user permission
   - `/hp check PlayerName example.perm` — checks permission
   - `/hp backup restore name` — shows confirmation, second invocation restores
   - `/hp debug tree PlayerName` — shows inheritance tree
   - `/hp editor` — opens web editor session
4. Tab completion works for subcommand names
5. Permission checks are enforced
