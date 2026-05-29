package com.hyperperms.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the deny-by-default contract fix in {@link HyperPermsPermissionSet}.
 * <p>
 * Hytale's {@code PermissionsModule.hasPermission(Set, id)} probes a permission set with the
 * coarse global wildcards {@code "-*"} and {@code "*"} as short-circuits, before/around the
 * per-node probes. HyperPerms must suppress ONLY those two probes (so its own
 * most-specific-first resolution decides each node), while still delegating per-node negations
 * and sub-wildcards. If the suppression is removed, a deny-all-then-grant group ({@code -*} plus
 * specific grants) silently denies every granted permission on native checks; if it is made too
 * broad, per-node negations and wildcards stop working.
 */
class HyperPermsPermissionSetTest {

    @Test
    void suppressesOnlyTheTwoGlobalProbes() {
        assertTrue(HyperPermsPermissionSet.isCoarseGlobalProbe("*"),
                "'*' (global grant probe) must be suppressed");
        assertTrue(HyperPermsPermissionSet.isCoarseGlobalProbe("-*"),
                "'-*' (global deny probe) must be suppressed");
    }

    @Test
    void doesNotSuppressPerNodeProbes() {
        // Exact nodes and their negations must be delegated, not suppressed.
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("essentials.home"));
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("-essentials.home"));
        // Sub-wildcards must be delegated too (suppressing them would break wildcard grants/denies).
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("essentials.*"));
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("-essentials.*"));
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("hytale.command.gamemode.*"));
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("-hytale.command.gamemode.*"));
        // A node that merely contains a star but is not the global probe.
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe("a.*.b"));
        assertFalse(HyperPermsPermissionSet.isCoarseGlobalProbe(""));
    }
}
