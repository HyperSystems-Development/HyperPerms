package com.hyperperms.registry;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages permission aliases for compatibility between HyperPerms simplified
 * permission nodes and Hytale's actual permission structure.
 * <p>
 * Hytale uses hierarchical permission paths like:
 * <ul>
 *   <li>{@code hytale.command.player.gamemode} (not {@code hytale.command.gamemode})</li>
 *   <li>{@code hytale.command.player.inventory.give} (not {@code hytale.command.give})</li>
 *   <li>{@code hytale.editor.builderTools} (camelCase, not {@code buildertools})</li>
 * </ul>
 * <p>
 * This class provides bidirectional aliasing so that:
 * <ol>
 *   <li>When a user is granted {@code hytale.command.gamemode}, they also get {@code hytale.command.player.gamemode}</li>
 *   <li>Wildcards like {@code hytale.command.*} properly expand to include all actual Hytale permissions</li>
 * </ol>
 */
public final class PermissionAliases {

    private static PermissionAliases instance;

    /**
     * Maps simplified/friendly permissions to their actual Hytale equivalents.
     * One simplified permission can map to multiple actual permissions.
     */
    private final Map<String, Set<String>> aliasToActual = new HashMap<>();

    /**
     * Maps actual Hytale permissions back to their simplified aliases.
     * Used for reverse lookups.
     */
    private final Map<String, Set<String>> actualToAlias = new HashMap<>();

    /**
     * Gets the singleton instance.
     */
    @NotNull
    public static PermissionAliases getInstance() {
        if (instance == null) {
            instance = new PermissionAliases();
        }
        return instance;
    }

    private PermissionAliases() {
        registerAllAliases();
    }

    /**
     * Registers all permission aliases.
     * <p>
     * These mappings translate between:
     * - Web UI friendly permissions (e.g., hytale.command.player.gamemode)
     * - Actual Hytale permission nodes (e.g., hytale.command.gamemode.self, hytale.command.gamemode.other)
     * <p>
     * Hytale uses a pattern of .self and .other variants for player-targeted commands.
     */
    private void registerAllAliases() {
        // ==================== CORE: Actual Hytale Permissions (.self/.other pattern) ====================
        // These are the ACTUAL permission nodes Hytale checks (discovered via decompilation)

        // Gamemode - Web UI → Actual Hytale
        alias("hytale.command.player.gamemode", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.command.gamemode", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.command.gamemode.self", "hytale.command.gamemode.self");
        alias("hytale.command.gamemode.other", "hytale.command.gamemode.other");
        // Specific gamemode variants
        alias("hytale.command.gamemode.survival", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.command.gamemode.creative", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.command.gamemode.adventure", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.command.gamemode.spectator", "hytale.command.gamemode.self", "hytale.command.gamemode.other");

        // Kill - Web UI → Actual Hytale
        alias("hytale.command.player.kill", "hytale.command.kill.self", "hytale.command.kill.other");
        alias("hytale.command.kill", "hytale.command.kill.self", "hytale.command.kill.other");
        alias("hytale.command.kill.self", "hytale.command.kill.self");
        alias("hytale.command.kill.other", "hytale.command.kill.other");

        // Give - Web UI → Actual Hytale
        alias("hytale.command.player.inventory.give", "hytale.command.give.self", "hytale.command.give.other");
        alias("hytale.command.give", "hytale.command.give.self", "hytale.command.give.other");
        alias("hytale.command.give.self", "hytale.command.give.self");
        alias("hytale.command.give.other", "hytale.command.give.other");

        // Damage - Web UI → Actual Hytale
        alias("hytale.command.player.damage", "hytale.command.damage.self", "hytale.command.damage.other");
        alias("hytale.command.damage", "hytale.command.damage.self", "hytale.command.damage.other");
        alias("hytale.command.damage.self", "hytale.command.damage.self");
        alias("hytale.command.damage.other", "hytale.command.damage.other");

        // Spawn (teleport to spawn) - Web UI → Actual Hytale
        alias("hytale.command.world.spawnblock", "hytale.command.spawn.self", "hytale.command.spawn.other");
        alias("hytale.command.spawn", "hytale.command.spawn.self", "hytale.command.spawn.other");
        alias("hytale.command.spawn.self", "hytale.command.spawn.self");
        alias("hytale.command.spawn.other", "hytale.command.spawn.other");
        alias("hytale.command.spawn.teleport", "hytale.command.spawn.self", "hytale.command.spawn.other");
        alias("hytale.command.spawn.set", "hytale.command.spawn.self", "hytale.command.spawn.other");

        // Whereami - Web UI → Actual Hytale
        alias("hytale.command.player.whereami", "hytale.command.whereami.self", "hytale.command.whereami.other");
        alias("hytale.command.whereami", "hytale.command.whereami.self", "hytale.command.whereami.other");
        alias("hytale.command.whereami.self", "hytale.command.whereami.self");
        alias("hytale.command.whereami.other", "hytale.command.whereami.other");

        // Refer - Web UI → Actual Hytale
        alias("hytale.command.player.refer", "hytale.command.refer.self", "hytale.command.refer.other");
        alias("hytale.command.refer", "hytale.command.refer.self", "hytale.command.refer.other");
        alias("hytale.command.refer.self", "hytale.command.refer.self");
        alias("hytale.command.refer.other", "hytale.command.refer.other");

        // ==================== Effect Commands (.self/.other pattern) ====================
        // Apply effect
        alias("hytale.command.player.effect.apply", "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other");
        alias("hytale.command.effect.apply", "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other");
        alias("hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.self");
        alias("hytale.command.player.effect.apply.other", "hytale.command.player.effect.apply.other");
        // Clear effect
        alias("hytale.command.player.effect.clear", "hytale.command.player.effect.clear.self", "hytale.command.player.effect.clear.other");
        alias("hytale.command.effect.clear", "hytale.command.player.effect.clear.self", "hytale.command.player.effect.clear.other");
        alias("hytale.command.player.effect.clear.self", "hytale.command.player.effect.clear.self");
        alias("hytale.command.player.effect.clear.other", "hytale.command.player.effect.clear.other");
        // Generic effect command
        alias("hytale.command.player.effect",
            "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other",
            "hytale.command.player.effect.clear.self", "hytale.command.player.effect.clear.other");
        alias("hytale.command.effect",
            "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other",
            "hytale.command.player.effect.clear.self", "hytale.command.player.effect.clear.other");
        alias("hytale.command.heal", "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other");

        // ==================== Teleport Commands (Complex Hierarchy) ====================
        // Base teleport - Web UI → Actual Hytale
        alias("hytale.command.player.teleport", "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.command.tp", "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.command.teleport", "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.command.teleport.self", "hytale.command.teleport.self");
        alias("hytale.command.teleport.other", "hytale.command.teleport.other");

        // Teleport sub-commands (these are actual Hytale permissions, no .self/.other)
        alias("hytale.command.player.teleport.all", "hytale.command.teleport.all");
        alias("hytale.command.tpall", "hytale.command.teleport.all");
        alias("hytale.command.teleport.all", "hytale.command.teleport.all");

        alias("hytale.command.player.teleport.back", "hytale.command.teleport.back");
        alias("hytale.command.tpback", "hytale.command.teleport.back");
        alias("hytale.command.teleport.back", "hytale.command.teleport.back");

        alias("hytale.command.player.teleport.forward", "hytale.command.teleport.forward");
        alias("hytale.command.tpforward", "hytale.command.teleport.forward");
        alias("hytale.command.teleport.forward", "hytale.command.teleport.forward");

        alias("hytale.command.player.teleport.top", "hytale.command.teleport.top");
        alias("hytale.command.tptop", "hytale.command.teleport.top");
        alias("hytale.command.teleport.top", "hytale.command.teleport.top");

        alias("hytale.command.player.teleport.home", "hytale.command.teleport.home");
        alias("hytale.command.tphome", "hytale.command.teleport.home");
        alias("hytale.command.teleport.home", "hytale.command.teleport.home");

        alias("hytale.command.player.teleport.world", "hytale.command.teleport.world");
        alias("hytale.command.teleport.world", "hytale.command.teleport.world");

        alias("hytale.command.player.teleport.history", "hytale.command.teleport.history");
        alias("hytale.command.teleport.history", "hytale.command.teleport.history");

        // ==================== Warp Commands (Actual Hytale Permissions) ====================
        alias("hytale.command.warp", "hytale.command.warp.go", "hytale.command.warp.list");
        alias("hytale.command.warp.use", "hytale.command.warp.go");
        alias("hytale.command.warp.go", "hytale.command.warp.go");
        alias("hytale.command.warp.list", "hytale.command.warp.list");
        alias("hytale.command.warp.set", "hytale.command.warp.set");
        alias("hytale.command.warp.remove", "hytale.command.warp.remove");
        alias("hytale.command.warp.reload", "hytale.command.warp.reload");
        alias("hytale.command.warp.admin", "hytale.command.warp.set", "hytale.command.warp.remove", "hytale.command.warp.reload");
        alias("hytale.command.world.warp", "hytale.command.warp.go", "hytale.command.warp.list");

        // ==================== Inventory Commands ====================
        // Invsee (view/modify other player inventories)
        alias("hytale.command.player.inventory.see", "hytale.command.invsee", "hytale.command.invsee.modify");
        alias("hytale.command.invsee", "hytale.command.invsee", "hytale.command.invsee.modify");
        alias("hytale.command.invsee.view", "hytale.command.invsee");
        alias("hytale.command.invsee.modify", "hytale.command.invsee.modify");

        // Spawnitem (distinct from give)
        alias("hytale.command.spawnitem", "hytale.command.spawnitem");
        alias("hytale.command.player.inventory.spawnitem", "hytale.command.spawnitem");

        // Clear inventory
        alias("hytale.command.player.inventory.clear", "hytale.command.player.inventory.clear");
        alias("hytale.command.clear", "hytale.command.player.inventory.clear");

        // ==================== Op Commands (Actual Hytale Permissions) ====================
        alias("hytale.command.op", "hytale.command.op.add", "hytale.command.op.remove");
        alias("hytale.command.op.add", "hytale.command.op.add");
        alias("hytale.command.op.remove", "hytale.command.op.remove");
        alias("hytale.command.opadd", "hytale.command.op.add");
        alias("hytale.command.opremove", "hytale.command.op.remove");

        // ==================== Editor Permissions (Case Sensitivity Matters!) ====================
        alias("hytale.editor.buildertools", "hytale.editor.builderTools");
        alias("hytale.editor.asset", "hytale.editor.asset");
        alias("hytale.editor.brush.use", "hytale.editor.brush.use");
        alias("hytale.editor.brush.config", "hytale.editor.brush.config");
        alias("hytale.editor.prefab.use", "hytale.editor.prefab.use");
        alias("hytale.editor.prefab.manage", "hytale.editor.prefab.manage");
        alias("hytale.editor.selection.use", "hytale.editor.selection.use");
        alias("hytale.editor.selection.clipboard", "hytale.editor.selection.clipboard");
        alias("hytale.editor.selection.modify", "hytale.editor.selection.modify");
        alias("hytale.editor.history", "hytale.editor.history");
        alias("hytale.editor.packs.create", "hytale.editor.packs.create");
        alias("hytale.editor.packs.edit", "hytale.editor.packs.edit");
        alias("hytale.editor.packs.delete", "hytale.editor.packs.delete");

        // ==================== Camera Permissions ====================
        alias("hytale.camera.flycam", "hytale.camera.flycam");
        alias("hytale.command.player.camera", "hytale.camera.flycam");

        // ==================== World Map Permissions ====================
        alias("hytale.world_map.teleport.coordinate", "hytale.world_map.teleport.coordinate");
        alias("hytale.world_map.teleport.marker", "hytale.world_map.teleport.marker");
        alias("hytale.world_map.teleport", "hytale.world_map.teleport.coordinate", "hytale.world_map.teleport.marker");

        // ==================== System Permissions ====================
        alias("hytale.system.update.notify", "hytale.system.update.notify");

        // ==================== Mods Permissions ====================
        alias("hytale.mods.outdated.notify", "hytale.mods.outdated.notify");
        aliasWildcard("hytale.mods.*",
            "hytale.mods.outdated.notify");

        // ==================== Legacy System Command Format ====================
        // Format: hytale.system.command.* → actual Hytale permissions (used by some older configs)
        alias("hytale.system.command.gamemode", "hytale.command.gamemode.self", "hytale.command.gamemode.other");
        alias("hytale.system.command.give", "hytale.command.give.self", "hytale.command.give.other");
        alias("hytale.system.command.kick", "hytale.command.server.kick", "hytale.command.kick");
        alias("hytale.system.command.ban", "hytale.command.server.ban", "hytale.command.ban");
        alias("hytale.system.command.unban", "hytale.command.server.unban", "hytale.command.unban");
        alias("hytale.system.command.kill", "hytale.command.kill.self", "hytale.command.kill.other");
        alias("hytale.system.command.heal", "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other");
        alias("hytale.system.command.tp", "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.system.command.teleport", "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.system.command.spawn", "hytale.command.spawn.self", "hytale.command.spawn.other");
        alias("hytale.system.command.stop", "hytale.command.server.stop", "hytale.command.stop");
        alias("hytale.system.command.op", "hytale.command.op.add", "hytale.command.op.remove");
        alias("hytale.system.command.opadd", "hytale.command.op.add");
        alias("hytale.system.command.opremove", "hytale.command.op.remove");
        alias("hytale.system.command.who", "hytale.command.server.who", "hytale.command.who");
        alias("hytale.system.command.help", "hytale.command.utility.help", "hytale.command.help");
        alias("hytale.system.command.version", "hytale.command.debug.version", "hytale.command.version");
        alias("hytale.system.command.ping", "hytale.command.debug.ping", "hytale.command.ping");
        alias("hytale.system.command.time", "hytale.command.world.time", "hytale.command.time");
        alias("hytale.system.command.weather", "hytale.command.world.weather", "hytale.command.weather");
        alias("hytale.system.command.clear", "hytale.command.player.inventory.clear", "hytale.command.clear");
        alias("hytale.system.command.effect", "hytale.command.player.effect.apply.self", "hytale.command.player.effect.apply.other");
        alias("hytale.system.command.emote", "hytale.command.emote");
        alias("hytale.system.command.plugin", "hytale.command.plugin");
        alias("hytale.system.command.backup", "hytale.command.utility.backup", "hytale.command.backup");

        // ==================== Server Commands ====================
        alias("hytale.command.kick", "hytale.command.server.kick");
        alias("hytale.command.stop", "hytale.command.server.stop");
        alias("hytale.command.who", "hytale.command.server.who");
        alias("hytale.command.maxplayers", "hytale.command.server.maxplayers");
        alias("hytale.command.ban", "hytale.command.server.ban");
        alias("hytale.command.unban", "hytale.command.server.unban");

        // ==================== Utility Commands ====================
        alias("hytale.command.backup", "hytale.command.utility.backup");
        alias("hytale.command.help", "hytale.command.utility.help");

        // ==================== Plugin Commands ====================
        alias("hytale.command.plugin", "hytale.command.plugin");
        alias("hytale.command.plugin.list", "hytale.command.plugin.list");
        alias("hytale.command.plugin.load", "hytale.command.plugin.load");
        alias("hytale.command.plugin.unload", "hytale.command.plugin.unload");
        alias("hytale.command.plugin.reload", "hytale.command.plugin.reload");
        alias("hytale.command.perm", "hytale.command.perm");

        // ==================== Debug Commands ====================
        alias("hytale.command.version", "hytale.command.debug.version");
        alias("hytale.command.ping", "hytale.command.debug.ping");

        // ==================== Other Commands ====================
        alias("hytale.command.emote", "hytale.command.emote");
        alias("hytale.command.whoami", "hytale.command.player.whoami");
        alias("hytale.command.hide", "hytale.command.player.hide");

        // ==================== Wildcard Mappings ====================
        // These ensure that wildcards expand to include all relevant ACTUAL Hytale permissions
        aliasWildcard("hytale.command.gamemode.*",
            "hytale.command.gamemode.self",
            "hytale.command.gamemode.other");
        aliasWildcard("hytale.command.give.*",
            "hytale.command.give.self",
            "hytale.command.give.other");
        aliasWildcard("hytale.command.kill.*",
            "hytale.command.kill.self",
            "hytale.command.kill.other");
        aliasWildcard("hytale.command.damage.*",
            "hytale.command.damage.self",
            "hytale.command.damage.other");
        aliasWildcard("hytale.command.spawn.*",
            "hytale.command.spawn.self",
            "hytale.command.spawn.other");
        aliasWildcard("hytale.command.whereami.*",
            "hytale.command.whereami.self",
            "hytale.command.whereami.other");
        aliasWildcard("hytale.command.refer.*",
            "hytale.command.refer.self",
            "hytale.command.refer.other");
        aliasWildcard("hytale.command.teleport.*",
            "hytale.command.teleport.self",
            "hytale.command.teleport.other",
            "hytale.command.teleport.all",
            "hytale.command.teleport.back",
            "hytale.command.teleport.forward",
            "hytale.command.teleport.top",
            "hytale.command.teleport.home",
            "hytale.command.teleport.world",
            "hytale.command.teleport.history");
        aliasWildcard("hytale.command.warp.*",
            "hytale.command.warp.go",
            "hytale.command.warp.set",
            "hytale.command.warp.remove",
            "hytale.command.warp.list",
            "hytale.command.warp.reload");
        aliasWildcard("hytale.command.op.*",
            "hytale.command.op.add",
            "hytale.command.op.remove");
        aliasWildcard("hytale.command.invsee.*",
            "hytale.command.invsee",
            "hytale.command.invsee.modify");
        aliasWildcard("hytale.command.inventory.*",
            "hytale.command.give.self",
            "hytale.command.give.other",
            "hytale.command.invsee",
            "hytale.command.invsee.modify",
            "hytale.command.spawnitem",
            "hytale.command.player.inventory.clear",
            "hytale.command.player.inventory.backpack",
            "hytale.command.player.inventory.item",
            "hytale.command.player.inventory.itemstate");
        aliasWildcard("hytale.command.effect.*",
            "hytale.command.player.effect.apply.self",
            "hytale.command.player.effect.apply.other",
            "hytale.command.player.effect.clear.self",
            "hytale.command.player.effect.clear.other");
        aliasWildcard("hytale.command.player.*",
            // Actual Hytale .self/.other permissions
            "hytale.command.gamemode.self",
            "hytale.command.gamemode.other",
            "hytale.command.kill.self",
            "hytale.command.kill.other",
            "hytale.command.give.self",
            "hytale.command.give.other",
            "hytale.command.damage.self",
            "hytale.command.damage.other",
            "hytale.command.spawn.self",
            "hytale.command.spawn.other",
            "hytale.command.whereami.self",
            "hytale.command.whereami.other",
            "hytale.command.refer.self",
            "hytale.command.refer.other",
            "hytale.command.teleport.self",
            "hytale.command.teleport.other",
            "hytale.command.player.effect.apply.self",
            "hytale.command.player.effect.apply.other",
            "hytale.command.player.effect.clear.self",
            "hytale.command.player.effect.clear.other",
            // Other player commands
            "hytale.command.player.hide",
            "hytale.command.player.reset",
            "hytale.command.player.respawn",
            "hytale.command.player.zone",
            "hytale.command.player.sudo",
            "hytale.command.player.whoami",
            "hytale.command.player.toggleblockplacementoverride",
            "hytale.camera.flycam",
            "hytale.command.invsee",
            "hytale.command.invsee.modify",
            "hytale.command.spawnitem",
            "hytale.command.player.inventory.clear",
            "hytale.command.player.inventory.backpack",
            "hytale.command.player.inventory.item",
            "hytale.command.player.inventory.itemstate",
            "hytale.command.player.stats",
            "hytale.command.player.stats.add",
            "hytale.command.player.stats.get",
            "hytale.command.player.stats.set",
            "hytale.command.player.stats.reset",
            "hytale.command.player.stats.dump",
            "hytale.command.player.stats.settomax",
            "hytale.command.player.viewradius",
            "hytale.command.player.viewradius.get",
            "hytale.command.player.viewradius.set");
        aliasWildcard("hytale.command.server.*",
            "hytale.command.server",
            "hytale.command.server.kick",
            "hytale.command.server.stop",
            "hytale.command.server.who",
            "hytale.command.server.maxplayers",
            "hytale.command.server.auth",
            "hytale.command.server.auth.login",
            "hytale.command.server.auth.logout",
            "hytale.command.server.auth.status",
            "hytale.command.server.auth.cancel",
            "hytale.command.server.auth.select",
            "hytale.command.server.auth.persistence");
        aliasWildcard("hytale.command.utility.*",
            "hytale.command.utility.backup",
            "hytale.command.utility.help",
            "hytale.command.utility.convertprefabs",
            "hytale.command.utility.eventtitle",
            "hytale.command.utility.notify",
            "hytale.command.utility.stash",
            "hytale.command.utility.validatecpb",
            "hytale.command.utility.lighting",
            "hytale.command.utility.lighting.get",
            "hytale.command.utility.lighting.info",
            "hytale.command.utility.lighting.send",
            "hytale.command.utility.lighting.calculation",
            "hytale.command.utility.lighting.invalidate",
            "hytale.command.utility.lighting.sendtoggle",
            "hytale.command.utility.sleep",
            "hytale.command.utility.sleep.offset",
            "hytale.command.utility.sleep.test",
            "hytale.command.utility.sound",
            "hytale.command.utility.sound.play2d",
            "hytale.command.utility.sound.play3d",
            "hytale.command.utility.worldmap",
            "hytale.command.utility.worldmap.discover",
            "hytale.command.utility.worldmap.undiscover",
            "hytale.command.utility.worldmap.reload",
            "hytale.command.utility.worldmap.clearmarkers",
            "hytale.command.utility.git.update",
            "hytale.command.utility.git.updateassets",
            "hytale.command.utility.git.updateprefabs",
            "hytale.command.utility.metacommands.commands",
            "hytale.command.utility.metacommands.dumpcommands",
            "hytale.command.utility.net.network");
        aliasWildcard("hytale.command.world.*",
            "hytale.command.world.spawnblock",
            "hytale.command.world.chunk",
            "hytale.command.world.chunk.info",
            "hytale.command.world.chunk.load",
            "hytale.command.world.chunk.unload",
            "hytale.command.world.chunk.loaded",
            "hytale.command.world.chunk.resend",
            "hytale.command.world.chunk.regenerate",
            "hytale.command.world.chunk.tint",
            "hytale.command.world.chunk.lighting",
            "hytale.command.world.chunk.tracker",
            "hytale.command.world.chunk.forcetick",
            "hytale.command.world.chunk.marksave",
            "hytale.command.world.chunk.maxsendrate",
            "hytale.command.world.chunk.fixheightmap",
            "hytale.command.world.entity",
            "hytale.command.world.entity.clean",
            "hytale.command.world.entity.clone",
            "hytale.command.world.entity.count",
            "hytale.command.world.entity.dump",
            "hytale.command.world.entity.effect",
            "hytale.command.world.entity.remove",
            "hytale.command.world.entity.resend",
            "hytale.command.world.entity.tracker",
            "hytale.command.world.entity.lod",
            "hytale.command.world.entity.nameplate",
            "hytale.command.world.entity.intangible",
            "hytale.command.world.entity.invulnerable",
            "hytale.command.world.entity.makeinteractable",
            "hytale.command.world.entity.hidefromadventureplayers",
            "hytale.command.world.worldgen",
            "hytale.command.world.worldgen.reload",
            "hytale.command.world.worldgen.benchmark");
        aliasWildcard("hytale.command.debug.*",
            "hytale.command.debug.version",
            "hytale.command.debug.ping",
            "hytale.command.debug.log",
            "hytale.command.debug.assets",
            "hytale.command.debug.assetsduplicates",
            "hytale.command.debug.assettags",
            "hytale.command.debug.packetstats",
            "hytale.command.debug.pidcheck",
            "hytale.command.debug.hitdetection",
            "hytale.command.debug.hudmanagertest",
            "hytale.command.debug.tagpattern",
            "hytale.command.debug.debugplayerposition",
            "hytale.command.debug.showbuildertoolshud",
            "hytale.command.debug.stopnetworkchunksending",
            "hytale.command.debug.messagetranslationtest",
            "hytale.command.debug.packs",
            "hytale.command.debug.packs.list",
            "hytale.command.debug.server",
            "hytale.command.debug.server.gc",
            "hytale.command.debug.server.dump",
            "hytale.command.debug.server.stats",
            "hytale.command.debug.stresstest",
            "hytale.command.debug.stresstest.start",
            "hytale.command.debug.stresstest.stop",
            "hytale.command.debug.component.hitboxcollision",
            "hytale.command.debug.component.hitboxcollision.add",
            "hytale.command.debug.component.hitboxcollision.remove",
            "hytale.command.debug.component.repulsion",
            "hytale.command.debug.component.repulsion.add",
            "hytale.command.debug.component.repulsion.remove");
        aliasWildcard("hytale.editor.*",
            // Note: builderTools uses camelCase (actual Hytale permission)
            "hytale.editor.asset",
            "hytale.editor.builderTools",
            "hytale.editor.history",
            "hytale.editor.brush.use",
            "hytale.editor.brush.config",
            "hytale.editor.prefab.use",
            "hytale.editor.prefab.manage",
            "hytale.editor.selection.use",
            "hytale.editor.selection.clipboard",
            "hytale.editor.selection.modify",
            "hytale.editor.packs.create",
            "hytale.editor.packs.edit",
            "hytale.editor.packs.delete");
        aliasWildcard("hytale.camera.*",
            "hytale.camera.flycam");
        aliasWildcard("hytale.world_map.*",
            "hytale.world_map.teleport.coordinate",
            "hytale.world_map.teleport.marker");

        // ==================== Additional Convenience Aliases ====================
        // Whitelist commands
        alias("hytale.command.whitelist", "hytale.command.server.whitelist");
        alias("hytale.command.whitelist.add", "hytale.command.server.whitelist.add");
        alias("hytale.command.whitelist.remove", "hytale.command.server.whitelist.remove");
        alias("hytale.command.whitelist.list", "hytale.command.server.whitelist.list");
        alias("hytale.command.whitelist.on", "hytale.command.server.whitelist.on");
        alias("hytale.command.whitelist.off", "hytale.command.server.whitelist.off");

        // Spawn set command (distinct from spawn teleport)
        alias("hytale.command.spawnset", "hytale.command.spawn.self", "hytale.command.spawn.other");

        // Building/Editor commands (simplified -> actual)
        alias("hytale.command.fill", "hytale.editor.selection.fill");
        alias("hytale.command.walls", "hytale.editor.selection.walls");
        alias("hytale.command.hollow", "hytale.editor.selection.hollow");
        alias("hytale.command.replace", "hytale.editor.selection.replace");
        alias("hytale.command.copy", "hytale.editor.selection.clipboard");
        alias("hytale.command.cut", "hytale.editor.selection.clipboard");
        alias("hytale.command.paste", "hytale.editor.selection.clipboard");
        alias("hytale.command.undo", "hytale.editor.history");
        alias("hytale.command.redo", "hytale.editor.history");
        alias("hytale.command.pos1", "hytale.editor.selection.use");
        alias("hytale.command.pos2", "hytale.editor.selection.use");
        alias("hytale.command.brush", "hytale.editor.brush.use");
        alias("hytale.command.prefab", "hytale.editor.prefab.use");

        // Entity commands
        alias("hytale.command.entity", "hytale.command.world.entity");
        alias("hytale.command.entity.spawn", "hytale.command.world.entity.spawn");
        alias("hytale.command.entity.remove", "hytale.command.world.entity.remove");
        alias("hytale.command.entity.clone", "hytale.command.world.entity.clone");
        alias("hytale.command.npc", "hytale.command.world.entity.npc");
        alias("hytale.command.mount", "hytale.command.player.mount");
        alias("hytale.command.dismount", "hytale.command.player.dismount");
        alias("hytale.command.hitbox", "hytale.command.debug.component.hitboxcollision");

        // World commands
        alias("hytale.command.worldgen", "hytale.command.world.worldgen");
        alias("hytale.command.chunk", "hytale.command.world.chunk");
        alias("hytale.command.time", "hytale.command.world.time");
        alias("hytale.command.time.set", "hytale.command.world.time.set");
        alias("hytale.command.time.get", "hytale.command.world.time.get");
        alias("hytale.command.weather", "hytale.command.world.weather");
        alias("hytale.command.weather.set", "hytale.command.world.weather.set");
        alias("hytale.command.weather.get", "hytale.command.world.weather.get");

        // Audio/Visual commands
        alias("hytale.command.sound", "hytale.command.utility.sound");
        alias("hytale.command.sound.play", "hytale.command.utility.sound.play2d", "hytale.command.utility.sound.play3d");
        alias("hytale.command.particle", "hytale.command.utility.particle");
        alias("hytale.command.ambience", "hytale.command.utility.ambience");
        alias("hytale.command.tint", "hytale.command.world.chunk.tint");
        alias("hytale.command.lighting", "hytale.command.utility.lighting");

        // Server/Debug commands
        alias("hytale.command.debug", "hytale.command.debug");
        alias("hytale.command.log", "hytale.command.debug.log");
        alias("hytale.command.network", "hytale.command.utility.net.network");
        alias("hytale.command.stresstest", "hytale.command.debug.stresstest");

        // Player state commands (using actual Hytale permissions)
        alias("hytale.command.player",
            "hytale.command.gamemode.self", "hytale.command.gamemode.other",
            "hytale.command.kill.self", "hytale.command.kill.other",
            "hytale.command.damage.self", "hytale.command.damage.other",
            "hytale.command.give.self", "hytale.command.give.other",
            "hytale.command.teleport.self", "hytale.command.teleport.other");
        alias("hytale.command.inventory",
            "hytale.command.give.self", "hytale.command.give.other",
            "hytale.command.invsee", "hytale.command.invsee.modify",
            "hytale.command.spawnitem",
            "hytale.command.player.inventory.clear");
        alias("hytale.command.sleep", "hytale.command.utility.sleep");
        alias("hytale.command.stats", "hytale.command.player.stats");

        // Instance/Auth commands
        alias("hytale.command.instance", "hytale.command.server.instance");
        alias("hytale.command.auth", "hytale.command.server.auth");
        alias("hytale.command.hub", "hytale.command.server.hub");
        alias("hytale.command.leave", "hytale.command.server.leave");

        // ==================== HyperPerms Plugin Permissions ====================
        // These ensure hyperperms.* wildcard properly expands to all HyperPerms permissions
        aliasWildcard("hyperperms.*",
            // Command wildcards
            "hyperperms.command.*",
            "hyperperms.admin.*",
            "hyperperms.chat.*",
            // User commands
            "hyperperms.command.user.info",
            "hyperperms.command.user.info.others",
            "hyperperms.command.user.permission",
            "hyperperms.command.user.group",
            "hyperperms.command.user.promote",
            "hyperperms.command.user.demote",
            "hyperperms.command.user.clear",
            "hyperperms.command.user.clone",
            "hyperperms.command.user.setprefix",
            "hyperperms.command.user.setsuffix",
            // Group commands
            "hyperperms.command.group.info",
            "hyperperms.command.group.list",
            "hyperperms.command.group.create",
            "hyperperms.command.group.delete",
            "hyperperms.command.group.permission",
            "hyperperms.command.group.parent",
            "hyperperms.command.group.modify",
            "hyperperms.command.group.rename",
            "hyperperms.command.group.setweight",
            "hyperperms.command.group.setprefix",
            "hyperperms.command.group.setsuffix",
            "hyperperms.command.group.setdisplayname",
            // Track commands
            "hyperperms.command.track.info",
            "hyperperms.command.track.list",
            "hyperperms.command.track.create",
            "hyperperms.command.track.delete",
            "hyperperms.command.track.modify",
            "hyperperms.command.track.append",
            "hyperperms.command.track.insert",
            "hyperperms.command.track.remove",
            // Admin commands
            "hyperperms.command.reload",
            "hyperperms.command.verbose",
            "hyperperms.command.cache",
            "hyperperms.command.export",
            "hyperperms.command.import",
            "hyperperms.command.listgroups",
            "hyperperms.command.listtracks",
            "hyperperms.command.check",
            "hyperperms.command.check.self",
            "hyperperms.command.check.others",
            // Editor commands
            "hyperperms.command.editor",
            "hyperperms.command.apply",
            // Debug commands
            "hyperperms.command.debug",
            "hyperperms.command.debug.tree",
            "hyperperms.command.debug.resolve",
            "hyperperms.command.debug.contexts",
            // Backup commands
            "hyperperms.command.backup",
            "hyperperms.command.backup.create",
            "hyperperms.command.backup.list",
            "hyperperms.command.backup.restore",
            "hyperperms.command.backup.delete",
            // Permission registry commands
            "hyperperms.command.perms",
            "hyperperms.command.perms.list",
            "hyperperms.command.perms.search",
            // Chat permissions
            "hyperperms.chat.color",
            "hyperperms.chat.color.hex",
            "hyperperms.chat.format",
            "hyperperms.chat.format.bold",
            "hyperperms.chat.format.italic",
            "hyperperms.chat.format.underline",
            "hyperperms.chat.format.strikethrough",
            "hyperperms.chat.format.magic",
            "hyperperms.chat.links",
            "hyperperms.chat.bypass.cooldown",
            "hyperperms.chat.bypass.filter",
            "hyperperms.chat.broadcast",
            "hyperperms.chat.pm",
            "hyperperms.chat.pm.receive",
            "hyperperms.chat.socialspy");

        aliasWildcard("hyperperms.command.*",
            "hyperperms.command.user.info",
            "hyperperms.command.user.info.others",
            "hyperperms.command.user.permission",
            "hyperperms.command.user.group",
            "hyperperms.command.user.promote",
            "hyperperms.command.user.demote",
            "hyperperms.command.user.clear",
            "hyperperms.command.user.clone",
            "hyperperms.command.user.setprefix",
            "hyperperms.command.user.setsuffix",
            "hyperperms.command.group.info",
            "hyperperms.command.group.list",
            "hyperperms.command.group.create",
            "hyperperms.command.group.delete",
            "hyperperms.command.group.permission",
            "hyperperms.command.group.parent",
            "hyperperms.command.group.modify",
            "hyperperms.command.group.rename",
            "hyperperms.command.group.setweight",
            "hyperperms.command.group.setprefix",
            "hyperperms.command.group.setsuffix",
            "hyperperms.command.group.setdisplayname",
            "hyperperms.command.track.info",
            "hyperperms.command.track.list",
            "hyperperms.command.track.create",
            "hyperperms.command.track.delete",
            "hyperperms.command.track.modify",
            "hyperperms.command.track.append",
            "hyperperms.command.track.insert",
            "hyperperms.command.track.remove",
            "hyperperms.command.reload",
            "hyperperms.command.verbose",
            "hyperperms.command.cache",
            "hyperperms.command.export",
            "hyperperms.command.import",
            "hyperperms.command.listgroups",
            "hyperperms.command.listtracks",
            "hyperperms.command.check",
            "hyperperms.command.check.self",
            "hyperperms.command.check.others",
            "hyperperms.command.editor",
            "hyperperms.command.apply",
            "hyperperms.command.debug",
            "hyperperms.command.debug.tree",
            "hyperperms.command.debug.resolve",
            "hyperperms.command.debug.contexts",
            "hyperperms.command.backup",
            "hyperperms.command.backup.create",
            "hyperperms.command.backup.list",
            "hyperperms.command.backup.restore",
            "hyperperms.command.backup.delete",
            "hyperperms.command.perms",
            "hyperperms.command.perms.list",
            "hyperperms.command.perms.search");

        aliasWildcard("hyperperms.command.user.*",
            "hyperperms.command.user.info",
            "hyperperms.command.user.info.others",
            "hyperperms.command.user.permission",
            "hyperperms.command.user.group",
            "hyperperms.command.user.promote",
            "hyperperms.command.user.demote",
            "hyperperms.command.user.clear",
            "hyperperms.command.user.clone",
            "hyperperms.command.user.setprefix",
            "hyperperms.command.user.setsuffix");

        aliasWildcard("hyperperms.command.group.*",
            "hyperperms.command.group.info",
            "hyperperms.command.group.list",
            "hyperperms.command.group.create",
            "hyperperms.command.group.delete",
            "hyperperms.command.group.permission",
            "hyperperms.command.group.parent",
            "hyperperms.command.group.modify",
            "hyperperms.command.group.rename",
            "hyperperms.command.group.setweight",
            "hyperperms.command.group.setprefix",
            "hyperperms.command.group.setsuffix",
            "hyperperms.command.group.setdisplayname");

        aliasWildcard("hyperperms.command.track.*",
            "hyperperms.command.track.info",
            "hyperperms.command.track.list",
            "hyperperms.command.track.create",
            "hyperperms.command.track.delete",
            "hyperperms.command.track.modify",
            "hyperperms.command.track.append",
            "hyperperms.command.track.insert",
            "hyperperms.command.track.remove");

        aliasWildcard("hyperperms.command.backup.*",
            "hyperperms.command.backup.create",
            "hyperperms.command.backup.list",
            "hyperperms.command.backup.restore",
            "hyperperms.command.backup.delete");

        aliasWildcard("hyperperms.command.debug.*",
            "hyperperms.command.debug.tree",
            "hyperperms.command.debug.resolve",
            "hyperperms.command.debug.contexts");

        aliasWildcard("hyperperms.command.perms.*",
            "hyperperms.command.perms.list",
            "hyperperms.command.perms.search");

        aliasWildcard("hyperperms.chat.*",
            "hyperperms.chat.color",
            "hyperperms.chat.color.hex",
            "hyperperms.chat.format",
            "hyperperms.chat.format.bold",
            "hyperperms.chat.format.italic",
            "hyperperms.chat.format.underline",
            "hyperperms.chat.format.strikethrough",
            "hyperperms.chat.format.magic",
            "hyperperms.chat.links",
            "hyperperms.chat.bypass.cooldown",
            "hyperperms.chat.bypass.filter",
            "hyperperms.chat.broadcast",
            "hyperperms.chat.pm",
            "hyperperms.chat.pm.receive",
            "hyperperms.chat.socialspy");

        // ==================== HyperHomes Plugin ====================
        // Core command permissions (these are what HyperHomes actually checks)
        alias("hyperhomes.homes", "com.hyperhomes.hyperhomes.command.homes");
        alias("hyperhomes.home", "com.hyperhomes.hyperhomes.command.home");
        alias("hyperhomes.sethome", "com.hyperhomes.hyperhomes.command.sethome");
        alias("hyperhomes.delhome", "com.hyperhomes.hyperhomes.command.delhome");
        alias("hyperhomes.gui", "com.hyperhomes.hyperhomes.command.homes.gui");

        // hyperhomes.use grants access to /home and /homes commands
        alias("hyperhomes.use",
            "hyperhomes.home",
            "hyperhomes.homes",
            "hyperhomes.gui",
            "com.hyperhomes.hyperhomes.command.homes",
            "com.hyperhomes.hyperhomes.command.home",
            "com.hyperhomes.hyperhomes.command.homes.gui");
        alias("hyperhomes.list",
            "hyperhomes.homes",
            "com.hyperhomes.hyperhomes.command.homes",
            "com.hyperhomes.hyperhomes.command.homes.list");

        // Aliases for backwards compatibility (hyperhomes.set -> hyperhomes.sethome)
        alias("hyperhomes.set",
            "hyperhomes.sethome",
            "com.hyperhomes.hyperhomes.command.sethome",
            "com.hyperhomes.hyperhomes.command.homes.set");
        alias("hyperhomes.delete",
            "hyperhomes.delhome",
            "com.hyperhomes.hyperhomes.command.delhome",
            "com.hyperhomes.hyperhomes.command.homes.delete");
        alias("hyperhomes.teleport",
            "hyperhomes.home",
            "com.hyperhomes.hyperhomes.command.home");

        // Wildcard expansion
        aliasWildcard("hyperhomes.*",
            "hyperhomes.use",
            "hyperhomes.sethome",
            "hyperhomes.delhome",
            "hyperhomes.home",
            "hyperhomes.homes",
            "hyperhomes.gui",
            "hyperhomes.list",
            "hyperhomes.share",
            "hyperhomes.share.public",
            "hyperhomes.share.invite",
            "hyperhomes.teleport",
            "hyperhomes.unlimited",
            "hyperhomes.bypass.cooldown",
            "hyperhomes.bypass.warmup",
            "hyperhomes.admin",
            "hyperhomes.admin.reload",
            "hyperhomes.admin.update",
            "hyperhomes.admin.migrate",
            "hyperhomes.admin.teleport.others",
            "hyperhomes.admin.list",
            "hyperhomes.admin.delete",
            "hyperhomes.admin.modify",
            "com.hyperhomes.hyperhomes.command.homes",
            "com.hyperhomes.hyperhomes.command.home",
            "com.hyperhomes.hyperhomes.command.sethome",
            "com.hyperhomes.hyperhomes.command.delhome",
            "com.hyperhomes.hyperhomes.command.homes.gui",
            "com.hyperhomes.hyperhomes.command.homes.list",
            "com.hyperhomes.hyperhomes.command.homes.set",
            "com.hyperhomes.hyperhomes.command.homes.delete");

        // HyperHomes bypass wildcard
        aliasWildcard("hyperhomes.bypass.*",
            "hyperhomes.bypass.cooldown",
            "hyperhomes.bypass.warmup");

        // HyperHomes admin wildcard
        aliasWildcard("hyperhomes.admin.*",
            "hyperhomes.admin.reload",
            "hyperhomes.admin.update",
            "hyperhomes.admin.migrate",
            "hyperhomes.admin.teleport.others",
            "hyperhomes.admin.list",
            "hyperhomes.admin.delete",
            "hyperhomes.admin.modify");

        // ==================== HyperWarps Plugin ====================
        alias("hyperwarps.warp", "com.hyperwarps.hyperwarps.command.warp");
        alias("hyperwarps.warps", "com.hyperwarps.hyperwarps.command.warps");
        alias("hyperwarps.setwarp", "com.hyperwarps.hyperwarps.command.setwarp");
        alias("hyperwarps.delwarp", "com.hyperwarps.hyperwarps.command.delwarp");
        alias("hyperwarps.warpinfo", "com.hyperwarps.hyperwarps.command.warpinfo");
        alias("hyperwarps.spawn", "com.hyperwarps.hyperwarps.command.spawn");
        alias("hyperwarps.spawns", "com.hyperwarps.hyperwarps.command.spawns");
        alias("hyperwarps.setspawn", "com.hyperwarps.hyperwarps.command.setspawn");
        alias("hyperwarps.delspawn", "com.hyperwarps.hyperwarps.command.delspawn");
        alias("hyperwarps.spawninfo", "com.hyperwarps.hyperwarps.command.spawninfo");
        alias("hyperwarps.tpa", "com.hyperwarps.hyperwarps.command.tpa");
        alias("hyperwarps.tpahere", "com.hyperwarps.hyperwarps.command.tpahere");
        alias("hyperwarps.tpaccept", "com.hyperwarps.hyperwarps.command.tpaccept");
        alias("hyperwarps.tpdeny", "com.hyperwarps.hyperwarps.command.tpdeny");
        alias("hyperwarps.tpcancel", "com.hyperwarps.hyperwarps.command.tpcancel");
        alias("hyperwarps.tptoggle", "com.hyperwarps.hyperwarps.command.tptoggle");
        alias("hyperwarps.back", "com.hyperwarps.hyperwarps.command.back");
        aliasWildcard("hyperwarps.*",
            "hyperwarps.use",
            "hyperwarps.admin",
            "hyperwarps.warp",
            "hyperwarps.warps",
            "hyperwarps.setwarp",
            "hyperwarps.delwarp",
            "hyperwarps.warpinfo",
            "hyperwarps.spawn",
            "hyperwarps.spawns",
            "hyperwarps.setspawn",
            "hyperwarps.delspawn",
            "hyperwarps.spawninfo",
            "hyperwarps.tpa",
            "hyperwarps.tpahere",
            "hyperwarps.tpaccept",
            "hyperwarps.tpdeny",
            "hyperwarps.tpcancel",
            "hyperwarps.tptoggle",
            "hyperwarps.back",
            "hyperwarps.bypass.warmup",
            "hyperwarps.bypass.cooldown",
            "hyperwarps.bypass.toggle",
            "com.hyperwarps.hyperwarps.command.warp",
            "com.hyperwarps.hyperwarps.command.warps",
            "com.hyperwarps.hyperwarps.command.setwarp",
            "com.hyperwarps.hyperwarps.command.delwarp",
            "com.hyperwarps.hyperwarps.command.warpinfo",
            "com.hyperwarps.hyperwarps.command.spawn",
            "com.hyperwarps.hyperwarps.command.spawns",
            "com.hyperwarps.hyperwarps.command.setspawn",
            "com.hyperwarps.hyperwarps.command.delspawn",
            "com.hyperwarps.hyperwarps.command.spawninfo",
            "com.hyperwarps.hyperwarps.command.tpa",
            "com.hyperwarps.hyperwarps.command.tpahere",
            "com.hyperwarps.hyperwarps.command.tpaccept",
            "com.hyperwarps.hyperwarps.command.tpdeny",
            "com.hyperwarps.hyperwarps.command.tpcancel",
            "com.hyperwarps.hyperwarps.command.tptoggle",
            "com.hyperwarps.hyperwarps.command.back");

        // HyperWarps bypass wildcard
        aliasWildcard("hyperwarps.bypass.*",
            "hyperwarps.bypass.warmup",
            "hyperwarps.bypass.cooldown",
            "hyperwarps.bypass.toggle");

        // ==================== HyperFactions Plugin ====================
        // Core command permissions (these are what HyperFactions actually checks)
        alias("hyperfactions.faction", "com.hyperfactions.hyperfactions.command.faction");
        alias("hyperfactions.f", "com.hyperfactions.hyperfactions.command.f");

        // hyperfactions.use grants access to /f and /faction commands
        alias("hyperfactions.use",
            "hyperfactions.faction",
            "hyperfactions.f",
            "com.hyperfactions.hyperfactions.command.faction",
            "com.hyperfactions.hyperfactions.command.f");

        // Core faction management
        alias("hyperfactions.create",
            "com.hyperfactions.hyperfactions.command.faction.create",
            "com.hyperfactions.hyperfactions.command.f.create");
        alias("hyperfactions.invite",
            "com.hyperfactions.hyperfactions.command.faction.invite",
            "com.hyperfactions.hyperfactions.command.f.invite");
        alias("hyperfactions.kick",
            "com.hyperfactions.hyperfactions.command.faction.kick",
            "com.hyperfactions.hyperfactions.command.f.kick");
        alias("hyperfactions.disband",
            "com.hyperfactions.hyperfactions.command.faction.disband",
            "com.hyperfactions.hyperfactions.command.f.disband");
        alias("hyperfactions.promote",
            "com.hyperfactions.hyperfactions.command.faction.promote",
            "com.hyperfactions.hyperfactions.command.f.promote");
        alias("hyperfactions.demote",
            "com.hyperfactions.hyperfactions.command.faction.demote",
            "com.hyperfactions.hyperfactions.command.f.demote");
        alias("hyperfactions.transfer",
            "com.hyperfactions.hyperfactions.command.faction.transfer",
            "com.hyperfactions.hyperfactions.command.f.transfer");

        // Territory management
        alias("hyperfactions.claim",
            "com.hyperfactions.hyperfactions.command.faction.claim",
            "com.hyperfactions.hyperfactions.command.f.claim");
        alias("hyperfactions.unclaim",
            "com.hyperfactions.hyperfactions.command.faction.unclaim",
            "com.hyperfactions.hyperfactions.command.f.unclaim");
        alias("hyperfactions.overclaim",
            "com.hyperfactions.hyperfactions.command.faction.overclaim",
            "com.hyperfactions.hyperfactions.command.f.overclaim");
        alias("hyperfactions.map",
            "com.hyperfactions.hyperfactions.command.faction.map",
            "com.hyperfactions.hyperfactions.command.f.map");

        // Home management
        alias("hyperfactions.home",
            "com.hyperfactions.hyperfactions.command.faction.home",
            "com.hyperfactions.hyperfactions.command.f.home");
        alias("hyperfactions.sethome",
            "com.hyperfactions.hyperfactions.command.faction.sethome",
            "com.hyperfactions.hyperfactions.command.f.sethome");

        // Faction settings
        alias("hyperfactions.rename",
            "com.hyperfactions.hyperfactions.command.faction.rename",
            "com.hyperfactions.hyperfactions.command.f.rename");
        alias("hyperfactions.desc",
            "com.hyperfactions.hyperfactions.command.faction.desc",
            "com.hyperfactions.hyperfactions.command.f.desc");
        alias("hyperfactions.color",
            "com.hyperfactions.hyperfactions.command.faction.color",
            "com.hyperfactions.hyperfactions.command.f.color");
        alias("hyperfactions.open",
            "com.hyperfactions.hyperfactions.command.faction.open",
            "com.hyperfactions.hyperfactions.command.f.open");
        alias("hyperfactions.close",
            "com.hyperfactions.hyperfactions.command.faction.close",
            "com.hyperfactions.hyperfactions.command.f.close");

        // Relations
        alias("hyperfactions.ally",
            "com.hyperfactions.hyperfactions.command.faction.ally",
            "com.hyperfactions.hyperfactions.command.f.ally");
        alias("hyperfactions.enemy",
            "com.hyperfactions.hyperfactions.command.faction.enemy",
            "com.hyperfactions.hyperfactions.command.f.enemy");
        alias("hyperfactions.neutral",
            "com.hyperfactions.hyperfactions.command.faction.neutral",
            "com.hyperfactions.hyperfactions.command.f.neutral");

        // Chat
        alias("hyperfactions.chat",
            "com.hyperfactions.hyperfactions.command.faction.chat",
            "com.hyperfactions.hyperfactions.command.f.chat");
        alias("hyperfactions.chat.faction",
            "com.hyperfactions.hyperfactions.command.faction.chat.faction",
            "com.hyperfactions.hyperfactions.command.f.chat.faction");
        alias("hyperfactions.chat.ally",
            "com.hyperfactions.hyperfactions.command.faction.chat.ally",
            "com.hyperfactions.hyperfactions.command.f.chat.ally");

        // Admin permissions
        alias("hyperfactions.admin",
            "hyperfactions.admin.reload",
            "hyperfactions.admin.bypass",
            "hyperfactions.admin.zones",
            "hyperfactions.admin.forcejoin",
            "hyperfactions.admin.forceleave",
            "hyperfactions.admin.forcedisband",
            "hyperfactions.admin.setpower",
            "hyperfactions.admin.setmaxpower");
        aliasWildcard("hyperfactions.admin.*",
            "hyperfactions.admin.reload",
            "hyperfactions.admin.bypass",
            "hyperfactions.admin.zones",
            "hyperfactions.admin.forcejoin",
            "hyperfactions.admin.forceleave",
            "hyperfactions.admin.forcedisband",
            "hyperfactions.admin.setpower",
            "hyperfactions.admin.setmaxpower");

        // Bypass permissions
        aliasWildcard("hyperfactions.bypass.*",
            "hyperfactions.bypass.warmup",
            "hyperfactions.bypass.cooldown",
            "hyperfactions.bypass.protection",
            "hyperfactions.bypass.build",
            "hyperfactions.bypass.interact",
            "hyperfactions.bypass.container",
            "hyperfactions.bypass.damage",
            "hyperfactions.bypass.use");

        // Wildcard expansion
        aliasWildcard("hyperfactions.*",
            "hyperfactions.use",
            "hyperfactions.create",
            "hyperfactions.invite",
            "hyperfactions.kick",
            "hyperfactions.disband",
            "hyperfactions.promote",
            "hyperfactions.demote",
            "hyperfactions.transfer",
            "hyperfactions.claim",
            "hyperfactions.unclaim",
            "hyperfactions.overclaim",
            "hyperfactions.map",
            "hyperfactions.home",
            "hyperfactions.sethome",
            "hyperfactions.rename",
            "hyperfactions.desc",
            "hyperfactions.color",
            "hyperfactions.open",
            "hyperfactions.close",
            "hyperfactions.ally",
            "hyperfactions.enemy",
            "hyperfactions.neutral",
            "hyperfactions.chat",
            "hyperfactions.chat.faction",
            "hyperfactions.chat.ally",
            "hyperfactions.admin",
            "hyperfactions.admin.reload",
            "hyperfactions.admin.bypass",
            "hyperfactions.admin.zones",
            "hyperfactions.admin.forcejoin",
            "hyperfactions.admin.forceleave",
            "hyperfactions.admin.forcedisband",
            "hyperfactions.admin.setpower",
            "hyperfactions.admin.setmaxpower",
            "hyperfactions.bypass.warmup",
            "hyperfactions.bypass.cooldown",
            "hyperfactions.bypass.protection",
            "hyperfactions.bypass.build",
            "hyperfactions.bypass.interact",
            "hyperfactions.bypass.container",
            "hyperfactions.bypass.damage",
            "hyperfactions.bypass.use",
            "com.hyperfactions.hyperfactions.command.faction",
            "com.hyperfactions.hyperfactions.command.f");

        // ==================== MMOSkillTree Plugin ====================
        // Alternate prefix aliases (ziggfreed.mmoskilltree.* → mmoskilltree.*)
        // MMST's PermissionUtil.checkPermission() checks BOTH player.hasPermission(perm)
        // AND player.hasPermission("ziggfreed." + perm), so we alias both directions.
        alias("ziggfreed.mmoskilltree.admin", "mmoskilltree.admin");
        alias("ziggfreed.mmoskilltree.skill.mining", "mmoskilltree.skill.mining");
        alias("ziggfreed.mmoskilltree.skill.woodcutting", "mmoskilltree.skill.woodcutting");
        alias("ziggfreed.mmoskilltree.skill.excavation", "mmoskilltree.skill.excavation");
        alias("ziggfreed.mmoskilltree.skill.harvesting", "mmoskilltree.skill.harvesting");
        alias("ziggfreed.mmoskilltree.skill.fishing", "mmoskilltree.skill.fishing");
        alias("ziggfreed.mmoskilltree.skill.swords", "mmoskilltree.skill.swords");
        alias("ziggfreed.mmoskilltree.skill.daggers", "mmoskilltree.skill.daggers");
        alias("ziggfreed.mmoskilltree.skill.polearms", "mmoskilltree.skill.polearms");
        alias("ziggfreed.mmoskilltree.skill.staves", "mmoskilltree.skill.staves");
        alias("ziggfreed.mmoskilltree.skill.axes", "mmoskilltree.skill.axes");
        alias("ziggfreed.mmoskilltree.skill.blunt", "mmoskilltree.skill.blunt");
        alias("ziggfreed.mmoskilltree.skill.archery", "mmoskilltree.skill.archery");
        alias("ziggfreed.mmoskilltree.skill.unarmed", "mmoskilltree.skill.unarmed");
        alias("ziggfreed.mmoskilltree.skill.defense", "mmoskilltree.skill.defense");
        alias("ziggfreed.mmoskilltree.skill.taming", "mmoskilltree.skill.taming");
        alias("ziggfreed.mmoskilltree.skill.acrobatics", "mmoskilltree.skill.acrobatics");
        alias("ziggfreed.mmoskilltree.skill.crafting", "mmoskilltree.skill.crafting");
        alias("ziggfreed.mmoskilltree.skill.repair", "mmoskilltree.skill.repair");
        alias("ziggfreed.mmoskilltree.skill.alchemy", "mmoskilltree.skill.alchemy");
        alias("ziggfreed.mmoskilltree.skill.enchanting", "mmoskilltree.skill.enchanting");
        alias("ziggfreed.mmoskilltree.skill.cooking", "mmoskilltree.skill.cooking");
        alias("ziggfreed.mmoskilltree.skill.smithing", "mmoskilltree.skill.smithing");
        alias("ziggfreed.mmoskilltree.skill.building", "mmoskilltree.skill.building");
        alias("ziggfreed.mmoskilltree.command.xp", "mmoskilltree.command.xp");
        alias("ziggfreed.mmoskilltree.command.skilltree", "mmoskilltree.command.skilltree");
        alias("ziggfreed.mmoskilltree.command.xpdisplay", "mmoskilltree.command.xpdisplay");
        alias("ziggfreed.mmoskilltree.command.setxp", "mmoskilltree.command.setxp");
        alias("ziggfreed.mmoskilltree.command.setxp.others", "mmoskilltree.command.setxp.others");
        alias("ziggfreed.mmoskilltree.command.quest", "mmoskilltree.command.quest");
        alias("ziggfreed.mmoskilltree.command.config", "mmoskilltree.command.config");

        // Hytale command path aliases
        alias("com.ziggfreed.mmoskilltree.command.skilltree", "mmoskilltree.command.skilltree");
        alias("com.ziggfreed.mmoskilltree.command.mmoxp", "mmoskilltree.command.xp");
        alias("com.ziggfreed.mmoskilltree.command.xpdisplay", "mmoskilltree.command.xpdisplay");
        alias("com.ziggfreed.mmoskilltree.command.setmmoxp", "mmoskilltree.command.setxp");
        alias("com.ziggfreed.mmoskilltree.command.quest", "mmoskilltree.command.quest");
        alias("com.ziggfreed.mmoskilltree.command.mmoconfig", "mmoskilltree.admin");
        alias("com.ziggfreed.mmoskilltree.command.mmoadmin", "mmoskilltree.admin");
        alias("com.ziggfreed.mmoskilltree.command.mmoboost", "mmoskilltree.admin");

        // MMOSkillTree wildcard expansions
        aliasWildcard("mmoskilltree.*",
            "mmoskilltree.admin",
            "mmoskilltree.command.xp",
            "mmoskilltree.command.skilltree",
            "mmoskilltree.command.xpdisplay",
            "mmoskilltree.command.setxp",
            "mmoskilltree.command.setxp.others",
            "mmoskilltree.command.quest",
            "mmoskilltree.command.config",
            "mmoskilltree.skill.mining",
            "mmoskilltree.skill.woodcutting",
            "mmoskilltree.skill.excavation",
            "mmoskilltree.skill.harvesting",
            "mmoskilltree.skill.fishing",
            "mmoskilltree.skill.swords",
            "mmoskilltree.skill.daggers",
            "mmoskilltree.skill.polearms",
            "mmoskilltree.skill.staves",
            "mmoskilltree.skill.axes",
            "mmoskilltree.skill.blunt",
            "mmoskilltree.skill.archery",
            "mmoskilltree.skill.unarmed",
            "mmoskilltree.skill.defense",
            "mmoskilltree.skill.taming",
            "mmoskilltree.skill.acrobatics",
            "mmoskilltree.skill.crafting",
            "mmoskilltree.skill.repair",
            "mmoskilltree.skill.alchemy",
            "mmoskilltree.skill.enchanting",
            "mmoskilltree.skill.cooking",
            "mmoskilltree.skill.smithing",
            "mmoskilltree.skill.building",
            "mmoskilltree.xpboosts.all.self.1_25.15.120",
            "mmoskilltree.xpboosts.all.self.1_5.30.120",
            "mmoskilltree.xpboosts.all.self.2_0.30.180",
            "mmoskilltree.xpboosts.all.all.1_5.30.300",
            "mmoskilltree.xpboosts.all.all.2_0.60.600");

        aliasWildcard("mmoskilltree.skill.*",
            "mmoskilltree.skill.mining",
            "mmoskilltree.skill.woodcutting",
            "mmoskilltree.skill.excavation",
            "mmoskilltree.skill.harvesting",
            "mmoskilltree.skill.fishing",
            "mmoskilltree.skill.swords",
            "mmoskilltree.skill.daggers",
            "mmoskilltree.skill.polearms",
            "mmoskilltree.skill.staves",
            "mmoskilltree.skill.axes",
            "mmoskilltree.skill.blunt",
            "mmoskilltree.skill.archery",
            "mmoskilltree.skill.unarmed",
            "mmoskilltree.skill.defense",
            "mmoskilltree.skill.taming",
            "mmoskilltree.skill.acrobatics",
            "mmoskilltree.skill.crafting",
            "mmoskilltree.skill.repair",
            "mmoskilltree.skill.alchemy",
            "mmoskilltree.skill.enchanting",
            "mmoskilltree.skill.cooking",
            "mmoskilltree.skill.smithing",
            "mmoskilltree.skill.building");

        aliasWildcard("mmoskilltree.command.*",
            "mmoskilltree.command.xp",
            "mmoskilltree.command.skilltree",
            "mmoskilltree.command.xpdisplay",
            "mmoskilltree.command.setxp",
            "mmoskilltree.command.setxp.others",
            "mmoskilltree.command.quest",
            "mmoskilltree.command.config");

        aliasWildcard("mmoskilltree.xpboosts.*",
            "mmoskilltree.xpboosts.all.self.1_25.15.120",
            "mmoskilltree.xpboosts.all.self.1_5.30.120",
            "mmoskilltree.xpboosts.all.self.2_0.30.180",
            "mmoskilltree.xpboosts.all.all.1_5.30.300",
            "mmoskilltree.xpboosts.all.all.2_0.60.600");

        aliasWildcard("ziggfreed.*",
            "ziggfreed.mmoskilltree.admin",
            "ziggfreed.mmoskilltree.skill.mining",
            "ziggfreed.mmoskilltree.skill.woodcutting",
            "ziggfreed.mmoskilltree.skill.excavation",
            "ziggfreed.mmoskilltree.skill.harvesting",
            "ziggfreed.mmoskilltree.skill.fishing",
            "ziggfreed.mmoskilltree.skill.swords",
            "ziggfreed.mmoskilltree.skill.daggers",
            "ziggfreed.mmoskilltree.skill.polearms",
            "ziggfreed.mmoskilltree.skill.staves",
            "ziggfreed.mmoskilltree.skill.axes",
            "ziggfreed.mmoskilltree.skill.blunt",
            "ziggfreed.mmoskilltree.skill.archery",
            "ziggfreed.mmoskilltree.skill.unarmed",
            "ziggfreed.mmoskilltree.skill.defense",
            "ziggfreed.mmoskilltree.skill.taming",
            "ziggfreed.mmoskilltree.skill.acrobatics",
            "ziggfreed.mmoskilltree.skill.crafting",
            "ziggfreed.mmoskilltree.skill.repair",
            "ziggfreed.mmoskilltree.skill.alchemy",
            "ziggfreed.mmoskilltree.skill.enchanting",
            "ziggfreed.mmoskilltree.skill.cooking",
            "ziggfreed.mmoskilltree.skill.smithing",
            "ziggfreed.mmoskilltree.skill.building",
            "ziggfreed.mmoskilltree.command.xp",
            "ziggfreed.mmoskilltree.command.skilltree",
            "ziggfreed.mmoskilltree.command.xpdisplay",
            "ziggfreed.mmoskilltree.command.setxp",
            "ziggfreed.mmoskilltree.command.setxp.others",
            "ziggfreed.mmoskilltree.command.quest",
            "ziggfreed.mmoskilltree.command.config");

        aliasWildcard("ziggfreed.mmoskilltree.*",
            "mmoskilltree.admin",
            "mmoskilltree.skill.mining",
            "mmoskilltree.skill.woodcutting",
            "mmoskilltree.skill.excavation",
            "mmoskilltree.skill.harvesting",
            "mmoskilltree.skill.fishing",
            "mmoskilltree.skill.swords",
            "mmoskilltree.skill.daggers",
            "mmoskilltree.skill.polearms",
            "mmoskilltree.skill.staves",
            "mmoskilltree.skill.axes",
            "mmoskilltree.skill.blunt",
            "mmoskilltree.skill.archery",
            "mmoskilltree.skill.unarmed",
            "mmoskilltree.skill.defense",
            "mmoskilltree.skill.taming",
            "mmoskilltree.skill.acrobatics",
            "mmoskilltree.skill.crafting",
            "mmoskilltree.skill.repair",
            "mmoskilltree.skill.alchemy",
            "mmoskilltree.skill.enchanting",
            "mmoskilltree.skill.cooking",
            "mmoskilltree.skill.smithing",
            "mmoskilltree.skill.building",
            "mmoskilltree.command.xp",
            "mmoskilltree.command.skilltree",
            "mmoskilltree.command.xpdisplay",
            "mmoskilltree.command.setxp",
            "mmoskilltree.command.setxp.others",
            "mmoskilltree.command.quest",
            "mmoskilltree.command.config",
            "mmoskilltree.xpboosts.all.self.1_25.15.120",
            "mmoskilltree.xpboosts.all.self.1_5.30.120",
            "mmoskilltree.xpboosts.all.self.2_0.30.180",
            "mmoskilltree.xpboosts.all.all.1_5.30.300",
            "mmoskilltree.xpboosts.all.all.2_0.60.600");

        aliasWildcard("ziggfreed.mmoskilltree.skill.*",
            "mmoskilltree.skill.mining",
            "mmoskilltree.skill.woodcutting",
            "mmoskilltree.skill.excavation",
            "mmoskilltree.skill.harvesting",
            "mmoskilltree.skill.fishing",
            "mmoskilltree.skill.swords",
            "mmoskilltree.skill.daggers",
            "mmoskilltree.skill.polearms",
            "mmoskilltree.skill.staves",
            "mmoskilltree.skill.axes",
            "mmoskilltree.skill.blunt",
            "mmoskilltree.skill.archery",
            "mmoskilltree.skill.unarmed",
            "mmoskilltree.skill.defense",
            "mmoskilltree.skill.taming",
            "mmoskilltree.skill.acrobatics",
            "mmoskilltree.skill.crafting",
            "mmoskilltree.skill.repair",
            "mmoskilltree.skill.alchemy",
            "mmoskilltree.skill.enchanting",
            "mmoskilltree.skill.cooking",
            "mmoskilltree.skill.smithing",
            "mmoskilltree.skill.building");

        aliasWildcard("ziggfreed.mmoskilltree.command.*",
            "mmoskilltree.command.xp",
            "mmoskilltree.command.skilltree",
            "mmoskilltree.command.xpdisplay",
            "mmoskilltree.command.setxp",
            "mmoskilltree.command.setxp.others",
            "mmoskilltree.command.quest",
            "mmoskilltree.command.config");

        aliasWildcard("com.ziggfreed.mmoskilltree.command.*",
            "mmoskilltree.command.skilltree",
            "mmoskilltree.command.xp",
            "mmoskilltree.command.xpdisplay",
            "mmoskilltree.command.setxp",
            "mmoskilltree.command.quest",
            "mmoskilltree.admin");
    }

    /**
     * Registers a simple alias mapping.
     */
    private void alias(String simplified, String... actual) {
        String lowerSimplified = simplified.toLowerCase();
        Set<String> actuals = aliasToActual.computeIfAbsent(lowerSimplified, k -> new HashSet<>());
        for (String a : actual) {
            actuals.add(a.toLowerCase());
            actualToAlias.computeIfAbsent(a.toLowerCase(), k -> new HashSet<>()).add(lowerSimplified);
        }
    }

    /**
     * Registers a wildcard alias mapping.
     */
    private void aliasWildcard(String wildcardPattern, String... actual) {
        alias(wildcardPattern, actual);
    }

    /**
     * Gets all actual permissions that a simplified permission maps to.
     *
     * @param permission the permission (may be simplified or actual)
     * @return set of actual Hytale permissions, empty if no aliases exist
     */
    @NotNull
    public Set<String> getActualPermissions(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        Set<String> actuals = aliasToActual.get(lowerPerm);
        return actuals != null ? Collections.unmodifiableSet(actuals) : Collections.emptySet();
    }

    /**
     * Gets all simplified aliases for an actual Hytale permission.
     *
     * @param permission the actual Hytale permission
     * @return set of simplified aliases, empty if no aliases exist
     */
    @NotNull
    public Set<String> getAliases(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        Set<String> aliases = actualToAlias.get(lowerPerm);
        return aliases != null ? Collections.unmodifiableSet(aliases) : Collections.emptySet();
    }

    /**
     * Expands a permission to include all its aliases.
     * This should be called when building the expanded permission set.
     *
     * @param permission the permission to expand
     * @return set containing the original permission and all its aliases
     */
    @NotNull
    public Set<String> expand(@NotNull String permission) {
        Set<String> result = new HashSet<>();
        String lowerPerm = permission.toLowerCase();
        result.add(lowerPerm);

        // Add all actual permissions this alias maps to
        Set<String> actuals = aliasToActual.get(lowerPerm);
        if (actuals != null) {
            result.addAll(actuals);
        }

        return result;
    }

    /**
     * Checks if a permission has any aliases.
     *
     * @param permission the permission to check
     * @return true if aliases exist
     */
    public boolean hasAliases(@NotNull String permission) {
        String lowerPerm = permission.toLowerCase();
        return aliasToActual.containsKey(lowerPerm) || actualToAlias.containsKey(lowerPerm);
    }

    /**
     * Gets all registered alias mappings.
     *
     * @return unmodifiable map of simplified -> actual permissions
     */
    @NotNull
    public Map<String, Set<String>> getAllAliases() {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : aliasToActual.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Clears and reloads all aliases.
     */
    public void reload() {
        aliasToActual.clear();
        actualToAlias.clear();
        registerAllAliases();
    }
}
