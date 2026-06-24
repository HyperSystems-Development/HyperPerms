package com.hyperperms.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestLinesCompatTest {

    @Test
    void detectsQuestLinesCountingNodes() {
        assertTrue(QuestLinesCompat.isQuestLinesNode("questlinesclaims.claim.chunks.10"));
        assertTrue(QuestLinesCompat.isQuestLinesNode("questlinesclaims.rent.limit.5"));
        assertTrue(QuestLinesCompat.isQuestLinesNode("questlinesclaims.claim.use"));
    }

    @Test
    void ignoresNonQuestLinesNodes() {
        assertFalse(QuestLinesCompat.isQuestLinesNode("hyperperms.admin"));
        assertFalse(QuestLinesCompat.isQuestLinesNode("essentials.home"));
        // No trailing dot — must not match a same-named-prefix vanity node.
        assertFalse(QuestLinesCompat.isQuestLinesNode("questlinesclaims"));
        assertFalse(QuestLinesCompat.isQuestLinesNode(null));
    }

    @Test
    void notInstalledWhenClassAbsent() {
        // QuestLines Claims is not on the test classpath, so detection must report false
        // (and thus the compat behaviour stays off — no functional change by default).
        assertFalse(QuestLinesCompat.isInstalled());
    }
}
