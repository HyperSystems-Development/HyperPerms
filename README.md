# HyperPerms

[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?logo=discord&logoColor=white)](https://discord.gg/SNPjyfkYPc)
[![GitHub](https://img.shields.io/github/stars/HyperSystemsDev/HyperPerms?style=social)](https://github.com/HyperSystemsDev/HyperPerms)

A production-grade permissions management plugin for Hytale servers. Part of the **HyperSystems** plugin suite.

**Version:** 2.7.7
**Game:** Hytale Early Access
**License:** GPLv3

---

## Overview

HyperPerms provides advanced permission management with contextual permissions, wildcard support, group inheritance, promotion tracks, and a web-based editor. Built for performance with LRU caching and async operations.

---

## Key Features

- **Web Editor** - Browser-based permission editing via [hyperperms.com](https://hyperperms.com)
- **Permission Templates** - Pre-built role configurations (admin, moderator, builder, etc.)
- **Contextual Permissions** - Per-world, per-region, per-server permission contexts
- **Wildcard Support** - Full wildcard matching (`plugin.command.*` matches `plugin.command.home`)
- **Timed Permissions** - Temporary permissions with automatic expiration cleanup
- **Group Inheritance** - Weight-based priority system with cycle detection
- **Track System** - Promotion/demotion tracks for rank progression
- **LuckPerms Migration** - Seamless migration from LuckPerms (YAML, JSON, H2, SQLite)
- **VaultUnlocked Integration** - Economy and chat prefix support
- **Analytics System** - Permission usage tracking and audit logs (optional)
- **Console Clickable Links** - OSC8 hyperlinks for modern terminals
- **Pluggable Storage** - JSON (default), SQLite support
- **LRU Caching** - High-performance caching with smart invalidation
- **Event System** - Full event bus for permission changes and checks
- **Async Operations** - Non-blocking storage operations

---

## Installation

1. Download the latest release JAR
2. Place in your server's `mods` folder
3. Start the server
4. Configure in `mods/com.hyperperms_HyperPerms/config.json`

---

## Web Editor

Edit permissions in your browser at [hyperperms.com](https://hyperperms.com):

1. Run `/hp editor` in-game or console
2. Click the generated link (or copy to browser)
3. Make changes in the visual editor
4. Apply with one click - changes sync automatically

No port forwarding required - uses secure Cloudflare Workers API.

---

## Permission Templates

Quick-start with pre-built role configurations:

| Command | Description |
|---------|-------------|
| `/hp template list` | View available templates |
| `/hp template apply <name>` | Apply a template |
| `/hp template preview <name>` | Preview before applying |

**Built-in templates:** `admin`, `moderator`, `builder`, `member`, `default`

**Custom templates:** Place in the `templates/` folder

---

## Migrating from LuckPerms

HyperPerms can import your existing LuckPerms data:

1. Run `/hp migrate luckperms`
2. Review the migration preview
3. Confirm to import groups, users, and tracks

**Supported formats:** YAML, JSON, H2, SQLite

---

## Optional: SQLite Support

SQLite enables analytics tracking and SQLite storage backend.
It's **not bundled** to keep the JAR small (~2.4MB vs ~15MB).

### Why SQLite is Optional

The SQLite JDBC driver includes native libraries for 20+ platforms (Linux, Windows, macOS, FreeBSD, Android - all architectures), which adds ~12MB to the JAR. Since most users only need JSON storage, we made it optional.

### Enabling SQLite Features

1. **Download the driver** from GitHub:
   - Releases page: https://github.com/xerial/sqlite-jdbc/releases/
   - Download the JAR file from the latest release

2. **Place the JAR** in your HyperPerms lib folder:
   ```
   mods/com.hyperperms_HyperPerms/lib/sqlite-jdbc-3.45.1.0.jar
   ```

3. **Restart your server**

The plugin will automatically detect and load the driver on startup.

### Verifying Installation

On startup, you'll see one of these messages:

**Driver found:**
```
[HyperPerms] [SQLite] Loading SQLite JDBC from: sqlite-jdbc-3.45.1.0.jar
[HyperPerms] [SQLite] Successfully loaded SQLite JDBC driver
```

**Driver not found (analytics enabled in config):**
```
[HyperPerms] [Analytics] SQLite driver not found. Analytics disabled.
[HyperPerms] [Analytics] To enable analytics, download sqlite-jdbc JAR to: .../lib/
```

### Without SQLite Driver

- Analytics is disabled (no impact on permissions)
- JSON storage works perfectly (default)
- All permission features work normally
- LuckPerms H2 migration still works (uses LuckPerms's bundled H2 driver)

---

## Analytics (Optional)

Track permission usage and audit changes. Requires SQLite (see above).

Enable in `config.json`:
```json
{
  "analytics": {
    "enabled": true,
    "trackChecks": true,
    "trackChanges": true,
    "retentionDays": 90
  }
}
```

**Commands:**
- `/hp analytics top` - Most checked permissions
- `/hp analytics audit` - Recent permission changes

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hp reload` | Reload configuration | `hyperperms.admin.reload` |
| `/hp info` | Plugin information | `hyperperms.admin.info` |
| `/hp editor` | Open web editor | `hyperperms.admin.editor` |
| `/hp user <player> info` | View player permissions | `hyperperms.user.info` |
| `/hp user <player> permission set <perm>` | Set permission | `hyperperms.user.permission.set` |
| `/hp user <player> parent add <group>` | Add to group | `hyperperms.user.parent.add` |
| `/hp group create <name>` | Create group | `hyperperms.group.create` |
| `/hp group <group> permission set <perm>` | Set group permission | `hyperperms.group.permission.set` |
| `/hp track create <name>` | Create track | `hyperperms.track.create` |
| `/hp track <track> promote <player>` | Promote player | `hyperperms.track.promote` |
| `/hp migrate luckperms` | Migrate from LuckPerms | `hyperperms.admin.migrate` |
| `/hp template list` | List templates | `hyperperms.admin.template` |
| `/hp template apply <name>` | Apply template | `hyperperms.admin.template` |
| `/hp analytics top` | Top checked permissions | `hyperperms.admin.analytics` |
| `/hp analytics audit` | Recent audit log | `hyperperms.admin.analytics` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hyperperms.admin.*` | Full admin access | op |
| `hyperperms.admin.reload` | Reload configuration | op |
| `hyperperms.admin.info` | View plugin info | op |
| `hyperperms.admin.editor` | Use web editor | op |
| `hyperperms.admin.migrate` | Migrate from LuckPerms | op |
| `hyperperms.admin.template` | Manage templates | op |
| `hyperperms.admin.analytics` | View analytics | op |
| `hyperperms.user.*` | User management | op |
| `hyperperms.group.*` | Group management | op |
| `hyperperms.track.*` | Track management | op |

---

## Configuration

Configuration file: `mods/com.hyperperms_HyperPerms/config.json`

```json
{
  "storage": {
    "type": "json"
  },
  "cache": {
    "enabled": true,
    "maxSize": 10000,
    "expireAfterAccessMinutes": 10
  },
  "defaultGroup": "default",
  "webEditor": {
    "enabled": true,
    "apiUrl": "https://api.hyperperms.com"
  },
  "analytics": {
    "enabled": false,
    "trackChecks": true,
    "trackChanges": true,
    "flushIntervalSeconds": 60,
    "retentionDays": 90
  },
  "console": {
    "clickableLinksEnabled": true,
    "forceOsc8": false
  }
}
```

---

## API Usage

```java
// Get the API instance
HyperPermsAPI api = HyperPerms.getApi();

// Check permissions
User user = api.getUserManager().getUser(uuid).join();
boolean canBuild = user.hasPermission("world.build");

// Add permission with context
Node node = Node.builder("world.build")
    .value(true)
    .withContext("world", "creative")
    .build();
user.addPermission(node);

// Create a group
Group admin = Group.builder("admin")
    .weight(100)
    .addPermission(Node.builder("*").build())
    .build();
api.getGroupManager().createGroup(admin);

// Track-based promotion
Track staffTrack = api.getTrackManager().getTrack("staff").join();
api.getTrackManager().promote(user, staffTrack);
```

---

## Architecture

```
com.hyperperms
├── api/                 # Public API interfaces
│   ├── context/         # Context system
│   └── events/          # Event bus and events
├── analytics/           # Permission analytics system
├── cache/               # LRU permission cache
├── config/              # Configuration handling
├── manager/             # User, Group, Track managers
├── migration/           # LuckPerms migration support
│   └── luckperms/       # LuckPerms storage readers
├── model/               # Core data models
├── resolver/            # Permission resolution engine
├── storage/             # Storage providers
│   ├── json/            # JSON storage implementation
│   └── sqlite/          # SQLite storage implementation
├── task/                # Background tasks
├── templates/           # Permission templates
├── util/                # Utility classes
└── webeditor/           # Web editor integration
```

---

## Building from Source

### Requirements

- Java 25 (Temurin recommended)
- Gradle 9.3+
- Shadow Plugin 9.3.1+ (for Java 25 ASM support)

```bash
# Build the plugin
./gradlew build

# Build shadow JAR (fat JAR with relocated dependencies)
./gradlew shadowJar

# Clean build
./gradlew clean shadowJar
```

The output JAR will be in `build/libs/`.

### Troubleshooting

If you see `Unsupported class file major version 69`:
- Ensure Shadow plugin is 9.3.1+ (includes ASM with Java 25/26 support)
- Ensure Gradle is 9.0+ (required for Shadow 9.x)

---

## Support

- **Discord:** https://discord.gg/SNPjyfkYPc
- **GitHub Issues:** https://github.com/HyperSystemsDev/HyperPerms/issues

---

## Credits

Developed by **HyperSystemsDev**

Part of the **HyperSystems** plugin suite:
- [HyperPerms](https://github.com/HyperSystemsDev/HyperPerms) - Advanced permissions
- [HyperHomes](https://github.com/HyperSystemsDev/HyperHomes) - Home teleportation
- [HyperFactions](https://github.com/HyperSystemsDev/HyperFactions) - Faction management
- [HyperWarp](https://github.com/HyperSystemsDev/HyperWarp) - Warps, spawns, TPA

---

*HyperPerms - Control Everything*
