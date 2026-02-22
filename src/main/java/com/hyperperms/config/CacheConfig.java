package com.hyperperms.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Cache configuration settings.
 * <p>
 * File: {@code cache.json}
 */
public final class CacheConfig extends ConfigFile {

    private boolean enabled;
    private int expirySeconds;
    private int maxSize;

    public CacheConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("cache.json"));
    }

    @Override
    protected void createDefaults() {
        enabled = true;
        expirySeconds = 300;
        maxSize = 10000;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        enabled = getBool(root, "enabled", true);
        expirySeconds = getInt(root, "expirySeconds", 300);
        maxSize = getInt(root, "maxSize", 10000);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("expirySeconds", expirySeconds);
        root.addProperty("maxSize", maxSize);
        return root;
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        expirySeconds = validateMin(result, "expirySeconds", expirySeconds, 1, 300);
        maxSize = validateMin(result, "maxSize", maxSize, 100, 10000);
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public int getExpirySeconds() { return expirySeconds; }
    public int getMaxSize() { return maxSize; }
}
