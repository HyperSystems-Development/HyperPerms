package com.hyperperms.chat;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hytale chat event listener for HyperPerms.
 * 
 * <p>This listener intercepts player chat messages and applies:
 * <ul>
 *   <li>Prefix/suffix from permissions groups</li>
 *   <li>Custom formatting from config</li>
 *   <li>Color code processing</li>
 *   <li>Placeholder replacement</li>
 * </ul>
 * 
 * <p>The listener registers as an async event handler to avoid blocking
 * the main thread while loading player data.
 */
public class ChatListener {
    
    private final HyperPerms plugin;
    private final ChatManager chatManager;
    
    // Event registration handle (for unregistering)
    private EventRegistration<?, ?> chatEventRegistration;
    
    // Message conversion patterns
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§([0-9a-fk-or])");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static final Pattern SIMPLE_HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
    
    // Configuration
    private volatile boolean allowPlayerColors = true;
    private volatile String colorPermission = "hyperperms.chat.color";
    
    /**
     * Creates a new ChatListener.
     *
     * @param plugin the HyperPerms plugin instance
     * @param chatManager the chat manager
     */
    public ChatListener(@NotNull HyperPerms plugin, @NotNull ChatManager chatManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.chatManager = Objects.requireNonNull(chatManager, "chatManager cannot be null");
    }
    
    /**
     * Checks if a factions plugin is installed (HyFactions or HyperFactions).
     * Used to determine event priority - when a factions plugin is present,
     * we run LAST to wrap its formatter instead of FIRST.
     *
     * @return true if HyFactions or HyperFactions is installed
     */
    private static boolean isHyFactionsInstalled() {
        // Check for HyFactions (legacy)
        try {
            Class.forName("com.kaws.hyfaction.Main");
            return true;
        } catch (ClassNotFoundException ignored) {}

        // Check for HyperFactions
        try {
            Class.forName("com.hyperfactions.HyperFactions");
            return true;
        } catch (ClassNotFoundException ignored) {}

        return false;
    }

    /**
     * Checks if Werchat plugin is installed.
     * When Werchat is present, it handles chat formatting itself by calling
     * HyperPerms' ChatAPI. We skip setting a formatter to avoid race conditions.
     *
     * @return true if Werchat is installed
     */
    private static boolean isWerchatInstalled() {
        try {
            Class.forName("com.werchat.WerchatPlugin");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Registers the chat event listener with the Hytale event registry.
     *
     * @param eventRegistry the event registry
     */
    public void register(@NotNull EventRegistry eventRegistry) {
        Objects.requireNonNull(eventRegistry, "eventRegistry cannot be null");

        // Check for Werchat first - if installed, it handles chat via ChatAPI
        // We don't need to register a formatter since Werchat cancels the event
        // and broadcasts manually using ChatAPI.getPrefix/getSuffix
        if (isWerchatInstalled()) {
            Logger.info("Werchat detected - HyperPerms will provide prefix/suffix via ChatAPI only");
            Logger.info("Chat formatting will be handled by Werchat");
            // Don't register a chat listener - let Werchat handle everything
            return;
        }

        // Determine priority based on HyFactions presence
        // If HyFactions is installed, run LAST to wrap its formatter
        // Otherwise, run FIRST to set up formatting before other plugins
        EventPriority priority;
        if (isHyFactionsInstalled()) {
            priority = EventPriority.LAST;
            Logger.info("HyFactions detected - using LAST priority to wrap its chat formatter");
        } else {
            priority = EventPriority.FIRST;
        }

        chatEventRegistration = eventRegistry.registerAsyncGlobal(
            priority,
            PlayerChatEvent.class,
            this::onPlayerChatAsync
        );

        Logger.info("Chat listener registered with priority: " + priority);
    }
    
    /**
     * Unregisters the chat event listener.
     * <p>
     * Note: Hytale's EventRegistry doesn't have a direct unregister method.
     * Event registrations are typically cleaned up when the plugin is disabled.
     *
     * @param eventRegistry the event registry (currently unused)
     */
    public void unregister(@NotNull EventRegistry eventRegistry) {
        // Hytale's EventRegistry doesn't have a direct unregister method
        // The registration is cleaned up automatically when the plugin shuts down
        if (chatEventRegistration != null) {
            chatEventRegistration = null;
            Logger.info("Chat listener marked for cleanup");
        }
    }
    
    /**
     * Handles the player chat event asynchronously.
     *
     * @param futureEvent the future containing the chat event
     * @return the modified future
     */
    private CompletableFuture<PlayerChatEvent> onPlayerChatAsync(
            CompletableFuture<PlayerChatEvent> futureEvent) {
        
        return futureEvent.thenCompose(event -> {
            try {
                // If chat formatting is disabled or event is cancelled, pass through
                if (!chatManager.isEnabled()) {
                    return CompletableFuture.completedFuture(event);
                }

                if (event.isCancelled()) {
                    return CompletableFuture.completedFuture(event);
                }

                PlayerRef sender = event.getSender();
                if (sender == null) {
                    return CompletableFuture.completedFuture(event);
                }

                UUID uuid = sender.getUuid();
                String playerName = sender.getUsername();
                String content = event.getContent();

                // Process player colors in message if allowed
                String processedContent = processPlayerColors(uuid, content);

                // Format the chat message
                return chatManager.formatChatMessage(uuid, playerName, processedContent)
                    .thenApply(formattedMessage -> {
                        // Check if another plugin already set a formatter
                        PlayerChatEvent.Formatter existingFormatter = event.getFormatter();
                        boolean hasCustomFormatter = existingFormatter != null
                            && existingFormatter != PlayerChatEvent.DEFAULT_FORMATTER;

                        // If HyFactions is installed, use our own formatter directly
                        // (FactionIntegration already includes faction prefix in our formatted message)
                        // This gives us full control over colors and formatting
                        if (isHyFactionsInstalled()) {
                            event.setFormatter(new HyperPermsFormatter(formattedMessage));
                        } else if (hasCustomFormatter) {
                            // Wrap other formatters (like WerChat) to inject our prefix/suffix
                            event.setFormatter(new WrappingFormatter(existingFormatter, formattedMessage));
                        } else {
                            // No other formatter - use our full formatter
                            event.setFormatter(new HyperPermsFormatter(formattedMessage));
                        }
                        return event;
                    })
                    .exceptionally(e -> {
                        Logger.warn("Failed to format chat message for %s: %s", playerName, e.getMessage());
                        e.printStackTrace();
                        // Return original event on error - don't set formatter
                        return event;
                    });
            } catch (Exception e) {
                Logger.warn("[Chat] Exception in chat handler: " + e.getMessage());
                e.printStackTrace();
                return CompletableFuture.completedFuture(event);
            }
        });
    }
    
    /**
     * Processes color codes in player messages if they have permission.
     */
    private String processPlayerColors(@NotNull UUID uuid, @NotNull String content) {
        if (!allowPlayerColors) {
            return content;
        }
        
        // Check if player has color permission
        if (!plugin.hasPermission(uuid, colorPermission)) {
            // Strip any color codes they tried to use
            return ColorUtil.stripColors(content);
        }
        
        // Player has permission - let colors through
        // They will be processed by the formatter
        return content;
    }
    
    // ========== Configuration Methods ==========
    
    /**
     * Sets whether players can use color codes in their messages.
     */
    public void setAllowPlayerColors(boolean allowPlayerColors) {
        this.allowPlayerColors = allowPlayerColors;
    }
    
    /**
     * Gets whether players can use color codes in their messages.
     */
    public boolean isAllowPlayerColors() {
        return allowPlayerColors;
    }
    
    /**
     * Sets the permission required for players to use color codes.
     */
    public void setColorPermission(@NotNull String colorPermission) {
        this.colorPermission = Objects.requireNonNull(colorPermission, "colorPermission cannot be null");
    }
    
    /**
     * Gets the permission required for players to use color codes.
     */
    @NotNull
    public String getColorPermission() {
        return colorPermission;
    }
    
    // ========== Message Conversion ==========
    
    /**
     * Converts a HyperPerms formatted string to a Hytale Message.
     * Uses Hytale's native Message.color() API for proper color support.
     *
     * Supports:
     * - Legacy color codes: §0-9, §a-f
     * - Hex colors: §x§R§R§G§G§B§B format
     * - Format codes: §l (bold), §o (italic), §n (underline), §m (strikethrough)
     * - Reset: §r
     *
     * @param formatted the formatted string with color codes
     * @return a Hytale Message object
     */
    @NotNull
    public static Message toHytaleMessage(@NotNull String formatted) {
        if (formatted == null || formatted.isEmpty()) {
            return Message.raw("");
        }

        try {
            // Parse § codes and build Message with proper colors
            Message result = null;
            StringBuilder currentText = new StringBuilder();
            Color currentColor = null;
            boolean bold = false;
            boolean italic = false;
            boolean underline = false;
            boolean strikethrough = false;

            int i = 0;
            while (i < formatted.length()) {
                char c = formatted.charAt(i);

                // Check for color/format code (§X)
                if (c == '§' && i + 1 < formatted.length()) {
                    char code = Character.toLowerCase(formatted.charAt(i + 1));

                    // Check for hex color format: §x§R§R§G§G§B§B (14 characters total)
                    if (code == 'x' && i + 13 < formatted.length()) {
                        // Try to parse hex color
                        Color hexColor = parseHexColorCode(formatted, i);
                        if (hexColor != null) {
                            // Flush current segment before changing color
                            if (currentText.length() > 0) {
                                result = appendMessageSegment(result, currentText.toString(),
                                    currentColor, bold, italic, underline, strikethrough);
                                currentText.setLength(0);
                            }
                            currentColor = hexColor;
                            bold = false;
                            italic = false;
                            underline = false;
                            strikethrough = false;
                            i += 14; // Skip §x§R§R§G§G§B§B
                            continue;
                        }
                    }

                    // Flush current segment before changing format (for colors and reset)
                    if (isColorCode(code) || code == 'r') {
                        if (currentText.length() > 0) {
                            result = appendMessageSegment(result, currentText.toString(),
                                currentColor, bold, italic, underline, strikethrough);
                            currentText.setLength(0);
                        }
                    }

                    // Handle the code
                    switch (code) {
                        // Colors (reset format flags like Minecraft)
                        case '0' -> { currentColor = new Color(0x000000); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '1' -> { currentColor = new Color(0x0000AA); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '2' -> { currentColor = new Color(0x00AA00); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '3' -> { currentColor = new Color(0x00AAAA); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '4' -> { currentColor = new Color(0xAA0000); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '5' -> { currentColor = new Color(0xAA00AA); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '6' -> { currentColor = new Color(0xFFAA00); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '7' -> { currentColor = new Color(0xAAAAAA); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '8' -> { currentColor = new Color(0x555555); bold = false; italic = false; underline = false; strikethrough = false; }
                        case '9' -> { currentColor = new Color(0x5555FF); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'a' -> { currentColor = new Color(0x55FF55); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'b' -> { currentColor = new Color(0x55FFFF); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'c' -> { currentColor = new Color(0xFF5555); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'd' -> { currentColor = new Color(0xFF55FF); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'e' -> { currentColor = new Color(0xFFFF55); bold = false; italic = false; underline = false; strikethrough = false; }
                        case 'f' -> { currentColor = new Color(0xFFFFFF); bold = false; italic = false; underline = false; strikethrough = false; }
                        // Formats (don't flush, just set flag)
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'n' -> underline = true;
                        case 'm' -> strikethrough = true;
                        case 'k' -> { } // Obfuscated - not supported by Hytale, skip
                        // Reset
                        case 'r' -> { currentColor = null; bold = false; italic = false; underline = false; strikethrough = false; }
                        // Unknown - skip
                        default -> { }
                    }

                    i += 2;
                    continue;
                }

                // Regular character
                currentText.append(c);
                i++;
            }

            // Flush remaining segment
            if (currentText.length() > 0) {
                result = appendMessageSegment(result, currentText.toString(),
                    currentColor, bold, italic, underline, strikethrough);
            }

            return result != null ? result : Message.raw("");
        } catch (Exception e) {
            Logger.warn("[Chat] Failed to parse colors, using stripped: " + e.getMessage());
            return Message.raw(ColorUtil.stripColors(formatted));
        }
    }

    /**
     * Parses a hex color code in the format §x§R§R§G§G§B§B starting at the given index.
     *
     * @param text the text containing the hex color code
     * @param startIndex the index of the first § character
     * @return the parsed Color, or null if invalid format
     */
    @Nullable
    private static Color parseHexColorCode(@NotNull String text, int startIndex) {
        // Need at least 14 characters: §x§R§R§G§G§B§B
        if (startIndex + 13 >= text.length()) {
            return null;
        }

        // Verify the format: §x followed by 6 pairs of §[hex digit]
        if (text.charAt(startIndex) != '§' ||
            Character.toLowerCase(text.charAt(startIndex + 1)) != 'x') {
            return null;
        }

        StringBuilder hexBuilder = new StringBuilder();
        for (int j = 0; j < 6; j++) {
            int pairIndex = startIndex + 2 + (j * 2);
            if (text.charAt(pairIndex) != '§') {
                return null;
            }
            char hexDigit = text.charAt(pairIndex + 1);
            if (!isHexDigit(hexDigit)) {
                return null;
            }
            hexBuilder.append(hexDigit);
        }

        try {
            int rgb = Integer.parseInt(hexBuilder.toString(), 16);
            return new Color(rgb);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Checks if a character is a hex digit (0-9, a-f, A-F).
     */
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }

    /**
     * Appends a text segment to a Message with the given styling.
     */
    @NotNull
    private static Message appendMessageSegment(
            @Nullable Message existing,
            @NotNull String text,
            @Nullable Color color,
            boolean bold,
            boolean italic,
            boolean underline,
            boolean strikethrough) {

        if (text.isEmpty()) {
            return existing != null ? existing : Message.raw("");
        }

        Message segment = Message.raw(text);
        if (color != null) {
            segment = segment.color(color);
        }
        if (bold) {
            segment = segment.bold(true);
        }
        if (italic) {
            segment = segment.italic(true);
        }
        // Note: underline and strikethrough may not be supported by Hytale's Message API
        // If they are supported in the future, add: segment = segment.underline(true), etc.

        return existing == null ? segment : Message.join(existing, segment);
    }
    
    /**
     * Checks if a character is a color code (0-9, a-f).
     */
    private static boolean isColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    // ========== Inner Classes ==========
    
    /**
     * Custom formatter that uses HyperPerms formatted messages.
     */
    private static class HyperPermsFormatter implements PlayerChatEvent.Formatter {
        
        private final ChatManager.FormattedChatMessage formattedMessage;
        
        HyperPermsFormatter(@NotNull ChatManager.FormattedChatMessage formattedMessage) {
            this.formattedMessage = formattedMessage;
        }
        
        @Override
        public Message format(PlayerRef sender, String content) {
            try {
                String formatted = formattedMessage.getFormatted();

                if (formatted == null || formatted.isEmpty()) {
                    Logger.warn("[Chat] Formatted message was empty, using fallback");
                    String fallback = sender.getUsername() + ": " + content;
                    return Message.raw(fallback);
                }
                
                // Convert our formatted string to a Hytale Message
                Message result = toHytaleMessage(formatted);
                
                if (result == null) {
                    Logger.warn("[Chat] toHytaleMessage returned null, using fallback");
                    return Message.raw(formatted);
                }
                
                return result;
            } catch (Exception e) {
                Logger.warn("[Chat] Error formatting message: " + e.getMessage());
                e.printStackTrace();
                // Fallback to simple format
                return Message.raw(sender.getUsername() + ": " + content);
            }
        }
    }
    
    /**
     * Formatter that wraps another plugin's formatter (like WerChat) and injects
     * HyperPerms prefix/suffix into the output.
     * <p>
     * This allows both plugins to coexist: WerChat handles channel formatting,
     * HyperPerms injects rank prefixes/suffixes.
     */
    private static class WrappingFormatter implements PlayerChatEvent.Formatter {
        
        private final PlayerChatEvent.Formatter wrapped;
        private final ChatManager.FormattedChatMessage formattedMessage;
        
        WrappingFormatter(@NotNull PlayerChatEvent.Formatter wrapped,
                         @NotNull ChatManager.FormattedChatMessage formattedMessage) {
            this.wrapped = wrapped;
            this.formattedMessage = formattedMessage;
        }
        
        @Override
        public Message format(PlayerRef sender, String content) {
            try {
                // Get the prefix from HyperPerms
                String prefix = formattedMessage.getPrefix();

                // Let the wrapped formatter (e.g., HyFactions) format the message
                Message wrappedResult = wrapped.format(sender, content);

                if (wrappedResult == null) {
                    Logger.warn("[Chat] Wrapped formatter returned null");
                    return Message.raw(sender.getUsername() + ": " + content);
                }

                // If we have no prefix, just return the wrapped result
                if (prefix == null || prefix.isEmpty()) {
                    return wrappedResult;
                }

                // Try to get raw text to inject prefix before player name
                String wrappedText = wrappedResult.getRawText();
                String playerName = sender.getUsername();
                String prefixStr = ColorUtil.colorize(prefix);

                // Add space after prefix if needed
                if (!prefixStr.endsWith(" ")) {
                    prefixStr = prefixStr + " ";
                }

                if (wrappedText != null && !wrappedText.isEmpty()) {
                    int playerNameIndex = wrappedText.indexOf(playerName);

                    if (playerNameIndex >= 0) {
                        // Insert prefix right before the player name
                        // "[Testing] Zenithus:" -> "[Testing] [Admin] Zenithus:"
                        String before = wrappedText.substring(0, playerNameIndex);
                        String after = wrappedText.substring(playerNameIndex);
                        String newText = before + prefixStr + after;
                        return toHytaleMessage(newText);
                    }
                }

                // Fallback: prepend prefix, then wrapped result
                // Result: "[Admin] [Testing] Zenithus: msg" (not ideal order but both visible)
                Message prefixMsg = toHytaleMessage(prefixStr);
                return Message.join(prefixMsg, wrappedResult);
            } catch (Exception e) {
                Logger.warn("[Chat] Error in wrapping formatter: " + e.getMessage());
                e.printStackTrace();
                try {
                    return wrapped.format(sender, content);
                } catch (Exception e2) {
                    return Message.raw(sender.getUsername() + ": " + content);
                }
            }
        }
    }
    
    /**
     * Alternative formatter that allows dynamic formatting at send time.
     * Useful if you want different formatting per recipient.
     */
    public static class DynamicFormatter implements PlayerChatEvent.Formatter {
        
        private final ChatManager chatManager;
        
        public DynamicFormatter(@NotNull ChatManager chatManager) {
            this.chatManager = chatManager;
        }
        
        @Override
        public Message format(PlayerRef sender, String content) {
            // This is called synchronously, so we use cached data
            // The async handler should have already loaded the data
            return Message.raw(content);
        }
    }
}
