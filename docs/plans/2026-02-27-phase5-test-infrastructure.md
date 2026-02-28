# Phase 5: Test Infrastructure — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a test infrastructure with an in-memory storage provider, a pre-wired test container, and foundational tests for the core subsystems: storage, permission resolution, color parsing, and lifecycle stages.

**Architecture:** `InMemoryStorageProvider` implements `StorageProvider` with `ConcurrentHashMap` — zero I/O, instant operations. `TestServiceContainer` pre-wires the in-memory provider with real managers, cache, and event bus for integration tests. Unit tests cover individual components; integration tests verify the full lifecycle.

**Tech Stack:** Java 25, JUnit 5 (already a test dependency)

**Worktree:** `.worktrees/architecture-rehaul` on branch `refactor/architecture-rehaul`

**Git Author:** `ZenithDevHQ <scrubc1ty4ever@gmail.com>`

**Depends on:** Phase 4 (final package structure must be in place)

**Base path:** `src/test/java/com/hyperperms/`

---

### Task 1: Create InMemoryStorageProvider

**Files:**
- Create: `src/test/java/com/hyperperms/test/InMemoryStorageProvider.java`

**Step 1: Create the in-memory storage implementation**

This implements the full `StorageProvider` interface using in-memory maps. All operations return completed futures immediately.

```java
package com.hyperperms.test;

import com.hyperperms.internal.storage.StorageProvider;
import com.hyperperms.model.Group;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage provider for testing. Zero I/O, instant operations.
 * All data is stored in ConcurrentHashMaps and lost when the JVM exits.
 */
public class InMemoryStorageProvider implements StorageProvider {

    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<String, Track> tracks = new ConcurrentHashMap<>();
    private final Map<String, String> uuidToName = new ConcurrentHashMap<>();
    private volatile boolean healthy = true;

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    // --- User operations ---

    @Override
    public CompletableFuture<@Nullable User> loadUser(@NotNull UUID uuid) {
        return CompletableFuture.completedFuture(users.get(uuid));
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull User user) {
        users.put(user.getUuid(), user);
        if (user.getUsername() != null) {
            uuidToName.put(user.getUuid().toString(), user.getUsername());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteUser(@NotNull UUID uuid) {
        users.remove(uuid);
        uuidToName.remove(uuid.toString());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<UUID, User>> loadAllUsers() {
        return CompletableFuture.completedFuture(new HashMap<>(users));
    }

    @Override
    public CompletableFuture<Set<UUID>> getUserUuids() {
        return CompletableFuture.completedFuture(new HashSet<>(users.keySet()));
    }

    @Override
    public CompletableFuture<@Nullable UUID> lookupUuid(@NotNull String username) {
        for (var entry : users.entrySet()) {
            if (entry.getValue().getUsername() != null
                    && entry.getValue().getUsername().equalsIgnoreCase(username)) {
                return CompletableFuture.completedFuture(entry.getKey());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    // --- Group operations ---

    @Override
    public CompletableFuture<@Nullable Group> loadGroup(@NotNull String name) {
        return CompletableFuture.completedFuture(groups.get(name.toLowerCase()));
    }

    @Override
    public CompletableFuture<Void> saveGroup(@NotNull Group group) {
        groups.put(group.getName().toLowerCase(), group);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteGroup(@NotNull String name) {
        groups.remove(name.toLowerCase());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Group>> loadAllGroups() {
        return CompletableFuture.completedFuture(new HashMap<>(groups));
    }

    @Override
    public CompletableFuture<Set<String>> getGroupNames() {
        return CompletableFuture.completedFuture(new HashSet<>(groups.keySet()));
    }

    // --- Track operations ---

    @Override
    public CompletableFuture<@Nullable Track> loadTrack(@NotNull String name) {
        return CompletableFuture.completedFuture(tracks.get(name.toLowerCase()));
    }

    @Override
    public CompletableFuture<Void> saveTrack(@NotNull Track track) {
        tracks.put(track.getName().toLowerCase(), track);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteTrack(@NotNull String name) {
        tracks.remove(name.toLowerCase());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Track>> loadAllTracks() {
        return CompletableFuture.completedFuture(new HashMap<>(tracks));
    }

    @Override
    public CompletableFuture<Set<String>> getTrackNames() {
        return CompletableFuture.completedFuture(new HashSet<>(tracks.keySet()));
    }

    // --- Bulk ---

    @Override
    public CompletableFuture<Void> saveAll() {
        return CompletableFuture.completedFuture(null); // already in memory
    }

    // --- Backup (no-op for testing) ---

    @Override
    public CompletableFuture<@Nullable String> createBackup(@NotNull String prefix) {
        return CompletableFuture.completedFuture("test-backup-" + prefix);
    }

    @Override
    public CompletableFuture<Boolean> restoreBackup(@NotNull String name) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<List<String>> listBackups() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Boolean> deleteBackup(@NotNull String name) {
        return CompletableFuture.completedFuture(true);
    }

    // --- Test helpers ---

    /** Clear all stored data. */
    public void clear() {
        users.clear();
        groups.clear();
        tracks.clear();
        uuidToName.clear();
    }

    /** Set health status for testing error handling. */
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
}
```

**Important:** The method signatures above are approximate. The implementer MUST read `StorageProvider.java` (now at `internal/storage/StorageProvider.java` after Phase 4) and match every method exactly. Some methods may have different signatures or additional methods not listed here.

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileTestJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/test/java/com/hyperperms/test/InMemoryStorageProvider.java
git commit -m "test: add InMemoryStorageProvider for zero-I/O testing"
```

---

### Task 2: Create TestServiceContainer

**Files:**
- Create: `src/test/java/com/hyperperms/test/TestServiceContainer.java`

**Step 1: Create pre-wired test container**

```java
package com.hyperperms.test;

import com.hyperperms.api.events.EventBus;
import com.hyperperms.internal.cache.CacheInvalidator;
import com.hyperperms.internal.cache.PermissionCache;
import com.hyperperms.internal.manager.GroupManagerImpl;
import com.hyperperms.internal.manager.TrackManagerImpl;
import com.hyperperms.internal.manager.UserManagerImpl;
import com.hyperperms.internal.storage.StorageProvider;
import com.hyperperms.lifecycle.ServiceContainer;

/**
 * Pre-wired ServiceContainer for testing.
 * Uses InMemoryStorageProvider and real managers.
 * One-liner setup for integration tests.
 */
public final class TestServiceContainer {

    private TestServiceContainer() {}

    /**
     * Create a fully wired container with in-memory storage, real cache, real managers.
     */
    public static ServiceContainer create() {
        ServiceContainer container = new ServiceContainer();

        // Storage
        InMemoryStorageProvider storage = new InMemoryStorageProvider();
        storage.init().join();
        container.register(StorageProvider.class, storage);
        container.register(InMemoryStorageProvider.class, storage);

        // Cache
        PermissionCache cache = new PermissionCache(1000, 60, true);
        container.register(PermissionCache.class, cache);

        CacheInvalidator cacheInvalidator = new CacheInvalidator(cache);
        container.register(CacheInvalidator.class, cacheInvalidator);

        // EventBus
        EventBus eventBus = new EventBus();
        container.register(EventBus.class, eventBus);

        // Managers
        GroupManagerImpl groupManager = new GroupManagerImpl(storage, cacheInvalidator, eventBus);
        TrackManagerImpl trackManager = new TrackManagerImpl(storage);
        UserManagerImpl userManager = new UserManagerImpl(storage, cache, eventBus, "default");

        container.register(GroupManagerImpl.class, groupManager);
        container.register(TrackManagerImpl.class, trackManager);
        container.register(UserManagerImpl.class, userManager);

        // Load (empty data)
        groupManager.loadAll().join();
        trackManager.loadAll().join();
        userManager.loadAll().join();

        return container;
    }
}
```

**Step 2: Verify and commit**

```bash
git add src/test/java/com/hyperperms/test/TestServiceContainer.java
git commit -m "test: add TestServiceContainer for pre-wired integration testing"
```

---

### Task 3: Write ColorParser tests

**Files:**
- Create: `src/test/java/com/hyperperms/chat/ColorParserTest.java`

**Step 1: Write comprehensive color parsing tests**

```java
package com.hyperperms.chat;

import com.hyperperms.internal.chat.ColorParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the single-pass ColorParser.
 * Ensures parity with the original ColorUtil regex chain.
 */
class ColorParserTest {

    @Test
    @DisplayName("Legacy color codes: &a-f, &0-9")
    void legacyCodes() {
        assertEquals("\u00A7aHello", ColorParser.parse("&aHello"));
        assertEquals("\u00A7cRed \u00A7bAqua", ColorParser.parse("&cRed &bAqua"));
        assertEquals("\u00A74Dark Red", ColorParser.parse("&4Dark Red"));
    }

    @Test
    @DisplayName("Legacy format codes: &l, &o, &n, &m, &k, &r")
    void formatCodes() {
        assertEquals("\u00A7l\u00A7oBold Italic", ColorParser.parse("&l&oBold Italic"));
        assertEquals("\u00A7rReset", ColorParser.parse("&rReset"));
    }

    @Test
    @DisplayName("Full hex codes: &#RRGGBB")
    void fullHex() {
        String result = ColorParser.parse("&#FF0000Red");
        assertTrue(result.startsWith("\u00A7x\u00A7f\u00A7f\u00A70\u00A70\u00A70\u00A70"));
        assertTrue(result.endsWith("Red"));
    }

    @Test
    @DisplayName("Short hex codes: &#RGB")
    void shortHex() {
        String result = ColorParser.parse("&#F00Red");
        assertTrue(result.startsWith("\u00A7x\u00A7f\u00A7f\u00A70\u00A70\u00A70\u00A70"));
        assertTrue(result.endsWith("Red"));
    }

    @Test
    @DisplayName("Named colors: {red}, {gold}, etc.")
    void namedColors() {
        String result = ColorParser.parse("{red}Hello");
        assertTrue(result.contains("\u00A7x")); // Contains hex section code
        assertTrue(result.endsWith("Hello"));
    }

    @Test
    @DisplayName("Gradient: <gradient:#FF0000:#0000FF>text</gradient>")
    void gradient() {
        String result = ColorParser.parse("<gradient:#FF0000:#0000FF>AB</gradient>");
        // Should contain 2 hex color codes (one per character)
        long sectionCount = result.chars().filter(c -> c == '\u00A7').count();
        assertTrue(sectionCount >= 14); // At least 2 full hex codes (7 § per hex)
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
    }

    @Test
    @DisplayName("Rainbow: <rainbow>text</rainbow>")
    void rainbow() {
        String result = ColorParser.parse("<rainbow>ABC</rainbow>");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
        assertTrue(result.contains("C"));
        long sectionCount = result.chars().filter(c -> c == '\u00A7').count();
        assertTrue(sectionCount >= 21); // 3 chars * 7 § per hex
    }

    @Test
    @DisplayName("Null and empty input")
    void nullAndEmpty() {
        assertEquals("", ColorParser.parse(""));
    }

    @Test
    @DisplayName("No color codes passes through unchanged")
    void noColorCodes() {
        assertEquals("Hello World", ColorParser.parse("Hello World"));
    }

    @Test
    @DisplayName("Mixed color formats in one string")
    void mixed() {
        String result = ColorParser.parse("&aGreen &#FF0000Red {gold}Gold");
        assertTrue(result.contains("\u00A7a")); // Legacy green
        assertTrue(result.contains("Green"));
        assertTrue(result.contains("Red"));
        assertTrue(result.contains("Gold"));
    }

    @Test
    @DisplayName("Ampersand that isn't a color code passes through")
    void notAColorCode() {
        assertEquals("Tom & Jerry", ColorParser.parse("Tom & Jerry"));
        assertEquals("&z stays", ColorParser.parse("&z stays"));
    }
}
```

**Step 2: Run tests**

Run: `cd .worktrees/architecture-rehaul && ./gradlew test --tests "com.hyperperms.chat.ColorParserTest" 2>&1 | tail -10`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/hyperperms/chat/ColorParserTest.java
git commit -m "test: add comprehensive ColorParser tests"
```

---

### Task 4: Write ServiceContainer tests

**Files:**
- Create: `src/test/java/com/hyperperms/lifecycle/ServiceContainerTest.java`

**Step 1: Write tests**

```java
package com.hyperperms.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class ServiceContainerTest {

    private ServiceContainer container;

    @BeforeEach
    void setUp() {
        container = new ServiceContainer();
    }

    @Test
    @DisplayName("Register and retrieve a service")
    void registerAndGet() {
        container.register(String.class, "hello");
        assertEquals("hello", container.get(String.class));
    }

    @Test
    @DisplayName("Get unregistered service throws")
    void getUnregistered() {
        assertThrows(IllegalStateException.class, () -> container.get(String.class));
    }

    @Test
    @DisplayName("Double registration throws")
    void doubleRegister() {
        container.register(String.class, "first");
        assertThrows(IllegalStateException.class, () -> container.register(String.class, "second"));
    }

    @Test
    @DisplayName("getOptional returns empty for unregistered")
    void getOptionalEmpty() {
        assertTrue(container.getOptional(String.class).isEmpty());
    }

    @Test
    @DisplayName("getOptional returns value for registered")
    void getOptionalPresent() {
        container.register(String.class, "hello");
        assertEquals("hello", container.getOptional(String.class).orElse(null));
    }

    @Test
    @DisplayName("has returns correct status")
    void hasService() {
        assertFalse(container.has(String.class));
        container.register(String.class, "hello");
        assertTrue(container.has(String.class));
    }

    @Test
    @DisplayName("clear removes all services")
    void clearServices() {
        container.register(String.class, "hello");
        container.register(Integer.class, 42);
        container.clear();
        assertFalse(container.has(String.class));
        assertFalse(container.has(Integer.class));
    }
}
```

**Step 2: Run tests**

Run: `cd .worktrees/architecture-rehaul && ./gradlew test --tests "com.hyperperms.lifecycle.ServiceContainerTest" 2>&1 | tail -10`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/hyperperms/lifecycle/ServiceContainerTest.java
git commit -m "test: add ServiceContainer unit tests"
```

---

### Task 5: Write InMemoryStorageProvider tests

**Files:**
- Create: `src/test/java/com/hyperperms/storage/StorageProviderTest.java`

**Step 1: Write abstract storage test suite**

These tests should pass against ANY `StorageProvider` implementation. Run them against `InMemoryStorageProvider` now. In the future, the same tests can run against JSON, SQLite, and MariaDB providers.

```java
package com.hyperperms.storage;

import com.hyperperms.internal.storage.StorageProvider;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.test.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StorageProviderTest {

    private StorageProvider storage;

    @BeforeEach
    void setUp() {
        InMemoryStorageProvider mem = new InMemoryStorageProvider();
        mem.init().join();
        mem.clear();
        storage = mem;
    }

    @Test
    @DisplayName("Save and load a user")
    void saveAndLoadUser() {
        UUID uuid = UUID.randomUUID();
        User user = new User(uuid, "TestPlayer");
        user.setNode(Node.builder("test.permission").value(true).build());

        storage.saveUser(user).join();
        User loaded = storage.loadUser(uuid).join();

        assertNotNull(loaded);
        assertEquals(uuid, loaded.getUuid());
        assertEquals("TestPlayer", loaded.getUsername());
    }

    @Test
    @DisplayName("Load nonexistent user returns null")
    void loadMissingUser() {
        User loaded = storage.loadUser(UUID.randomUUID()).join();
        assertNull(loaded);
    }

    @Test
    @DisplayName("Delete a user")
    void deleteUser() {
        UUID uuid = UUID.randomUUID();
        storage.saveUser(new User(uuid, "Test")).join();
        storage.deleteUser(uuid).join();
        assertNull(storage.loadUser(uuid).join());
    }

    @Test
    @DisplayName("Save and load a group")
    void saveAndLoadGroup() {
        Group group = new Group("admin");
        group.setWeight(100);
        storage.saveGroup(group).join();

        Group loaded = storage.loadGroup("admin").join();
        assertNotNull(loaded);
        assertEquals("admin", loaded.getName());
    }

    @Test
    @DisplayName("Load all groups")
    void loadAllGroups() {
        storage.saveGroup(new Group("admin")).join();
        storage.saveGroup(new Group("moderator")).join();

        var groups = storage.loadAllGroups().join();
        assertEquals(2, groups.size());
    }

    @Test
    @DisplayName("Get user UUIDs")
    void getUserUuids() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        storage.saveUser(new User(uuid1, "Player1")).join();
        storage.saveUser(new User(uuid2, "Player2")).join();

        var uuids = storage.getUserUuids().join();
        assertEquals(2, uuids.size());
        assertTrue(uuids.contains(uuid1));
        assertTrue(uuids.contains(uuid2));
    }

    @Test
    @DisplayName("Lookup UUID by username")
    void lookupUuid() {
        UUID uuid = UUID.randomUUID();
        storage.saveUser(new User(uuid, "TestPlayer")).join();

        UUID found = storage.lookupUuid("TestPlayer").join();
        assertEquals(uuid, found);
    }

    @Test
    @DisplayName("isHealthy returns true by default")
    void isHealthy() {
        assertTrue(storage.isHealthy());
    }
}
```

**Important:** The `User` and `Group` constructors above are approximate. The implementer must check the actual constructors in `model/User.java` and `model/Group.java` and adjust accordingly.

**Step 2: Run tests**

Run: `cd .worktrees/architecture-rehaul && ./gradlew test --tests "com.hyperperms.storage.StorageProviderTest" 2>&1 | tail -10`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/hyperperms/storage/StorageProviderTest.java
git commit -m "test: add abstract StorageProvider test suite with InMemoryStorageProvider"
```

---

### Task 6: Write PluginLifecycle tests

**Files:**
- Create: `src/test/java/com/hyperperms/lifecycle/PluginLifecycleTest.java`

**Step 1: Write lifecycle tests**

```java
package com.hyperperms.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginLifecycleTest {

    @Test
    @DisplayName("Stages initialize in order")
    void stagesInitializeInOrder() throws Exception {
        List<String> order = new ArrayList<>();
        ServiceContainer container = new ServiceContainer();
        PluginLifecycle lifecycle = new PluginLifecycle(container);

        lifecycle.addStage(testStage("B", 200, order));
        lifecycle.addStage(testStage("A", 100, order));
        lifecycle.addStage(testStage("C", 300, order));

        lifecycle.initialize();

        assertEquals(List.of("A:init", "B:init", "C:init"), order);
    }

    @Test
    @DisplayName("Stages shutdown in reverse order")
    void stagesShutdownInReverse() throws Exception {
        List<String> order = new ArrayList<>();
        ServiceContainer container = new ServiceContainer();
        PluginLifecycle lifecycle = new PluginLifecycle(container);

        lifecycle.addStage(testStage("A", 100, order));
        lifecycle.addStage(testStage("B", 200, order));
        lifecycle.addStage(testStage("C", 300, order));

        lifecycle.initialize();
        order.clear();
        lifecycle.shutdown();

        assertEquals(List.of("C:shutdown", "B:shutdown", "A:shutdown"), order);
    }

    @Test
    @DisplayName("Failed stage shuts down previous stages")
    void failedStageRollback() {
        List<String> order = new ArrayList<>();
        ServiceContainer container = new ServiceContainer();
        PluginLifecycle lifecycle = new PluginLifecycle(container);

        lifecycle.addStage(testStage("A", 100, order));
        lifecycle.addStage(failingStage("B", 200));
        lifecycle.addStage(testStage("C", 300, order));

        assertThrows(Exception.class, lifecycle::initialize);

        // A should have been shut down, C should never have initialized
        assertTrue(order.contains("A:init"));
        assertTrue(order.contains("A:shutdown"));
        assertFalse(order.contains("C:init"));
    }

    private Stage testStage(String name, int order, List<String> tracker) {
        return new Stage() {
            public String name() { return name; }
            public int order() { return order; }
            public void initialize(ServiceContainer c) { tracker.add(name + ":init"); }
            public void shutdown(ServiceContainer c) { tracker.add(name + ":shutdown"); }
        };
    }

    private Stage failingStage(String name, int order) {
        return new Stage() {
            public String name() { return name; }
            public int order() { return order; }
            public void initialize(ServiceContainer c) throws Exception {
                throw new RuntimeException("Stage " + name + " failed");
            }
        };
    }
}
```

**Step 2: Run tests**

Run: `cd .worktrees/architecture-rehaul && ./gradlew test --tests "com.hyperperms.lifecycle.PluginLifecycleTest" 2>&1 | tail -10`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/hyperperms/lifecycle/PluginLifecycleTest.java
git commit -m "test: add PluginLifecycle tests for ordering and rollback"
```

---

### Task 7: Run all tests and verify

**Step 1: Run the full test suite**

Run: `cd .worktrees/architecture-rehaul && ./gradlew test 2>&1 | tail -20`
Expected: All tests PASS

**Step 2: Final commit**

```bash
git commit --allow-empty -m "test: Phase 5 complete — test infrastructure in place

InMemoryStorageProvider, TestServiceContainer, and foundational tests
for ColorParser, ServiceContainer, StorageProvider, and PluginLifecycle."
```

---

## Verification Checklist

1. `./gradlew test` — all tests pass
2. `./gradlew compileTestJava` — compiles without errors
3. InMemoryStorageProvider implements every method in StorageProvider
4. TestServiceContainer creates a fully functional service container
5. ColorParser tests cover all format types (legacy, hex, named, gradient, rainbow)
6. PluginLifecycle tests verify ordering and failure rollback
7. StorageProvider tests are abstract enough to run against any provider
