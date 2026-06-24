package com.hyperperms.command.suggest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuggestionFilterTest {

    @Test
    void matchesCaseInsensitivePrefix() {
        List<String> result = SuggestionFilter.filter(
                List.of("Mod", "Admin", "admin-helper", "builder"), "ad");
        assertEquals(List.of("Admin", "admin-helper"), result);
    }

    @Test
    void emptyInputReturnsAllSorted() {
        List<String> result = SuggestionFilter.filter(
                List.of("Mod", "Admin", "builder"), "");
        assertEquals(List.of("Admin", "builder", "Mod"), result);
    }

    @Test
    void blankInputTreatedAsEmpty() {
        List<String> result = SuggestionFilter.filter(List.of("b", "a"), "   ");
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void noMatchReturnsEmpty() {
        assertTrue(SuggestionFilter.filter(List.of("Mod", "Admin"), "xyz").isEmpty());
    }

    @Test
    void prefixIsCaseInsensitiveOnBothSides() {
        assertEquals(List.of("Admin"), SuggestionFilter.filter(List.of("Admin"), "AD"));
    }

    @Test
    void nullCandidatesReturnsEmpty() {
        assertTrue(SuggestionFilter.filter(null, "a").isEmpty());
    }

    @Test
    void nullInputTreatedAsEmpty() {
        assertEquals(List.of("a", "b"), SuggestionFilter.filter(List.of("b", "a"), null));
    }
}
