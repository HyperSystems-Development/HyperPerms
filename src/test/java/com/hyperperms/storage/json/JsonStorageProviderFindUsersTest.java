package com.hyperperms.storage.json;

import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link JsonStorageProvider#findUsersWithNode(String)}.
 * <p>
 * This backs Hytale 0.6.0's {@code PermissionProvider#getUsersWithPermission}, which drives
 * {@code /whitelist list} and {@code /whitelist clear}. The engine treats the result as
 * "revoking from these users will stick", so anything held indirectly must be excluded.
 */
class JsonStorageProviderFindUsersTest {

    private static final String JOIN = "hytale.server.join";

    private JsonStorageProvider storage;

    @BeforeEach
    void setUp(@TempDir Path dataDirectory) throws Exception {
        storage = new JsonStorageProvider(dataDirectory);
        storage.init().get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.shutdown().get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("finds a user holding the node directly")
    void findsDirectGrant() throws Exception {
        UUID uuid = save(user -> user.setNode(Node.of(JOIN)));

        assertEquals(Set.of(uuid), find(JOIN));
    }

    @Test
    @DisplayName("excludes a user whose node is a negation")
    void excludesNegation() throws Exception {
        save(user -> user.setNode(Node.builder(JOIN).value(false).build()));

        assertTrue(find(JOIN).isEmpty(), "a denied node is not a grant");
    }

    @Test
    @DisplayName("excludes a user whose node has expired")
    void excludesExpiredGrant() throws Exception {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        save(user -> user.setNode(Node.builder(JOIN).expiry(past).build()));

        assertTrue(find(JOIN).isEmpty(), "a lapsed timed node is not a live grant");
    }

    @Test
    @DisplayName("includes a user whose node expires in the future")
    void includesUnexpiredGrant() throws Exception {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        UUID uuid = save(user -> user.setNode(Node.builder(JOIN).expiry(future).build()));

        assertEquals(Set.of(uuid), find(JOIN));
    }

    @Test
    @DisplayName("excludes a user who would only inherit the node from a group")
    void excludesGroupInheritance() throws Exception {
        // The group itself is not consulted: this method answers "granted to the user", and
        // revoking a group's grant from a member would not stick.
        save(user -> user.addGroup("staff"));

        assertTrue(find(JOIN).isEmpty(), "group membership is not a direct grant");
    }

    @Test
    @DisplayName("excludes a user holding only a wildcard that would cover the node")
    void excludesWildcard() throws Exception {
        save(user -> user.setNode(Node.of("hytale.*")));

        assertTrue(find(JOIN).isEmpty(), "the node is matched literally, not expanded");
    }

    @Test
    @DisplayName("returns every direct holder and nobody else")
    void returnsAllDirectHolders() throws Exception {
        UUID first = save(user -> user.setNode(Node.of(JOIN)));
        UUID second = save(user -> user.setNode(Node.of(JOIN)));
        save(user -> user.setNode(Node.of("hyperperms.command.reload")));

        assertEquals(Set.of(first, second), find(JOIN));
    }

    @Test
    @DisplayName("returns empty when no user is stored")
    void returnsEmptyWhenNoUsers() throws Exception {
        assertTrue(find(JOIN).isEmpty());
    }

    private Set<UUID> find(String permission) throws Exception {
        return storage.findUsersWithNode(permission).get(10, TimeUnit.SECONDS);
    }

    /**
     * Persists a user built by the given mutator and returns their UUID.
     */
    private UUID save(java.util.function.Consumer<User> mutator) throws Exception {
        UUID uuid = UUID.randomUUID();
        User user = new User(uuid, "player-" + uuid.toString().substring(0, 8));
        mutator.accept(user);
        storage.saveUser(user).get(10, TimeUnit.SECONDS);
        return uuid;
    }
}
