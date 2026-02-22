package com.hyperperms.resolver;

import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Matches permission nodes against wildcard patterns with negation support.
 * <p>
 * This implementation follows Hytale's native permission resolution order.
 * <p>
 * Wildcard patterns:
 * <ul>
 *   <li>{@code *} - matches any permission (universal grant)</li>
 *   <li>{@code plugin.*} - matches any permission starting with "plugin."</li>
 *   <li>{@code plugin.command.*} - matches "plugin.command.home", etc.</li>
 * </ul>
 * <p>
 * Negation patterns (prefix with '-'):
 * <ul>
 *   <li>{@code -plugin.admin} - explicitly denies "plugin.admin"</li>
 *   <li>{@code -plugin.admin.*} - denies all permissions under "plugin.admin."</li>
 *   <li>{@code -*} - universal negation (denies everything)</li>
 * </ul>
 * <p>
 * <strong>Resolution Order (Hytale-native):</strong>
 * <ol>
 *   <li>Global wildcard (*) - grants everything, checked FIRST</li>
 *   <li>Global negation (-*) - denies everything</li>
 *   <li>Exact permissions (grant checked before deny)</li>
 *   <li>Prefix wildcards (shortest prefix first: "a.*" before "a.b.*")</li>
 * </ol>
 * <p>
 * <strong>Wildcard Restrictions:</strong> Middle wildcards (e.g., {@code hytale.*.ban})
 * are <b>not</b> supported. The {@code *} in such patterns is treated as a literal
 * character, not a wildcard. This matches vanilla Hytale behavior. Wildcards are only
 * valid in two positions:
 * <ul>
 *   <li>Standalone: {@code *} (grant all) or {@code -*} (deny all)</li>
 *   <li>Trailing: {@code prefix.*} (grant all under prefix) or {@code -prefix.*} (deny all under prefix)</li>
 * </ul>
 * <p>
 * <strong>Important:</strong> With this resolution order, {@code ["*", "-ban"]} checking
 * {@code ban} returns TRUE because global {@code *} is checked first and wins.
 * To deny specific permissions, avoid using global {@code *} and use more specific
 * grants like {@code admin.*} combined with negations.
 */
public final class WildcardMatcher {

    private WildcardMatcher() {}

    /**
     * Checks if a permission matches a wildcard pattern.
     *
     * @param permission the permission to check
     * @param pattern    the wildcard pattern
     * @return true if matches
     */
    public static boolean matches(@NotNull String permission, @NotNull String pattern) {
        Objects.requireNonNull(permission, "permission cannot be null");
        Objects.requireNonNull(pattern, "pattern cannot be null");

        if (permission.isEmpty() || pattern.isEmpty()) {
            return permission.equals(pattern);
        }

        // Exact match
        if (permission.equals(pattern)) {
            return true;
        }

        // Universal wildcard
        if (pattern.equals("*")) {
            return true;
        }

        // Prefix wildcard (plugin.* matches plugin.command.home)
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // Remove the *
            return permission.startsWith(prefix);
        }

        return false;
    }

    /**
     * Common package prefixes that are often stripped in user-friendly permission names.
     * For example, "com.hyperwarps.command" might be referred to as just "hyperwarps.command".
     */
    private static final String[] COMMON_PREFIXES = {"com.", "net.", "org.", "io.", "me."};

    @NotNull
    public static TriState check(@NotNull String permission, @NotNull Map<String, Boolean> values) {
        Objects.requireNonNull(permission, "permission cannot be null");
        Objects.requireNonNull(values, "values cannot be null");

        if (permission.isEmpty()) {
            return TriState.UNDEFINED;
        }

        String lowerPerm = permission.toLowerCase();

        // Build stripped versions upfront (e.g., com.plugin.cmd -> plugin.cmd)
        java.util.List<String> strippedVersions = new java.util.ArrayList<>();
        for (String prefix : COMMON_PREFIXES) {
            if (lowerPerm.startsWith(prefix)) {
                strippedVersions.add(lowerPerm.substring(prefix.length()));
            }
        }

        // === HYTALE RESOLUTION ORDER ===

        // 1. Global wildcard (*) - checked FIRST, grants everything
        if (values.getOrDefault("*", false)) {
            return TriState.TRUE;
        }

        // 2. Global negation (-*) - denies everything
        if (values.getOrDefault("-*", false)) {
            return TriState.FALSE;
        }

        // 3. Exact permissions (grant first, then deny)
        // 3a. Exact grant (original)
        if (values.containsKey(lowerPerm)) {
            return values.get(lowerPerm) ? TriState.TRUE : TriState.FALSE;
        }
        // 3b. Exact negation (original)
        if (values.containsKey("-" + lowerPerm)) {
            return values.get("-" + lowerPerm) ? TriState.FALSE : TriState.TRUE;
        }
        // 3c. Exact grant/deny (stripped versions)
        for (String stripped : strippedVersions) {
            if (values.containsKey(stripped)) {
                return values.get(stripped) ? TriState.TRUE : TriState.FALSE;
            }
            if (values.containsKey("-" + stripped)) {
                return values.get("-" + stripped) ? TriState.FALSE : TriState.TRUE;
            }
        }

        // 4. Prefix wildcards (shortest prefix first: "a.*" before "a.b.*")
        String[] parts = lowerPerm.split("\\.");
        for (int prefixLen = 1; prefixLen < parts.length; prefixLen++) {
            String prefix = buildWildcardFromLength(parts, prefixLen);

            // Grant first, then deny at each level
            if (values.getOrDefault(prefix, false)) {
                return TriState.TRUE;
            }
            if (values.getOrDefault("-" + prefix, false)) {
                return TriState.FALSE;
            }
            // Also check value=false format (negation stored as wildcard=false)
            if (values.containsKey(prefix) && !values.get(prefix)) {
                return TriState.FALSE;
            }
        }

        // 4b. Prefix wildcards (stripped versions, shortest first)
        for (String stripped : strippedVersions) {
            String[] strippedParts = stripped.split("\\.");
            for (int prefixLen = 1; prefixLen < strippedParts.length; prefixLen++) {
                String prefix = buildWildcardFromLength(strippedParts, prefixLen);

                if (values.getOrDefault(prefix, false)) {
                    return TriState.TRUE;
                }
                if (values.getOrDefault("-" + prefix, false)) {
                    return TriState.FALSE;
                }
                if (values.containsKey(prefix) && !values.get(prefix)) {
                    return TriState.FALSE;
                }
            }
        }

        return TriState.UNDEFINED;
    }

    /**
     * Builds a wildcard pattern with a specific prefix length.
     * For parts ["a", "b", "c"] with prefixLen=2, returns "a.b.*"
     */
    private static String buildWildcardFromLength(String[] parts, int prefixLen) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < prefixLen; j++) {
            sb.append(parts[j]).append(".");
        }
        sb.append("*");
        return sb.toString();
    }

    

    /**
     * Checks a permission with detailed match information for tracing.
     * <p>
     * Follows Hytale's resolution order:
     * <ol>
     *   <li>Global wildcard (*) - checked FIRST</li>
     *   <li>Global negation (-*)</li>
     *   <li>Exact permissions (grant before deny)</li>
     *   <li>Prefix wildcards (shortest prefix first)</li>
     * </ol>
     *
     * @param permission the permission to check
     * @param values     map of permission patterns to their values
     * @return the match result with trace information
     */
    @NotNull
    public static MatchResult checkWithTrace(@NotNull String permission, @NotNull Map<String, Boolean> values) {
        Objects.requireNonNull(permission, "permission cannot be null");
        Objects.requireNonNull(values, "values cannot be null");

        if (permission.isEmpty()) {
            return new MatchResult(TriState.UNDEFINED, null, MatchType.NONE);
        }

        String lowerPerm = permission.toLowerCase();

        // === HYTALE RESOLUTION ORDER ===

        // 1. Global wildcard (*) - checked FIRST
        if (values.getOrDefault("*", false)) {
            return new MatchResult(TriState.TRUE, "*", MatchType.UNIVERSAL);
        }

        // 2. Global negation (-*)
        if (values.getOrDefault("-*", false)) {
            return new MatchResult(TriState.FALSE, "-*", MatchType.UNIVERSAL_NEGATION);
        }

        // 3. Exact permissions (grant first, then deny)
        if (values.containsKey(lowerPerm)) {
            boolean val = values.get(lowerPerm);
            return new MatchResult(
                    val ? TriState.TRUE : TriState.FALSE,
                    lowerPerm,
                    MatchType.EXACT
            );
        }

        String negated = "-" + lowerPerm;
        if (values.containsKey(negated)) {
            boolean val = values.get(negated);
            return new MatchResult(
                    val ? TriState.FALSE : TriState.TRUE,
                    negated,
                    MatchType.EXACT_NEGATION
            );
        }

        // 4. Prefix wildcards (shortest prefix first)
        String[] parts = lowerPerm.split("\\.");
        for (int prefixLen = 1; prefixLen < parts.length; prefixLen++) {
            String wildcard = buildWildcardFromLength(parts, prefixLen);

            // Grant first at each level
            if (values.getOrDefault(wildcard, false)) {
                return new MatchResult(TriState.TRUE, wildcard, MatchType.WILDCARD);
            }

            // Then deny
            String negatedWildcard = "-" + wildcard;
            if (values.getOrDefault(negatedWildcard, false)) {
                return new MatchResult(TriState.FALSE, negatedWildcard, MatchType.WILDCARD_NEGATION);
            }

            // Also check value=false format
            if (values.containsKey(wildcard) && !values.get(wildcard)) {
                return new MatchResult(TriState.FALSE, wildcard, MatchType.WILDCARD);
            }
        }

        return new MatchResult(TriState.UNDEFINED, null, MatchType.NONE);
    }

    /**
     * Builds a wildcard pattern from permission parts.
     *
     * @param parts the permission parts
     * @param count the number of parts to include before the wildcard
     * @return the wildcard pattern
     */
    private static String buildWildcard(String[] parts, int count) {
        if (count == 0) {
            return "*";
        }
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < count; j++) {
            sb.append(parts[j]).append(".");
        }
        sb.append("*");
        return sb.toString();
    }

    /**
     * Generates all possible wildcard patterns for a permission.
     * <p>
     * For "plugin.command.home", returns:
     * ["plugin.command.home", "plugin.command.*", "plugin.*", "*"]
     *
     * @param permission the permission
     * @return array of patterns from most to least specific
     */
    @NotNull
    public static String[] generatePatterns(@NotNull String permission) {
        if (permission.isEmpty()) {
            return new String[]{ permission, "*" };
        }
        String[] parts = permission.split("\\.");
        if (parts.length == 0) {
            return new String[]{ permission, "*" };
        }
        String[] patterns = new String[parts.length + 1];

        patterns[0] = permission; // Exact match

        for (int i = 1; i <= parts.length; i++) {
            patterns[i] = buildWildcard(parts, parts.length - i);
        }

        return patterns;
    }

    /**
     * Tri-state result for permission checks.
     */
    public enum TriState {
        /**
         * Permission is explicitly granted.
         */
        TRUE,

        /**
         * Permission is explicitly denied.
         */
        FALSE,

        /**
         * Permission is not set (undefined).
         */
        UNDEFINED;

        /**
         * Converts to boolean, treating undefined as false.
         *
         * @return true if TRUE, false otherwise
         */
        public boolean asBoolean() {
            return this == TRUE;
        }

        /**
         * Converts to boolean with a default value for undefined.
         *
         * @param defaultValue the default value for undefined
         * @return the boolean value
         */
        public boolean asBoolean(boolean defaultValue) {
            return switch (this) {
                case TRUE -> true;
                case FALSE -> false;
                case UNDEFINED -> defaultValue;
            };
        }
    }

    /**
     * Types of permission matches.
     */
    public enum MatchType {
        /**
         * No match found.
         */
        NONE,

        /**
         * Exact permission match.
         */
        EXACT,

        /**
         * Exact negation match (-permission).
         */
        EXACT_NEGATION,

        /**
         * Wildcard match (prefix.*).
         */
        WILDCARD,

        /**
         * Negated wildcard match (-prefix.*).
         */
        WILDCARD_NEGATION,

        /**
         * Universal wildcard (*).
         */
        UNIVERSAL,

        /**
         * Universal negation (-*).
         */
        UNIVERSAL_NEGATION
    }

    /**
     * Result of a permission check with match information.
     *
     * @param result      the tri-state result
     * @param matchedNode the permission node that caused the match, or null
     * @param matchType   the type of match
     */
    public record MatchResult(
            @NotNull TriState result,
            @Nullable String matchedNode,
            @NotNull MatchType matchType
    ) {
        /**
         * Checks if this result represents a match (not NONE).
         *
         * @return true if matched
         */
        public boolean isMatched() {
            return matchType != MatchType.NONE;
        }

        /**
         * Checks if this result is from a negation.
         *
         * @return true if negation match
         */
        public boolean isNegation() {
            return matchType == MatchType.EXACT_NEGATION ||
                   matchType == MatchType.WILDCARD_NEGATION ||
                   matchType == MatchType.UNIVERSAL_NEGATION;
        }

        /**
         * Checks if this result is from a wildcard.
         *
         * @return true if wildcard match
         */
        public boolean isWildcard() {
            return matchType == MatchType.WILDCARD ||
                   matchType == MatchType.WILDCARD_NEGATION ||
                   matchType == MatchType.UNIVERSAL ||
                   matchType == MatchType.UNIVERSAL_NEGATION;
        }
    }
}
