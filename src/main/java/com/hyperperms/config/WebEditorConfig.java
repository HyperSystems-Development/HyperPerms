package com.hyperperms.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Web editor service configuration.
 * <p>
 * File: {@code webeditor.json}
 */
public final class WebEditorConfig extends ConfigFile {

    private String url;
    private String apiUrl;
    private int timeoutSeconds;

    public WebEditorConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("webeditor.json"));
    }

    @Override
    protected void createDefaults() {
        url = "https://www.hyperperms.com";
        apiUrl = "";
        timeoutSeconds = 10;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        url = getString(root, "url", "https://www.hyperperms.com");
        apiUrl = getString(root, "apiUrl", "");
        timeoutSeconds = getInt(root, "timeoutSeconds", 10);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("url", url);
        root.addProperty("apiUrl", apiUrl);
        root.addProperty("timeoutSeconds", timeoutSeconds);
        return root;
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        timeoutSeconds = validateRange(result, "timeoutSeconds", timeoutSeconds, 1, 120, 10);
        return result;
    }

    @NotNull
    public String getUrl() { return url; }

    /**
     * Gets the API URL. Falls back to main URL if not configured.
     */
    @NotNull
    public String getApiUrl() {
        return apiUrl.isEmpty() ? url : apiUrl;
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
}
