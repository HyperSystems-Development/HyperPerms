package com.hyperperms.command.suggest;

import com.hyperperms.HyperPerms;
import com.hyperperms.HyperPermsBootstrap;
import com.hyperperms.platform.HyperPermsPlugin;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builds Hytale {@link SuggestionProvider}s backed by live HyperPerms data for each
 * {@link ArgKind}. All prefix matching is delegated to {@link SuggestionFilter} (which is
 * pure and unit-tested); this class is the thin Hytale-facing adapter and is verified by
 * the dual-channel build + smoke testing.
 */
public final class HpSuggestionProviders {

    /** Cap to avoid flooding the client; the result is marked truncated past this. */
    private static final int MAX_SUGGESTIONS = 50;

    private HpSuggestionProviders() {}

    /**
     * Returns a suggestion provider for the given kind, or {@code null} for
     * {@link ArgKind#STRING} (no suggestions — preserves prior behaviour).
     *
     * @param kind   the argument kind declared on the annotation
     * @param plugin the HyperPerms instance used to source candidates
     */
    public static SuggestionProvider forKind(ArgKind kind, HyperPerms plugin) {
        final Supplier<Collection<String>> source = candidateSource(kind, plugin);
        if (source == null) {
            return null;
        }
        return (sender, textAlreadyEntered, numParametersTyped, result) -> {
            List<String> matches = SuggestionFilter.filter(safeGet(source), textAlreadyEntered);
            // Cap to avoid flooding the client. (SuggestionResult.markTruncated() is pre-release
            // only, so we don't flag truncation — keeping a single source for both channels.)
            int count = Math.min(matches.size(), MAX_SUGGESTIONS);
            for (int i = 0; i < count; i++) {
                result.suggest(matches.get(i));
            }
        };
    }

    private static Supplier<Collection<String>> candidateSource(ArgKind kind, HyperPerms plugin) {
        return switch (kind) {
            case GROUP -> () -> plugin.getGroupManager().getGroupNames();
            case TRACK -> () -> plugin.getTrackManager().getTrackNames();
            case NODE -> () -> knownNodes(plugin);
            case PLAYER -> HpSuggestionProviders::onlinePlayerNames;
            case STRING -> null;
        };
    }

    /** Registered permission nodes plus everything seen at runtime via discovery. */
    private static Collection<String> knownNodes(HyperPerms plugin) {
        List<String> nodes = new ArrayList<>();
        plugin.getPermissionRegistry().getAll().forEach(info -> nodes.add(info.getPermission()));
        var discovery = plugin.getRuntimeDiscovery();
        if (discovery != null) {
            nodes.addAll(discovery.getDiscoveredPermissions().keySet());
        }
        return nodes;
    }

    /** Online usernames via the platform adapter (reached through the bootstrap holder). */
    private static Collection<String> onlinePlayerNames() {
        Object pluginObj = HyperPermsBootstrap.getPlugin();
        if (pluginObj instanceof HyperPermsPlugin hp && hp.getAdapter() != null) {
            return hp.getAdapter().getOnlineUsernames();
        }
        return Collections.emptyList();
    }

    private static Collection<String> safeGet(Supplier<Collection<String>> source) {
        try {
            Collection<String> c = source.get();
            return c != null ? c : Collections.emptyList();
        } catch (Exception e) {
            Logger.debug("Suggestion source failed: %s", e.getMessage());
            return Collections.emptyList();
        }
    }
}
