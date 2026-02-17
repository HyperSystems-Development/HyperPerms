package com.hyperperms.util;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Centralized player name/UUID resolution for HyperPerms commands.
 *
 * Resolution chain (in order):
 * 1. UUID parse — if the identifier is a valid UUID, check loaded users,
 *    then load from storage, then optionally create
 * 2. Loaded users by name — case-insensitive match against in-memory users
 * 3. Online players — via PlayerContextProvider.findOnlineUuidByName,
 *    a safety net for the race where a player is connected but their async
 *    user load hasn't completed yet
 * 4. Storage lookup — StorageProvider.lookupUuid for offline players
 *    with previously saved data
 * 5. PlayerDB API — external API lookup as a last resort for any Hytale player
 */
public final class PlayerResolver {

    private PlayerResolver() {}

    /**
     * Resolves a player identifier (name or UUID string) to a User.
     * Loads from storage if necessary. Does not create new users.
     *
     * @param hp         the HyperPerms instance
     * @param identifier player name or UUID string
     * @return the User, or null if not found
     */
    @Nullable
    public static User resolve(@NotNull HyperPerms hp, @NotNull String identifier) {
        return resolve(hp, identifier, false);
    }

    /**
     * Resolves a player identifier to a User, creating the user if not found.
     * This is useful for Tebex integration where players may not have joined
     * the server yet — a UUID can be provided to pre-create permission data.
     *
     * @param hp         the HyperPerms instance
     * @param identifier player name or UUID string
     * @return the User, or null if name-based lookup finds nothing
     */
    @Nullable
    public static User resolveOrCreate(@NotNull HyperPerms hp, @NotNull String identifier) {
        return resolve(hp, identifier, true);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private static User resolve(HyperPerms hp, String identifier, boolean createIfNotExists) {
        // 1. Try as UUID
        Optional<UUID> uuidOpt = parseUuid(identifier);
        if (uuidOpt.isPresent()) {
            UUID uuid = uuidOpt.get();
            User user = hp.getUserManager().getUser(uuid);
            if (user != null) {
                return user;
            }
            Optional<User> loaded = hp.getUserManager().loadUser(uuid).join();
            if (loaded.isPresent()) {
                return loaded.get();
            }
            if (createIfNotExists) {
                return hp.getUserManager().getOrCreateUser(uuid);
            }
            return null;
        }

        // 2. Check in-memory loaded users by name (case-insensitive)
        User byName = findUserByName(hp, identifier);
        if (byName != null) {
            return byName;
        }

        // 3. Check online players — safety net for the race condition where a player
        //    is connected but their async loadUser hasn't completed yet
        UUID onlineUuid = hp.getPlayerContextProvider().findOnlineUuidByName(identifier);
        if (onlineUuid != null) {
            Logger.debug("resolve: '%s' is online (UUID: %s) but not in loadedUsers yet, loading synchronously",
                    identifier, onlineUuid);
            Optional<User> loaded = hp.getUserManager().loadUser(onlineUuid).join();
            if (loaded.isPresent()) {
                return loaded.get();
            }
        }

        // 4. Storage lookup (previously connected players)
        Logger.debug("resolve: trying storage lookup for '%s'", identifier);
        Optional<UUID> storedUuid = hp.getStorage().lookupUuid(identifier).join();
        if (storedUuid.isPresent()) {
            Logger.debug("resolve: storage found UUID %s for '%s'", storedUuid.get(), identifier);
            Optional<User> loaded = hp.getUserManager().loadUser(storedUuid.get()).join();
            if (loaded.isPresent()) {
                return loaded.get();
            }
        }

        // 5. PlayerDB external API (last resort)
        var playerDbInfo = PlayerDBService.lookup(identifier).join();
        if (playerDbInfo != null) {
            Logger.debug("resolve: PlayerDB found %s (%s) for '%s'",
                    playerDbInfo.username(), playerDbInfo.uuid(), identifier);
            User user;
            if (createIfNotExists) {
                user = hp.getUserManager().getOrCreateUser(playerDbInfo.uuid());
            } else {
                Optional<User> loaded = hp.getUserManager().loadUser(playerDbInfo.uuid()).join();
                user = loaded.orElse(null);
            }
            if (user != null) {
                user.setUsername(playerDbInfo.username());
                hp.getUserManager().saveUser(user);
                return user;
            }
        }

        Logger.debug("resolve: no match for '%s'", identifier);
        return null;
    }

    /**
     * Finds a user by name from loaded users (case-insensitive exact match).
     */
    private static User findUserByName(HyperPerms hp, String name) {
        for (User user : hp.getUserManager().getLoadedUsers()) {
            String username = user.getUsername();
            if (username != null && name.equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Tries to parse a string as UUID.
     */
    private static Optional<UUID> parseUuid(String input) {
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
