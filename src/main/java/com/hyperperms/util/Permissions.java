package com.hyperperms.util;

/**
 * Constants for HyperPerms' own permission nodes.
 * <p>
 * These permissions control who can use HyperPerms commands and features.
 * <p>
 * Permission Structure:
 * <ul>
 *   <li>{@code hyperperms.*} - Full admin access</li>
 *   <li>{@code hyperperms.command.*} - All command access</li>
 *   <li>{@code hyperperms.command.<category>.*} - Category wildcard</li>
 *   <li>{@code hyperperms.command.<category>.<action>} - Specific action</li>
 *   <li>{@code hyperperms.chat.*} - All chat permissions</li>
 * </ul>
 *
 * @see <a href="PERMISSION_REFERENCE.md">Permission Reference Documentation</a>
 */
public final class Permissions {

    private Permissions() {}

    // ==================== Base Permissions ====================

    /**
     * Base permission for all HyperPerms commands.
     */
    public static final String BASE = "hyperperms.command";

    /**
     * Permission to use any HyperPerms command (admin wildcard).
     */
    public static final String ADMIN = "hyperperms.*";

    /**
     * Permission for all HyperPerms commands.
     */
    public static final String COMMAND_ALL = "hyperperms.command.*";

    /**
     * Permission for all admin commands.
     */
    public static final String ADMIN_ALL = "hyperperms.admin.*";

    // ==================== User Commands ====================

    /**
     * Permission to view user information.
     */
    public static final String USER_INFO = "hyperperms.command.user.info";

    /**
     * Permission to modify user permissions.
     */
    public static final String USER_PERMISSION = "hyperperms.command.user.permission";

    /**
     * Permission to modify user groups.
     */
    public static final String USER_GROUP = "hyperperms.command.user.group";

    /**
     * Permission to promote users.
     */
    public static final String USER_PROMOTE = "hyperperms.command.user.promote";

    /**
     * Permission to demote users.
     */
    public static final String USER_DEMOTE = "hyperperms.command.user.demote";

    /**
     * Permission to clear user permissions.
     */
    public static final String USER_CLEAR = "hyperperms.command.user.clear";

    /**
     * Permission to clone user permissions.
     */
    public static final String USER_CLONE = "hyperperms.command.user.clone";

    // ==================== Group Commands ====================

    /**
     * Permission to view group information.
     */
    public static final String GROUP_INFO = "hyperperms.command.group.info";

    /**
     * Permission to create groups.
     */
    public static final String GROUP_CREATE = "hyperperms.command.group.create";

    /**
     * Permission to delete groups.
     */
    public static final String GROUP_DELETE = "hyperperms.command.group.delete";

    /**
     * Permission to modify group permissions.
     */
    public static final String GROUP_PERMISSION = "hyperperms.command.group.permission";

    /**
     * Permission to modify group parents.
     */
    public static final String GROUP_PARENT = "hyperperms.command.group.parent";

    /**
     * Permission to modify group settings.
     */
    public static final String GROUP_MODIFY = "hyperperms.command.group.modify";

    // ==================== Track Commands ====================

    /**
     * Permission to view track information.
     */
    public static final String TRACK_INFO = "hyperperms.command.track.info";

    /**
     * Permission to create tracks.
     */
    public static final String TRACK_CREATE = "hyperperms.command.track.create";

    /**
     * Permission to delete tracks.
     */
    public static final String TRACK_DELETE = "hyperperms.command.track.delete";

    /**
     * Permission to modify tracks.
     */
    public static final String TRACK_MODIFY = "hyperperms.command.track.modify";

    // ==================== Admin Commands ====================

    /**
     * Permission to reload the plugin.
     */
    public static final String RELOAD = "hyperperms.command.reload";

    /**
     * Permission to use verbose mode.
     */
    public static final String VERBOSE = "hyperperms.command.verbose";

    /**
     * Permission to clear the cache.
     */
    public static final String CACHE = "hyperperms.command.cache";

    /**
     * Permission to export data.
     */
    public static final String EXPORT = "hyperperms.command.export";

    /**
     * Permission to import data.
     */
    public static final String IMPORT = "hyperperms.command.import";

    /**
     * Permission to list all groups.
     */
    public static final String LIST_GROUPS = "hyperperms.command.listgroups";

    /**
     * Permission to list all tracks.
     */
    public static final String LIST_TRACKS = "hyperperms.command.listtracks";


    // ==================== Web Editor Commands ====================

    /**
     * Permission to use the web editor.
     */
    public static final String EDITOR = "hyperperms.command.editor";

    /**
     * Permission to apply changes from the web editor.
     */
    public static final String APPLY = "hyperperms.command.apply";

    // ==================== Chat Permissions ====================

    /**
     * Base permission for all chat features.
     */
    public static final String CHAT_BASE = "hyperperms.chat";

    /**
     * Permission for all chat features.
     */
    public static final String CHAT_ALL = "hyperperms.chat.*";

    /**
     * Permission to use color codes in chat.
     */
    public static final String CHAT_COLOR = "hyperperms.chat.color";

    /**
     * Permission to use hex colors in chat (#RRGGBB).
     */
    public static final String CHAT_COLOR_HEX = "hyperperms.chat.color.hex";

    /**
     * Permission to use formatting codes in chat.
     */
    public static final String CHAT_FORMAT = "hyperperms.chat.format";

    /**
     * Permission to use bold text in chat (&amp;l).
     */
    public static final String CHAT_FORMAT_BOLD = "hyperperms.chat.format.bold";

    /**
     * Permission to use italic text in chat (&amp;o).
     */
    public static final String CHAT_FORMAT_ITALIC = "hyperperms.chat.format.italic";

    /**
     * Permission to use underlined text in chat (&amp;n).
     */
    public static final String CHAT_FORMAT_UNDERLINE = "hyperperms.chat.format.underline";

    /**
     * Permission to use strikethrough text in chat (&amp;m).
     */
    public static final String CHAT_FORMAT_STRIKETHROUGH = "hyperperms.chat.format.strikethrough";

    /**
     * Permission to use obfuscated text in chat (&amp;k).
     */
    public static final String CHAT_FORMAT_MAGIC = "hyperperms.chat.format.magic";

    /**
     * Permission to post clickable links in chat.
     */
    public static final String CHAT_LINKS = "hyperperms.chat.links";

    /**
     * Permission to bypass chat cooldown.
     */
    public static final String CHAT_BYPASS_COOLDOWN = "hyperperms.chat.bypass.cooldown";

    /**
     * Permission to bypass chat filter.
     */
    public static final String CHAT_BYPASS_FILTER = "hyperperms.chat.bypass.filter";

    /**
     * Permission to send server broadcasts.
     */
    public static final String CHAT_BROADCAST = "hyperperms.chat.broadcast";

    /**
     * Permission to send private messages.
     */
    public static final String CHAT_PM = "hyperperms.chat.pm";

    /**
     * Permission to receive private messages.
     */
    public static final String CHAT_PM_RECEIVE = "hyperperms.chat.pm.receive";

    /**
     * Permission to see other players' private messages.
     */
    public static final String CHAT_SOCIALSPY = "hyperperms.chat.socialspy";

    // ==================== Backup Permissions ====================

    /**
     * Base permission for backup commands.
     */
    public static final String BACKUP = "hyperperms.command.backup";

    /**
     * Permission for all backup operations.
     */
    public static final String BACKUP_ALL = "hyperperms.command.backup.*";

    /**
     * Permission to create backups.
     */
    public static final String BACKUP_CREATE = "hyperperms.command.backup.create";

    /**
     * Permission to list backups.
     */
    public static final String BACKUP_LIST = "hyperperms.command.backup.list";

    /**
     * Permission to restore backups.
     */
    public static final String BACKUP_RESTORE = "hyperperms.command.backup.restore";

    /**
     * Permission to delete backups.
     */
    public static final String BACKUP_DELETE = "hyperperms.command.backup.delete";

    // ==================== Debug Permissions ====================

    /**
     * Permission for debug commands.
     */
    public static final String DEBUG = "hyperperms.command.debug";

    /**
     * Permission for all debug operations.
     */
    public static final String DEBUG_ALL = "hyperperms.command.debug.*";

    /**
     * Permission to view inheritance tree.
     */
    public static final String DEBUG_TREE = "hyperperms.command.debug.tree";

    /**
     * Permission to debug permission resolution.
     */
    public static final String DEBUG_RESOLVE = "hyperperms.command.debug.resolve";

    /**
     * Permission to view player contexts.
     */
    public static final String DEBUG_CONTEXTS = "hyperperms.command.debug.contexts";

    // ==================== Permission Registry Commands ====================

    /**
     * Permission for permission registry commands.
     */
    public static final String PERMS = "hyperperms.command.perms";

    /**
     * Permission for all permission registry operations.
     */
    public static final String PERMS_ALL = "hyperperms.command.perms.*";

    /**
     * Permission to list registered permissions.
     */
    public static final String PERMS_LIST = "hyperperms.command.perms.list";

    /**
     * Permission to search registered permissions.
     */
    public static final String PERMS_SEARCH = "hyperperms.command.perms.search";

    // ==================== Check Permissions ====================

    /**
     * Permission to check permissions.
     */
    public static final String CHECK = "hyperperms.command.check";

    /**
     * Permission to check own permissions.
     */
    public static final String CHECK_SELF = "hyperperms.command.check.self";

    /**
     * Permission to check other players' permissions.
     */
    public static final String CHECK_OTHERS = "hyperperms.command.check.others";

    // ==================== Extended User Permissions ====================

    /**
     * Permission for all user commands.
     */
    public static final String USER_ALL = "hyperperms.command.user.*";

    /**
     * Permission to set user prefix.
     */
    public static final String USER_SETPREFIX = "hyperperms.command.user.setprefix";

    /**
     * Permission to set user suffix.
     */
    public static final String USER_SETSUFFIX = "hyperperms.command.user.setsuffix";

    /**
     * Permission to view info of other users.
     */
    public static final String USER_INFO_OTHERS = "hyperperms.command.user.info.others";

    // ==================== Extended Group Permissions ====================

    /**
     * Permission for all group commands.
     */
    public static final String GROUP_ALL = "hyperperms.command.group.*";

    /**
     * Permission to list groups.
     */
    public static final String GROUP_LIST = "hyperperms.command.group.list";

    /**
     * Permission to rename groups.
     */
    public static final String GROUP_RENAME = "hyperperms.command.group.rename";

    /**
     * Permission to set group weight.
     */
    public static final String GROUP_SETWEIGHT = "hyperperms.command.group.setweight";

    /**
     * Permission to set group prefix.
     */
    public static final String GROUP_SETPREFIX = "hyperperms.command.group.setprefix";

    /**
     * Permission to set group suffix.
     */
    public static final String GROUP_SETSUFFIX = "hyperperms.command.group.setsuffix";

    /**
     * Permission to set group display name.
     */
    public static final String GROUP_SETDISPLAYNAME = "hyperperms.command.group.setdisplayname";

    // ==================== Extended Track Permissions ====================

    /**
     * Permission for all track commands.
     */
    public static final String TRACK_ALL = "hyperperms.command.track.*";

    /**
     * Permission to list tracks.
     */
    public static final String TRACK_LIST = "hyperperms.command.track.list";

    /**
     * Permission to append group to track.
     */
    public static final String TRACK_APPEND = "hyperperms.command.track.append";

    /**
     * Permission to insert group in track.
     */
    public static final String TRACK_INSERT = "hyperperms.command.track.insert";

    /**
     * Permission to remove group from track.
     */
    public static final String TRACK_REMOVE = "hyperperms.command.track.remove";

    // ==================== Update Permissions ====================

    /**
     * Permission for update commands.
     */
    public static final String UPDATE = "hyperperms.command.update";

    /**
     * Permission for all update operations.
     */
    public static final String UPDATES_ALL = "hyperperms.updates.*";

    /**
     * Permission to toggle update notifications.
     */
    public static final String UPDATES_TOGGLE = "hyperperms.updates.toggle";

    /**
     * Permission to receive update notifications on join.
     */
    public static final String UPDATES_NOTIFY = "hyperperms.updates.notify";

    // ==================== Utility Methods ====================

    /**
     * Checks if a permission string is a HyperPerms internal permission.
     *
     * @param permission the permission to check
     * @return true if the permission starts with "hyperperms."
     */
    public static boolean isInternalPermission(String permission) {
        return permission != null && permission.startsWith("hyperperms.");
    }

    /**
     * Checks if a permission string is a HyperPerms command permission.
     *
     * @param permission the permission to check
     * @return true if the permission starts with "hyperperms.command."
     */
    public static boolean isCommandPermission(String permission) {
        return permission != null && permission.startsWith("hyperperms.command.");
    }

    /**
     * Checks if a permission string is a HyperPerms chat permission.
     *
     * @param permission the permission to check
     * @return true if the permission starts with "hyperperms.chat."
     */
    public static boolean isChatPermission(String permission) {
        return permission != null && permission.startsWith("hyperperms.chat.");
    }
}
