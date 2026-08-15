# Hytale Permission Reference

This document contains the **actual permission nodes** that Hytale's server checks, discovered by decompiling HytaleServer.jar. HyperPerms uses an alias system to map user-friendly web UI permissions to these actual Hytale permissions.

**Current target:** Hytale `0.6.0-pre.12.2` (Update 6).

> **This list is a curated subset, not the whole set.** Since HyperPerms 3.0.0 the registry also
> imports every node the running server registered with `PermissionsModule.registerPermission`,
> which is the authoritative list for whatever build you are on. Update 6 added around forty
> commands; the ones worth reading about by name are documented below, the rest are picked up
> automatically. Run `/hp perms list --category hytale` to see what your server actually registered.

---

## Update 6 (0.6.0): the whitelist is now a permission

The single change most likely to surprise you. `HytaleWhitelistProvider` is **gone**. When
`RequireJoinPermission` is on, the server refuses any player who does not resolve:

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.server.join` | Connect to the server while the join requirement is on |

No built-in group is granted it, so turning the requirement on refuses everyone until you grant it
(admins keep their way in because the admin group holds every permission). An existing
`whitelist.json` is migrated to grants on first boot and renamed `whitelist.json.migrated`.

What this means on a HyperPerms server:

- **Grant it to a group to whitelist that group.** `/hp group permission set <group> hytale.server.join`
  is the idiomatic approach, and it composes with contexts and tracks like any other node.
- **`/whitelist add <player>` works** and stores a direct user node — HyperPerms makes a specific
  exception for this node, because it normally ignores engine-issued grants.
- **`/whitelist remove` only revokes a direct grant.** It cannot take back a grant that belongs to a
  group; that is Hytale's model, not a HyperPerms limitation. Use `/hp group permission unset`.
- **`/whitelist list` shows direct grants only.** A player whitelisted via a group will not appear,
  even though they can connect.

---

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
| `hytale.editor.blockSpawner` | Open the block spawner panel (**camelCase!**, Update 6) |

---

## Other Permissions

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.camera.flycam` | Fly camera mode |
| `hytale.movement.noclip` | Server-side no-clip (Update 6; also needs fly) |
| `hytale.world_map.teleport.coordinate` | Teleport via coordinates |
| `hytale.world_map.teleport.marker` | Teleport via markers |
| `hytale.system.update.notify` | Update notifications |
| `hytale.mods.outdated.notify` | Receive outdated mod notifications |
| `hytale.status.backup.error` | Receive backup failure notifications (Update 6) |

---

## Spectator Mode (Update 6)

Follows the usual `.self`/`.other` split, with a third node for following a named player.

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.spectate.self` | Enter and leave spectator mode |
| `hytale.command.spectate.watch` | Spectate a specific player, or fly freely |
| `hytale.command.spectate.other` | Force another player into or out of spectator mode |

---

## Hardcore Lives (Update 6)

`/player lives` sits in the WorldEditor group; `/player respawn` is in Builder, with reviving
someone else split into its own node.

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.player.lives.get` | View remaining hardcore lives |
| `hytale.command.player.lives.set` | Set remaining hardcore lives |
| `hytale.command.player.lives.clear` | Clear hardcore lives tracking |
| `hytale.command.player.respawn.other` | Revive another player |

---

## Newly Split `.other` Nodes (Update 6)

Each of these was previously covered by its parent command's node. Update 6 split the
act-on-someone-else case out, and **no built-in group is granted them by default** — an operator
who relied on the parent node will need to grant these explicitly. HyperPerms' bare aliases
(`hytale.command.model`, `hytale.command.recipe`, `hytale.command.give.armor`) expand to cover
both halves, so an existing HyperPerms group grant keeps working.

| Actual Hytale Permission | Description |
|--------------------------|-------------|
| `hytale.command.give.armor.other` | `/give armor --player <other>` |
| `hytale.command.model.other` | Change another player's model |
| `hytale.command.model.set.other` | Set another player's model |
| `hytale.command.model.reset.other` | Reset another player's model |
| `hytale.command.recipe.learn.other` | Teach another player a recipe |
| `hytale.command.recipe.forget.other` | Make another player forget a recipe |
| `hytale.command.recipe.list.other` | List another player's recipes |
| `hytale.command.warp.go` | Travel to a warp (previously implied by `/warp list`) |

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

### Vanilla `/perm user remove` Reaches HyperPerms Data (Update 6)

Before 0.6.0, `PermissionsModule.removeUserPermission` wrote only to the *first* registered
provider. Update 6 changed it to iterate **every** provider, on the reasoning that a check reads
every provider so a removal must too. HyperPerms is first in the chain, so this is not a change in
who receives the call — but the built-in provider now also gets it, and any other provider on the
server does too. If you run a second permission plugin, expect `/perm user remove` to affect both.

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
