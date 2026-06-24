package com.hyperperms.integration;

import com.hyperperms.util.ReflectionUtil;

/**
 * Compatibility shim for <b>QuestLines Claims</b>.
 * <p>
 * QuestLines computes a player's claim/rent limits by <b>summing</b> the numeric suffix of
 * permission nodes ({@code questlinesclaims.claim.chunks.<n>}, {@code questlinesclaims.rent.limit.<n>})
 * across <i>every</i> registered {@code PermissionProvider} AND across <i>both</i> the user's
 * permissions and each of the user's groups' permissions (see {@code ClaimsManager.permissionNodeBonus}).
 * That assumes a provider where user-direct and group permissions are disjoint.
 * <p>
 * HyperPerms intentionally exposes a user's <i>fully resolved</i> permission set through several of
 * those surfaces at once — {@code getUserPermissions(uuid)}, the virtual {@code user:<uuid>} group's
 * {@code getGroupPermissions(...)}, and the resolved-permission sync into Hytale's native provider —
 * so QuestLines counts each node two or three times. (QuestLines special-cases LuckPerms via a
 * dedicated bridge to avoid exactly this; HyperPerms hits its generic path.)
 * <p>
 * When QuestLines is present, HyperPerms collapses those to a <b>single</b> summable source —
 * {@code getUserPermissions(uuid)} — by (1) returning no permissions from the virtual user group and
 * (2) excluding {@code questlinesclaims.*} nodes from the native-provider sync. Boolean checks for
 * those nodes still resolve correctly because HyperPerms is the first provider, so QuestLines'
 * {@code hasPermission} calls are unaffected. Everything is gated on QuestLines being installed, so
 * servers without it see no behavioural change.
 */
public final class QuestLinesCompat {

    private static final String DETECT_CLASS = "net.evilcraft.questlinesclaims.QuestLinesClaimsPlugin";

    /** Common prefix of every QuestLines Claims permission node. */
    public static final String NODE_PREFIX = "questlinesclaims.";

    /** Cached detection result. Null = not yet checked. */
    private static volatile Boolean installed;

    private QuestLinesCompat() {}

    /**
     * @return {@code true} if QuestLines Claims is installed. The check is performed once, lazily, on
     *         the first call (which happens at runtime — player connect / a QuestLines query — by which
     *         point all plugins have loaded regardless of load order) and then cached.
     */
    public static boolean isInstalled() {
        Boolean cached = installed;
        if (cached == null) {
            cached = ReflectionUtil.isClassAvailable(DETECT_CLASS);
            installed = cached;
        }
        return cached;
    }

    /**
     * @param node a permission node (may be {@code null})
     * @return {@code true} if the node belongs to QuestLines Claims — i.e. one HyperPerms must expose
     *         through exactly one summable surface so QuestLines does not multiply-count it.
     */
    public static boolean isQuestLinesNode(String node) {
        return node != null && node.startsWith(NODE_PREFIX);
    }
}
