package com.hyperperms.tablist;

import com.hyperperms.HyperPerms;
import com.hyperperms.chat.ColorUtil;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hytale event listener for player list (tab list) name formatting.
 *
 * <p>This listener uses Hytale's packet system to send formatted player names
 * to the "Server Players" section of the player list. Unlike reflection-based
 * approaches, this directly sends {@link AddToServerPlayerList} packets with
 * formatted {@link ServerPlayerListPlayer} entries.
 *
 * <p>Formatting flow:
 * <ol>
 *   <li>On player connect, send full player list to joining player</li>
 *   <li>Send new player's formatted entry to all existing players</li>
 *   <li>On permission/group changes, refresh the affected player's entry</li>
 * </ol>
 */
public class TabListListener {

    private final HyperPerms plugin;
    private final TabListManager tabListManager;

    // Track connected players
    private final ConcurrentMap<UUID, PlayerRef> trackedPlayers = new ConcurrentHashMap<>();

    // Event registrations
    private EventRegistration<?, ?> connectRegistration;
    private EventRegistration<?, ?> disconnectRegistration;

    /**
     * Creates a new TabListListener.
     *
     * @param plugin the HyperPerms plugin instance
     * @param tabListManager the tab list manager
     */
    public TabListListener(@NotNull HyperPerms plugin, @NotNull TabListManager tabListManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.tabListManager = Objects.requireNonNull(tabListManager, "tabListManager cannot be null");
    }

    /**
     * Registers the event listeners with the Hytale event registry.
     *
     * @param eventRegistry the event registry
     */
    public void register(@NotNull EventRegistry eventRegistry) {
        Objects.requireNonNull(eventRegistry, "eventRegistry cannot be null");

        // Register handler for PlayerConnectEvent
        connectRegistration = eventRegistry.register(
            PlayerConnectEvent.class,
            this::onPlayerConnect
        );

        // Register handler for PlayerDisconnectEvent
        disconnectRegistration = eventRegistry.register(
            PlayerDisconnectEvent.class,
            this::onPlayerDisconnect
        );

        // Set up the name applier callback for when permissions change
        tabListManager.setNameApplier(this::refreshPlayerInList);

        Logger.info("Player list formatting registered (packet-based)");
    }

    /**
     * Unregisters the event listeners.
     *
     * @param eventRegistry the event registry
     */
    public void unregister(@NotNull EventRegistry eventRegistry) {
        // Clear registrations
        if (connectRegistration != null) {
            connectRegistration = null;
        }
        if (disconnectRegistration != null) {
            disconnectRegistration = null;
        }

        // Clear name applier
        tabListManager.setNameApplier(null);

        // Clear tracked players
        trackedPlayers.clear();

        Logger.info("Player list listener marked for cleanup");
    }

    /**
     * Handles player connect event.
     * Sends the full formatted player list to the joining player and
     * notifies existing players about the new player.
     *
     * @param event the player connect event
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef joiningPlayer = event.getPlayerRef();
        if (joiningPlayer == null) {
            return;
        }

        UUID uuid = joiningPlayer.getUuid();
        String playerName = joiningPlayer.getUsername();

        // Track the player
        trackedPlayers.put(uuid, joiningPlayer);

        Logger.debug("Player list: Player connecting: %s (%s)", playerName, uuid);

        // Send player list updates via packets
        if (tabListManager.isEnabled()) {
            updatePlayerList(joiningPlayer);
        }
    }

    /**
     * Updates the player list when a new player joins.
     * Sends the full list to the joining player and the new player entry to others.
     *
     * @param joiningPlayer the joining player
     */
    private void updatePlayerList(@NotNull PlayerRef joiningPlayer) {
        List<PlayerRef> allPlayers = new ArrayList<>(Universe.get().getPlayers());

        // Build list entries for all players asynchronously
        List<CompletableFuture<ServerPlayerListPlayer>> futures = new ArrayList<>();
        for (PlayerRef player : allPlayers) {
            futures.add(createServerPlayerListPlayerAsync(player));
        }

        // Track which index the joining player is at
        int joiningPlayerIndex = -1;
        for (int i = 0; i < allPlayers.size(); i++) {
            if (allPlayers.get(i).getUuid().equals(joiningPlayer.getUuid())) {
                joiningPlayerIndex = i;
                break;
            }
        }
        final int finalJoiningIndex = joiningPlayerIndex;

        // Wait for all entries to be created, then send packets
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
            .thenAccept(v -> {
                // Collect all completed entries
                ServerPlayerListPlayer[] serverListPlayers = new ServerPlayerListPlayer[futures.size()];
                for (int i = 0; i < futures.size(); i++) {
                    serverListPlayers[i] = futures.get(i).join();
                }

                // Sort by group weight if enabled (descending — highest weight first)
                if (tabListManager.isSortByWeight()) {
                    sortByWeight(serverListPlayers);
                }

                // Send full list to the joining player
                AddToServerPlayerList fullListPacket = new AddToServerPlayerList(serverListPlayers);
                joiningPlayer.getPacketHandler().write(fullListPacket);

                // Get the joining player's entry by index and send to others
                if (finalJoiningIndex >= 0 && finalJoiningIndex < serverListPlayers.length) {
                    ServerPlayerListPlayer joiningEntry = serverListPlayers[finalJoiningIndex];
                    AddToServerPlayerList newPlayerPacket = new AddToServerPlayerList(
                        new ServerPlayerListPlayer[]{joiningEntry}
                    );

                    for (PlayerRef player : allPlayers) {
                        if (!player.getUuid().equals(joiningPlayer.getUuid())) {
                            player.getPacketHandler().write(newPlayerPacket);
                        }
                    }
                }

                Logger.debug("Player list: Sent updates for %s to %d players",
                    joiningPlayer.getUsername(), allPlayers.size());
            })
            .exceptionally(e -> {
                Logger.warn("Player list: Failed to update for %s: %s",
                    joiningPlayer.getUsername(), e.getMessage());
                return null;
            });
    }

    /**
     * Handles player disconnect event.
     *
     * @param event the player disconnect event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        // Untrack the player
        trackedPlayers.remove(uuid);

        // Invalidate cache
        tabListManager.invalidateCache(uuid);

        Logger.debug("Player list: Player disconnected: %s", playerRef.getUsername());
    }

    /**
     * Refreshes a specific player's entry in all players' lists.
     * Called when a player's display name should be updated (e.g., permission change).
     *
     * @param uuid the player's UUID
     * @param formattedName the new formatted name (used by the callback interface)
     */
    private void refreshPlayerInList(@NotNull UUID uuid, @NotNull String formattedName) {
        PlayerRef targetPlayer = trackedPlayers.get(uuid);
        if (targetPlayer == null) {
            Logger.debug("Player list: Player %s not tracked, cannot refresh", uuid);
            return;
        }

        // Create updated entry asynchronously
        createServerPlayerListPlayerAsync(targetPlayer)
            .thenAccept(updatedEntry -> {
                AddToServerPlayerList updatePacket = new AddToServerPlayerList(
                    new ServerPlayerListPlayer[]{updatedEntry}
                );

                // Send to all connected players
                Collection<PlayerRef> allPlayers = Universe.get().getPlayers();
                for (PlayerRef player : allPlayers) {
                    player.getPacketHandler().write(updatePacket);
                }

                Logger.debug("Player list: Refreshed entry for %s", targetPlayer.getUsername());
            })
            .exceptionally(e -> {
                Logger.warn("Player list: Failed to refresh entry for %s: %s",
                    targetPlayer.getUsername(), e.getMessage());
                return null;
            });
    }

    /**
     * Updates a specific player's tab list name.
     *
     * @param uuid the player's UUID
     */
    public void updatePlayer(@NotNull UUID uuid) {
        PlayerRef playerRef = trackedPlayers.get(uuid);
        if (playerRef == null) {
            return;
        }

        tabListManager.updatePlayer(uuid, playerRef.getUsername());
    }

    /**
     * Updates all connected players' tab list names.
     * Sends a complete refresh to all players.
     */
    public void updateAllPlayers() {
        if (!tabListManager.isEnabled()) {
            return;
        }

        Collection<PlayerRef> allPlayers = Universe.get().getPlayers();
        if (allPlayers.isEmpty()) {
            return;
        }

        // Build complete player list asynchronously
        List<CompletableFuture<ServerPlayerListPlayer>> futures = new ArrayList<>();
        for (PlayerRef player : allPlayers) {
            futures.add(createServerPlayerListPlayerAsync(player));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
            .thenAccept(v -> {
                ServerPlayerListPlayer[] serverListPlayers = new ServerPlayerListPlayer[futures.size()];
                for (int i = 0; i < futures.size(); i++) {
                    serverListPlayers[i] = futures.get(i).join();
                }

                // Sort by group weight if enabled (descending — highest weight first)
                if (tabListManager.isSortByWeight()) {
                    sortByWeight(serverListPlayers);
                }

                // Send to all players
                AddToServerPlayerList fullListPacket = new AddToServerPlayerList(serverListPlayers);
                for (PlayerRef player : allPlayers) {
                    player.getPacketHandler().write(fullListPacket);
                }

                Logger.debug("Player list: Full refresh sent to %d players", allPlayers.size());
            })
            .exceptionally(e -> {
                Logger.warn("Player list: Failed to refresh all players: %s", e.getMessage());
                return null;
            });
    }

    /**
     * Gets the number of tracked players.
     *
     * @return the tracked player count
     */
    public int getTrackedPlayerCount() {
        return trackedPlayers.size();
    }

    /**
     * Sorts a ServerPlayerListPlayer array by group weight (descending — highest weight first).
     * Players within the same weight are sorted alphabetically by username.
     *
     * @param players the array to sort in-place
     */
    private void sortByWeight(@NotNull ServerPlayerListPlayer[] players) {
        Arrays.sort(players, Comparator
            .<ServerPlayerListPlayer>comparingInt(p -> -tabListManager.getWeight(p.uuid))
            .thenComparing(p -> p.username != null ? p.username : ""));
    }

    /**
     * Creates a ServerPlayerListPlayer entry for a player with formatted name asynchronously.
     *
     * @param playerRef the player reference
     * @return a future containing the server player list entry
     */
    @NotNull
    private CompletableFuture<ServerPlayerListPlayer> createServerPlayerListPlayerAsync(@NotNull PlayerRef playerRef) {
        String playerName = playerRef.getUsername();
        UUID uuid = playerRef.getUuid();

        // Get ping value (synchronous, fast)
        int ping = getPingValue(playerRef.getPacketHandler());
        UUID worldUuid = playerRef.getWorldUuid();

        // Format the display name asynchronously
        return formatPlayerNameAsync(uuid, playerName)
            .thenApply(formattedName -> new ServerPlayerListPlayer(
                uuid,
                formattedName,
                worldUuid,
                ping
            ));
    }

    /**
     * Formats a player's name with their HyperPerms prefix/suffix asynchronously.
     * Color codes are stripped since Hytale's player list doesn't support them.
     *
     * @param uuid the player's UUID
     * @param playerName the player's base name
     * @return a future containing the formatted name (without color codes)
     */
    @NotNull
    private CompletableFuture<String> formatPlayerNameAsync(@NotNull UUID uuid, @NotNull String playerName) {
        if (!tabListManager.isEnabled()) {
            return CompletableFuture.completedFuture(playerName);
        }

        return tabListManager.formatTabListName(uuid, playerName)
            .thenApply(TabListListener::stripAllFormatting) // Strip all formatting - Hytale player list doesn't support it
            .exceptionally(e -> {
                Logger.debug("Player list: Using plain name for %s due to: %s", playerName, e.getMessage());
                return playerName;
            });
    }

    /**
     * Strips all formatting codes and invisible characters from text.
     * More aggressive than ColorUtil.stripColors() to ensure clean player list names.
     *
     * @param text the input text
     * @return clean text with no formatting codes or invisible characters
     */
    @NotNull
    private static String stripAllFormatting(@NotNull String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // First use ColorUtil's strip
        String stripped = ColorUtil.stripColors(text);

        // Then remove any remaining § characters (in case of malformed codes)
        stripped = stripped.replace("§", "").replace("\u00A7", "");

        // Remove common invisible/zero-width Unicode characters
        stripped = stripped
            .replace("\u200B", "")  // Zero-width space
            .replace("\u200C", "")  // Zero-width non-joiner
            .replace("\u200D", "")  // Zero-width joiner
            .replace("\uFEFF", "")  // BOM / Zero-width no-break space
            .replace("\u00AD", ""); // Soft hyphen

        // Add leading spaces for padding (Hytale's player list clips the left edge)
        return "  " + stripped.trim();
    }

    /**
     * Gets the ping value for a player's packet handler.
     *
     * @param handler the packet handler
     * @return the ping in milliseconds
     */
    private static int getPingValue(@NotNull PacketHandler handler) {
        try {
            HistoricMetric historicMetric = handler.getPingInfo(PongType.Direct).getPingMetricSet();
            double average = historicMetric.getAverage(0);
            return (int) PacketHandler.PingInfo.TIME_UNIT.toMillis(MathUtil.fastCeil(average));
        } catch (Exception e) {
            // Return reasonable default if ping calculation fails
            return 50;
        }
    }

    /**
     * Converts a formatted string with color codes to a Hytale Message.
     *
     * @param formatted the formatted string
     * @return the Hytale Message
     */
    @NotNull
    public static Message toHytaleMessage(@NotNull String formatted) {
        if (formatted == null || formatted.isEmpty()) {
            return Message.raw("");
        }

        try {
            // Parse color codes and build Message
            Message result = null;
            StringBuilder currentText = new StringBuilder();
            Color currentColor = null;
            boolean bold = false;
            boolean italic = false;

            int i = 0;
            while (i < formatted.length()) {
                char c = formatted.charAt(i);

                // Check for color/format code
                if (c == '§' && i + 1 < formatted.length()) {
                    // Flush current segment
                    if (currentText.length() > 0) {
                        Message segment = Message.raw(currentText.toString());
                        if (currentColor != null) {
                            segment = segment.color(currentColor);
                        }
                        if (bold) {
                            segment = segment.bold(true);
                        }
                        if (italic) {
                            segment = segment.italic(true);
                        }
                        result = (result == null) ? segment : Message.join(result, segment);
                        currentText.setLength(0);
                    }

                    char code = Character.toLowerCase(formatted.charAt(i + 1));

                    // Handle code
                    switch (code) {
                        case '0' -> { currentColor = new Color(0x000000); bold = false; italic = false; }
                        case '1' -> { currentColor = new Color(0x0000AA); bold = false; italic = false; }
                        case '2' -> { currentColor = new Color(0x00AA00); bold = false; italic = false; }
                        case '3' -> { currentColor = new Color(0x00AAAA); bold = false; italic = false; }
                        case '4' -> { currentColor = new Color(0xAA0000); bold = false; italic = false; }
                        case '5' -> { currentColor = new Color(0xAA00AA); bold = false; italic = false; }
                        case '6' -> { currentColor = new Color(0xFFAA00); bold = false; italic = false; }
                        case '7' -> { currentColor = new Color(0xAAAAAA); bold = false; italic = false; }
                        case '8' -> { currentColor = new Color(0x555555); bold = false; italic = false; }
                        case '9' -> { currentColor = new Color(0x5555FF); bold = false; italic = false; }
                        case 'a' -> { currentColor = new Color(0x55FF55); bold = false; italic = false; }
                        case 'b' -> { currentColor = new Color(0x55FFFF); bold = false; italic = false; }
                        case 'c' -> { currentColor = new Color(0xFF5555); bold = false; italic = false; }
                        case 'd' -> { currentColor = new Color(0xFF55FF); bold = false; italic = false; }
                        case 'e' -> { currentColor = new Color(0xFFFF55); bold = false; italic = false; }
                        case 'f' -> { currentColor = new Color(0xFFFFFF); bold = false; italic = false; }
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'r' -> { currentColor = null; bold = false; italic = false; }
                        default -> { }
                    }

                    i += 2;
                    continue;
                }

                currentText.append(c);
                i++;
            }

            // Flush remaining
            if (currentText.length() > 0) {
                Message segment = Message.raw(currentText.toString());
                if (currentColor != null) {
                    segment = segment.color(currentColor);
                }
                if (bold) {
                    segment = segment.bold(true);
                }
                if (italic) {
                    segment = segment.italic(true);
                }
                result = (result == null) ? segment : Message.join(result, segment);
            }

            return result != null ? result : Message.raw("");
        } catch (Exception e) {
            Logger.warn("Player list: Failed to parse colors: %s", e.getMessage());
            return Message.raw(ColorUtil.stripColors(formatted));
        }
    }
}
