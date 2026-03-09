package com.hyperperms.resolver;

import com.hyperperms.resolver.WildcardMatcher.MatchResult;
import com.hyperperms.resolver.WildcardMatcher.MatchType;
import com.hyperperms.resolver.WildcardMatcher.TriState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WildcardMatcherTest {

    // ==================== matches() Tests ====================

    @Test
    void testExactMatch() {
        assertTrue(WildcardMatcher.matches("plugin.command.home", "plugin.command.home"));
        assertFalse(WildcardMatcher.matches("plugin.command.home", "plugin.command.spawn"));
    }

    @Test
    void testUniversalWildcard() {
        assertTrue(WildcardMatcher.matches("anything.at.all", "*"));
        assertTrue(WildcardMatcher.matches("single", "*"));
    }

    @Test
    void testPrefixWildcard() {
        assertTrue(WildcardMatcher.matches("plugin.command.home", "plugin.*"));
        assertTrue(WildcardMatcher.matches("plugin.command.home", "plugin.command.*"));
        assertFalse(WildcardMatcher.matches("other.command.home", "plugin.*"));
    }

    // ==================== Most-Specific-First Resolution Tests ====================

    @Test
    void testSpecificGrantOverridesGlobalDeny() {
        // The core bug: [- *, + simpleclaims.edit-party] should grant simpleclaims.edit-party
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-*", true);
        perms.put("simpleclaims.edit-party", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("simpleclaims.edit-party", perms),
                "Specific grant must override global deny");
        assertEquals(TriState.FALSE, WildcardMatcher.check("anything.else", perms),
                "Non-granted permissions should still be denied by -*");
    }

    @Test
    void testSpecificGrantOverridesGlobalDenyNormalized() {
        // Tests applyNode's normalized form: "*" -> false means deny-all
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", false);
        perms.put("simpleclaims.edit-party", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("simpleclaims.edit-party", perms),
                "Specific grant must override normalized global deny");
        assertEquals(TriState.FALSE, WildcardMatcher.check("anything.else", perms),
                "Non-granted permissions should still be denied by * -> false");
    }

    @Test
    void testSpecificDenyOverridesGlobalGrant() {
        // [*, -plugin.admin] should deny plugin.admin but grant others
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);
        perms.put("-plugin.admin", true);

        assertEquals(TriState.FALSE, WildcardMatcher.check("plugin.admin", perms),
                "Specific deny must override global grant");
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.command", perms),
                "Other permissions should still be granted by *");
    }

    @Test
    void testExactNegationOverridesPrefixWildcard() {
        // essentials.* + -essentials.god → essentials.god = FALSE, essentials.home = TRUE
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("essentials.*", true);
        perms.put("-essentials.god", true);

        assertEquals(TriState.FALSE, WildcardMatcher.check("essentials.god", perms),
                "Exact negation must override prefix wildcard");
        assertEquals(TriState.TRUE, WildcardMatcher.check("essentials.home", perms));
    }

    @Test
    void testLongerPrefixOverridesShorterPrefix() {
        // plugin.* + -plugin.admin.* → plugin.admin.bypass = FALSE, plugin.command = TRUE
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.*", true);
        perms.put("-plugin.admin.*", true);

        assertEquals(TriState.FALSE, WildcardMatcher.check("plugin.admin.bypass", perms),
                "Longer prefix -plugin.admin.* must override shorter plugin.*");
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.command", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.admin", perms),
                "plugin.admin itself is not under plugin.admin.* so plugin.* grants it");
    }

    @Test
    void testGlobalDenyAloneWorks() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-*", true);

        assertEquals(TriState.FALSE, WildcardMatcher.check("anything", perms));
        assertEquals(TriState.FALSE, WildcardMatcher.check("plugin.command", perms));
    }

    @Test
    void testGlobalDenyNormalizedFormWorks() {
        // "*" -> false is the normalized form of deny-all (from applyNode)
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", false);

        assertEquals(TriState.FALSE, WildcardMatcher.check("anything", perms));
        assertEquals(TriState.FALSE, WildcardMatcher.check("plugin.command", perms));
    }

    @Test
    void testGlobalGrantAloneWorks() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("anything", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.command", perms));
    }

    @Test
    void testGlobalGrantAndGlobalDeny() {
        // When both * grant and -* deny exist, grant wins (checked first at global level)
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);
        perms.put("-*", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("anything", perms),
                "Global grant should win over global deny");
    }

    @Test
    void testExactGrantBeforeExactDeny() {
        // When both exact grant and exact deny exist, grant is checked first
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.admin", true);
        perms.put("-plugin.admin", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.admin", perms),
                "Exact grant should be checked before exact deny");
    }

    @Test
    void testCaseInsensitivity() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.command", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("PLUGIN.COMMAND", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("Plugin.Command", perms));
    }

    @Test
    void testUndefinedPermission() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("other.permission", true);

        assertEquals(TriState.UNDEFINED, WildcardMatcher.check("plugin.command", perms));
    }

    @Test
    void testStrippedPrefixMatching() {
        // Permission com.plugin.cmd should match stripped version plugin.cmd
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.cmd", true);

        assertEquals(TriState.TRUE, WildcardMatcher.check("com.plugin.cmd", perms));
    }

    @Test
    void testTriState() {
        assertTrue(TriState.TRUE.asBoolean());
        assertFalse(TriState.FALSE.asBoolean());
        assertFalse(TriState.UNDEFINED.asBoolean());

        assertTrue(TriState.UNDEFINED.asBoolean(true));
        assertFalse(TriState.UNDEFINED.asBoolean(false));
    }

    @Test
    void testGeneratePatterns() {
        String[] patterns = WildcardMatcher.generatePatterns("plugin.command.home");
        assertEquals(4, patterns.length);
        assertEquals("plugin.command.home", patterns[0]);
        assertEquals("plugin.command.*", patterns[1]);
        assertEquals("plugin.*", patterns[2]);
        assertEquals("*", patterns[3]);
    }

    // ==================== Trace Tests ====================

    @Test
    void testCheckWithTraceSpecificOverridesGlobalDeny() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-*", true);
        perms.put("simpleclaims.edit-party", true);

        MatchResult result = WildcardMatcher.checkWithTrace("simpleclaims.edit-party", perms);
        assertEquals(TriState.TRUE, result.result());
        assertEquals("simpleclaims.edit-party", result.matchedNode());
        assertEquals(MatchType.EXACT, result.matchType());
        assertTrue(result.isMatched());
    }

    @Test
    void testCheckWithTraceGlobalDenyFallthrough() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-*", true);
        perms.put("simpleclaims.edit-party", true);

        MatchResult result = WildcardMatcher.checkWithTrace("anything.else", perms);
        assertEquals(TriState.FALSE, result.result());
        assertEquals("-*", result.matchedNode());
        assertEquals(MatchType.UNIVERSAL_NEGATION, result.matchType());
        assertTrue(result.isNegation());
    }

    // ==================== Additional Trace Tests ====================

    @Test
    void testCheckWithTraceExactMatch() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.command", true);

        MatchResult result = WildcardMatcher.checkWithTrace("plugin.command", perms);
        assertEquals(TriState.TRUE, result.result());
        assertEquals("plugin.command", result.matchedNode());
        assertEquals(MatchType.EXACT, result.matchType());
        assertTrue(result.isMatched());
        assertFalse(result.isWildcard());
        assertFalse(result.isNegation());
    }

    @Test
    void testCheckWithTraceWildcard() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.*", true);

        MatchResult result = WildcardMatcher.checkWithTrace("plugin.command.home", perms);
        assertEquals(TriState.TRUE, result.result());
        assertEquals("plugin.*", result.matchedNode());
        assertEquals(MatchType.WILDCARD, result.matchType());
        assertTrue(result.isWildcard());
        assertFalse(result.isNegation());
    }

    @Test
    void testCheckWithTraceNegation() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-plugin.admin", true);

        MatchResult result = WildcardMatcher.checkWithTrace("plugin.admin", perms);
        assertEquals(TriState.FALSE, result.result());
        assertEquals("-plugin.admin", result.matchedNode());
        assertEquals(MatchType.EXACT_NEGATION, result.matchType());
        assertTrue(result.isNegation());
        assertFalse(result.isWildcard());
    }

    @Test
    void testCheckWithTraceNegatedWildcard() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("-plugin.admin.*", true);

        MatchResult result = WildcardMatcher.checkWithTrace("plugin.admin.bypass", perms);
        assertEquals(TriState.FALSE, result.result());
        assertEquals("-plugin.admin.*", result.matchedNode());
        assertEquals(MatchType.WILDCARD_NEGATION, result.matchType());
        assertTrue(result.isNegation());
        assertTrue(result.isWildcard());
    }

    @Test
    void testCheckWithTraceUniversal() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("*", true);

        MatchResult result = WildcardMatcher.checkWithTrace("anything", perms);
        assertEquals(TriState.TRUE, result.result());
        assertEquals("*", result.matchedNode());
        assertEquals(MatchType.UNIVERSAL, result.matchType());
        assertTrue(result.isWildcard());
    }

    @Test
    void testCheckWithTraceNoMatch() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("other.permission", true);

        MatchResult result = WildcardMatcher.checkWithTrace("plugin.command", perms);
        assertEquals(TriState.UNDEFINED, result.result());
        assertNull(result.matchedNode());
        assertEquals(MatchType.NONE, result.matchType());
        assertFalse(result.isMatched());
    }

    // ==================== Check method additional coverage ====================

    @Test
    void testCheck() {
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("myplugin.command.*", true);
        perms.put("myplugin.admin", false);

        assertEquals(TriState.TRUE, WildcardMatcher.check("myplugin.command.home", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("myplugin.command.spawn", perms));
        assertEquals(TriState.FALSE, WildcardMatcher.check("myplugin.admin", perms));
        assertEquals(TriState.UNDEFINED, WildcardMatcher.check("otherplugin.command", perms));
    }

    @Test
    void testNegationPriorityOverWildcard() {
        // -plugin.admin is more specific than plugin.* so it wins
        Map<String, Boolean> perms = new HashMap<>();
        perms.put("plugin.*", true);
        perms.put("-plugin.admin", true);

        assertEquals(TriState.FALSE, WildcardMatcher.check("plugin.admin", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.command", perms));
        assertEquals(TriState.TRUE, WildcardMatcher.check("plugin.other.thing", perms));
    }
}
