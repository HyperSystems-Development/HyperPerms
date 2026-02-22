package com.hyperperms.chat;

import static com.hyperperms.util.Logger.debugChat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive chat message formatter with placeholder support.
 * <p>
 * Built-in placeholders:
 * <ul>
 *   <li>{@code %player%} - Player's display name</li>
 *   <li>{@code %playername%} - Player's raw username</li>
 *   <li>{@code %uuid%} - Player's UUID</li>
 *   <li>{@code %prefix%} - Player's effective prefix</li>
 *   <li>{@code %suffix%} - Player's effective suffix</li>
 *   <li>{@code %group%} - Player's primary group</li>
 *   <li>{@code %groups%} - All player's groups (comma-separated)</li>
 *   <li>{@code %world%} - Player's current world</li>
 *   <li>{@code %gamemode%} - Player's game mode</li>
 *   <li>{@code %message%} - Chat message content</li>
 *   <li>{@code %server%} - Server name</li>
 *   <li>{@code %time%} - Current time (HH:mm)</li>
 *   <li>{@code %date%} - Current date (yyyy-MM-dd)</li>
 *   <li>{@code %online%} - Online player count</li>
 * </ul>
 * <p>
 * Custom placeholders can be registered via {@link #registerPlaceholder(String, Function)}.
 * <p>
 * Special input handling:
 * <ul>
 *   <li>Pipe character (|) is converted to space in command input</li>
 *   <li>Backslash escapes special characters</li>
 * </ul>
 */
public final class ChatFormatter {

    /**
     * Default chat format if none is configured.
     */
    public static final String DEFAULT_FORMAT = "%prefix%%player%%suffix%&8: &f%message%";

    /**
     * Pattern to match placeholders: %name% or %name:arg%
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "%([a-zA-Z0-9_]+)(?::([^%]*))?%"
    );

    /**
     * Pattern to match conditional placeholders: %?condition?then:else%
     */
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile(
        "%\\?([a-zA-Z0-9_]+)\\?([^:]*):([^%]*)%"
    );

    /**
     * Map of registered placeholder handlers.
     */
    private static final Map<String, Function<PlaceholderContext, String>> PLACEHOLDERS = new HashMap<>();

    // Time formatters
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        // Register built-in placeholders
        registerBuiltInPlaceholders();
    }

    public ChatFormatter() {}

    // ==================== Main Formatting Methods ====================

    /**
     * Formats a chat message with all placeholders and colors.
     *
     * @param format  the format string with placeholders
     * @param context the placeholder context
     * @return the fully formatted and colorized message
     */
    @NotNull
    public static String format(@NotNull String format, @NotNull PlaceholderContext context) {
        debugChat("ChatFormatter.format: input='%s', player=%s", format, context.getPlayerName());

        String result = format;

        // Process conditional placeholders first
        result = processConditionals(result, context);

        // Process regular placeholders
        result = processPlaceholders(result, context);

        debugChat("ChatFormatter.format: afterPlaceholders='%s'", result);

        // Apply color codes
        result = ColorUtil.colorize(result);

        return result;
    }

    /**
     * Formats a chat message using the default format.
     *
     * @param context the placeholder context
     * @return the formatted message
     */
    @NotNull
    public static String format(@NotNull PlaceholderContext context) {
        return format(DEFAULT_FORMAT, context);
    }

    /**
     * Applies only placeholder substitution without colorizing.
     *
     * @param text    the text with placeholders
     * @param context the placeholder context
     * @return text with placeholders replaced
     */
    @NotNull
    public static String applyPlaceholders(@NotNull String text, @NotNull PlaceholderContext context) {
        String result = processConditionals(text, context);
        return processPlaceholders(result, context);
    }

    // ==================== Placeholder Processing ====================

    /**
     * Processes regular placeholders in text.
     */
    @NotNull
    private static String processPlaceholders(@NotNull String text, @NotNull PlaceholderContext context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase();
            String arg = matcher.group(2); // May be null

            // Look up placeholder handler
            Function<PlaceholderContext, String> handler = PLACEHOLDERS.get(name);
            String replacement;

            if (handler != null) {
                // If there's an argument, add it to context temporarily
                if (arg != null) {
                    context = context.withArg(arg);
                }
                replacement = handler.apply(context);
                if (replacement == null) {
                    replacement = "";
                }
            } else {
                // Unknown placeholder - leave as-is or empty
                replacement = "";
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Processes conditional placeholders: %?condition?then:else%
     * <p>
     * The condition is truthy if the placeholder value is non-empty and not "false" or "0".
     */
    @NotNull
    private static String processConditionals(@NotNull String text, @NotNull PlaceholderContext context) {
        Matcher matcher = CONDITIONAL_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String conditionName = matcher.group(1).toLowerCase();
            String thenValue = matcher.group(2);
            String elseValue = matcher.group(3);

            // Evaluate the condition
            Function<PlaceholderContext, String> handler = PLACEHOLDERS.get(conditionName);
            boolean isTruthy = false;

            if (handler != null) {
                String value = handler.apply(context);
                isTruthy = value != null && !value.isEmpty() 
                        && !value.equalsIgnoreCase("false") 
                        && !value.equals("0");
            }

            String replacement = isTruthy ? thenValue : elseValue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // ==================== Placeholder Registration ====================

    /**
     * Registers a custom placeholder.
     *
     * @param name    the placeholder name (without %)
     * @param handler function that returns the placeholder value
     */
    public static void registerPlaceholder(@NotNull String name, 
                                           @NotNull Function<PlaceholderContext, String> handler) {
        PLACEHOLDERS.put(name.toLowerCase(), handler);
    }

    /**
     * Registers a simple string placeholder.
     *
     * @param name  the placeholder name
     * @param value the static value
     */
    public static void registerPlaceholder(@NotNull String name, @NotNull String value) {
        registerPlaceholder(name, ctx -> value);
    }

    /**
     * Unregisters a placeholder.
     *
     * @param name the placeholder name
     * @return true if a placeholder was removed
     */
    public static boolean unregisterPlaceholder(@NotNull String name) {
        return PLACEHOLDERS.remove(name.toLowerCase()) != null;
    }

    /**
     * Checks if a placeholder is registered.
     *
     * @param name the placeholder name
     * @return true if registered
     */
    public static boolean hasPlaceholder(@NotNull String name) {
        return PLACEHOLDERS.containsKey(name.toLowerCase());
    }

    /**
     * Registers all built-in placeholders.
     */
    private static void registerBuiltInPlaceholders() {
        // Player placeholders
        registerPlaceholder("player", ctx -> 
            ctx.getDisplayName() != null ? ctx.getDisplayName() : ctx.getPlayerName());
        registerPlaceholder("playername", PlaceholderContext::getPlayerName);
        registerPlaceholder("displayname", ctx -> 
            ctx.getDisplayName() != null ? ctx.getDisplayName() : ctx.getPlayerName());
        registerPlaceholder("uuid", ctx -> 
            ctx.getUuid() != null ? ctx.getUuid().toString() : "");

        // Prefix/suffix
        registerPlaceholder("prefix", ctx -> 
            ctx.getPrefix() != null ? ctx.getPrefix() : "");
        registerPlaceholder("suffix", ctx -> 
            ctx.getSuffix() != null ? ctx.getSuffix() : "");

        // Group placeholders
        registerPlaceholder("group", PlaceholderContext::getPrimaryGroup);
        registerPlaceholder("primarygroup", PlaceholderContext::getPrimaryGroup);
        registerPlaceholder("groups", ctx -> {
            if (ctx.getGroups() == null || ctx.getGroups().isEmpty()) {
                return ctx.getPrimaryGroup();
            }
            return String.join(", ", ctx.getGroups());
        });

        // World/context placeholders
        registerPlaceholder("world", ctx -> 
            ctx.getWorld() != null ? ctx.getWorld() : "");
        registerPlaceholder("gamemode", ctx -> 
            ctx.getGameMode() != null ? ctx.getGameMode() : "");
        registerPlaceholder("server", ctx -> 
            ctx.getServer() != null ? ctx.getServer() : "");

        // Message placeholder
        registerPlaceholder("message", ctx -> 
            ctx.getMessage() != null ? ctx.getMessage() : "");
        registerPlaceholder("msg", ctx -> 
            ctx.getMessage() != null ? ctx.getMessage() : "");

        // Time placeholders
        registerPlaceholder("time", ctx -> LocalDateTime.now().format(TIME_FORMAT));
        registerPlaceholder("date", ctx -> LocalDateTime.now().format(DATE_FORMAT));
        registerPlaceholder("datetime", ctx -> LocalDateTime.now().format(DATETIME_FORMAT));

        // Server info placeholders
        registerPlaceholder("online", ctx -> 
            ctx.getOnlineCount() >= 0 ? String.valueOf(ctx.getOnlineCount()) : "?");
        registerPlaceholder("maxplayers", ctx -> 
            ctx.getMaxPlayers() >= 0 ? String.valueOf(ctx.getMaxPlayers()) : "?");

        // Utility placeholders
        registerPlaceholder("newline", ctx -> "\n");
        registerPlaceholder("nl", ctx -> "\n");
        registerPlaceholder("tab", ctx -> "\t");

        // Color shortcut placeholders
        registerPlaceholder("reset", ctx -> ColorUtil.RESET);
        registerPlaceholder("bold", ctx -> ColorUtil.BOLD);
        registerPlaceholder("italic", ctx -> ColorUtil.ITALIC);
        registerPlaceholder("underline", ctx -> ColorUtil.UNDERLINE);
        registerPlaceholder("strike", ctx -> ColorUtil.STRIKETHROUGH);
        registerPlaceholder("magic", ctx -> ColorUtil.OBFUSCATED);
    }

    // ==================== Input Parsing ====================

    /**
     * Parses command input, converting pipes to spaces.
     * <p>
     * Users can't type spaces in command arguments, so they use | instead.
     * Example: {@code &a[VIP]|%player%} becomes {@code &a[VIP] %player%}
     *
     * @param input the input string
     * @return the parsed string with pipes converted to spaces
     */
    @NotNull
    public static String parseInput(@NotNull String input) {
        if (input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                // Previous character was backslash - output this char literally
                result.append(c);
                escaped = false;
            } else if (c == '\\' && i + 1 < input.length()) {
                // Backslash - escape next character
                escaped = true;
            } else if (c == '|') {
                // Pipe - convert to space
                result.append(' ');
            } else {
                result.append(c);
            }
        }

        // If ended with backslash, add it
        if (escaped) {
            result.append('\\');
        }

        return result.toString();
    }

    /**
     * Encodes a string for use in commands, converting spaces to pipes.
     * Inverse of {@link #parseInput(String)}.
     *
     * @param text the text to encode
     * @return the encoded string
     */
    @NotNull
    public static String encodeForCommand(@NotNull String text) {
        return text.replace(" ", "|")
                   .replace("\\", "\\\\");
    }

    // ==================== Placeholder Context ====================

    /**
     * Context object containing all available placeholder values.
     * <p>
     * Use {@link Builder} to construct instances.
     */
    public static final class PlaceholderContext {
        private final String playerName;
        private final String displayName;
        private final java.util.UUID uuid;
        private final String prefix;
        private final String suffix;
        private final String primaryGroup;
        private final java.util.List<String> groups;
        private final String world;
        private final String gameMode;
        private final String server;
        private final String message;
        private final int onlineCount;
        private final int maxPlayers;
        private final String arg;
        private final Map<String, String> extra;

        private PlaceholderContext(Builder builder) {
            this.playerName = builder.playerName;
            this.displayName = builder.displayName;
            this.uuid = builder.uuid;
            this.prefix = builder.prefix;
            this.suffix = builder.suffix;
            this.primaryGroup = builder.primaryGroup;
            this.groups = builder.groups;
            this.world = builder.world;
            this.gameMode = builder.gameMode;
            this.server = builder.server;
            this.message = builder.message;
            this.onlineCount = builder.onlineCount;
            this.maxPlayers = builder.maxPlayers;
            this.arg = builder.arg;
            this.extra = builder.extra;
        }

        // Getters
        @Nullable public String getPlayerName() { return playerName; }
        @Nullable public String getDisplayName() { return displayName; }
        @Nullable public java.util.UUID getUuid() { return uuid; }
        @Nullable public String getPrefix() { return prefix; }
        @Nullable public String getSuffix() { return suffix; }
        @Nullable public String getPrimaryGroup() { return primaryGroup; }
        @Nullable public java.util.List<String> getGroups() { return groups; }
        @Nullable public String getWorld() { return world; }
        @Nullable public String getGameMode() { return gameMode; }
        @Nullable public String getServer() { return server; }
        @Nullable public String getMessage() { return message; }
        public int getOnlineCount() { return onlineCount; }
        public int getMaxPlayers() { return maxPlayers; }
        @Nullable public String getArg() { return arg; }

        /**
         * Gets an extra value by key.
         */
        @Nullable
        public String getExtra(@NotNull String key) {
            return extra != null ? extra.get(key) : null;
        }

        /**
         * Creates a new context with an argument added.
         */
        @NotNull
        PlaceholderContext withArg(@Nullable String arg) {
            return new Builder(this).arg(arg).build();
        }

        /**
         * Creates a new builder from this context.
         */
        @NotNull
        public Builder toBuilder() {
            return new Builder(this);
        }

        /**
         * Creates a new builder.
         */
        @NotNull
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for PlaceholderContext.
         */
        public static final class Builder {
            private String playerName;
            private String displayName;
            private java.util.UUID uuid;
            private String prefix;
            private String suffix;
            private String primaryGroup = "default";
            private java.util.List<String> groups;
            private String world;
            private String gameMode;
            private String server;
            private String message;
            private int onlineCount = -1;
            private int maxPlayers = -1;
            private String arg;
            private Map<String, String> extra;

            public Builder() {}

            private Builder(PlaceholderContext ctx) {
                this.playerName = ctx.playerName;
                this.displayName = ctx.displayName;
                this.uuid = ctx.uuid;
                this.prefix = ctx.prefix;
                this.suffix = ctx.suffix;
                this.primaryGroup = ctx.primaryGroup;
                this.groups = ctx.groups;
                this.world = ctx.world;
                this.gameMode = ctx.gameMode;
                this.server = ctx.server;
                this.message = ctx.message;
                this.onlineCount = ctx.onlineCount;
                this.maxPlayers = ctx.maxPlayers;
                this.arg = ctx.arg;
                this.extra = ctx.extra != null ? new HashMap<>(ctx.extra) : null;
            }

            public Builder playerName(@Nullable String playerName) {
                this.playerName = playerName;
                return this;
            }

            public Builder displayName(@Nullable String displayName) {
                this.displayName = displayName;
                return this;
            }

            public Builder uuid(@Nullable java.util.UUID uuid) {
                this.uuid = uuid;
                return this;
            }

            public Builder prefix(@Nullable String prefix) {
                this.prefix = prefix;
                return this;
            }

            public Builder suffix(@Nullable String suffix) {
                this.suffix = suffix;
                return this;
            }

            public Builder primaryGroup(@Nullable String primaryGroup) {
                this.primaryGroup = primaryGroup != null ? primaryGroup : "default";
                return this;
            }

            public Builder groups(@Nullable java.util.List<String> groups) {
                this.groups = groups;
                return this;
            }

            public Builder world(@Nullable String world) {
                this.world = world;
                return this;
            }

            public Builder gameMode(@Nullable String gameMode) {
                this.gameMode = gameMode;
                return this;
            }

            public Builder server(@Nullable String server) {
                this.server = server;
                return this;
            }

            public Builder message(@Nullable String message) {
                this.message = message;
                return this;
            }

            public Builder onlineCount(int onlineCount) {
                this.onlineCount = onlineCount;
                return this;
            }

            public Builder maxPlayers(int maxPlayers) {
                this.maxPlayers = maxPlayers;
                return this;
            }

            public Builder arg(@Nullable String arg) {
                this.arg = arg;
                return this;
            }

            public Builder extra(@NotNull String key, @Nullable String value) {
                if (this.extra == null) {
                    this.extra = new HashMap<>();
                }
                this.extra.put(key, value);
                return this;
            }

            @NotNull
            public PlaceholderContext build() {
                return new PlaceholderContext(this);
            }
        }
    }

    // ==================== Convenience Methods ====================

    /**
     * Quickly formats a simple message with player and message only.
     *
     * @param format     the format string
     * @param playerName the player name
     * @param message    the chat message
     * @return formatted string
     */
    @NotNull
    public static String quickFormat(@NotNull String format, 
                                     @NotNull String playerName, 
                                     @NotNull String message) {
        return format(format, PlaceholderContext.builder()
            .playerName(playerName)
            .message(message)
            .build());
    }

    /**
     * Creates a colorized prefix/suffix string.
     * <p>
     * This is a convenience method for formatting prefixes and suffixes
     * entered by admins via commands.
     *
     * @param input the input string (may contain pipes for spaces)
     * @return colorized string with pipes converted to spaces
     */
    @NotNull
    public static String formatPrefixSuffix(@NotNull String input) {
        return ColorUtil.colorize(parseInput(input));
    }

    /**
     * Validates a chat format string.
     * <p>
     * Checks that essential placeholders are present.
     *
     * @param format the format to validate
     * @return null if valid, error message if invalid
     */
    @Nullable
    public static String validateFormat(@NotNull String format) {
        if (!format.contains("%message%") && !format.contains("%msg%")) {
            return "Format must contain %message% placeholder";
        }
        return null;
    }

    /**
     * Gets a preview of how a format will look.
     *
     * @param format the format string
     * @return preview with example values
     */
    @NotNull
    public static String previewFormat(@NotNull String format) {
        return format(format, PlaceholderContext.builder()
            .playerName("ExamplePlayer")
            .displayName("ExamplePlayer")
            .prefix("&a[VIP] ")
            .suffix(" &7[MVP]")
            .primaryGroup("vip")
            .message("Hello world!")
            .world("world")
            .gameMode("survival")
            .server("main")
            .build());
    }
}
