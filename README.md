# HyperPerms

[![Latest Release](https://img.shields.io/github/v/release/HyperSystems-Development/HyperPerms?label=version)](https://github.com/HyperSystems-Development/HyperPerms/releases)
[![License](https://img.shields.io/badge/license-GPLv3-green)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?logo=discord&logoColor=white)](https://discord.com/invite/aZaa5vcFYh)
[![GitHub Stars](https://img.shields.io/github/stars/HyperSystems-Development/HyperPerms?style=social)](https://github.com/HyperSystems-Development/HyperPerms)

**The permission system for Hytale.** Web editor, 11 server templates, plugin integrations, and everything you need out of the box.

**[Documentation](https://www.hyperperms.com/wiki)** | **[Web Editor](https://hyperperms.com)** | **[Discord](https://discord.com/invite/aZaa5vcFYh)** | **[CurseForge](https://www.curseforge.com/hytale/mods/hyperperms)**

![Web Editor](web-editor.png)

## Features

**Web Editor** — Edit permissions in your browser at [hyperperms.com](https://hyperperms.com). Drag-and-drop groups, visual inheritance graphs, smart autocomplete with 300+ permissions, and live chat preview. No port forwarding needed. Want to self-host? The editor is [open source](https://github.com/HyperSystemsDev/HyperPermsEditor) and can run offline on your own infrastructure.

**11 Server Templates** — Survival, RPG, factions, skyblock, prison, creative, SMP, towny, minigames, vanilla, and staff. One command gives you groups, permissions, inheritance, prefixes, and tracks.

**Plugin Integrations** — First-class support for VaultUnlocked, MMOSkillTree (200+ nodes), HyperFactions, PlaceholderAPI, MysticNameTags, and WerChat. Auto-detected, zero configuration.

**Storage Backends** — JSON (default), SQLite, or MariaDB/MySQL with HikariCP connection pooling for multi-server networks.

**Contextual Permissions** — Scope permissions per-world, per-gamemode, or per-server.

**Wildcards & Negation** — `plugin.command.*` matches all subpermissions. `-hytale.command.spawn` denies explicitly.

**Tracks & Inheritance** — Promotion/demotion tracks with weight-based group priority and unlimited inheritance depth.

**Timed Permissions** — Temporary permissions and group membership with automatic expiration (`1d`, `2h30m`, `1w`).

**Analytics & Auditing** — Track permission usage, view hotspots, and audit change history (requires SQLite).

**Runtime Discovery** — Automatically scans installed plugins and discovers their permission nodes. Discovered permissions appear in the web editor with "Installed" badges.

**LuckPerms Migration** — One-command import from LuckPerms (YAML, JSON, H2, MySQL/MariaDB). Automatic backup before migration.

## Quick Start

1. Drop `HyperPerms.jar` in your `mods/` folder
2. Start your server
3. Run `/hp editor` to open the web editor, or use commands:

```
/hp template apply survival          # Instant rank hierarchy
/hp group create admin               # Create a group
/hp group admin setperm *            # Grant all permissions
/hp user Steve addgroup admin        # Add player to group
```

## Commands

| Command | Description |
|---------|-------------|
| `/hp editor` | Open web-based permission editor |
| `/hp user <player> info` | View player's groups and permissions |
| `/hp user <player> setperm <perm> [value] [duration]` | Set a permission (optionally temporary) |
| `/hp user <player> addgroup <group> [duration]` | Add player to group |
| `/hp user <player> promote <track>` | Promote on a track |
| `/hp group create <name>` | Create a new group |
| `/hp group <name> setperm <perm> [value] [duration]` | Set group permission |
| `/hp group <name> parent add <parent> [duration]` | Add parent group |
| `/hp template list` | List available templates |
| `/hp template apply <name>` | Apply a server template |
| `/hp check <player> <perm>` | Test a permission |
| `/hp debug toggle <category>` | Toggle debug logging |
| `/hp backup create [name]` | Create a backup |
| `/hp migrate luckperms [--confirm]` | Import from LuckPerms |
| `/hp reload` | Reload configuration |

<details>
<summary><strong>All Permissions</strong></summary>

| Permission | Description |
|------------|-------------|
| `hyperperms.command.*` | Full admin access |
| `hyperperms.command.user.*` | User management |
| `hyperperms.command.group.*` | Group management |
| `hyperperms.command.track.*` | Track management |
| `hyperperms.command.check.self` | Check own permissions |
| `hyperperms.command.check.others` | Check other players' permissions |

</details>

## Configuration

Config file: `mods/com.hyperperms_HyperPerms/config.json`

<details>
<summary><strong>View full config</strong></summary>

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
    "retentionDays": 90
  },
  "console": {
    "clickableLinksEnabled": true
  }
}
```

**Storage types:** `"json"` (default), `"sqlite"`, `"mariadb"`, `"mysql"`

For MariaDB/MySQL, add connection details:
```json
{
  "storage": {
    "type": "mariadb",
    "host": "localhost",
    "port": 3306,
    "database": "hyperperms",
    "username": "root",
    "password": "",
    "poolSize": 10,
    "useSSL": false
  }
}
```

</details>

## Important: Vanilla Group Overwrite

Hytale's built-in permission system forcibly resets the `OP` and `Default` groups every time the server starts. Any custom permissions added to these groups via `/perm` will be **lost on restart**. Always use HyperPerms groups instead (`/hp group create <name>`). HyperPerms logs a warning at startup if it detects custom permissions in vanilla groups.

## Optional: SQLite & Analytics

SQLite enables analytics tracking and audit logs. It's **not bundled** to keep the JAR small (~7MB vs ~20MB).

<details>
<summary><strong>Enable SQLite features</strong></summary>

1. Download from [sqlite-jdbc releases](https://github.com/xerial/sqlite-jdbc/releases/)
2. Place the JAR in `mods/com.hyperperms_HyperPerms/lib/`
3. Restart your server

**Without SQLite:** Everything works fine — analytics is simply disabled and JSON storage is used.

**Analytics commands:**
- `/hp analytics summary` — Permission health overview
- `/hp analytics hotspots` — Most checked permissions
- `/hp analytics audit` — Change history

</details>

## For Developers

<details>
<summary><strong>Maven Dependency (JitPack)</strong></summary>

Add HyperPerms as a dependency to build integrations:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.HyperSystemsDev:HyperPerms:2.8.9'
}
```

</details>

<details>
<summary><strong>API Usage</strong></summary>

```java
HyperPermsAPI api = HyperPerms.getApi();

// Check permissions
User user = api.getUserManager().getUser(uuid).join();
boolean canBuild = user.hasPermission("world.build");

// Add contextual permission
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
```

</details>

<details>
<summary><strong>Building from Source</strong></summary>

**Requirements:** Java 25, Gradle 9.3+

All dependencies are resolved automatically from Maven. The Hytale Server API comes from `maven.hytale.com` and VaultUnlocked from `repo.codemc.io`.

```bash
./gradlew shadowJar
# Output: build/libs/HyperPerms-<version>.jar
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for full development setup and contribution guidelines.

</details>

## Links

- [Documentation](https://www.hyperperms.com/wiki) - Full wiki and guides
- [Discord](https://discord.com/invite/aZaa5vcFYh) - Support & community
- [Issues](https://github.com/HyperSystems-Development/HyperPerms/issues) - Bug reports & features
- [Releases](https://github.com/HyperSystems-Development/HyperPerms/releases) - Downloads

---

Part of the **[HyperSystems](https://github.com/HyperSystems-Development)** suite: [HyperPerms](https://github.com/HyperSystems-Development/HyperPerms) | [HyperEssentials](https://github.com/HyperSystems-Development/HyperEssentials) | [HyperFactions](https://github.com/HyperSystems-Development/HyperFactions)
