package dev.argus.client.config.importing;

import com.google.gson.*;
import dev.argus.client.settings.*;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArgusModuleCatalogTest {
    enum Choice implements NamedEntry { FIRST, SECOND; public String getName() { return name(); } }
    @Test void capturesDeclaredDefaultsWithoutMutatingValuesOrCallingCallbacks() {
        var calls = new AtomicInteger();
        var flag = new BooleanSetting("Flag", true); flag.setValue(false); flag.setChangeCallback(calls::incrementAndGet);
        var number = new NumberSetting<>("Number", 3, 0, 10); number.setValue(8);
        var text = new StringSetting("Text", "default"); text.setValue("changed");
        assertTrue(ArgusModuleCatalog.describe(flag).defaults().getAsBoolean());
        assertEquals(3, ArgusModuleCatalog.describe(number).defaults().getAsInt());
        assertEquals("default", ArgusModuleCatalog.describe(text).defaults().getAsString());
        assertFalse(flag.getValue()); assertEquals(8, number.getValue()); assertEquals(0, calls.get());
    }
    // Real case: base AutoNameTag declares Delay default 0.0 with minimum 0.1. Native setValue
    // clamps, so the effective default is 0.1; the catalog must agree or every import fails to load.
    @Test void outOfBoundsDeclaredDefaultsUseTheClampedEffectiveValue() {
        var delay = new NumberSetting<>("Delay", 0f, .1f, 5f);
        var field = ArgusModuleCatalog.describe(delay);
        assertEquals(new JsonPrimitive(.1f), field.defaults());
        assertEquals(field.defaults(), field.validate(field.defaults()));
        assertEquals(new JsonPrimitive(.1f), ArgusModuleCatalog.current(delay));
        var count = ArgusModuleCatalog.describe(new NumberSetting<>("Count", 20, 0, 10));
        assertEquals(new JsonPrimitive(10), count.defaults());
        assertThrows(IllegalArgumentException.class, () -> field.validate(new JsonPrimitive(0)));
    }
    @Test void canonicalizesCollectionsIndependentlyOfOrderAndDuplicates() {
        var field=ArgusModuleCatalog.describe(new StringSetSetting("Names", "one", "two"));
        assertEquals(field.validate(JsonParser.parseString("[\"two\",\"one\",\"one\"]")),field.validate(JsonParser.parseString("[\"one\",\"two\"]")));
    }
    @Test void snapshotsRawColourAndFlagsWithoutThemeOrRainbowSampling() {
        var color=new ColorSetting("Colour",new Color(1,2,3,4));color.setColor(new Color(10,20,30,40));color.setRainbow(true);color.setSynced(true);
        var stored=ArgusModuleCatalog.current(color).getAsJsonObject();
        assertEquals(10,stored.getAsJsonArray("rgba").get(0).getAsInt());assertTrue(stored.get("rainbow").getAsBoolean());assertTrue(stored.get("sync").getAsBoolean());
    }
    @Test void normalizesFractionalFloatsToTheActualSettingType() {
        var field = ArgusModuleCatalog.describe(new NumberSetting<>("Float", .1f, 0f, 1f));
        assertEquals(new JsonPrimitive(.1f), field.validate(JsonParser.parseString("0.1")));
        var minimum = ArgusModuleCatalog.describe(new NumberSetting<>("Minimum", .1f, .1f, 1f));
        assertEquals(new JsonPrimitive(.1f), minimum.validate(JsonParser.parseString("0.1")));
    }
    @Test void validatesEnumsColoursBoundsAndCollectionEntries() {
        var choice = ArgusModuleCatalog.describe(new EnumSetting<>("Choice", Choice.FIRST));
        assertEquals("FIRST", choice.defaults().getAsString());
        assertThrows(IllegalArgumentException.class, () -> choice.validate(new JsonPrimitive("invented")));
        var color = new ColorSetting("Colour", new Color(10,20,30,40)); color.setRainbow(true); color.setSynced(true);
        var encoded = ArgusModuleCatalog.describe(color).defaults().getAsJsonObject();
        assertEquals(40, encoded.getAsJsonArray("rgba").get(3).getAsInt());
        assertFalse(encoded.get("rainbow").getAsBoolean()); assertFalse(encoded.get("sync").getAsBoolean());
        var numeric = ArgusModuleCatalog.describe(new NumberSetting<>("N", 2, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> numeric.validate(new JsonPrimitive(11)));
        assertThrows(IllegalArgumentException.class, () -> numeric.validate(new JsonPrimitive(1.5)));
        var strings = new StringSetSetting("Names", "one", "two"); strings.addParsedEntry("three");
        assertEquals(2, ArgusModuleCatalog.describe(strings).defaults().getAsJsonArray().size());
    }
}
