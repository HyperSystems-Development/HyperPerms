package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.command.util.CommandUtil;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Discovers {@link CommandGroup} classes and builds the command tree
 * by wrapping annotated methods as {@link HpSubCommand} instances.
 */
public final class CommandScanner {

    private final HyperPerms plugin;

    public CommandScanner(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    /**
     * Scan a {@code @CommandGroup(root=false)} class and return a container command
     * with all annotated methods registered as subcommands.
     */
    @NotNull
    public HpContainerCommand scanGroup(@NotNull Object instance) {
        Class<?> clazz = instance.getClass();
        CommandGroup group = clazz.getAnnotation(CommandGroup.class);
        if (group == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not annotated with @CommandGroup");
        }
        if (group.root()) {
            throw new IllegalArgumentException(clazz.getName() + " is a root group; use scanRootCommands()");
        }

        HpContainerCommand container = new HpContainerCommand(group.name(), group.description()) {};

        for (Method method : clazz.getDeclaredMethods()) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) continue;

            validateCommandMethod(method);
            AbstractCommand sub = buildSubCommand(instance, method, cmd, group.name());
            container.addSubCommand(sub);

            Logger.debug("[CommandScanner] Registered: /%s %s", group.name(), cmd.name());
        }

        return container;
    }

    /**
     * Scan a {@code @CommandGroup(root=true)} class and return standalone commands
     * to be registered directly under /hp.
     */
    @NotNull
    public List<AbstractCommand> scanRootCommands(@NotNull Object instance) {
        Class<?> clazz = instance.getClass();
        CommandGroup group = clazz.getAnnotation(CommandGroup.class);
        if (group == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not annotated with @CommandGroup");
        }
        if (!group.root()) {
            throw new IllegalArgumentException(clazz.getName() + " is not a root group; use scanGroup()");
        }

        List<AbstractCommand> commands = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) continue;

            validateCommandMethod(method);
            commands.add(buildSubCommand(instance, method, cmd, "hp"));
        }

        return commands;
    }

    private void validateCommandMethod(Method method) {
        if (!CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException(
                    "Command method " + method.getName() + " must return CompletableFuture");
        }

        Parameter[] params = method.getParameters();
        if (params.length == 0 || !CommandContext.class.isAssignableFrom(params[0].getType())) {
            throw new IllegalArgumentException(
                    "Command method " + method.getName() + " must have CommandContext as first parameter");
        }
    }

    @SuppressWarnings("unchecked")
    private AbstractCommand buildSubCommand(Object instance, Method method,
                                             Command cmd, String groupName) {
        method.setAccessible(true);

        Permission permission = method.getAnnotation(Permission.class);
        Confirm confirm = method.getAnnotation(Confirm.class);

        // Count annotated parameters (skip CommandContext at index 0)
        Parameter[] params = method.getParameters();
        int paramCount = params.length - 1;

        HpSubCommand sub = new HpSubCommand(cmd.name(), cmd.description()) {
            // Arg references stored during init block
            private final Object[] argRefs = new Object[paramCount];

            {
                for (int i = 0; i < paramCount; i++) {
                    Parameter param = params[i + 1];
                    Arg arg = param.getAnnotation(Arg.class);
                    OptionalArg optArg = param.getAnnotation(OptionalArg.class);

                    if (arg != null) {
                        argRefs[i] = describeArg(arg.name(), arg.description(), ArgTypes.STRING);
                    } else if (optArg != null) {
                        argRefs[i] = describeOptionalArg(optArg.name(), optArg.description(), ArgTypes.STRING);
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
                    String confirmKey = cmd.name() + ":" + ctx.sender().getUuid();
                    if (!CommandDispatcher.handleConfirmation(ctx, confirmKey, confirm)) {
                        return CompletableFuture.completedFuture(null);
                    }
                }

                // Build argument array
                Object[] args = new Object[params.length];
                args[0] = ctx;

                for (int i = 0; i < paramCount; i++) {
                    if (argRefs[i] instanceof RequiredArg<?> r) {
                        args[i + 1] = ctx.get((RequiredArg<String>) r);
                    } else if (argRefs[i] instanceof com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg<?> o) {
                        args[i + 1] = ctx.get((com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg<String>) o);
                    }
                }

                try {
                    return (CompletableFuture<Void>) method.invoke(instance, args);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    Logger.warn("Error executing command /%s %s: %s", groupName, cmd.name(), cause.getMessage());
                    ctx.sender().sendMessage(CommandUtil.error("An error occurred. Check console."));
                    return CompletableFuture.completedFuture(null);
                }
            }
        };

        // Register aliases
        if (cmd.aliases().length > 0) {
            sub.addAliases(cmd.aliases());
        }

        return sub;
    }
}
