# HyperPerms Architecture Rehaul Design

## Context

HyperPerms has grown to ~1000 users and the codebase needs to reflect that maturity. Built incrementally, the plugin has solid abstractions (StorageProvider, EventBus, ContextManager) but suffers from a god-class initialization method (250+ lines), 48 command classes with repetitive boilerplate, a chat system with duplicated logic and fragile reflection-based integrations, and no test infrastructure.

This rehaul modernizes the architecture across 5 phased branches without breaking existing functionality.

## Phases

| Phase | Branch | Focus | Depends On |
|-------|--------|-------|------------|
| 1 | `refactor/lifecycle` | Staged Lifecycle & Service Container | None |
| 2 | `refactor/commands` | Annotation-Driven Command Framework | Phase 1 |
| 3 | `refactor/chat` | Chat & Display System Rewrite | Phase 1 |
| 4 | `refactor/packages` | Package Reorganization | Phases 2 & 3 |
| 5 | `refactor/tests` | Test Infrastructure | Phase 4 |

---

## Phase 1: Staged Lifecycle & Service Container

### Problem
`HyperPerms.enable()` is 250+ lines performing 16 sequential initialization steps. Untestable, hard to reason about, impossible to modify safely.

### Design

**ServiceContainer** — typed singleton registry:
```java
public class ServiceContainer {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    public <T> void register(Class<T> type, T instance) { ... }
    public <T> T get(Class<T> type) { ... }
    public <T> Optional<T> getOptional(Class<T> type) { ... }
}
```

**Stage interface**:
```java
public interface Stage {
    String name();
    int order();
    void initialize(ServiceContainer container) throws Exception;
    void shutdown(ServiceContainer container);
}
```

**PluginLifecycle** — manages stages:
```java
public class PluginLifecycle {
    public void addStage(Stage stage);
    public void initialize();   // runs stages in order
    public void shutdown();     // runs stages in reverse order
}
```

**Stages** (in order):
1. `ConfigStage` — loads HyperPermsConfig
2. `StorageStage` — creates/inits StorageProvider via StorageFactory
3. `CacheStage` — creates PermissionCache + CacheInvalidator
4. `CoreManagerStage` — creates EventBus, GroupManager, TrackManager, UserManager; loads all data
5. `ResolverStage` — creates PermissionResolver, ContextManager, registers context calculators
6. `RegistryStage` — PermissionRegistry, RuntimePermissionDiscovery
7. `CommandStage` — registers all commands
8. `ChatStage` — ChatManager, TabListManager
9. `IntegrationStage` — soft-dependency plugin bridges (Vault, PAPI, WerChat, HyperFactions, MysticNameTags)
10. `WebStage` — WebEditorService, BackupManager
11. `SchedulerStage` — periodic tasks (expiry cleanup, discovery auto-save)
12. `AnalyticsStage` — AnalyticsManager, UpdateChecker, MetricsAPI

**Result**: `HyperPerms.enable()` becomes ~15 lines.

### Files to create
- `internal/lifecycle/Stage.java`
- `internal/lifecycle/PluginLifecycle.java`
- `internal/lifecycle/ServiceContainer.java`
- `internal/lifecycle/stages/ConfigStage.java`
- `internal/lifecycle/stages/StorageStage.java`
- `internal/lifecycle/stages/CacheStage.java`
- `internal/lifecycle/stages/CoreManagerStage.java`
- `internal/lifecycle/stages/ResolverStage.java`
- `internal/lifecycle/stages/RegistryStage.java`
- `internal/lifecycle/stages/CommandStage.java`
- `internal/lifecycle/stages/ChatStage.java`
- `internal/lifecycle/stages/IntegrationStage.java`
- `internal/lifecycle/stages/WebStage.java`
- `internal/lifecycle/stages/SchedulerStage.java`
- `internal/lifecycle/stages/AnalyticsStage.java`

### Files to modify
- `HyperPerms.java` — replace enable()/disable() with lifecycle delegation

---

## Phase 2: Annotation-Driven Command Framework

### Problem
48 command classes with repetitive boilerplate (argument definition, permission checks, player resolution, feedback messaging). Adding a command requires a new file, manual registration, and duplicating patterns.

### Design

**Annotations**:
- `@CommandGroup(name, permission)` — marks a class as a command group
- `@Command(name, description, aliases)` — marks a method as a subcommand
- `@Permission(value)` — permission required to execute
- `@Arg(name, description)` — required argument
- `@OptionalArg(name, description, defaultValue)` — optional argument
- `@Confirm(message, timeoutSeconds)` — requires double-invocation to confirm

**Command group classes** (replace 48 individual classes):
- `GroupCommands.java` — all `/hp group` subcommands (~13 methods)
- `UserCommands.java` — all `/hp user` subcommands (~13 methods)
- `TrackCommands.java` — all `/hp track` subcommands
- `DebugCommands.java` — all `/hp debug` subcommands
- `BackupCommands.java` — all `/hp backup` subcommands
- `PermsCommands.java` — all `/hp perms` subcommands
- `UtilityCommands.java` — check, reload, export, import, resetgroups
- `WebCommands.java` — editor, apply (conditional on web editor enabled)

**Framework classes**:
- `CommandScanner.java` — discovers @CommandGroup classes, builds command tree
- `CommandDispatcher.java` — routes execution, extracts arguments, checks permissions
- `CommandUtil.java` — shared formatting utilities (keep existing, refine)

**Result**: ~48 files → ~13 files. Each command is a ~15-25 line method with zero boilerplate.

### Files to create
- `internal/command/annotation/CommandGroup.java`
- `internal/command/annotation/Command.java`
- `internal/command/annotation/Permission.java`
- `internal/command/annotation/Arg.java`
- `internal/command/annotation/OptionalArg.java`
- `internal/command/annotation/Confirm.java`
- `internal/command/CommandScanner.java`
- `internal/command/CommandDispatcher.java`
- `internal/command/groups/GroupCommands.java`
- `internal/command/groups/UserCommands.java`
- `internal/command/groups/TrackCommands.java`
- `internal/command/groups/DebugCommands.java`
- `internal/command/groups/BackupCommands.java`
- `internal/command/groups/PermsCommands.java`
- `internal/command/groups/UtilityCommands.java`
- `internal/command/groups/WebCommands.java`

### Files to delete
- All 48 individual command classes in `command/`

---

## Phase 3: Chat & Display System Rewrite

### Problem
ChatManager (660 lines) and TabListManager duplicate formatting logic. ColorUtil does 5+ regex passes per message. No per-rank chat formats. Plugin integrations use fragile reflection. No cache size limits.

### Design

**Shared formatting core**:
- `DisplayFormatter` — abstract base with placeholder resolution, color processing, caching
- `ChatManager extends DisplayFormatter` — chat-specific formatting + event handling
- `TabListManager extends DisplayFormatter` — tab list formatting + update scheduling

**Single-pass color parser**:
- State machine in `ColorParser.java` replaces regex chain in ColorUtil
- Handles `&c`, `&#hex`, `{named}`, `<gradient>`, `<rainbow>` in one pass
- ColorUtil becomes a thin facade delegating to ColorParser

**Per-rank chat formats**:
```json
{
  "chatFormats": {
    "default": "%prefix% %player%: %message%",
    "admin": "&c[Admin] &f%player%: %message%",
    "vip": "&6[VIP] &f%player%: %message%"
  },
  "formatResolution": "highest-weight"
}
```
Resolution: find highest-weight group the player belongs to that has a format entry; fall back to "default".

**Integration API overhaul**:
- `PluginBridge` interface — common contract for all integrations
- `HyperFactions`: use `HyperFactionsAPI` directly (no reflection — it has proper static methods)
- `WerChat`: expose a proper `WerChatAPI` class in WerChat itself, use it directly (you own both plugins)
- `PlaceholderAPI`: keep expansion, switch external parsing from reflection to direct API
- `MysticNameTags`: keep event-driven invalidation, clean up reflection

**Integration registry**:
```java
if (server.getPluginManager().isLoaded("WerChat")) {
    container.register(WerChatBridge.class, new WerChatBridge());
}
```

**Chat event pipeline**:
- `ChatFormatEvent` fired via HyperPerms EventBus before broadcast
- External plugins subscribe to modify formatted messages
- Replaces competing `PlayerChatEvent.Formatter` priority fights

**Caffeine caches**:
- Replace manual ConcurrentHashMap+TTL caches with Caffeine
- Max size bounds, automatic eviction, statistics

### Files to create
- `internal/chat/DisplayFormatter.java`
- `internal/chat/ColorParser.java`
- `internal/chat/ChatFormatEvent.java`
- `internal/integration/PluginBridge.java`
- `internal/integration/bridges/WerChatBridge.java`
- `internal/integration/bridges/HyperFactionsBridge.java`
- `internal/integration/bridges/PlaceholderApiBridge.java`
- `internal/integration/bridges/MysticNameTagsBridge.java`
- `internal/integration/bridges/VaultBridge.java`

### Files to modify
- `chat/ChatManager.java` — extend DisplayFormatter, slim down
- `chat/TabListManager.java` — extend DisplayFormatter, slim down
- `chat/ColorUtil.java` — delegate to ColorParser
- `chat/ChatFormatter.java` — simplify with shared base
- `chat/PrefixSuffixResolver.java` — minor cleanup
- `chat/ChatConfig.java` — add per-rank format support
- `api/ChatAPI.java` — expose new chat events

### Files to delete
- `integration/WerChatIntegration.java` (replaced by WerChatBridge)
- `integration/FactionIntegration.java` (replaced by HyperFactionsBridge)
- `integration/PlaceholderAPIIntegration.java` (replaced by PlaceholderApiBridge)
- `integration/MysticNameTagsIntegration.java` (replaced by MysticNameTagsBridge)

---

## Phase 4: Package Reorganization

### Problem
No clear API boundary for external consumers. Root package has implementation classes. Integration classes are 600-800 line monoliths.

### Target structure
```
com.hyperperms/
├── api/                    # Public API (stable contracts)
│   ├── event/              # All event classes
│   ├── context/            # Context, ContextSet
│   ├── chat/               # ChatAPI
│   └── HyperPermsAPI.java
├── internal/               # Implementation (can change freely)
│   ├── lifecycle/          # Stage, PluginLifecycle, ServiceContainer
│   ├── command/            # Command framework + groups
│   ├── manager/            # UserManager, GroupManager, TrackManager
│   ├── storage/            # Storage providers (JSON, SQLite, MariaDB)
│   ├── resolver/           # PermissionResolver, InheritanceGraph
│   ├── cache/              # PermissionCache, CacheInvalidator
│   ├── chat/               # Chat formatting implementation
│   ├── context/            # Context calculators
│   ├── registry/           # PermissionRegistry, RuntimeDiscovery
│   ├── integration/        # Plugin bridges
│   ├── config/             # HyperPermsConfig
│   ├── backup/             # BackupManager
│   ├── web/                # WebEditorService
│   ├── analytics/          # AnalyticsManager
│   └── util/               # Logger, ColorUtil, TimeUtil
├── model/                  # Domain models (User, Group, Track, Node)
└── HyperPerms.java         # Entry point (thin)
```

**Key principle**: `api/` is the stable contract. `internal/` can be refactored freely. `model/` is shared between both.

**Legacy class path support**: Deprecated re-export classes at old package locations (e.g. `com.hyperperms.chat.ChatManager` extends/delegates to `com.hyperperms.internal.chat.ChatManager`). This prevents breaking any external plugins that reference the old paths. Legacy classes are marked `@Deprecated` with Javadoc pointing to the new location.

---

## Phase 5: Test Infrastructure

### Problem
No tests exist. Components are hard to test in isolation due to initialization coupling.

### Design

**InMemoryStorageProvider** — implements `StorageProvider` with `ConcurrentHashMap`:
- Zero I/O, instant operations
- Returns completed futures
- All tests use this instead of file/DB storage

**TestServiceContainer** — pre-wired container:
- InMemoryStorageProvider
- Real PermissionCache, EventBus, managers
- One-liner setup for integration tests

**Test categories**:
- Stage unit tests — each stage initializes/shuts down correctly in isolation
- Command tests — invoke command methods, verify side effects on in-memory storage
- Chat formatter tests — placeholder substitution, color parsing, per-rank format selection
- Permission resolver tests — inheritance chains, wildcards, context-aware resolution, cycle detection
- Storage provider tests — abstract test suite that runs against all providers (JSON, SQLite, MariaDB, InMemory)
- Integration tests — verify plugin bridges handle missing plugins gracefully

### Files to create
- `src/test/java/com/hyperperms/test/InMemoryStorageProvider.java`
- `src/test/java/com/hyperperms/test/TestServiceContainer.java`
- `src/test/java/com/hyperperms/lifecycle/StageTests.java`
- `src/test/java/com/hyperperms/command/CommandTests.java`
- `src/test/java/com/hyperperms/chat/ChatFormatterTest.java`
- `src/test/java/com/hyperperms/chat/ColorParserTest.java`
- `src/test/java/com/hyperperms/resolver/PermissionResolverTest.java`
- `src/test/java/com/hyperperms/storage/AbstractStorageProviderTest.java`

---

## Verification

Each phase is verified independently before merging:

**Phase 1**: Plugin starts and shuts down correctly. All stages initialize in order. `./gradlew build` passes. Manual test on dev server — all existing functionality works.

**Phase 2**: All existing commands work identically. Tab completion works. Permission checks enforced. `./gradlew build` passes.

**Phase 3**: Chat formatting produces identical output to current system. Per-rank formats work when configured. Plugin bridges detect and integrate with available plugins. Color codes render correctly. Tab list updates work.

**Phase 4**: `./gradlew build` passes. No broken imports. All functionality works. External API consumers (if any) can still access `api/` package.

**Phase 5**: `./gradlew test` passes. All test categories have at least basic coverage. InMemoryStorageProvider passes the same abstract tests as other providers.
