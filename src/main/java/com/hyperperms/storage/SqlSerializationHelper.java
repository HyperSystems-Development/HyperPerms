package com.hyperperms.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared serialization helpers for SQL-based storage providers.
 * <p>
 * Uses Gson for JSON serialization/deserialization to correctly handle
 * special characters in group names and context values.
 */
public final class SqlSerializationHelper {

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private SqlSerializationHelper() {}

    @NotNull
    public static List<String> parseGroupsList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> result = GSON.fromJson(json, STRING_LIST_TYPE);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            Logger.warn("Failed to parse groups JSON: %s", e.getMessage());
            return new ArrayList<>();
        }
    }

    @NotNull
    public static String serializeGroupsList(@NotNull List<String> groups) {
        return GSON.toJson(groups);
    }

    @NotNull
    public static String serializeContexts(ContextSet contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "[]";
        }
        List<String> entries = new ArrayList<>();
        for (Context ctx : contexts) {
            entries.add(ctx.key() + "=" + ctx.value());
        }
        return GSON.toJson(entries);
    }

    @NotNull
    public static ContextSet deserializeContexts(String json) {
        if (json == null || json.isBlank()) {
            return ContextSet.empty();
        }
        List<String> entries;
        try {
            entries = GSON.fromJson(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            Logger.warn("Failed to parse contexts JSON: %s", e.getMessage());
            return ContextSet.empty();
        }
        if (entries == null || entries.isEmpty()) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        for (String entry : entries) {
            if (entry.contains("=")) {
                try {
                    builder.add(Context.parse(entry));
                } catch (IllegalArgumentException e) {
                    Logger.warn("Skipping invalid context entry: %s", entry);
                }
            }
        }
        return builder.build();
    }
}
