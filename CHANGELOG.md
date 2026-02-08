# Changelog

All notable changes to HyperPerms will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

*No changes yet*

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
