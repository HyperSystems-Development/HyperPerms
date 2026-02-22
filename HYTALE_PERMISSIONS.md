# Hytale Permission Reference

This document contains the **actual permission nodes** that Hytale's server checks, discovered by decompiling HytaleServer.jar. HyperPerms uses an alias system to map user-friendly web UI permissions to these actual Hytale permissions.

## Permission Generation Pattern

From `HytalePermissions.java`:
```java
public static String fromCommand(String name) {
    return "hytale.command." + name;  // e.g., "hytale.command.gamemode.self"
}
```

## .self/.other Pattern

Many player-targeted commands use a `.self`/`.other` suffix pattern:
- `.self` - Permission to use the command on yourself
- `.other` - Permission to use the command on other players

Example: To change another player's gamemode, you need `hytale.command.gamemode.other`

---

## Player Commands (with .self/.other variants)

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.gamemode.self` | Change own gamemode |
| `hytale.command.gamemode.other` | Change other player's gamemode |
| `hytale.command.give.self` | Give items to self |
| `hytale.command.give.other` | Give items to others |
| `hytale.command.kill.self` | Kill self |
| `hytale.command.kill.other` | Kill other players |
| `hytale.command.damage.self` | Damage self |
| `hytale.command.damage.other` | Damage others |
| `hytale.command.spawn.self` | Teleport self to spawn |
| `hytale.command.spawn.other` | Teleport others to spawn |
| `hytale.command.whereami.self` | Show own location |
| `hytale.command.whereami.other` | Show other's location |
| `hytale.command.refer.self` | Refer self |
| `hytale.command.refer.other` | Refer others |
| `hytale.command.player.effect.apply.self` | Apply effects to self |
| `hytale.command.player.effect.apply.other` | Apply effects to others |
| `hytale.command.player.effect.clear.self` | Clear own effects |
| `hytale.command.player.effect.clear.other` | Clear others' effects |

---

## Teleport Commands

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.teleport.self` | Teleport self |
| `hytale.command.teleport.other` | Teleport others |
| `hytale.command.teleport.all` | Teleport all players |
| `hytale.command.teleport.back` | Teleport back |
| `hytale.command.teleport.forward` | Teleport forward |
| `hytale.command.teleport.top` | Teleport to top |
| `hytale.command.teleport.home` | Teleport home |
| `hytale.command.teleport.world` | Teleport to world |
| `hytale.command.teleport.history` | View teleport history |

---

## Warp Commands

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.warp.go` | Use warps |
| `hytale.command.warp.set` | Set warps |
| `hytale.command.warp.remove` | Remove warps |
| `hytale.command.warp.list` | List warps |
| `hytale.command.warp.reload` | Reload warps |

---

## Op/Permissions Commands

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.op.add` | Add operators |
| `hytale.command.op.remove` | Remove operators |

---

## Inventory Commands

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.invsee` | View other inventories |
| `hytale.command.invsee.modify` | Modify other inventories |
| `hytale.command.spawnitem` | Spawn items |

---

## Editor Permissions

**Note:** `builderTools` uses camelCase (case sensitivity matters!)

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.editor.asset` | Asset editor access |
| `hytale.editor.builderTools` | Builder tools (**camelCase!**) |
| `hytale.editor.brush.use` | Use brushes |
| `hytale.editor.brush.config` | Configure brushes |
| `hytale.editor.prefab.use` | Use prefabs |
| `hytale.editor.prefab.manage` | Manage prefabs |
| `hytale.editor.selection.use` | Use selection |
| `hytale.editor.selection.clipboard` | Copy/paste |
| `hytale.editor.selection.modify` | Modify selections |
| `hytale.editor.history` | Undo/redo |
| `hytale.editor.packs.create` | Create packs |
| `hytale.editor.packs.edit` | Edit packs |
| `hytale.editor.packs.delete` | Delete packs |

---

## Other Permissions

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.camera.flycam` | Fly camera mode |
| `hytale.world_map.teleport.coordinate` | Teleport via coordinates |
| `hytale.world_map.teleport.marker` | Teleport via markers |
| `hytale.system.update.notify` | Update notifications |
| `hytale.mods.outdated.notify` | Receive outdated mod notifications |

---

## Web UI to Actual Hytale Permission Mapping

HyperPerms translates web UI permissions to actual Hytale permissions:

| Web UI Permission | Expands To (Actual Hytale) |
|-------------------|----------------------------|
| `hytale.command.player.gamemode` | `hytale.command.gamemode.self`, `hytale.command.gamemode.other` |
| `hytale.command.player.kill` | `hytale.command.kill.self`, `hytale.command.kill.other` |
| `hytale.command.player.inventory.give` | `hytale.command.give.self`, `hytale.command.give.other` |
| `hytale.command.player.damage` | `hytale.command.damage.self`, `hytale.command.damage.other` |
| `hytale.command.player.teleport` | `hytale.command.teleport.self`, `hytale.command.teleport.other` |
| `hytale.command.world.spawnblock` | `hytale.command.spawn.self`, `hytale.command.spawn.other` |
| `hytale.command.player.effect.apply` | `hytale.command.player.effect.apply.self`, `.other` |
| `hytale.command.player.effect.clear` | `hytale.command.player.effect.clear.self`, `.other` |
| `hytale.command.player.whereami` | `hytale.command.whereami.self`, `hytale.command.whereami.other` |
| `hytale.command.player.inventory.see` | `hytale.command.invsee`, `hytale.command.invsee.modify` |
| `hytale.command.op` | `hytale.command.op.add`, `hytale.command.op.remove` |
| `hytale.command.warp` | `hytale.command.warp.go`, `hytale.command.warp.list` |
| `hytale.command.warp.admin` | `hytale.command.warp.set`, `.remove`, `.reload` |

---

## Shorthand Aliases

Common shorthand permissions also expand to actual Hytale permissions:

| Shorthand | Expands To |
|-----------|------------|
| `hytale.command.gamemode` | `hytale.command.gamemode.self`, `hytale.command.gamemode.other` |
| `hytale.command.tp` | `hytale.command.teleport.self`, `hytale.command.teleport.other` |
| `hytale.command.kill` | `hytale.command.kill.self`, `hytale.command.kill.other` |
| `hytale.command.give` | `hytale.command.give.self`, `hytale.command.give.other` |
| `hytale.command.damage` | `hytale.command.damage.self`, `hytale.command.damage.other` |
| `hytale.command.spawn` | `hytale.command.spawn.self`, `hytale.command.spawn.other` |
| `hytale.command.heal` | `hytale.command.player.effect.apply.self`, `.other` |

---

## Wildcard Patterns

Wildcards expand to include all actual Hytale permissions in that category:

| Wildcard | Expands To |
|----------|------------|
| `hytale.command.gamemode.*` | `.self`, `.other` |
| `hytale.command.teleport.*` | `.self`, `.other`, `.all`, `.back`, `.forward`, `.top`, `.home`, `.world`, `.history` |
| `hytale.command.warp.*` | `.go`, `.set`, `.remove`, `.list`, `.reload` |
| `hytale.command.op.*` | `.add`, `.remove` |
| `hytale.editor.*` | All editor permissions including `builderTools` (camelCase) |

---

## How This Works

1. **User assigns permission** via Web UI (e.g., `hytale.command.player.gamemode`)
2. **HyperPerms stores** that permission in the group/user data
3. **When permission is checked**, `PermissionAliases.expand()` adds all actual Hytale equivalents
4. **Hytale checks** `hytale.command.gamemode.self` - which is now in the expanded set

This allows the web UI to use friendly, hierarchical permission names while ensuring compatibility with Hytale's actual permission checks.

---

## Important Notes

### Vanilla OP/Default Group Overwrite

Hytale's built-in `HytalePermissionsProvider` forcibly re-inserts the default OP (`["*"]`) and Default (`[]`) groups using `put()` every time `permissions.json` is loaded. This happens on server startup and `/perm reload`. Any custom permissions added to these vanilla groups via `/perm` commands **will be lost on restart**.

**Recommendation:** Always use HyperPerms groups instead of modifying vanilla's OP or Default groups. Use `/hp group create <name>` to create persistent groups.

HyperPerms logs a warning at startup if it detects custom permissions in vanilla's OP or Default groups.

### Multi-Provider Group Aggregation

Hytale's `PermissionsModule.getGroupsForUser()` aggregates non-empty group sets from **all** registered providers. This means HyperPerms users will appear in both the HyperPerms virtual group (`user:<uuid>`) and vanilla's `Default` group (since `HytalePermissionsProvider` returns `["Default"]` for users without explicit vanilla groups).

This is expected behavior — vanilla's Default group has no permissions by default, so it's harmless. However, if someone adds permissions to vanilla's Default group via `/perm`, those permissions will apply but be lost on restart due to the overwrite behavior described above.

### Wildcard Restrictions

Middle wildcards (e.g., `hytale.*.ban`) are **not** supported. The `*` character in such patterns is treated as a literal, not a wildcard. This matches vanilla Hytale behavior. Wildcards only work in two positions:
- **Standalone:** `*` (grant all) or `-*` (deny all)
- **Trailing:** `prefix.*` (grant all under prefix) or `-prefix.*` (deny all under prefix)

### Vanilla `permissions.json` Initialization

When `HytalePermissionsProvider.create()` is called (first server run), it writes an empty JSON object `{}` to `permissions.json`. The default OP and Default groups are injected in-memory by `read()`, not stored in the file.

## Verification Steps

1. Build the plugin: `./gradlew build`
2. Deploy to test server with HyperPerms
3. Create a test group with `hytale.command.player.gamemode`
4. Join as a player in that group
5. Try `/gamemode creative` - should work if aliases expand correctly
6. Use `/hyperperms verbose` to see permission expansion in action
