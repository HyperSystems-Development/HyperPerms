# Phase 3: Chat & Display System Rewrite — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rewrite the chat and tab list system with a shared formatting base, single-pass color parser, per-rank chat format support, and direct API integrations (no more reflection for WerChat/HyperFactions).

**Architecture:** A `DisplayFormatter` abstract base provides shared placeholder resolution, color processing, and Caffeine caching. `ChatManager` and `TabListManager` extend it, inheriting common logic. A `ColorParser` state machine replaces the 5-regex chain in `ColorUtil`. A `PluginBridge` interface standardizes all plugin integrations. `ChatConfig` gains per-rank format support.

**Tech Stack:** Java 25, Caffeine (existing dep), GSON (existing dep)

**Worktree:** `.worktrees/architecture-rehaul` on branch `refactor/architecture-rehaul`

**Git Author:** `ZenithDevHQ <scrubc1ty4ever@gmail.com>`

**Depends on:** Phase 1 (ServiceContainer for integration registration)

---

### Task 1: Create ColorParser — single-pass state machine

**Files:**
- Create: `src/main/java/com/hyperperms/chat/ColorParser.java`

**Step 1: Create the ColorParser**

The parser processes a string in one pass, handling all color format types:
- Legacy codes: `&0-9`, `&a-f`, `&k-o`, `&r` → `§` equivalents
- Short hex: `&#RGB` → `§x§R§R§G§G§B§B`
- Full hex: `&#RRGGBB` → `§x§R§R§G§G§B§B`
- Bukkit hex: `&x&R&R&G&G&B&B` → `§x§R§R§G§G§B§B`
- Named colors: `{red}`, `{gold}`, `{aqua}`, etc. → hex values
- Gradients: `<gradient:#RRGGBB:#RRGGBB>text</gradient>` → per-character interpolated hex
- Rainbow: `<rainbow>text</rainbow>` → HSB-based per-character cycling

```java
package com.hyperperms.chat;

import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single-pass color parser that handles all HyperPerms color formats.
 * Replaces the multi-regex chain in ColorUtil for better performance.
 */
public final class ColorParser {

    // Named color lookup (16 standard + 20 extended)
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            // Standard 16
            Map.entry("black", "000000"), Map.entry("dark_blue", "0000AA"),
            Map.entry("dark_green", "00AA00"), Map.entry("dark_aqua", "00AAAA"),
            Map.entry("dark_red", "AA0000"), Map.entry("dark_purple", "AA00AA"),
            Map.entry("gold", "FFAA00"), Map.entry("gray", "AAAAAA"),
            Map.entry("dark_gray", "555555"), Map.entry("blue", "5555FF"),
            Map.entry("green", "55FF55"), Map.entry("aqua", "55FFFF"),
            Map.entry("red", "FF5555"), Map.entry("light_purple", "FF55FF"),
            Map.entry("yellow", "FFFF55"), Map.entry("white", "FFFFFF"),
            // Extended palette
            Map.entry("orange", "FF8C00"), Map.entry("lime", "32CD32"),
            Map.entry("teal", "008080"), Map.entry("indigo", "4B0082"),
            Map.entry("pink", "FF69B4"), Map.entry("cyan", "00CED1"),
            Map.entry("magenta", "FF00FF"), Map.entry("brown", "8B4513"),
            Map.entry("coral", "FF7F50"), Map.entry("crimson", "DC143C"),
            Map.entry("emerald", "50C878"), Map.entry("ivory", "FFFFF0"),
            Map.entry("lavender", "E6E6FA"), Map.entry("maroon", "800000"),
            Map.entry("navy", "000080"), Map.entry("olive", "808000"),
            Map.entry("peach", "FFDAB9"), Map.entry("salmon", "FA8072"),
            Map.entry("silver", "C0C0C0"), Map.entry("violet", "EE82EE")
    );

    // Pre-compiled patterns for gradient and rainbow (these need regex since they span content)
    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>(.*?)</gradient>");
    private static final Pattern RAINBOW_PATTERN =
            Pattern.compile("<rainbow>(.*?)</rainbow>");

    private ColorParser() {}

    /**
     * Parse all color codes in the input string in a single pass.
     *
     * @param input the raw string with color codes
     * @return the string with all color codes converted to § format
     */
    @NotNull
    public static String parse(@NotNull String input) {
        // Phase 1: Handle gradient and rainbow (these are structural, need regex)
        String result = processGradients(input);
        result = processRainbow(result);

        // Phase 2: Single-pass scan for &codes, &#hex, {named}
        return scanAndReplace(result);
    }

    /**
     * Single-pass scanner for &codes, &#hex, {named} colors.
     */
    private static String scanAndReplace(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int len = input.length();
        int i = 0;

        while (i < len) {
            char c = input.charAt(i);

            // Named color: {name}
            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end != -1) {
                    String name = input.substring(i + 1, end).toLowerCase();
                    String hex = NAMED_COLORS.get(name);
                    if (hex != null) {
                        out.append(hexToSection(hex));
                        i = end + 1;
                        continue;
                    }
                }
                out.append(c);
                i++;
                continue;
            }

            // & codes
            if (c == '&' && i + 1 < len) {
                char next = input.charAt(i + 1);

                // &#RRGGBB or &#RGB
                if (next == '#' && i + 2 < len) {
                    // Try 6-char hex
                    if (i + 8 <= len && isHexString(input, i + 2, 6)) {
                        out.append(hexToSection(input.substring(i + 2, i + 8)));
                        i += 8;
                        continue;
                    }
                    // Try 3-char hex
                    if (i + 5 <= len && isHexString(input, i + 2, 3)) {
                        String shortHex = input.substring(i + 2, i + 5);
                        String fullHex = "" + shortHex.charAt(0) + shortHex.charAt(0)
                                + shortHex.charAt(1) + shortHex.charAt(1)
                                + shortHex.charAt(2) + shortHex.charAt(2);
                        out.append(hexToSection(fullHex));
                        i += 5;
                        continue;
                    }
                }

                // Bukkit hex: &x&R&R&G&G&B&B
                if (next == 'x' && i + 13 < len) {
                    boolean valid = true;
                    StringBuilder hex = new StringBuilder(6);
                    for (int j = 0; j < 6; j++) {
                        int pos = i + 2 + (j * 2);
                        if (pos + 1 < len && input.charAt(pos) == '&' && isHexChar(input.charAt(pos + 1))) {
                            hex.append(input.charAt(pos + 1));
                        } else {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        out.append(hexToSection(hex.toString()));
                        i += 14;
                        continue;
                    }
                }

                // Legacy codes: &0-9, &a-f, &k-o, &r
                if (isLegacyCode(next)) {
                    out.append('\u00A7').append(Character.toLowerCase(next));
                    i += 2;
                    continue;
                }
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }

    private static String processGradients(String input) {
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Color start = Color.decode(matcher.group(1));
            Color end = Color.decode(matcher.group(2));
            String text = matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(applyGradient(text, start, end)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String processRainbow(String input) {
        Matcher matcher = RAINBOW_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String text = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(applyRainbow(text)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String applyGradient(String text, Color start, Color end) {
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float ratio = len > 1 ? (float) i / (len - 1) : 0;
            int r = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
            int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
            int b = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
            sb.append(hexToSection(String.format("%02x%02x%02x", r, g, b)));
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private static String applyRainbow(String text) {
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float hue = (float) i / Math.max(len, 1);
            Color color = Color.getHSBColor(hue, 1.0f, 1.0f);
            sb.append(hexToSection(String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue())));
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    /** Convert a 6-char hex string to §x§R§R§G§G§B§B format. */
    private static String hexToSection(String hex) {
        StringBuilder sb = new StringBuilder(14);
        sb.append('\u00A7').append('x');
        for (char c : hex.toCharArray()) {
            sb.append('\u00A7').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static boolean isHexString(String s, int start, int length) {
        if (start + length > s.length()) return false;
        for (int i = start; i < start + length; i++) {
            if (!isHexChar(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isLegacyCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || c == 'k' || c == 'K' || c == 'l' || c == 'L' || c == 'm' || c == 'M'
                || c == 'n' || c == 'N' || c == 'o' || c == 'O' || c == 'r' || c == 'R';
    }
}
```

**Important:** Compare the named colors map with the existing `ColorUtil.java` to ensure all entries match exactly. The implementer must read `ColorUtil.java` and verify nothing is missing.

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/chat/ColorParser.java
git commit -m "feat(chat): add single-pass ColorParser state machine

Replaces multi-regex chain for &codes, &#hex, {named}, gradients,
and rainbow colors. One pass for simple codes, regex only for
structural patterns (gradient/rainbow)."
```

---

### Task 2: Create DisplayFormatter shared base

**Files:**
- Create: `src/main/java/com/hyperperms/chat/DisplayFormatter.java`

**Step 1: Create the abstract base**

Extract common logic from ChatManager and TabListManager:
- Placeholder registration and resolution
- Caffeine-based display data caching (replaces manual ConcurrentHashMap+TTL)
- Color processing via ColorParser
- Prefix/suffix resolution delegation

```java
package com.hyperperms.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hyperperms.HyperPerms;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Abstract base for chat and tab list formatting.
 * Provides shared placeholder resolution, color processing, and caching.
 */
public abstract class DisplayFormatter {

    protected final HyperPerms plugin;
    protected final PrefixSuffixResolver prefixSuffixResolver;
    protected final ChatFormatter chatFormatter;

    /** Caffeine cache for resolved display data. Max 500 entries, 5s expiry. */
    protected final Cache<UUID, DisplayData> displayDataCache;

    /** Custom placeholder providers. */
    protected final Map<String, BiFunction<UUID, String, String>> customPlaceholders = new ConcurrentHashMap<>();

    protected DisplayFormatter(@NotNull HyperPerms plugin, @NotNull PrefixSuffixResolver resolver) {
        this.plugin = plugin;
        this.prefixSuffixResolver = resolver;
        this.chatFormatter = new ChatFormatter();
        this.displayDataCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Register a custom placeholder provider.
     */
    public void registerCustomPlaceholder(@NotNull String name,
                                           @NotNull BiFunction<UUID, String, String> provider) {
        customPlaceholders.put(name.toLowerCase(), provider);
        chatFormatter.registerPlaceholder(name, provider);
    }

    /**
     * Resolve display data (prefix, suffix, group) for a player.
     * Uses Caffeine cache with 5s TTL.
     */
    @NotNull
    protected DisplayData resolveDisplayData(@NotNull UUID uuid, @NotNull String playerName) {
        DisplayData cached = displayDataCache.getIfPresent(uuid);
        if (cached != null) return cached;

        User user = plugin.getUserManager().getUser(uuid);
        if (user == null) {
            user = plugin.getUserManager().loadUser(uuid).join();
        }

        String prefix = "";
        String suffix = "";
        String group = plugin.getConfig().getDefaultGroup();

        if (user != null) {
            prefix = prefixSuffixResolver.resolvePrefix(user);
            suffix = prefixSuffixResolver.resolveSuffix(user);
            group = user.getPrimaryGroup() != null ? user.getPrimaryGroup() : group;
        }

        DisplayData data = new DisplayData(prefix, suffix, group, playerName);
        displayDataCache.put(uuid, data);
        return data;
    }

    /**
     * Apply color processing to a string.
     */
    @NotNull
    protected String colorize(@NotNull String input) {
        return ColorParser.parse(input);
    }

    /**
     * Invalidate cached display data for a player.
     */
    public void invalidateCache(@NotNull UUID uuid) {
        displayDataCache.invalidate(uuid);
    }

    /**
     * Invalidate all cached display data.
     */
    public void invalidateAllCaches() {
        displayDataCache.invalidateAll();
    }

    public @NotNull PrefixSuffixResolver getPrefixSuffixResolver() {
        return prefixSuffixResolver;
    }

    /**
     * Resolved display data for a player.
     */
    public record DisplayData(
            @NotNull String prefix,
            @NotNull String suffix,
            @NotNull String group,
            @NotNull String playerName
    ) {}
}
```

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/chat/DisplayFormatter.java
git commit -m "feat(chat): add DisplayFormatter shared base with Caffeine caching"
```

---

### Task 3: Update ChatConfig for per-rank formats

**Files:**
- Modify: `src/main/java/com/hyperperms/config/ChatConfig.java`

**Step 1: Add per-rank format fields**

Add to ChatConfig:
```java
// Per-rank chat formats (group name -> format string)
private Map<String, String> chatFormats = new LinkedHashMap<>();
private String formatResolution = "highest-weight"; // "highest-weight" or "primary-group"
```

Update `createDefaults()`:
```java
chatFormats = new LinkedHashMap<>();
chatFormats.put("default", format); // fallback uses main format
formatResolution = "highest-weight";
```

Update `loadFromJson()`:
```java
JsonObject formats = getSection(root, "chatFormats");
if (formats != null) {
    chatFormats = new LinkedHashMap<>();
    for (String key : formats.keySet()) {
        chatFormats.put(key, formats.get(key).getAsString());
    }
}
formatResolution = getString(root, "formatResolution", "highest-weight");
```

Update `toJson()`:
```java
JsonObject formatsObj = new JsonObject();
for (var entry : chatFormats.entrySet()) {
    formatsObj.addProperty(entry.getKey(), entry.getValue());
}
root.add("chatFormats", formatsObj);
root.addProperty("formatResolution", formatResolution);
```

Add getters:
```java
public Map<String, String> getChatFormats() { return chatFormats; }
public String getFormatResolution() { return formatResolution; }
```

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/config/ChatConfig.java
git commit -m "feat(chat): add per-rank chat format support to ChatConfig"
```

---

### Task 4: Refactor ChatManager to extend DisplayFormatter

**Files:**
- Modify: `src/main/java/com/hyperperms/chat/ChatManager.java`

**Step 1: Make ChatManager extend DisplayFormatter**

- Change class declaration: `public class ChatManager extends DisplayFormatter`
- Remove duplicated fields: `prefixSuffixResolver`, `chatFormatter`, `displayDataCache`, `customPlaceholders`
- Update constructor to call `super(plugin, new PrefixSuffixResolver(plugin))`
- Replace manual cache methods (`invalidateCache`, `invalidateAllCaches`, `cleanupExpiredCaches`) with inherited Caffeine-based versions
- Update `formatChatMessage()` to use `resolveDisplayData()` from base class
- Add per-rank format resolution: look up the player's highest-weight group in `chatFormats` map, fall back to "default" key, fall back to main format string
- Replace `ColorUtil.colorize()` calls with `colorize()` (inherited, delegates to ColorParser)

**Key change — per-rank format resolution:**
```java
private String resolveFormatForPlayer(UUID uuid) {
    Map<String, String> formats = chatConfig.getChatFormats();
    if (formats == null || formats.isEmpty()) {
        return chatConfig.getFormat(); // single format fallback
    }

    User user = plugin.getUserManager().getUser(uuid);
    if (user == null) return formats.getOrDefault("default", chatConfig.getFormat());

    // Find highest-weight group that has a format entry
    String bestFormat = null;
    int bestWeight = Integer.MIN_VALUE;
    for (String groupName : user.getInheritedGroups()) {
        if (formats.containsKey(groupName)) {
            var group = plugin.getGroupManager().getGroup(groupName);
            int weight = group != null ? group.getWeight() : 0;
            if (weight > bestWeight) {
                bestWeight = weight;
                bestFormat = formats.get(groupName);
            }
        }
    }

    return bestFormat != null ? bestFormat : formats.getOrDefault("default", chatConfig.getFormat());
}
```

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/chat/ChatManager.java
git commit -m "refactor(chat): ChatManager extends DisplayFormatter with per-rank formats

Replace manual ConcurrentHashMap caches with Caffeine.
Add per-rank format resolution (highest-weight group match).
Delegate color processing to ColorParser."
```

---

### Task 5: Refactor TabListManager to extend DisplayFormatter

**Files:**
- Modify: `src/main/java/com/hyperperms/tablist/TabListManager.java`

**Step 1: Make TabListManager extend DisplayFormatter**

- Change class declaration: `public class TabListManager extends DisplayFormatter`
- Remove duplicated fields: `prefixSuffixResolver`, `chatFormatter`, `displayNameCache`
- Update constructor to call `super(plugin, resolver)` where `resolver` is shared with ChatManager if available
- Replace manual cache with inherited Caffeine cache
- Replace `ColorUtil.colorize()` calls with `colorize()`

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/tablist/TabListManager.java
git commit -m "refactor(chat): TabListManager extends DisplayFormatter

Eliminates ~200 lines of duplicated formatting and caching logic."
```

---

### Task 6: Create PluginBridge interface and integration bridges

**Files:**
- Create: `src/main/java/com/hyperperms/integration/PluginBridge.java`
- Create: `src/main/java/com/hyperperms/integration/bridges/HyperFactionsBridge.java`
- Create: `src/main/java/com/hyperperms/integration/bridges/WerChatBridge.java`
- Create: `src/main/java/com/hyperperms/integration/bridges/PlaceholderApiBridge.java`
- Create: `src/main/java/com/hyperperms/integration/bridges/MysticNameTagsBridge.java`

**Step 1: Create PluginBridge interface**

```java
package com.hyperperms.integration;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Common interface for all plugin integrations.
 * Bridges register placeholders and provide data for the chat formatter.
 */
public interface PluginBridge {
    /** Plugin name this bridge integrates with. */
    @NotNull String pluginName();
    /** Whether the target plugin is available and loaded. */
    boolean isAvailable();
    /** Initialize the bridge (called once during startup). */
    void initialize();
    /** Shutdown the bridge (called during plugin disable). */
    void shutdown();
    /** Get placeholder values for a player. Keys are placeholder names without %. */
    @NotNull Map<String, String> getPlaceholders(@NotNull UUID playerUuid);
}
```

**Step 2: Create HyperFactionsBridge**

Port from `FactionIntegration.java`. Key change: use `HyperFactionsAPI` directly instead of reflection (HyperFactions already has a proper API).

Read `FactionIntegration.java` and `HyperFactionsAPI.java` to understand the API surface. The bridge should call `HyperFactionsAPI.getPlayerFaction()`, `HyperFactionsAPI.getFactionByName()`, etc. directly.

**Step 3: Create WerChatBridge**

Since the user owns WerChat, this bridge assumes a proper `WerChatAPI` class will be exposed in WerChat. For now, keep reflection as a fallback but add a TODO for direct API access once WerChat is updated.

**Step 4: Create remaining bridges**

Port logic from existing integration classes into the bridge pattern.

**Step 5: Verify and commit**

```bash
git add src/main/java/com/hyperperms/integration/PluginBridge.java src/main/java/com/hyperperms/integration/bridges/
git commit -m "feat(chat): add PluginBridge interface and integration bridges

Standardize plugin integrations behind common interface.
HyperFactions uses direct API. WerChat uses reflection with
TODO for direct API once WerChat exposes one."
```

---

### Task 7: Update ColorUtil to delegate to ColorParser

**Files:**
- Modify: `src/main/java/com/hyperperms/chat/ColorUtil.java`

**Step 1: Make ColorUtil a thin facade**

Keep all existing public method signatures for backward compatibility. Internal implementation delegates to `ColorParser.parse()`.

```java
// Replace the body of colorize() with:
public static String colorize(String input) {
    if (input == null || input.isEmpty()) return input;
    return ColorParser.parse(input);
}
```

Keep utility methods that aren't related to parsing (e.g., `stripColor()`, `translateHexToSection()`) if other code uses them.

**Step 2: Verify and commit**

```bash
git add src/main/java/com/hyperperms/chat/ColorUtil.java
git commit -m "refactor(chat): delegate ColorUtil to ColorParser

Maintains backward-compatible API. Internal processing now uses
single-pass ColorParser."
```

---

### Task 8: Create ChatFormatEvent

**Files:**
- Create: `src/main/java/com/hyperperms/api/events/ChatFormatEvent.java`

**Step 1: Create the event**

```java
package com.hyperperms.api.events;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired before a formatted chat message is broadcast.
 * External plugins can modify the formatted message before it's sent to players.
 */
public class ChatFormatEvent extends HyperPermsEvent implements Cancellable {

    private final UUID playerUuid;
    private final String playerName;
    private final String originalMessage;
    private String formattedMessage;
    private boolean cancelled;

    public ChatFormatEvent(@NotNull UUID playerUuid, @NotNull String playerName,
                           @NotNull String originalMessage, @NotNull String formattedMessage) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.originalMessage = originalMessage;
        this.formattedMessage = formattedMessage;
    }

    public @NotNull UUID getPlayerUuid() { return playerUuid; }
    public @NotNull String getPlayerName() { return playerName; }
    public @NotNull String getOriginalMessage() { return originalMessage; }
    public @NotNull String getFormattedMessage() { return formattedMessage; }

    public void setFormattedMessage(@NotNull String formattedMessage) {
        this.formattedMessage = formattedMessage;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
```

**Step 2: Fire event in ChatListener**

Update `ChatListener.java` to fire `ChatFormatEvent` via EventBus after formatting but before broadcasting. If the event is cancelled, don't send the message. If `formattedMessage` was modified by a subscriber, use the modified version.

**Step 3: Verify and commit**

```bash
git add src/main/java/com/hyperperms/api/events/ChatFormatEvent.java
git commit -m "feat(chat): add ChatFormatEvent for external plugin chat modification"
```

---

## Verification Checklist

1. `./gradlew compileJava` — passes
2. `./gradlew shadowJar` — produces valid JAR
3. Chat formatting produces identical output for existing single-format config
4. Per-rank formats work: configure `chatFormats` in `chat.json`, verify different groups see different formats
5. All color codes render correctly: `&c`, `&#FF0000`, `{red}`, `<gradient>`, `<rainbow>`
6. Plugin integrations (HyperFactions, WerChat, PAPI, MysticNameTags) still work
7. Tab list formatting still works
8. `ColorUtil.colorize()` calls from other parts of the codebase still work
9. ChatFormatEvent fires and can be subscribed to by external plugins
