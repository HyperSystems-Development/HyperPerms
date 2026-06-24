package com.hyperperms.command.suggest;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure prefix-matching logic shared by all HyperPerms command suggestion providers.
 * <p>
 * Deliberately free of any Hytale API types so it can be unit-tested without the
 * (compile-only) server dependency on the test classpath. The Hytale
 * {@code SuggestionProvider} adapters in {@code HpSuggestionProviders} delegate here.
 */
public final class SuggestionFilter {

    /** Case-insensitive primary order with a natural-order tiebreaker for deterministic output. */
    private static final Comparator<String> ORDER =
            Comparator.comparing((String s) -> s, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Comparator.naturalOrder());

    private SuggestionFilter() {}

    /**
     * Returns the candidates that begin with {@code typed} (case-insensitive), sorted
     * case-insensitively. A {@code null}/blank {@code typed} returns every candidate.
     * {@code null} candidates yield an empty list.
     *
     * @param candidates the full candidate set (e.g. group names, online players)
     * @param typed      the text already entered for the argument
     * @return the matching suggestions, sorted; never {@code null}
     */
    public static List<String> filter(Collection<String> candidates, String typed) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String prefix = typed == null ? "" : typed.trim();
        String lowerPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
        return candidates.stream()
                .filter(c -> c != null
                        && (lowerPrefix.isEmpty()
                            || c.toLowerCase(java.util.Locale.ROOT).startsWith(lowerPrefix)))
                .sorted(ORDER)
                .collect(Collectors.toList());
    }
}
