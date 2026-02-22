package com.hyperperms.storage;

import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared serialization helpers for SQL-based storage providers.
 */
public final class SqlSerializationHelper {

    private SqlSerializationHelper() {}

    @NotNull
    public static List<String> parseGroupsList(String json) {
        List<String> groups = new ArrayList<>();
        if (json != null && json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1).trim();
            if (!content.isEmpty()) {
                for (String item : content.split(",")) {
                    String trimmed = item.trim();
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        groups.add(trimmed.substring(1, trimmed.length() - 1));
                    }
                }
            }
        }
        return groups;
    }

    @NotNull
    public static String serializeGroupsList(@NotNull List<String> groups) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(groups.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    @NotNull
    public static String serializeContexts(ContextSet contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Context ctx : contexts) {
            if (!first) sb.append(",");
            sb.append("\"").append(ctx.key()).append("=").append(ctx.value()).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @NotNull
    public static ContextSet deserializeContexts(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) {
            return ContextSet.empty();
        }
        String content = json.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1).trim();
        }
        if (content.isEmpty()) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        for (String item : content.split(",")) {
            String trimmed = item.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.contains("=")) {
                try {
                    builder.add(Context.parse(trimmed));
                } catch (IllegalArgumentException e) {
                    Logger.warn("Skipping invalid context entry: " + trimmed);
                }
            }
        }
        return builder.build();
    }
}
