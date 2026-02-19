package com.hyperperms.update;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Listens for player connect events and notifies operators about available updates.
 * <p>
 * Notifications are sent only to players with the required permission and who have
 * not disabled notifications via their preferences.
 */
public final class UpdateNotificationListener {

    /** Delay before sending notification to avoid join message spam */
    private static final long NOTIFICATION_DELAY_MS = 1500;

    /** Permissions that allow receiving update notifications */
    private static final String PERMISSION_WILDCARD = com.hyperperms.util.Permissions.ADMIN;
    private static final String PERMISSION_ADMIN = com.hyperperms.util.Permissions.ADMIN;
    private static final String PERMISSION_NOTIFY = com.hyperperms.util.Permissions.UPDATES_NOTIFY;

    private final HyperPerms hyperPerms;
    private final ScheduledExecutorService scheduler;

    /** Tracks players who have already been notified this session */
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new update notification listener.
     *
     * @param hyperPerms the HyperPerms instance
     */
    public UpdateNotificationListener(@NotNull HyperPerms hyperPerms) {
        this.hyperPerms = hyperPerms;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyperPerms-UpdateNotifier");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Registers this listener with the event registry.
     *
     * @param eventRegistry the event registry to register with
     */
    public void register(@NotNull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        Logger.debug("[UpdateNotify] Registered update notification listener");
    }

    /**
     * Unregisters this listener and shuts down the scheduler.
     *
     * @param eventRegistry the event registry to unregister from
     */
    public void unregister(@NotNull EventRegistry eventRegistry) {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        notifiedPlayers.clear();
        Logger.debug("[UpdateNotify] Unregistered update notification listener");
    }

    /**
     * Handles player connect event.
     * Schedules a delayed notification check for eligible players.
     *
     * @param event the player connect event
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();

        // Schedule the notification check with a delay
        scheduler.schedule(() -> {
            try {
                checkAndNotify(playerRef);
            } catch (Exception e) {
                Logger.warn("[UpdateNotify] Failed to send notification to %s: %s", 
                    playerRef.getUsername(), e.getMessage());
            }
        }, NOTIFICATION_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles player disconnect event.
     * Clears the notified flag so they can be notified again next session.
     *
     * @param event the player disconnect event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        notifiedPlayers.remove(event.getPlayerRef().getUuid());
    }

    /**
     * Checks if the player should receive a notification and sends it.
     *
     * @param playerRef the player to check
     */
    private void checkAndNotify(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();

        // Already notified this session
        if (notifiedPlayers.contains(uuid)) {
            return;
        }

        // Check permissions
        if (!hasNotifyPermission(playerRef)) {
            Logger.debug("[UpdateNotify] %s does not have notify permission", playerRef.getUsername());
            return;
        }

        // Check user preferences
        UpdateNotificationPreferences prefs = hyperPerms.getNotificationPreferences();
        if (prefs != null && !prefs.isEnabled(uuid)) {
            Logger.debug("[UpdateNotify] Notifications disabled for %s", playerRef.getUsername());
            return;
        }

        // Check if update checker is available
        UpdateChecker checker = hyperPerms.getUpdateChecker();
        if (checker == null) {
            return;
        }

        // Mark as notified before sending to prevent duplicates
        notifiedPlayers.add(uuid);

        // Send appropriate notification
        if (checker.hasUpdateAvailable()) {
            sendUpdateAvailableMessage(playerRef, checker);
        } else {
            sendUpToDateMessage(playerRef, checker);
        }
    }

    /**
     * Checks if the player has permission to receive update notifications.
     *
     * @param playerRef the player reference
     * @return true if they should receive notifications
     */
    private boolean hasNotifyPermission(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        
        // Check HyperPerms permissions (wildcard, admin, or specific notify permission)
        if (hyperPerms.hasPermission(uuid, PERMISSION_WILDCARD) ||
            hyperPerms.hasPermission(uuid, PERMISSION_ADMIN) ||
            hyperPerms.hasPermission(uuid, PERMISSION_NOTIFY)) {
            return true;
        }
        
        // Check Hytale's native permission system (for operators)
        // This handles server operators who may not have HyperPerms permissions set
        try {
            var permModule = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
            if (permModule != null) {
                return permModule.hasPermission(uuid, PERMISSION_ADMIN) || 
                       permModule.hasPermission(uuid, PERMISSION_NOTIFY) ||
                       permModule.hasPermission(uuid, "*"); // Operators typically have *
            }
        } catch (Exception e) {
            Logger.debug("[UpdateNotify] Failed to check native permissions: %s", e.getMessage());
        }
        
        return false;
    }

    /**
     * Sends the "update available" notification message.
     *
     * @param playerRef the player to notify
     * @param checker   the update checker
     */
    private void sendUpdateAvailableMessage(PlayerRef playerRef, UpdateChecker checker) {
        UpdateChecker.UpdateInfo info = checker.getCachedUpdate();
        if (info == null) {
            return;
        }

        String currentVersion = checker.getCurrentVersion();
        String newVersion = info.version();

        // [HyperPerms] A new version is available!
        playerRef.sendMessage(
            Message.raw("[HyperPerms] ").color(GOLD)
                .insert(Message.raw("A new version is available!").color(GOLD).bold(true))
        );

        // Current: v2.5.0 → Latest: v2.6.0
        playerRef.sendMessage(
            Message.raw("Current: ").color(GRAY)
                .insert(Message.raw("v" + currentVersion).color(WHITE))
                .insert(Message.raw(" → ").color(GRAY))
                .insert(Message.raw("Latest: ").color(GRAY))
                .insert(Message.raw("v" + newVersion).color(GREEN))
        );

        // Run /hp update to update the plugin.
        playerRef.sendMessage(
            Message.raw("Run ").color(GRAY)
                .insert(Message.raw("/hp update").color(GREEN))
                .insert(Message.raw(" to update the plugin.").color(GRAY))
        );

        Logger.debug("[UpdateNotify] Sent update notification to %s", playerRef.getUsername());
    }

    /**
     * Sends the "up-to-date" notification message.
     *
     * @param playerRef the player to notify
     * @param checker   the update checker
     */
    private void sendUpToDateMessage(PlayerRef playerRef, UpdateChecker checker) {
        String currentVersion = checker.getCurrentVersion();

        // [HyperPerms] Plugin is up-to-date (v2.5.0)
        playerRef.sendMessage(
            Message.raw("[HyperPerms] ").color(GRAY)
                .insert(Message.raw("Plugin is up-to-date ").color(GRAY))
                .insert(Message.raw("(v" + currentVersion + ")").color(GREEN))
        );

        Logger.debug("[UpdateNotify] Sent up-to-date notification to %s", playerRef.getUsername());
    }
}
