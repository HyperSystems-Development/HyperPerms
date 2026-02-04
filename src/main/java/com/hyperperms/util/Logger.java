package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Wrapped logger with HyperPerms prefix and formatting.
 */
public final class Logger {

    private static final String PREFIX = "[HyperPerms] ";
    private static java.util.logging.Logger logger;

    /**
     * Enable this flag to output detailed permission check logs at INFO level.
     * Useful for debugging permission issues between plugins.
     * Set via /hp debug command or programmatically.
     */
    private static volatile boolean permissionDebugEnabled = false;

    private Logger() {}

    /**
     * Initializes the logger.
     *
     * @param parentLogger the parent logger from the plugin
     */
    public static void init(@NotNull java.util.logging.Logger parentLogger) {
        logger = parentLogger;
    }

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

    /**
     * Logs a debug message (only if debug is enabled).
     *
     * @param message the message
     */
    public static void debug(@NotNull String message) {
        if (permissionDebugEnabled) {
            // When permission debug is enabled, log at INFO level for visibility
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

    /**
     * Enables or disables permission debug mode.
     * When enabled, all debug logs are output at INFO level for easy visibility.
     *
     * @param enabled true to enable permission debug logging
     */
    public static void setPermissionDebugEnabled(boolean enabled) {
        permissionDebugEnabled = enabled;
        if (enabled) {
            info("Permission debug logging ENABLED - detailed permission checks will be logged");
        } else {
            info("Permission debug logging DISABLED");
        }
    }

    /**
     * Checks if permission debug mode is enabled.
     *
     * @return true if enabled
     */
    public static boolean isPermissionDebugEnabled() {
        return permissionDebugEnabled;
    }

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
