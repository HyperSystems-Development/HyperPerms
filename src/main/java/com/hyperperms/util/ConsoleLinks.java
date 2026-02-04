package com.hyperperms.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for generating clickable hyperlinks in terminal output.
 * <p>
 * Uses OSC 8 escape sequences to create clickable links in supported terminals.
 * Falls back to displaying URLs in parentheses for unsupported terminals.
 * <p>
 * Supported terminals include:
 * <ul>
 *   <li>iTerm2</li>
 *   <li>Windows Terminal</li>
 *   <li>GNOME Terminal (VTE-based)</li>
 *   <li>Konsole</li>
 *   <li>kitty</li>
 *   <li>Hyper</li>
 *   <li>WezTerm</li>
 * </ul>
 */
public final class ConsoleLinks {

    private static volatile boolean osc8Supported = detectOsc8Support();
    private static volatile boolean enabled = true;
    private static volatile boolean forceOsc8 = false;

    private ConsoleLinks() {
        // Utility class
    }

    /**
     * Detects whether the current terminal supports OSC 8 hyperlinks.
     * <p>
     * Detection is based on environment variables set by popular terminals.
     *
     * @return true if OSC 8 is likely supported
     */
    private static boolean detectOsc8Support() {
        try {
            // iTerm2
            if (getEnv("ITERM_SESSION_ID") != null) {
                return true;
            }

            // Windows Terminal
            if (getEnv("WT_SESSION") != null) {
                return true;
            }

            // GNOME Terminal / VTE-based terminals (version 0.50+)
            String vteVersion = getEnv("VTE_VERSION");
            if (vteVersion != null) {
                try {
                    int version = Integer.parseInt(vteVersion);
                    if (version >= 5000) { // VTE 0.50.0+
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // Konsole
            if (getEnv("KONSOLE_VERSION") != null) {
                return true;
            }

            // kitty
            if (getEnv("KITTY_WINDOW_ID") != null) {
                return true;
            }

            // Hyper
            if ("Hyper".equals(getEnv("TERM_PROGRAM"))) {
                return true;
            }

            // WezTerm
            if (getEnv("WEZTERM_PANE") != null) {
                return true;
            }

            // Generic check for TERM_PROGRAM
            String termProgram = getEnv("TERM_PROGRAM");
            if (termProgram != null) {
                String lower = termProgram.toLowerCase();
                if (lower.contains("iterm") || lower.contains("wezterm") ||
                    lower.contains("kitty") || lower.contains("hyper")) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static String getEnv(@NotNull String name) {
        try {
            return System.getenv(name);
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * Checks if OSC 8 hyperlinks are supported in the current terminal.
     *
     * @return true if OSC 8 is supported
     */
    public static boolean isOsc8Supported() {
        return forceOsc8 || osc8Supported;
    }

    /**
     * Checks if console links are enabled.
     *
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables console links.
     *
     * @param enabled true to enable
     */
    public static void setEnabled(boolean enabled) {
        ConsoleLinks.enabled = enabled;
    }

    /**
     * Forces OSC 8 mode regardless of terminal detection.
     *
     * @param force true to force OSC 8
     */
    public static void setForceOsc8(boolean force) {
        ConsoleLinks.forceOsc8 = force;
    }

    /**
     * Re-runs terminal detection.
     * <p>
     * Useful if the terminal environment has changed.
     */
    public static void redetect() {
        osc8Supported = detectOsc8Support();
    }

    /**
     * Creates a clickable link for console output.
     * <p>
     * If OSC 8 is supported and enabled, returns an escape sequence that creates
     * a clickable link. Otherwise, returns the display text with the URL in
     * parentheses.
     *
     * @param url         the URL to link to
     * @param displayText the text to display
     * @return a formatted string with the link
     */
    @NotNull
    public static String link(@NotNull String url, @NotNull String displayText) {
        if (!enabled) {
            return displayText + " (" + url + ")";
        }

        if (forceOsc8 || osc8Supported) {
            // OSC 8 escape sequence format:
            // ESC ] 8 ; params ; URL ST text ESC ] 8 ; ; ST
            // Where ST (String Terminator) is ESC \
            return "\u001b]8;;" + url + "\u001b\\" + displayText + "\u001b]8;;\u001b\\";
        }

        // Fallback: show URL in parentheses
        return displayText + " (" + url + ")";
    }

    /**
     * Creates a clickable link using the URL as the display text.
     *
     * @param url the URL to link to and display
     * @return a formatted string with the link
     */
    @NotNull
    public static String link(@NotNull String url) {
        return link(url, url);
    }

    /**
     * Creates a clickable link with an ID for hover tooltips.
     * <p>
     * Some terminals support additional parameters like tooltip IDs.
     *
     * @param url         the URL to link to
     * @param displayText the text to display
     * @param id          an optional ID for the link
     * @return a formatted string with the link
     */
    @NotNull
    public static String link(@NotNull String url, @NotNull String displayText, @Nullable String id) {
        if (!enabled) {
            return displayText + " (" + url + ")";
        }

        if (forceOsc8 || osc8Supported) {
            String params = id != null && !id.isEmpty() ? "id=" + id : "";
            return "\u001b]8;" + params + ";" + url + "\u001b\\" + displayText + "\u001b]8;;\u001b\\";
        }

        return displayText + " (" + url + ")";
    }

    /**
     * Formats a URL for display, optionally as a clickable link.
     * <p>
     * This is the recommended method to use when outputting URLs to console.
     *
     * @param url   the URL
     * @param label a human-readable label for the link
     * @return formatted string
     */
    @NotNull
    public static String formatUrl(@NotNull String url, @NotNull String label) {
        if (!enabled) {
            return label + ": " + url;
        }

        if (forceOsc8 || osc8Supported) {
            return label + ": " + link(url);
        }

        return label + ": " + url;
    }

    /**
     * Gets the current terminal detection status as a debug string.
     *
     * @return debug information about terminal detection
     */
    @NotNull
    public static String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConsoleLinks Debug Info:\n");
        sb.append("  Enabled: ").append(enabled).append("\n");
        sb.append("  OSC8 Detected: ").append(osc8Supported).append("\n");
        sb.append("  Force OSC8: ").append(forceOsc8).append("\n");
        sb.append("  Effective OSC8: ").append(isOsc8Supported()).append("\n");
        sb.append("  Environment:\n");
        
        String[] vars = {"TERM_PROGRAM", "ITERM_SESSION_ID", "WT_SESSION", 
                         "VTE_VERSION", "KONSOLE_VERSION", "KITTY_WINDOW_ID", 
                         "WEZTERM_PANE"};
        for (String var : vars) {
            String value = getEnv(var);
            if (value != null) {
                sb.append("    ").append(var).append("=").append(value).append("\n");
            }
        }
        
        return sb.toString();
    }
}
