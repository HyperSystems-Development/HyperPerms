# Changelog

All notable changes to HyperPerms will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **LuckPerms H2 migration version mismatch** - Use isolated classloader (platform classloader as parent) when loading H2 driver from LuckPerms libs, preventing other plugins' H2 versions from being picked up via parent-first delegation
- **H2 driver selection** - Prefer modern H2 driver (`h2-driver-2.1.214.jar`) over legacy (`h2-driver-legacy-1.4.199.jar`) to match the current LuckPerms database format (`luckperms-h2-v2.mv.db`)

## [2.9.1] - 2026-03-08

**Server Version:** `2026.02.19-1a311a592`

### Added

- **PermissionsPlus Migration Tool** - Migrate from PermissionsPlus with a single command
  - `/hp migrate permissionsplus` - Preview migration (dry-run)
  - `/hp migrate permissionsplus --confirm` - Execute migration
  - Reads PermissionsPlus JSON data files and transforms groups, users, and permissions into HyperPerms format
  - Permission cleaning and validation for PermissionsPlus-specific formats
- **SSP Auto-Owner Assignment** - First player to join an SSP world is automatically assigned the owner group

### Fixed

- **Permission resolution order** - Changed to most-specific-first resolution, so `a.b.c` is evaluated before `a.b.*` before `a.*` before `*`
- **Template application with existing groups** - Templates now gracefully handle groups that already exist instead of failing
- **Template clearing nodes and tracks** - Use proper mutable methods when clearing nodes and tracks during template application, preventing `UnsupportedOperationException`
- **SSP owner assignment race condition** - Prevent race condition when multiple players join simultaneously during first-player owner assignment

## [2.9.0] - 2026-02-28

**Server Version:** `2026.02.19-1a311a592`

### Fixed

- **Web editor session create returning 500** - The gzip compression added in 2.8.9 was applied to all session create requests, but the Cloudflare Worker API does not support `Content-Encoding: gzip` on incoming request bodies. All `/hp editor` commands failed with "Server returned status 500". Compression is now only applied to payloads exceeding 500KB to protect very large servers from HTTP 413 errors while keeping normal requests uncompressed.

## [2.8.9] - 2026-02-28

**Server Version:** `2026.02.19-1a311a592`

### Added

- **First-Class MMOSkillTree Integration** - Full permission support for MMOSkillTree, one of the biggest plugins on Hytale
  - 200+ permission nodes registered across admin, command, skill, boost, and alternate prefix categories
  - All 23 individual skill nodes (`mmoskilltree.skill.mining`, `.woodcutting`, `.excavation`, etc.)
  - All 140 XP boost permission nodes with the encoded format `mmoskilltree.xpboosts.<target>.<scope>.<multiplier>.<duration>.<cooldown>`
  - Full alias support for MMOSkillTree's `ziggfreed.*` alternate prefix pattern — granting `mmoskilltree.skill.mining` also resolves `ziggfreed.mmoskilltree.skill.mining` checks
  - Hytale command path aliases (`com.ziggfreed.mmoskilltree.command.*` → `mmoskilltree.command.*`)
  - Wildcard expansion for all MMST permission categories
  - Tab completion and web editor support for all MMST permissions
  - Updated RPG and Survival permission templates with appropriate MMST permissions per rank tier
- **Annotation-Based Command Framework** - New declarative command system replacing the old individual command class pattern
  - `@CommandGroup`, `@Command`, `@Arg`, `@OptionalArg`, `@Permission`, `@Confirm` annotations
  - `CommandScanner` automatically discovers and registers annotated command methods
  - `CommandDispatcher` handles argument parsing, permission checks, and confirmation flows
  - 5 annotated command groups: `GroupCommands`, `UserCommands`, `DebugCommands`, `RootCommands`, `PermsCommands`, `BackupCommands`
- **Staged Plugin Lifecycle** - New `PluginLifecycle` orchestrator with `ServiceContainer` dependency injection
  - 11 ordered stages: Config, Storage, CoreManager, Resolver, Registry, Chat, Integration, Web, Scheduler, Analytics, DefaultGroups
  - Stages initialize in order and shut down in reverse — if any stage fails, previously initialized stages are safely torn down
  - `ServiceContainer` provides typed service registration and retrieval across stages
- **Gzip Compressed Web Editor Sessions** - Session create requests are now gzip compressed before sending to the API, preventing HTTP 413 errors on servers with many groups/permissions

### Refactored

- **Plugin Initialization** - `HyperPerms.java` reduced from ~400 lines of monolithic initialization to a clean staged lifecycle (~25 lines). All setup logic moved to dedicated `Stage` implementations in `com.hyperperms.lifecycle.stages`
- **Command System** - Removed 42 old individual command classes (3,500+ lines). Replaced with 5 annotated command group classes (~2,000 lines) — net reduction of ~1,500 lines with better maintainability

### Fixed

- **Config null during stage initialization** - Resolved a race condition where config was not yet available when stages attempted to read it during early lifecycle setup
- **Default groups created before storage ready** - Moved `loadDefaultGroups()` into its own `DefaultGroupsStage` that runs after storage and managers are fully initialized

## [2.8.8] - 2026-02-23

**Server Version:** `2026.02.19-1a311a592`

### Fixed

- **Permission pollution in Hytale's permissions.json** - `syncPermissionsToHytale()` previously pushed all resolved permissions on every change, causing hundreds of permissions to accumulate. Now uses diff-based sync that computes the delta between Hytale's current state and HyperPerms' resolved set, only adding missing and removing stale permissions
- **Race condition in concurrent permission syncs** - Multiple threads (command thread, scheduler, CF pool, web editor) could call `syncPermissionsToHytale()` simultaneously for the same user, racing on Hytale's non-thread-safe `HashSet` view from `getUserPermissions()`. Added per-UUID synchronization locks and defensive copying of the live view
- **Scattered manual sync calls** - Six user commands and `HyperPermsPermissionProvider` each had their own inline `syncPermissionsToHytale()` call via bootstrap reflection. Centralized all sync logic into a `CacheInvalidator.setSyncListener()` hook — every cache invalidation now automatically triggers Hytale sync for affected online users
- **Group commands invalidated entire cache** - Group permission/property changes (`setperm`, `unsetperm`, `setprefix`, `setsuffix`, `setweight`, `setexpiry`, `parent add/remove`) called `invalidateAll()` instead of targeted `invalidateGroup()`, causing unnecessary cache churn for unrelated users
- **Expired permissions not synced to Hytale** - `ExpiryCleanupTask` removed expired nodes but didn't invalidate the cache or trigger Hytale sync, so expired permissions remained active until the player reconnected
- **Inconsistent cache invalidation API** - Some commands used `getCache().invalidate()` (bypassing sync) while others used `getCacheInvalidator().invalidate()` (with sync). Unified all commands to use `getCacheInvalidator()`

## [2.8.7] - 2026-02-22

### Fixed

- **Permissions not applied after permissions.json wipe** - `syncPermissionsToHytale()` only removed negated permissions from Hytale's internal storage but never added granted permissions. After an OOM crash wiped Hytale's `permissions.json`, third-party plugins (OrbisGuard, etc.) using `PermissionsModule.hasPermission()` saw an empty permission set. Now pushes all expanded granted permissions (with wildcard and alias resolution) to other providers, then removes denied permissions — ensuring negations still override grants
- **JSON storage data loss on JVM crash** - `saveUser()`, `saveGroup()`, and `saveTrack()` used `Files.writeString()` with `TRUNCATE_EXISTING`, which could leave files empty or corrupt if the JVM crashed mid-write. Now writes to a `.tmp` file first, then atomically renames to the target path
- **Corrupt JSON file crashes entire load** - `loadAllUsers()`, `loadAllGroups()`, and `loadAllTracks()` only caught `IOException`, not `JsonParseException` (a `RuntimeException`). A single corrupt file would crash the entire load and prevent all other files from loading. Now catches all exceptions, logs a warning with the filename and error, and continues loading remaining files

## [2.8.6] - 2026-02-22

### Added

- **MariaDB/MySQL Storage Backend** - Full database storage provider as an alternative to JSON file-based storage, designed for multi-server deployments sharing a central database
  - `MariaDBStorageProvider` (~1,050 lines) with HikariCP connection pooling
  - Complete async CRUD for users, groups, tracks, and permission nodes
  - JSON dump backup/restore strategy for networked databases
  - 5-table schema: `users`, `groups`, `user_nodes`, `group_nodes`, `tracks` (InnoDB, utf8mb4)
  - Configure via `storage.type: "mariadb"` or `"mysql"` in config.json with full connection options (host, port, database, username, password, poolSize, useSSL)
  - `useSSL` config option with automatic config migration from older versions
  - HikariCP 6.2.1 and MariaDB JDBC 3.5.1 bundled in shadow JAR
- **Category-based debug logging** - New `Logger.DebugCategory` enum with 10 categories (`RESOLUTION`, `CACHE`, `STORAGE`, `CONTEXT`, `INHERITANCE`, `INTEGRATION`, `CHAT`, `WEB`, `MIGRATION`, `EXPIRY`) — each toggleable individually via `/hp debug toggle <category>`
  - Debug traces throughout chat pipeline (`ChatListener`, `ChatManager`, `ChatFormatter`, `PrefixSuffixResolver`)
  - Debug traces for integration setup (Factions, WerChat, PlaceholderAPI, MysticNameTags, VaultUnlocked)
- **Missing `hytale.mods.outdated.notify` permission** - Registered in PermissionRegistry and PermissionAliases, matching the constant defined in Hytale's `HytalePermissions` class
- **Vanilla OP/Default overwrite warning** - Startup check warns server operators if custom permissions are detected in vanilla's OP or Default groups, which are forcibly reset on every server restart by `HytalePermissionsProvider.read()`
- **JitPack publishing** - Other developers can now depend on HyperPerms via `com.github.HyperSystems-Development:HyperPerms:<version>` from JitPack
- **CONTRIBUTING.md** - New contributor guide with build setup, soft dependency instructions, code style, and branch strategy
- **Update permission constants** - Added `UPDATES_ALL`, `UPDATES_TOGGLE`, `UPDATES_NOTIFY` to `Permissions` utility class

### Fixed

- **Permissions not syncing to Hytale after group commands** - User group commands (`/hp user addgroup`, `removegroup`, `promote`, `demote`, `setprimarygroup`, `clone`) only invalidated the Caffeine permission cache but missed ChatAPI/TabListAPI cache invalidation and Hytale permission sync. Negated permissions weren't being removed from Hytale's internal storage after command-based group changes. Now calls full cache invalidation and `syncPermissionsToHytale()`
- **Tab list not sorting by group weight** - `TabListListener` never actually sorted entries by group weight — players were sent in arbitrary order and the client sorted alphabetically. Now sorts the `ServerPlayerListPlayer[]` array by group weight (descending) before sending packets
- **Web editor resetting prefix/suffix priority to 0** - `SessionData.GroupDto` was missing `prefixPriority` and `suffixPriority` fields, so saving from the web editor silently reset priorities to 0
- **Prefix priority using stale data** - `PrefixSuffixResolver` loaded groups from raw storage instead of the GroupManager cache, meaning prefix priority changes via commands could be ignored until the async storage save completed. Now uses `GroupManager.loadGroup()` for all group lookups
- **Primary group excluded from prefix priority** - `PrefixSuffixResolver` only used `user.getInheritedGroups()`, not the user's primary group field. If the primary group wasn't also an inherited group node, it wouldn't participate in prefix priority comparison. Now includes the primary group consistently with `PermissionResolver`
- **Web editor HTTP/2 connection failures** - Java HttpClient defaults to HTTP/2, causing "HTTP/1.1 header parser received no bytes" errors when ALPN negotiation fails. Forced HTTP/1.1 for web editor client connections
- **Noisy gamemode group warnings** - Hytale calls `addUserToGroup` with virtual gamemode groups (Creative, Adventure) on every player login. Downgraded from warning to debug level
- **MariaDB resource leak** - Fixed unclosed connection in backup/restore, missing backups directory initialization, and redundant `setAutoCommit` call
- **Duplicate javadoc** - Fixed `setPlayerContextProvider()` javadoc accidentally duplicated onto the getter

### Changed

- **Build system overhaul** - Hytale Server API now resolved automatically from `maven.hytale.com` instead of local JAR files. Use `-Phytale_channel=pre-release` to build against the pre-release server. VaultUnlocked upgraded to 2.19.0 via Maven coordinate from `repo.codemc.io`
- **Hytale permissions alignment** - Aligned with `hytale-permissions-docs` v1.1.0: documented multi-provider group aggregation, nondeterministic iteration avoidance via virtual user group, wildcard restrictions matching vanilla behavior, and vanilla `permissions.json` initialization semantics

### Refactored

- **Command system extraction** - Decomposed monolithic `HyperPermsCommand` (3,000 lines) into 48 focused command classes under `com.hyperperms.command.*` organized by domain (`user/`, `group/`, `debug/`, `util/`). Root command class is now 90 lines — registration and help only
- **ConfigManager system** - New `com.hyperperms.config` package with `ConfigManager` orchestrating typed config files (`CoreConfig`, `CacheConfig`, `ChatConfig`, `DebugConfig`, `IntegrationConfig`, `WebEditorConfig`) with validation via `ValidationResult`
- **PermissionHolderBase** - Extracted shared node storage, listener, and `PermissionHolder` API from `Group` and `User` into abstract base class — removes ~200 lines of duplicated code
- **AbstractStorageProvider** - Extracted shared executor lifecycle, health tracking, and `runAsync()` helper from `JsonStorageProvider` and `SQLiteStorageProvider` into a common base class
- **AbstractSqlLuckPermsReader** - Extracted shared SQL migration logic from `H2StorageReader` and `SqlStorageReader` — eliminates ~300 lines of duplicated JDBC code
- **SimpleContextCalculator** - Extracted shared boilerplate from 5 context calculators (`Biome`, `GameMode`, `Region`, `Time`, `World`) into a generic base class with `computeValue()` template method
- **CommandUtil shared utilities** - Extracted common message colors, `join()` helper, and confirmation tracking — eliminates duplicated constants across all commands
- **ReflectionUtil** - Centralized reflection helpers used by integration classes

## [2.8.5] - 2026-02-17

**Server Version:** `2026.02.17-255364b8e`

### Fixed

- **Server compatibility**: Compile against latest Hytale server JAR to resolve `NoSuchMethodError` on `PacketHandler.write()` (TabListListener crash)
- **User load race condition**: `UserManagerImpl.loadUser()` now uses first-writer-wins to prevent concurrent loads from replacing a user whose username was already set by `onPlayerConnect`
- **Server version warning**: Manifest now specifies target server version (prevents PluginManager "does not specify a target server version" warning)

### Added

- **Offline player resolution**: `resolveUser()` now falls back to storage lookup and PlayerDB API when in-memory search fails, enabling commands like `/hp user <name> info` to work for offline players
- **PlayerDB integration**: New `PlayerDBService` utility for looking up any Hytale player by username via the playerdb.co API (5-minute TTL cache)
- **Online player safety net**: New `findOnlineUuidByName()` on `PlayerContextProvider` resolves players who are connected but whose async user load hasn't completed yet

### Changed

- **PlayerResolver extraction**: Moved inline `resolveUser()` logic from `HyperPermsCommand` to dedicated `PlayerResolver` utility with 5-step resolution chain (UUID parse → loaded users → online players → storage → PlayerDB)
- **Improved logging**: Player connect/disconnect, user loading, and permission sync now use info level for better server diagnostics
- **Target-aware build**: Compile against release or prerelease server JAR via `-PhytaleTarget` Gradle flag

## [2.8.4] - 2026-02-14

### Changed

- **HyperFactions permission registry overhaul**: Reorganized all HyperFactions permissions into a proper hierarchical structure with category wildcards (`hyperfactions.faction.*`, `hyperfactions.member.*`, `hyperfactions.territory.*`, etc.) and better descriptions
- **Runtime discovery namespace filtering**: Only keeps permissions whose namespace matches the plugin's JAR filename, manifest Name, or manifest Group — eliminates false positives from bundled/relocated dependencies

### Fixed

- **Web editor showing `com.*` command path permissions**: Hytale command path format permissions (e.g., `com.hyperfactions.hyperfactions.command.faction`) are now filtered from the web UI plugin permission scanner (still used internally for wildcard resolution)
- **Runtime discovery no longer skips HyperSystems plugins**: Removed hardcoded exclusion of `hyperhomes`, `hyperwarps`, `hyperfactions` from discovery — these plugins register their own permissions via the built-in registry and discovery should not interfere

## [2.8.3] - 2026-02-11

### Fixed

- **EssentialsPlus Compatibility** - Fixed parameterized permission queries failing silently
  - Plugins like EssentialsPlus use `getFirstPermissionProvider().getGroupPermissions()` to enumerate permissions for prefix scanning (e.g. `essentialsplus.sethome.limit.[n]`, `essentialsplus.home.reduce.cooldown.30s`)
  - HyperPerms was being registered last in the provider chain, so the native Hytale provider (which doesn't understand HyperPerms' virtual user groups) was returned first, yielding empty results
  - Provider registration now reorders the chain to ensure HyperPerms is the primary (first) provider

### Added

- **Permission Enumeration API** - `HyperPermsAPI.getResolvedPermissions(UUID)` returns all granted permission strings for a user
  - Includes permissions from direct nodes and group inheritance, resolved against current contexts
  - Enables any plugin to scan permissions by prefix without depending on the native provider chain

## [2.8.2] - 2026-02-08

### Added

- **Temporary Permissions** - Duration/expiry support for permissions and group membership
  - `/hp user setperm <player> <perm> [value] [duration]` - Set permissions with optional expiry (e.g. `1d`, `2h30m`, `1w`)
  - `/hp group setperm <group> <perm> [value] [duration]` - Same for groups
  - `/hp user setexpiry <player> <perm> <duration|permanent>` - Modify expiry on existing permissions
  - `/hp group setexpiry <group> <perm> <duration|permanent>` - Same for groups
  - `/hp group parent add <group> <parent> [duration]` - Temporary group inheritance
  - `/hp user addgroup <player> <group> [duration]` - Temporary group membership
  - All duration arguments are optional, defaulting to permanent (backwards compatible)
  - `/hp user info` and `/hp group info` now display expiry in amber for temporary permissions
  - Uses existing `TimeUtil` duration parsing (`30s`, `5m`, `2h`, `1d`, `1w`, combos, `permanent`)

### Fixed

- **Web Editor Expiry Pipeline** - Fixed web editor silently dropping expiry data when applying changes
  - `Change.java` now carries expiry field through the DTO pipeline
  - `WebEditorService` reads expiry from JSON in all parsing paths
  - `ChangeApplier.buildNode()` applies expiry when building permission nodes
  - Web editor UI already supported expiry — only the Java-side pipeline was broken

## [2.8.1] - 2026-02-08

### Fixed

- **Permission Negation Bug** - Fixed critical bug where negated permissions set via web editor were granted instead of denied
  - Web editor sent conflicting data (`-permission` prefix with `value: false`), causing double negation in the permission resolver
  - Backend `ChangeApplier` now normalizes `-` prefix permissions to always use `value: true`
  - Frontend `toBackendNode` now sends correct value for negated permissions
- **Permission Display** - Fixed `/hp group info` and `/hp user info` showing raw internal format for negated permissions
  - Was showing `+ -hytale.command.spawn` or `- -hytale.command.spawn`
  - Now correctly shows `- hytale.command.spawn` with red color
  - Also fixed in `/hp user tree` inheritance display
- **Command Feedback** - Fixed setperm commands showing "Granted" for denied permissions
  - `/hp group setperm group perm false` now correctly says "Denied perm on group"
  - `/hp group setperm group -perm` now correctly says "Denied perm on group"
- **Permission List Sorting** - Group and user info commands now display permissions in alphabetical order

### Changed

- **Build System** - Fixed Shadow JAR clobbering in multi-project Gradle builds
  - Added `jar { archiveClassifier = 'plain' }` to prevent the plain JAR task from overwriting the fat JAR

## [2.8.0] - 2026-02-07

### Added

- **HyperPerms API v2 Foundation** - Completely overhauled developer API
  - New event system: GroupCreateEvent, GroupDeleteEvent, GroupModifyEvent, UserGroupChangeEvent, UserLoadEvent, UserUnloadEvent, DataReloadEvent, TrackPromotionEvent, TrackDemotionEvent
  - Cancellable events with EventPriority (LOWEST through MONITOR)
  - Async permission methods: `hasPermissionAsync()`, `getPermissionValueAsync()`, fluent `checkAsync()` builder
  - Permission query API for bulk operations and complex permission lookups
  - Metrics tracking for permission operations
- **PlaceholderAPI Integration** - Native support for PlaceholderAPI on Hytale
  - Faction placeholders, group/rank placeholders, and prefix/suffix placeholders
  - Works with PlaceholderAPI and WiFlow PlaceholderAPI
- **Permission Templates System** - 11 pre-built server configurations
  - Templates: factions, survival, creative, minigames, smp, skyblock, prison, rpg, towny, vanilla, staff
  - `/hp template list`, `/hp template preview`, `/hp template apply`, `/hp template export`
  - Custom templates via JSON files in `templates/` folder
- **Analytics & Auditing System** - Track permission usage (requires SQLite)
  - `/hp analytics summary` - Overview of permission health
  - `/hp analytics hotspots` - Most frequently checked permissions
  - Change history audit trail
- **Cloudflare Workers API** - Split architecture for better performance and cost
  - Game server API routed through `api.hyperperms.com` (Cloudflare Workers)
  - Web editor UI served from `www.hyperperms.com` (Vercel)
  - New `apiUrl` config option with automatic migration
- **LuckPerms H2 Migration** - Complete H2 database reader support
  - Dynamically loads H2 driver from LuckPerms `libs/` folder
  - Handles locked databases by creating temporary copies
  - Support for various LuckPerms folder naming conventions
- **Console Improvements** - Clickable hyperlinks in supported terminals
- **Expanded HyperFactions Integration** - Improved faction permission interop

### Changed

- **Optional SQLite Driver** - JAR size reduced from ~15MB to ~2.4MB
  - SQLite JDBC driver no longer bundled; users download separately if needed
  - H2 driver fallback removed for CurseForge compliance

### Fixed

- **Async Threading** - Fixed threading issue in permission checks
- **Permission Cache Bypass** - Fixed `HyperPermsPermissionSet.contains()` bypassing Caffeine cache, reducing CPU usage by 90%+
- **Group Weight Priority** - Group weight now used as default prefix/suffix priority
- **Web Editor Error Messaging** - Improved error messaging for empty web editor changes
- **Windows H2 File Lock** - Better error message for Windows H2 file lock issue

## [2.7.0] - 2026-02-02

### Added

- **Track-Based Promote/Demote Commands** - Easily manage user progression through rank tracks
  - `/hp user promote <player> <track>` - Promotes user to next rank on track
  - `/hp user demote <player> <track>` - Demotes user to previous rank on track
  - Handles edge cases gracefully (already at top/bottom, not on track)

### Fixed

- **`/hp update confirm` Command** - Fixed "expected 0, given 1" argument error by refactoring to nested subcommand pattern

## [2.6.0] - 2026-02-01

### Added

- **HyperFactions Integration** - Built-in support for HyperFactions permission integration
  - Seamless permission checking between HyperPerms and HyperFactions
  - Automatic permission provider registration when HyperFactions is detected

### Fixed

- **Permission Set Checks** - Fixed user data not being properly loaded during permission set validation

## [2.5.1] - 2026-01-31

### Changed

- **Permission Resolution Order** - Aligned with Hytale's native implementation
  - Global wildcard (`*`) now checked first
  - Prefix wildcards resolve shortest-first (`a.*` before `a.b.*`)

### Fixed

- **User Loading** - Fixed user not being loaded during permission set checks
- **Runtime Permission Discovery** - Fixed plugins directory not being found

## [2.5.0] - 2026-01-30

### Added

- **Runtime Permission Discovery** - Automatically discovers and registers permissions from all installed plugins
  - JAR scanning at startup for permission strings in bytecode
  - Intelligent filtering with blacklist of code-related words
  - Results cached in `jar-scan-cache.json` for performance
  - Web editor displays discovered permissions with "Installed" badges

- **Operator Update Notification System** - Never miss an update
  - `/hp update` - Check for available updates
  - `/hp update confirm` - Download update to mods folder
  - `/hp updates on|off` - Toggle join notifications
  - Preferences persist in `notification-preferences.json`

- **LuckPerms Migration Tool** - Migrate with a single command
  - `/hp migrate luckperms` - Preview migration (dry-run)
  - `/hp migrate luckperms --confirm` - Execute migration
  - Supports YAML, JSON, H2, MySQL/MariaDB backends
  - Migrates groups, users, tracks, temporary permissions, contexts

### Fixed

- **Hex Color Support** - Added hex color parsing (`§x§R§R§G§G§B§B` format)
- **Werchat Compatibility** - HyperPerms defers chat handling when Werchat is installed
- **HyperPerms + HyFactions Chat** - Resolved chat prefix conflict
- **Player List Formatting** - Complete rewrite using Hytale's packet system
- **Universal Permission Negation** - Restructured `WildcardMatcher.check()` to properly evaluate negations before grants
- **User Permission Leak** - Fixed `PermissionProvider.addUserPermissions()` incorrectly persisting every permission check

## [2.4.5] - 2026-01-26

### Added

- **VaultUnlocked Integration** - First Hytale permission plugin with full VaultUnlocked support
  - Automatic registration as VaultUnlocked permission provider
  - Supports permission checks, group operations, context-aware resolution
  - Zero configuration required

- **Dynamic Permission Support** - Web editor shows "Installed" badges for server plugins
- **HyperFactions & HyperHomes Permission Registry** - 31+ pre-registered permissions

### Fixed

- Optional dependency format for Hytale's plugin loader
- Transient permission handling with graceful fallback

## [2.4.3] - 2026-01-24

### Added

- **Hytale Permission Discovery** - Discovered actual permission nodes Hytale checks
  - `.self`/`.other` suffix pattern for player-targeted commands
  - ~100+ new permission mappings between web UI and actual Hytale nodes
- **New Command Support** - Warp commands, inventory commands, teleport sub-commands
- **Documentation** - Added `HYTALE_PERMISSIONS.md` reference

### Changed

- **Command System Overhaul** - Centralized formatting, flag syntax for optional arguments
- Confirmation steps for destructive commands
- Per-entity locking for concurrent modifications

### Fixed

- Alias expansion in `getUserDirectPermissions()`
- Case-insensitive permission checking for Hytale compatibility
- `/hp check` command argument handling
- `clearNodes()` avoiding `UnsupportedOperationException`

## [2.4.1] - 2026-01-24

### Fixed

- **Critical: Player Group Assignments Preserved on Restart** - Fixed user data not being loaded during server startup
  - Added `userManager.loadAll().join()` during initialization
  - Modified `loadUser()` to use atomic `compute()` operations
  - Changed critical `saveUser()` calls to await completion

## [2.3.5] - 2026-01-23

### Added

- **HyperHomes Integration** - Permission aliasing for HyperHomes plugin
  - `hyperhomes.gui` maps to actual Hytale permission nodes
  - Wildcard `hyperhomes.*` expands to all HyperHomes permissions

### Fixed

- User permissions not being recognized by Hytale's built-in system
- Added virtual user group mechanism for direct user permissions

## [2.3.4] - 2026-01-21

### Fixed

- **ChatAPI Race Condition** - Fixed prefixes returning empty strings for external plugins
  - Single atomic preload operation instead of separate async operations
  - ChatAPI cache preloaded when players connect
  - Player data invalidated from cache on disconnect

## [2.3.3] - 2026-01-21

### Fixed

- **ChatAPI.getPrefix() Returning Empty** - Increased cache TTL and sync timeout
- **Web Editor Null Pointer Exceptions** - Added comprehensive null-safety checks to JSON parsing
- **JSON Parsing Robustness** - Handle multiple field name variations gracefully

## [2.3.2] - 2026-01-20

### Added

- `/hp resetgroups --confirm` command to reset all groups to plugin defaults

### Fixed

- Faction placeholders (`%faction%`, `%faction_rank%`, `%faction_tag%`) not resolving in chat
- Permission inheritance - inherited permissions from parent groups now work correctly
- Default group permission nodes changed from `hytale.command.*` to `hytale.system.command.*`

## [2.3.1] - 2026-01-20

### Fixed

- Faction placeholders not resolving properly in chat
- Permission inheritance for Hytale commands

## [2.3.0] - 2026-01-20

### Added

- **Tab List Formatting** - Native prefix/suffix display in server tab list
  - New `tabList` config section with customizable format
  - Automatic cache invalidation when permissions change
  - TabListAPI for external plugin integration

- **Tebex/Donation System Support** - Commands now support offline players via UUID
  - `/hp user addgroup {uuid} <group>` creates user if needed
  - Works with Tebex `{id}` placeholder

### Fixed

- Web editor "type field is null" crash when fetching changes

## [2.2.5] - 2026-01-18

### Fixed

- Non-OP players couldn't use commands even with correct permissions
- `HyperPermsPermissionProvider` now properly creates users with default group

## [2.2.4] - 2026-01-18

### Fixed

- Auto-updater exception on Windows when backing up old JAR (Windows locks loaded JAR files)

## [2.2.0] - 2026-01-18

### Added

- **Auto-Update System** - Check for updates with `/hp version` and install with `/hp update`
- Compatibility with latest Hytale Server JAR

### Fixed

- Chat prefixes now update instantly when permissions change
- Group prefix display when adding player to groups
- Faction tags and group prefixes display together properly
- Various caching issues causing outdated info
