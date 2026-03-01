package com.hyperperms.migration.permissionsplus;

import com.hyperperms.migration.permissionsplus.PermissionsPlusData.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsPlusDataTest {

    @Test
    void ppGroup_getPermissionCount() {
        PPGroup group = new PPGroup("Admin", List.of("perm.one", "perm.two", "perm.three"));
        assertEquals(3, group.getPermissionCount());
    }

    @Test
    void ppGroup_emptyPermissions() {
        PPGroup group = new PPGroup("Default", List.of());
        assertEquals(0, group.getPermissionCount());
    }

    @Test
    void ppUser_hasCustomPermissions_true() {
        PPUser user = new PPUser(
            UUID.randomUUID(), "TestPlayer",
            List.of("Admin"), List.of("custom.perm")
        );
        assertTrue(user.hasCustomPermissions());
    }

    @Test
    void ppUser_hasCustomPermissions_false() {
        PPUser user = new PPUser(
            UUID.randomUUID(), "TestPlayer",
            List.of("Admin"), List.of()
        );
        assertFalse(user.hasCustomPermissions());
    }

    @Test
    void ppUser_nullUsername() {
        PPUser user = new PPUser(
            UUID.randomUUID(), null,
            List.of("Default"), List.of()
        );
        assertNull(user.username());
    }

    @Test
    void ppConfig_defaultGroup() {
        PPConfig config = new PPConfig("Norddeich", Map.of());
        assertEquals("Norddeich", config.defaultGroup());
    }

    @Test
    void ppDataSet_empty() {
        PPDataSet dataSet = PPDataSet.empty();
        assertTrue(dataSet.groups().isEmpty());
        assertTrue(dataSet.users().isEmpty());
        assertEquals("default", dataSet.config().defaultGroup());
    }
}
