package com.hyperperms.config;

import com.google.gson.JsonObject;
import com.hyperperms.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Debug category configuration — persists enabled debug categories across restarts.
 * <p>
 * File: {@code debug.json}
 */
public final class DebugConfig extends ConfigFile {

    private final EnumSet<Logger.DebugCategory> enabledCategories = EnumSet.noneOf(Logger.DebugCategory.class);

    public DebugConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("debug.json"));
    }

    @Override
    protected void createDefaults() {
        enabledCategories.clear();
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        enabledCategories.clear();
        for (Logger.DebugCategory category : Logger.DebugCategory.values()) {
            String key = category.name().toLowerCase();
            if (getBool(root, key, false)) {
                enabledCategories.add(category);
            }
        }
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        for (Logger.DebugCategory category : Logger.DebugCategory.values()) {
            root.addProperty(category.name().toLowerCase(), enabledCategories.contains(category));
        }
        return root;
    }

    /**
     * Applies the persisted debug config state to the Logger.
     */
    public void applyToLogger() {
        for (Logger.DebugCategory category : Logger.DebugCategory.values()) {
            Logger.setDebugEnabled(category, enabledCategories.contains(category));
        }
    }

    /**
     * Syncs the current Logger state back into this config and saves.
     */
    public void syncFromLogger() {
        enabledCategories.clear();
        for (Logger.DebugCategory category : Logger.DebugCategory.values()) {
            if (Logger.isDebugEnabled(category)) {
                enabledCategories.add(category);
            }
        }
        save();
    }

    /**
     * Enables a category, updates the Logger, and saves.
     */
    public void setEnabled(@NotNull Logger.DebugCategory category, boolean enabled) {
        if (enabled) {
            enabledCategories.add(category);
        } else {
            enabledCategories.remove(category);
        }
        Logger.setDebugEnabled(category, enabled);
        save();
    }

    /**
     * Checks if a category is persisted as enabled.
     */
    public boolean isEnabled(@NotNull Logger.DebugCategory category) {
        return enabledCategories.contains(category);
    }
}
