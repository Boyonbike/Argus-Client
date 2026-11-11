import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Bakes the loading-screen glyph sheet offline so Minecraft never touches AWT/FreeType at startup.
 * Layout must match LoadingAtlas: 48x64 cells, 10 per row, baseline at y=50, glyph centred in its cell.
 *
 * Usage: java tools/BakeLoadingGlyphs.java [font.ttf] [assetsDir]
 */
public final class BakeLoadingGlyphs {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789%#/<>+-:.";

    public static void main(String[] args) throws Exception {
        Path fontSource = Path.of(args.length > 0 ? args[0] : "tools/font-source/lexend-deca.ttf");
        Path assets = Path.of(args.length > 1 ? args[1] : "src/main/resources/assets/argusclient");
        Font font = Font.createFont(Font.TRUETYPE_FONT, fontSource.toFile()).deriveFont(Font.PLAIN, 46f);
        int cellW = 48, cellH = 64, columns = 10, baseline = 50;
        BufferedImage sheet = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.setFont(font);
        for (int i = 0; i < CHARS.length(); i++) {
            String ch = String.valueOf(CHARS.charAt(i));
            float advance = (float) font.getStringBounds(ch, g.getFontRenderContext()).getWidth();
            g.drawString(ch, (i % columns) * cellW + (cellW - advance) / 2f, (i / columns) * cellH + baseline);
        }
        g.dispose();
        Path out = assets.resolve("textures/loading_glyphs.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(sheet, "png", out.toFile());
        System.out.println("Wrote " + out);
    }
}
