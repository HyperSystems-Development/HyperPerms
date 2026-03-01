package com.hyperperms.migration.permissionsplus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsPlusMigratorTest {

    private List<String> warnings;

    @BeforeEach
    void setUp() {
        warnings = new ArrayList<>();
    }

    // ==================== cleanPermission tests ====================

    @Test
    void cleanPermission_normalPermission_returnsUnchanged() {
        String result = PermissionsPlusMigrator.cleanPermission("simpleclaims.claim", warnings, "test");
        assertEquals("simpleclaims.claim", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void cleanPermission_stripsDescriptionSuffix() {
        String result = PermissionsPlusMigrator.cleanPermission("hychat.reload: Use /hychat reload command", warnings, "test");
        assertEquals("hychat.reload", result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Stripped description"));
    }

    @Test
    void cleanPermission_skipsCommandStylePermission() {
        String result = PermissionsPlusMigrator.cleanPermission("/permsplus", warnings, "test");
        assertNull(result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("command-style"));
    }

    @Test
    void cleanPermission_emptyAfterClean_returnsNull() {
        String result = PermissionsPlusMigrator.cleanPermission("", warnings, "test");
        assertNull(result);
    }

    @Test
    void cleanPermission_wildcardStar_returnsUnchanged() {
        String result = PermissionsPlusMigrator.cleanPermission("*", warnings, "test");
        assertEquals("*", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void cleanPermission_dotWildcard_returnsUnchanged() {
        String result = PermissionsPlusMigrator.cleanPermission("snipr.afkplugin.*", warnings, "test");
        assertEquals("snipr.afkplugin.*", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void cleanPermission_descriptionWithColon_stripsCorrectly() {
        String result = PermissionsPlusMigrator.cleanPermission("hychat.bypass.filter: Bypass word filter", warnings, "test");
        assertEquals("hychat.bypass.filter", result);
        assertEquals(1, warnings.size());
    }

    // ==================== validatePermission tests ====================

    @Test
    void validatePermission_validPermission_returnsUnchanged() {
        String result = PermissionsPlusMigrator.validatePermission("simpleclaims.claim", warnings, "test");
        assertEquals("simpleclaims.claim", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void validatePermission_wildcardStar_returnsUnchanged() {
        String result = PermissionsPlusMigrator.validatePermission("*", warnings, "test");
        assertEquals("*", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void validatePermission_dotWildcard_returnsUnchanged() {
        String result = PermissionsPlusMigrator.validatePermission("snipr.afkplugin.*", warnings, "test");
        assertEquals("snipr.afkplugin.*", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void validatePermission_nonAscii_returnsNull() {
        String result = PermissionsPlusMigrator.validatePermission("perms.t\u00ebst", warnings, "test");
        assertNull(result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("non-ASCII"));
    }

    @Test
    void validatePermission_invalidChars_returnsNull() {
        String result = PermissionsPlusMigrator.validatePermission("perms with spaces", warnings, "test");
        assertNull(result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("invalid characters"));
    }

    @Test
    void validatePermission_mixedCase_returnsUnchanged() {
        String result = PermissionsPlusMigrator.validatePermission("HytaleBetterAdmin.mute.use", warnings, "test");
        assertEquals("HytaleBetterAdmin.mute.use", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void validatePermission_tooLong_truncates() {
        String longPerm = "a".repeat(300);
        String result = PermissionsPlusMigrator.validatePermission(longPerm, warnings, "test");
        assertNotNull(result);
        assertEquals(256, result.length());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("exceeds max length"));
    }
}
