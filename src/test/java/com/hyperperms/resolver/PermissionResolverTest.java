package com.hyperperms.resolver;

import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PermissionResolverTest {

    private Map<String, Group> groups;
    private PermissionResolver resolver;

    @BeforeEach
    void setUp() {
        groups = new HashMap<>();
        resolver = new PermissionResolver(groups::get);
    }

    private Group createGroup(String name, int weight) {
        Group group = new Group(name, weight);
        groups.put(name, group);
        return group;
    }

    @Test
    void testDirectPermission() {
        createGroup("default", 0);
        User user = new User(UUID.randomUUID(), "Test");
        user.addNode(Node.of("test.permission"));

        assertTrue(resolver.check(user, "test.permission", ContextSet.empty()).asBoolean());
        assertFalse(resolver.check(user, "other.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testGroupInheritance() {
        Group defaultGroup = createGroup("default", 0);
        defaultGroup.addNode(Node.of("default.permission"));

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");

        assertTrue(resolver.check(user, "default.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testMultipleGroupInheritance() {
        Group defaultGroup = createGroup("default", 0);
        defaultGroup.addNode(Node.of("default.permission"));

        Group vipGroup = createGroup("vip", 10);
        vipGroup.addNode(Node.of("vip.permission"));

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");
        user.addGroup("vip");

        assertTrue(resolver.check(user, "default.permission", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "vip.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testGroupParentInheritance() {
        Group defaultGroup = createGroup("default", 0);
        defaultGroup.addNode(Node.of("default.permission"));

        Group vipGroup = createGroup("vip", 10);
        vipGroup.addParent("default"); // vip inherits from default
        vipGroup.addNode(Node.of("vip.permission"));

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("vip");

        // User should have both default and vip permissions
        assertTrue(resolver.check(user, "default.permission", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "vip.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testWeightPriority() {
        Group lowWeight = createGroup("low", 0);
        lowWeight.addNode(Node.builder("test.permission").denied().build());

        Group highWeight = createGroup("high", 100);
        highWeight.addNode(Node.of("test.permission")); // Grant

        User user = new User(UUID.randomUUID(), "Test");
        user.addGroup("low");
        user.addGroup("high");

        // High weight should win
        assertTrue(resolver.check(user, "test.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testUserOverridesGroup() {
        Group group = createGroup("default", 0);
        group.addNode(Node.builder("test.permission").denied().build());

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");
        user.addNode(Node.of("test.permission")); // User grants it

        // User's direct permission should override group
        assertTrue(resolver.check(user, "test.permission", ContextSet.empty()).asBoolean());
    }

    @Test
    void testWildcardPermission() {
        Group group = createGroup("default", 0);
        group.addNode(Node.of("myplugin.*"));

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");

        assertTrue(resolver.check(user, "myplugin.command", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "myplugin.command.home", ContextSet.empty()).asBoolean());
        assertFalse(resolver.check(user, "otherplugin.command", ContextSet.empty()).asBoolean());
    }

    @Test
    void testContextFiltering() {
        Group group = createGroup("default", 0);
        group.addNode(Node.builder("fly.permission")
                .world("creative")
                .build());

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");

        ContextSet creativeContext = ContextSet.of(Context.world("creative"));
        ContextSet survivalContext = ContextSet.of(Context.world("survival"));

        assertTrue(resolver.check(user, "fly.permission", creativeContext).asBoolean());
        assertFalse(resolver.check(user, "fly.permission", survivalContext).asBoolean());
    }

    @Test
    void testResolvedPermissions() {
        Group group = createGroup("default", 0);
        group.addNode(Node.of("perm1"));
        group.addNode(Node.of("perm2"));
        group.addNode(Node.builder("perm3").denied().build());

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");

        var resolved = resolver.resolve(user, ContextSet.empty());

        assertEquals(3, resolved.size());
        assertTrue(resolved.hasPermission("perm1"));
        assertTrue(resolved.hasPermission("perm2"));
        assertFalse(resolved.hasPermission("perm3"));

        var granted = resolved.getGrantedPermissions();
        assertEquals(2, granted.size());

        var denied = resolved.getDeniedPermissions();
        assertEquals(1, denied.size());
    }

    @Test
    void testCyclicInheritance() {
        // Create a cycle: A -> B -> C -> A
        Group groupA = createGroup("a", 0);
        Group groupB = createGroup("b", 0);
        Group groupC = createGroup("c", 0);

        groupA.addParent("b");
        groupB.addParent("c");
        groupC.addParent("a");

        groupA.addNode(Node.of("perm.a"));
        groupB.addNode(Node.of("perm.b"));
        groupC.addNode(Node.of("perm.c"));

        User user = new User(UUID.randomUUID(), "Test");
        user.addGroup("a");

        // Should not stack overflow, should get all permissions
        assertTrue(resolver.check(user, "perm.a", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "perm.b", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "perm.c", ContextSet.empty()).asBoolean());
    }

    // ==================== P0 Specification Tests ====================

    @Test
    void testUserNegationOverridesGroupGrant() {
        Group vip = createGroup("vip", 100);
        vip.addNode(Node.of("fly.use"));

        User user = new User(UUID.randomUUID(), "Steve");
        user.addGroup("vip");
        user.addNode(Node.builder("fly.use").denied().build()); // -fly.use

        // User's explicit denial should override group's grant
        assertFalse(resolver.check(user, "fly.use", ContextSet.empty()).asBoolean());
    }

    @Test
    void testSpecificNegationWithWildcard() {
        Group member = createGroup("member", 50);
        member.addNode(Node.of("hytale.command.*"));

        User user = new User(UUID.randomUUID(), "Steve");
        user.addGroup("member");
        user.addNode(Node.builder("hytale.command.emote").denied().build());

        // Specific negation blocks emote
        assertFalse(resolver.check(user, "hytale.command.emote", ContextSet.empty()).asBoolean());
        // Wildcard still allows other commands
        assertTrue(resolver.check(user, "hytale.command.spawn", ContextSet.empty()).asBoolean());
        // Nested commands also allowed by .*
        assertTrue(resolver.check(user, "hytale.command.home.set", ContextSet.empty()).asBoolean());
    }

    @Test
    void testContextSpecificOverridesGlobal() {
        Group group = createGroup("default", 0);
        // Server-specific grant (contextual permissions override when context matches)
        group.addNode(Node.builder("fly.use").server("lobby").build());

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");

        ContextSet lobbyContext = ContextSet.of(Context.server("lobby"));
        ContextSet survivalContext = ContextSet.of(Context.server("survival"));

        assertTrue(resolver.check(user, "fly.use", lobbyContext).asBoolean());
        assertFalse(resolver.check(user, "fly.use", survivalContext).asBoolean()); // No permission in survival
    }

    @Test
    void testExpiredPermissionTreatedAsUndefined() {
        Group group = createGroup("default", 0);
        group.addNode(Node.of("chat.use")); // Group has chat

        User user = new User(UUID.randomUUID(), "Test");
        user.setPrimaryGroup("default");
        // Add expired fly permission
        user.addNode(Node.builder("fly.use")
                .expiry(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build());

        // Expired permission should be undefined, fall through to group
        assertFalse(resolver.check(user, "fly.use", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "chat.use", ContextSet.empty()).asBoolean());
    }

    @Test
    void testDefaultGroupFallback() {
        Group defaultGroup = createGroup("default", 0);
        defaultGroup.addNode(Node.of("chat.use"));
        defaultGroup.addNode(Node.of("help.use"));

        User user = new User(UUID.randomUUID(), "NewPlayer");
        user.setPrimaryGroup("default");
        // New player has no direct permissions

        // Should inherit from default group
        assertTrue(resolver.check(user, "chat.use", ContextSet.empty()).asBoolean());
        assertTrue(resolver.check(user, "help.use", ContextSet.empty()).asBoolean());
        assertFalse(resolver.check(user, "fly.use", ContextSet.empty()).asBoolean());
    }

    @Test
    void testWeightPriorityWithConflict() {
        Group builder = createGroup("builder", 90);
        builder.addNode(Node.builder("worldedit.use").denied().build()); // Deny

        Group moderator = createGroup("moderator", 80);
        moderator.addNode(Node.of("worldedit.use")); // Grant

        User user = new User(UUID.randomUUID(), "Steve");
        user.addGroup("builder");
        user.addGroup("moderator");

        // Builder (weight 90) should win over Moderator (weight 80)
        assertFalse(resolver.check(user, "worldedit.use", ContextSet.empty()).asBoolean());
    }

    @Test
    void testDenyAllThenSpecificGrant() {
        // The standard LuckPerms-style model: deny everything (-*) then grant specific nodes.
        // The explicit grant must win because it is more specific than the global deny. This is
        // the resolution foundation behind the HyperPermsPermissionSet "-*"/"*" probe fix: the
        // resolver already returns TRUE here, so the native permission check must agree.
        createGroup("default", 0);
        User user = new User(UUID.randomUUID(), "Test");
        user.addNode(Node.builder("*").denied().build()); // -*  (deny all)
        user.addNode(Node.of("essentials.home"));          // explicit grant

        assertTrue(resolver.check(user, "essentials.home", ContextSet.empty()).asBoolean(),
                "explicit grant must beat global deny (-*)");
        assertFalse(resolver.check(user, "essentials.kit", ContextSet.empty()).asBoolean(),
                "an ungranted node remains denied under -*");
    }

    @Test
    void testGrantAllThenSpecificDeny() {
        // Grant everything (*), then deny a specific node. The explicit negation must win.
        createGroup("default", 0);
        User user = new User(UUID.randomUUID(), "Test");
        user.addNode(Node.of("*"));                                     // grant all
        user.addNode(Node.builder("essentials.home").denied().build()); // -essentials.home

        assertFalse(resolver.check(user, "essentials.home", ContextSet.empty()).asBoolean(),
                "explicit negation must beat global grant (*)");
        assertTrue(resolver.check(user, "essentials.kit", ContextSet.empty()).asBoolean(),
                "other nodes remain granted under *");
    }
}
