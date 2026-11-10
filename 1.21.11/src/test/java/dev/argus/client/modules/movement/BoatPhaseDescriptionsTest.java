package dev.argus.client.modules.movement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every Boat Phase and Boat Speed setting has hover text in descriptions.tsv. */
class BoatPhaseDescriptionsTest {
    private static final Pattern SETTING = Pattern.compile("new (?:BooleanSetting|NumberSetting<>|KeyBindSetting)\\(\"([^\"]+)\"");

    private static void assertDescribed(String module, Path source, String tsv) throws Exception {
        Matcher m = SETTING.matcher(Files.readString(source, StandardCharsets.UTF_8));
        List<String> missing = new ArrayList<>();
        int found = 0;
        while (m.find()) {
            found++;
            if (!tsv.contains("\n" + module + "\t" + m.group(1) + "\t")) missing.add(m.group(1));
        }
        assertTrue(found > 0, "no settings found in " + source);
        assertTrue(missing.isEmpty(), module + " settings without a description: " + missing);
        assertTrue(tsv.contains("\n" + module + "\t\t") || module.equals("BoatPhase"), module + " has no module description");
    }

    @Test void everySettingHasADescription() throws Exception {
        String tsv = Files.readString(Path.of("src/main/resources/assets/argusclient/descriptions.tsv"), StandardCharsets.UTF_8);
        assertDescribed("BoatPhase", Path.of("src/main/java/dev/argus/client/modules/movement/BoatPhaseModule.java"), tsv);
        assertDescribed("Boat Speed", Path.of("src/main/java/dev/argus/client/modules/hud/BoatSpeedHudModule.java"), tsv);
    }
}
