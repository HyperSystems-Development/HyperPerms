# HyperPerms

[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?logo=discord&logoColor=white)](https://discord.gg/SNPjyfkYPc)
[![GitHub](https://img.shields.io/github/stars/HyperSystemsDev/HyperPerms?style=social)](https://github.com/HyperSystemsDev/HyperPerms)

A production-grade permissions management plugin for Hytale servers. Part of the **HyperSystems** plugin suite.

**Version:** 2.4.3
**Game:** Hytale Early Access
**License:** GPLv3

---

## Overview

HyperPerms provides advanced permission management with contextual permissions, wildcard support, group inheritance, promotion tracks, and a web-based editor. Built for performance with LRU caching and async operations.

---

## Key Features

- **Contextual Permissions** - Per-world, per-region, per-server permission contexts
- **Wildcard Support** - Full wildcard matching (`plugin.command.*` matches `plugin.command.home`)
- **Timed Permissions** - Temporary permissions with automatic expiration cleanup
- **Group Inheritance** - Weight-based priority system with cycle detection
- **Track System** - Promotion/demotion tracks for rank progression
- **Pluggable Storage** - JSON, SQLite, and MySQL support
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

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hp reload` | Reload configuration | `hyperperms.admin.reload` |
| `/hp info` | Plugin information | `hyperperms.admin.info` |
| `/hp user <player> info` | View player permissions | `hyperperms.user.info` |
| `/hp user <player> permission set <perm>` | Set permission | `hyperperms.user.permission.set` |
| `/hp user <player> parent add <group>` | Add to group | `hyperperms.user.parent.add` |
| `/hp group create <name>` | Create group | `hyperperms.group.create` |
| `/hp group <group> permission set <perm>` | Set group permission | `hyperperms.group.permission.set` |
| `/hp track create <name>` | Create track | `hyperperms.track.create` |
| `/hp track <track> promote <player>` | Promote player | `hyperperms.track.promote` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hyperperms.admin.*` | Full admin access | op |
| `hyperperms.admin.reload` | Reload configuration | op |
| `hyperperms.admin.info` | View plugin info | op |
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
    "maxSize": 10000,
    "expireAfterAccessMinutes": 10
  },
  "defaultGroup": "default"
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
├── cache/               # LRU permission cache
├── config/              # Configuration handling
├── manager/             # User, Group, Track managers
├── model/               # Core data models
├── resolver/            # Permission resolution engine
├── storage/             # Storage providers
│   └── json/            # JSON storage implementation
├── task/                # Background tasks
└── util/                # Utility classes
```

---

## Building from Source

### Requirements

- Java 21+ (for building)
- Java 25 (for running on Hytale server)
- Gradle 8.12+
- Hytale Server (Early Access)

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

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
