package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Wrapped logger with HyperPerms prefix, formatting, and category-based debug system.
 * <p>
 * Debug categories allow granular control over which subsystems produce debug output.
 * Categories can be toggled individually via {@code /hp debug toggle <category>} or
 * persisted via {@link com.hyperperms.config.DebugConfig}.
 */
public final class Logger {

    private static final String PREFIX = "[HyperPerms] ";
    private static java.util.logging.Logger logger;

    /**
     * Debug categories for HyperPerms subsystems.
     */
    public enum DebugCategory {
        RESOLUTION("Permission resolution steps, wildcard matching, negation"),
        CACHE("Cache hits, misses, invalidations, evictions"),
        STORAGE("Storage read/write operations, errors"),
        CONTEXT("Context calculation, context changes"),
        INHERITANCE("Group inheritance resolution, parent chain"),
        INTEGRATION("Integration setup, availability checks"),
        CHAT("Chat formatting, prefix/suffix resolution"),
        WEB("Web editor session creation, change application"),
        MIGRATION("Migration progress, conflict resolution"),
        EXPIRY("Permission expiry checks, cleanup");

        private final String description;

        DebugCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Thread-safe set of enabled debug categories.
     * Uses volatile for visibility across threads.
     */
    private static volatile EnumSet<DebugCategory> enabledCategories = EnumSet.noneOf(DebugCategory.class);

    private Logger() {}

    /**
     * Initializes the logger.
     *
     * @param parentLogger the parent logger from the plugin
     */
    public static void init(@NotNull java.util.logging.Logger parentLogger) {
        logger = parentLogger;
    }

    // ==================== Standard Logging ====================

    /**
     * Logs an info message.
     *
     * @param message the message
     */
    public static void info(@NotNull String message) {
        if (logger != null) {
            logger.info(PREFIX + message);
        } else {
            System.out.println(PREFIX + "[INFO] " + message);
        }
    }

    /**
     * Logs an info message with formatting.
     *
     * @param message the message format
     * @param args    the format arguments
     */
    public static void info(@NotNull String message, Object... args) {
        info(String.format(message, args));
    }

    /**
     * Logs a warning message.
     *
     * @param message the message
     */
    public static void warn(@NotNull String message) {
        if (logger != null) {
            logger.warning(PREFIX + message);
        } else {
            System.out.println(PREFIX + "[WARN] " + message);
        }
    }

    /**
     * Logs a warning message with formatting.
     *
     * @param message the message format
     * @param args    the format arguments
     */
    public static void warn(@NotNull String message, Object... args) {
        warn(String.format(message, args));
    }

    /**
     * Logs a severe error message.
     *
     * @param message the message
     */
    public static void severe(@NotNull String message) {
        if (logger != null) {
            logger.severe(PREFIX + message);
        } else {
            System.err.println(PREFIX + "[SEVERE] " + message);
        }
    }

    /**
     * Logs a severe error message with formatting.
     *
     * @param message the message format
     * @param args    the format arguments
     */
    public static void severe(@NotNull String message, Object... args) {
        severe(String.format(message, args));
    }

    /**
     * Logs a severe error with exception.
     *
     * @param message   the message
     * @param throwable the exception
     */
    public static void severe(@NotNull String message, @NotNull Throwable throwable) {
        if (logger != null) {
            logger.log(Level.SEVERE, PREFIX + message, throwable);
        } else {
            System.err.println(PREFIX + "[SEVERE] " + message);
            throwable.printStackTrace();
        }
    }

    // ==================== Generic Debug ====================

    /**
     * Logs a debug message. If any category is enabled, logs at INFO level for visibility.
     * Otherwise logs at FINE level.
     *
     * @param message the message
     */
    public static void debug(@NotNull String message) {
        if (!enabledCategories.isEmpty()) {
            info("[DEBUG] " + message);
        } else if (logger != null) {
            logger.fine(PREFIX + "[DEBUG] " + message);
        }
    }

    /**
     * Logs a debug message with formatting.
     *
     * @param message the message format
     * @param args    the format arguments
     */
    public static void debug(@NotNull String message, Object... args) {
        debug(String.format(message, args));
    }

    // ==================== Category-Based Debug ====================

    /**
     * Logs a debug message for the given category, only if that category is enabled.
     *
     * @param category the debug category
     * @param message  the message
     */
    public static void debug(@NotNull DebugCategory category, @NotNull String message) {
        if (enabledCategories.contains(category)) {
            info("[DEBUG:" + category.name() + "] " + message);
        }
    }

    /**
     * Logs a debug message for the given category with formatting.
     *
     * @param category the debug category
     * @param message  the message format
     * @param args     the format arguments
     */
    public static void debug(@NotNull DebugCategory category, @NotNull String message, Object... args) {
        if (enabledCategories.contains(category)) {
            debug(category, String.format(message, args));
        }
    }

    // Convenience methods for each category

    public static void debugResolution(@NotNull String message, Object... args) {
        debug(DebugCategory.RESOLUTION, message, args);
    }

    public static void debugCache(@NotNull String message, Object... args) {
        debug(DebugCategory.CACHE, message, args);
    }

    public static void debugStorage(@NotNull String message, Object... args) {
        debug(DebugCategory.STORAGE, message, args);
    }

    public static void debugContext(@NotNull String message, Object... args) {
        debug(DebugCategory.CONTEXT, message, args);
    }

    public static void debugInheritance(@NotNull String message, Object... args) {
        debug(DebugCategory.INHERITANCE, message, args);
    }

    public static void debugIntegration(@NotNull String message, Object... args) {
        debug(DebugCategory.INTEGRATION, message, args);
    }

    public static void debugChat(@NotNull String message, Object... args) {
        debug(DebugCategory.CHAT, message, args);
    }

    public static void debugWeb(@NotNull String message, Object... args) {
        debug(DebugCategory.WEB, message, args);
    }

    public static void debugMigration(@NotNull String message, Object... args) {
        debug(DebugCategory.MIGRATION, message, args);
    }

    public static void debugExpiry(@NotNull String message, Object... args) {
        debug(DebugCategory.EXPIRY, message, args);
    }

    // ==================== Category Management ====================

    /**
     * Enables or disables a specific debug category.
     *
     * @param category the category
     * @param enabled  true to enable, false to disable
     */
    public static void setDebugEnabled(@NotNull DebugCategory category, boolean enabled) {
        EnumSet<DebugCategory> copy = EnumSet.copyOf(enabledCategories.isEmpty()
                ? EnumSet.noneOf(DebugCategory.class) : enabledCategories);
        if (enabled) {
            copy.add(category);
        } else {
            copy.remove(category);
        }
        enabledCategories = copy;
    }

    /**
     * Checks if a debug category is enabled.
     *
     * @param category the category
     * @return true if enabled
     */
    public static boolean isDebugEnabled(@NotNull DebugCategory category) {
        return enabledCategories.contains(category);
    }

    /**
     * Enables all debug categories.
     */
    public static void enableAllDebug() {
        enabledCategories = EnumSet.allOf(DebugCategory.class);
        info("All debug categories ENABLED");
    }

    /**
     * Disables all debug categories.
     */
    public static void disableAllDebug() {
        enabledCategories = EnumSet.noneOf(DebugCategory.class);
        info("All debug categories DISABLED");
    }

    /**
     * Gets the set of currently enabled debug categories.
     *
     * @return unmodifiable set of enabled categories
     */
    @NotNull
    public static Set<DebugCategory> getEnabledCategories() {
        return Set.copyOf(enabledCategories);
    }

    /**
     * Checks if any debug category is currently enabled.
     *
     * @return true if at least one category is enabled
     */
    public static boolean isAnyDebugEnabled() {
        return !enabledCategories.isEmpty();
    }

    // ==================== Backward Compatibility ====================

    /**
     * Legacy method: enables or disables permission debug mode.
     * Now delegates to the RESOLUTION category.
     *
     * @param enabled true to enable permission debug logging
     */
    public static void setPermissionDebugEnabled(boolean enabled) {
        setDebugEnabled(DebugCategory.RESOLUTION, enabled);
        if (enabled) {
            info("Permission debug logging ENABLED - detailed permission checks will be logged");
        } else {
            info("Permission debug logging DISABLED");
        }
    }

    /**
     * Legacy method: checks if permission debug mode is enabled.
     * Now delegates to the RESOLUTION category.
     *
     * @return true if enabled
     */
    public static boolean isPermissionDebugEnabled() {
        return isDebugEnabled(DebugCategory.RESOLUTION);
    }

    // ==================== Clickable Links ====================

    /**
     * Logs an info message with a clickable link.
     * <p>
     * Uses OSC 8 escape sequences in supported terminals, or falls back to
     * displaying the URL in parentheses.
     *
     * @param message     the message (may contain %s for link text insertion)
     * @param url         the URL to link to
     * @param displayText the text to display for the link
     */
    public static void infoLink(@NotNull String message, @NotNull String url, @NotNull String displayText) {
        String linkText = ConsoleLinks.link(url, displayText);
        if (message.contains("%s")) {
            info(String.format(message, linkText));
        } else {
            info(message + " " + linkText);
        }
    }

    /**
     * Logs an info message with a clickable link using the URL as display text.
     *
     * @param message the message
     * @param url     the URL to link to and display
     */
    public static void infoLink(@NotNull String message, @NotNull String url) {
        infoLink(message, url, url);
    }
}
