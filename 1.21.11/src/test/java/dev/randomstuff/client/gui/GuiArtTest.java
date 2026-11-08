package dev.randomstuff.client.gui;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GuiArtTest {
    private static final String[] ICONS = {"combat", "movement", "render", "player", "exploit", "client", "misc", "favourites", "search",
            "chevron_down", "chevron_right", "chevron_left", "check", "close", "plus", "star", "keyboard", "palette", "people"};

    @Test void iconsAreHighDensityAntialiasedMasks() throws Exception {
        for (String name : ICONS) {
            try (var source = getClass().getResourceAsStream("/assets/randomstuffclient/textures/icons/" + name + ".png")) {
                assertNotNull(source, "Missing icon " + name);
                var image = ImageIO.read(source);
                assertEquals(128, image.getWidth(), name); assertEquals(128, image.getHeight(), name);
                boolean smooth = false, drawn = false;
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (alpha > 0 && alpha < 255) smooth = true;
                    if (alpha > 0) drawn = true;
                }
                assertTrue(drawn, name + " is empty"); assertTrue(smooth, name + " needs antialiased edges");
            }
        }
    }

    @Test void roundedShapesAreDrawnByTheDistanceFieldShader() throws Exception {
        for (String stage : new String[]{"vsh", "fsh"}) {
            try (var source = getClass().getResourceAsStream("/assets/randomstuffclient/shaders/core/ui_shape." + stage)) {
                assertNotNull(source, "Missing ui_shape." + stage);
                String text = new String(source.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.startsWith("#version 330"));
                if (stage.equals("vsh")) { assertTrue(text.contains("in ivec2 UV1;")); assertTrue(text.contains("in ivec2 UV2;")); }
                else assertTrue(text.contains("roundedBox"));
            }
        }
    }

    @Test void menuFontIsPackagedForExactSizeRasterisation() throws Exception {
        try (var source = getClass().getResourceAsStream("/assets/randomstuffclient/fonts/lexend-deca.ttf")) {
            assertNotNull(source);
            byte[] header = source.readNBytes(4);
            assertArrayEquals(new byte[]{0, 1, 0, 0}, header, "Expected a TrueType outline font");
        }
    }
}
