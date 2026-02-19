package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.util.CommandUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Base class for HyperPerms leaf commands with proper argument tracking and help text.
 * <p>
 * Extracted from the inner {@code HpCommand} class in HyperPermsCommand.java.
 * Uses {@link CommandUtil} for colors and messaging.
 */
public abstract class HpSubCommand extends AbstractCommand {

    private final List<ArgDescriptor> argDescriptors = new ArrayList<>();

    protected HpSubCommand(String name, String description) {
        super(name, description);
    }

    protected <D> RequiredArg<D> describeArg(String name, String description, ArgumentType<D> type) {
        argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, false));
        return withRequiredArg(name, description, type);
    }

    protected <D> RequiredArg<D> describeFlagArg(String name, String description, ArgumentType<D> type) {
        argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, true));
        return withRequiredArg(name, description, type);
    }

    protected <D> OptionalArg<D> describeOptionalArg(String name, String description, ArgumentType<D> type) {
        argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, false, true));
        return withOptionalArg(name, description, type);
    }

    protected static String stripQuotes(String value) {
        if (value != null && value.length() >= 2
                && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public Message getUsageString(CommandSender sender) {
        List<Message> parts = new ArrayList<>();
        String name = getFullyQualifiedName();

        // Header bar
        parts.add(header(name));

        // Description
        parts.add(Message.raw("  " + getDescription() + "\n\n").color(WHITE));

        // Usage line
        parts.add(Message.raw("  Usage: ").color(GOLD));
        parts.add(Message.raw("/").color(GRAY));
        parts.add(Message.raw(name).color(GOLD));
        for (ArgDescriptor arg : argDescriptors) {
            if (arg.required) {
                String display = arg.isFlag ? " <--" + arg.name + "=value>" : " <" + arg.name + ">";
                parts.add(Message.raw(display).color(GREEN));
            } else {
                String display = arg.isFlag ? " [--" + arg.name + "=value]" : " [" + arg.name + "]";
                parts.add(Message.raw(display).color(GRAY));
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

        // Footer
        parts.add(footer());

        return join(parts);
    }

    @Override
    public Message getUsageShort(CommandSender sender, boolean showAll) {
        List<Message> parts = new ArrayList<>();

        parts.add(Message.raw("  Usage: ").color(GOLD));
        parts.add(Message.raw("/").color(GRAY));
        parts.add(Message.raw(getFullyQualifiedName()).color(GOLD));
        for (ArgDescriptor arg : argDescriptors) {
            if (arg.required) {
                String display = arg.isFlag ? " <--" + arg.name + "=value>" : " <" + arg.name + ">";
                parts.add(Message.raw(display).color(RED));
            } else {
                String display = arg.isFlag ? " [--" + arg.name + "=value]" : " [" + arg.name + "]";
                parts.add(Message.raw(display).color(GRAY));
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

        return join(parts);
    }

    private static String getTypeName(ArgumentType<?> type) {
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
