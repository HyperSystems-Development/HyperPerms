package com.hyperperms.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;

import java.util.ArrayList;
import java.util.List;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Base class for HyperPerms commands that need proper argument registration.
 * <p>
 * This class wraps argument registration methods to track arguments in a descriptor list,
 * which the command framework uses for help text generation and argument validation.
 * <p>
 * Use {@link #describeArg} instead of {@code withRequiredArg} and
 * {@link #describeOptionalArg} instead of {@code withOptionalArg}.
 *
 * @deprecated Use {@link com.hyperperms.command.HpSubCommand} instead.
 */
@Deprecated
public abstract class HpCommand extends AbstractCommand {

    private final List<ArgDescriptor> argDescriptors = new ArrayList<>();

    protected HpCommand(String name, String description) {
        super(name, description);
    }

    /**
     * Registers a required argument with descriptor tracking.
     */
    protected <D> RequiredArg<D> describeArg(String name, String description, ArgumentType<D> type) {
        argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, false));
        return withRequiredArg(name, description, type);
    }

    /**
     * Registers a required flag argument with descriptor tracking.
     */
    protected <D> RequiredArg<D> describeFlagArg(String name, String description, ArgumentType<D> type) {
        argDescriptors.add(new ArgDescriptor(name, getTypeName(type), description, true, true));
        return withRequiredArg(name, description, type);
    }

    /**
     * Registers an optional argument with descriptor tracking.
     */
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
        parts.add(footer());

        return join(parts);
    }

    @Override
    public Message getUsageShort(CommandSender sender, boolean showAll) {
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
