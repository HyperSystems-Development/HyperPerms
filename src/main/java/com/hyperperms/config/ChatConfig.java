package com.hyperperms.config;

import com.google.gson.JsonObject;
import com.hyperperms.util.Permissions;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Chat formatting configuration settings.
 * <p>
 * File: {@code chat.json}
 */
public final class ChatConfig extends ConfigFile {

    private boolean enabled;
    private String format;
    private boolean allowPlayerColors;
    private String colorPermission;

    // Tab list
    private boolean tabListEnabled;
    private String tabListFormat;
    private boolean tabListSortByWeight;
    private int tabListUpdateIntervalTicks;

    public ChatConfig(@NotNull Path dataDirectory) {
        super(dataDirectory.resolve("chat.json"));
    }

    @Override
    protected void createDefaults() {
        enabled = true;
        format = "%prefix%%player%%suffix%&8: &f%message%";
        allowPlayerColors = true;
        colorPermission = Permissions.CHAT_COLOR;
        tabListEnabled = true;
        tabListFormat = "%prefix%%player%";
        tabListSortByWeight = true;
        tabListUpdateIntervalTicks = 20;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        enabled = getBool(root, "enabled", true);
        format = getString(root, "format", "%prefix%%player%%suffix%&8: &f%message%");
        allowPlayerColors = getBool(root, "allowPlayerColors", true);
        colorPermission = getString(root, "colorPermission", "hyperperms.chat.color");

        JsonObject tabList = getSection(root, "tabList");
        tabListEnabled = getBool(tabList, "enabled", true);
        tabListFormat = getString(tabList, "format", "%prefix%%player%");
        tabListSortByWeight = getBool(tabList, "sortByWeight", true);
        tabListUpdateIntervalTicks = getInt(tabList, "updateIntervalTicks", 20);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("format", format);
        root.addProperty("allowPlayerColors", allowPlayerColors);
        root.addProperty("colorPermission", colorPermission);

        JsonObject tabList = new JsonObject();
        tabList.addProperty("enabled", tabListEnabled);
        tabList.addProperty("format", tabListFormat);
        tabList.addProperty("sortByWeight", tabListSortByWeight);
        tabList.addProperty("updateIntervalTicks", tabListUpdateIntervalTicks);
        root.add("tabList", tabList);

        return root;
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        tabListUpdateIntervalTicks = validateMin(result, "tabList.updateIntervalTicks",
                tabListUpdateIntervalTicks, 1, 20);
        return result;
    }

    public boolean isEnabled() { return enabled; }
    @NotNull public String getFormat() { return format; }
    public boolean isAllowPlayerColors() { return allowPlayerColors; }
    @NotNull public String getColorPermission() { return colorPermission; }
    public boolean isTabListEnabled() { return tabListEnabled; }
    @NotNull public String getTabListFormat() { return tabListFormat; }
    public boolean isTabListSortByWeight() { return tabListSortByWeight; }
    public int getTabListUpdateIntervalTicks() { return tabListUpdateIntervalTicks; }
}
