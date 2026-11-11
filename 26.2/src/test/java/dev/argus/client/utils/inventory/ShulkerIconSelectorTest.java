package dev.argus.client.utils.inventory;

import dev.argus.client.utils.inventory.ShulkerIconSelector.Entry;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Match;
import dev.argus.client.utils.inventory.ShulkerIconSelector.NameMode;
import dev.argus.client.utils.inventory.ShulkerIconSelector.ContentsMode;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Style;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShulkerIconSelectorTest {
    @Test
    void detectsNamedEmptyShulkerAndKeepsPriority() {
        var nameRule = rule("Rocket", NameMode.Contains, ContentsMode.All, 1, List.of(), "name");
        var itemRule = new Rule<>("Legacy", true, List.of("paper"), "legacy");
        assertEquals("name", ShulkerIconSelector.selectIcon(List.of(), "ROCKET supplies", List.of(nameRule)).item());
        assertNull(ShulkerIconSelector.selectIcon(List.of(), "", List.of(nameRule)));
        var entries = List.of(new Entry<>("paper", 1));
        assertEquals("legacy", ShulkerIconSelector.selectIcon(entries, "Rocket", List.of(itemRule, nameRule)).item());
    }

    @Test
    void matchesQuantitiesAndCombinesNameWithContents() {
        var exact = rule("Kit", NameMode.Exact, ContentsMode.Exact, 8, List.of("paper"), "exact");
        var any = rule("", NameMode.Contains, ContentsMode.Any, 8, List.of("paper", "string"), "any");
        var entries = List.of(new Entry<>("paper", 4), new Entry<>("paper", 4));
        assertEquals("exact", ShulkerIconSelector.selectIcon(entries, "kit", List.of(exact, any)).item());
        assertEquals("any", ShulkerIconSelector.selectIcon(entries, "kit 2", List.of(exact, any)).item());
        assertEquals("any", ShulkerIconSelector.selectIcon(
            List.of(new Entry<>("paper", 8), new Entry<>("stone", 1)), "Kit", List.of(exact, any)).item());
        assertEquals("paper", ShulkerIconSelector.selectIcon(List.of(new Entry<>("paper", 7)), "Kit", List.of(exact, any)).item());
    }

    @Test
    void savesIndependentPixelsAndPreservesThemWhenTogglingRule() {
        int[] pixels = new int[16 * 16];
        pixels[0] = 0x80402010;
        pixels[255] = 0xffffffff;
        var icon = new PixelIcon(pixels);
        var original = new Rule<>("Pixels", true, List.of("paper"), "base", Style.Normal, -1, Match.DEFAULT, icon);

        int[] decoded = PixelIcon.decode(icon.encode()).copyPixels();
        assertArrayEquals(pixels, decoded);
        pixels[0] = 0;
        decoded[255] = 0;
        var selected = ShulkerIconSelector.selectIcon(
            List.of(new Entry<>("paper", 1)), List.of(original.withEnabled(false).withEnabled(true)));
        assertEquals(0x80402010, selected.pixels().pixel(0, 0));
        assertEquals(0xffffffff, selected.pixels().pixel(15, 15));
    }

    private Rule<String> rule(String name, NameMode names, ContentsMode contents, int count, List<String> required, String icon) {
        return new Rule<>("Rule", true, required, icon, Style.Normal, -1, new Match(name, names, contents, count), null);
    }
}
