package dev.argus.client.gui;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class GuiSearchTest {
    private final List<GuiSearch.Entry> entries = List.of(
        new GuiSearch.Entry("AutoCrystal", "Combat", List.of("Crystal Aura", "ca"), List.of("PlaceRange", "BreakDelay")),
        new GuiSearch.Entry("AutoTool", "Player", List.of(), List.of("SwitchDelay")),
        new GuiSearch.Entry("Reach", "Combat", List.of(), List.of("Range")));
    @Test void exactNamesRankAboveSimilarNames() {
        assertEquals("AutoCrystal", GuiSearch.search(entries, "AutoCrystal").getFirst().module());
    }
    @Test void findsAliasesAndMinorTypingMistakes() {
        assertEquals("ca", GuiSearch.search(entries, "ca").getFirst().alias());
        assertEquals("AutoCrystal", GuiSearch.search(entries, "autocrstal").getFirst().module());
    }
    @Test void settingsDoNotAppearInSearchResults() {
        assertTrue(GuiSearch.search(entries, "place range").isEmpty());
        assertTrue(GuiSearch.search(entries, "auto").stream().noneMatch(GuiSearch.Hit::isSetting));
    }
    @Test void emptyAndUnrelatedQueriesDoNotReturnNoise() {
        assertTrue(GuiSearch.search(entries, "  ").isEmpty());
        assertTrue(GuiSearch.search(entries, "zqxv987654").isEmpty());
    }
    @Test void caseWhitespaceAndPunctuationDoNotChangeResults() {
        assertEquals(GuiSearch.search(entries, "AutoCrystal"), GuiSearch.search(entries, " AUTO-CRYSTAL "));
    }
}
