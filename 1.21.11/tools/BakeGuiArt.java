import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Original GUI icons as 128px antialiased white masks on a 16-unit grid. The client area-averages
 * them down to the exact pixel size they are displayed at, so keep strokes at 1.5 units or more.
 *
 * Run from the project directory: java tools/BakeGuiArt.java
 */
public final class BakeGuiArt {
    private static Path icons = Path.of("src/main/resources/assets/randomstuffclient/textures/icons");
    private static final String[] NAMES = {"combat", "movement", "render", "player", "exploit", "client", "misc", "favourites",
            "search", "chevron_down", "chevron_right", "chevron_left", "check", "close", "plus", "star", "keyboard", "palette", "people"};

    public static void main(String[] args) throws Exception {
        if (args.length > 0) icons = Path.of(args[0]);
        Files.createDirectories(icons);
        for (String name : NAMES) {
            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(image); g.scale(8, 8);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "search" -> { g.draw(new Ellipse2D.Double(2.5, 2.5, 8, 8)); g.draw(new Line2D.Double(10, 10, 13.5, 13.5)); }
                case "combat" -> { g.draw(new Line2D.Double(3, 2.5, 12.5, 12)); g.draw(new Line2D.Double(13, 2.5, 3.5, 12)); g.draw(new Line2D.Double(2, 10.5, 5.5, 14)); g.draw(new Line2D.Double(10.5, 14, 14, 10.5)); }
                case "movement" -> { Path2D p = new Path2D.Double(); p.moveTo(3.5, 7.5); p.lineTo(8, 3); p.lineTo(12.5, 7.5); p.moveTo(8, 3); p.lineTo(8, 13.5); g.draw(p); }
                case "render" -> { Path2D p = new Path2D.Double(); p.moveTo(1.5, 8); p.quadTo(8, -1, 14.5, 8); p.quadTo(8, 17, 1.5, 8); g.draw(p); g.fill(new Ellipse2D.Double(5.8, 5.8, 4.4, 4.4)); }
                case "player" -> { g.draw(new Ellipse2D.Double(5, 2, 6, 6)); g.draw(new Arc2D.Double(2.5, 9.5, 11, 9, 0, 180, Arc2D.OPEN)); }
                case "exploit" -> { Path2D p = new Path2D.Double(); p.moveTo(9, 1.5); p.lineTo(3.5, 9); p.lineTo(7.5, 9); p.lineTo(6.5, 14.5); p.lineTo(12.5, 6.5); p.lineTo(8.5, 6.5); p.closePath(); g.fill(p); }
                case "client" -> { g.draw(new Ellipse2D.Double(4.5, 4.5, 7, 7)); g.fill(new Ellipse2D.Double(6.6, 6.6, 2.8, 2.8)); for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; g.draw(new Line2D.Double(8 + 5.2 * Math.cos(a), 8 + 5.2 * Math.sin(a), 8 + 6.8 * Math.cos(a), 8 + 6.8 * Math.sin(a))); } }
                case "misc" -> { for (int x : new int[]{2, 9}) for (int y : new int[]{2, 9}) g.draw(new RoundRectangle2D.Double(x + .3, y + .3, 4.6, 4.6, 1.6, 1.6)); }
                case "favourites", "star" -> { Path2D p = star(); if (name.equals("star")) g.fill(p); else g.fill(p); }
                case "chevron_down" -> { Path2D p = new Path2D.Double(); p.moveTo(4, 6); p.lineTo(8, 10); p.lineTo(12, 6); g.draw(p); }
                case "chevron_right" -> { Path2D p = new Path2D.Double(); p.moveTo(6, 4); p.lineTo(10, 8); p.lineTo(6, 12); g.draw(p); }
                case "chevron_left" -> { Path2D p = new Path2D.Double(); p.moveTo(10, 4); p.lineTo(6, 8); p.lineTo(10, 12); g.draw(p); }
                case "check" -> { g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); Path2D p = new Path2D.Double(); p.moveTo(3.5, 8.5); p.lineTo(6.6, 11.5); p.lineTo(12.5, 4.5); g.draw(p); }
                case "close" -> { g.draw(new Line2D.Double(4.5, 4.5, 11.5, 11.5)); g.draw(new Line2D.Double(11.5, 4.5, 4.5, 11.5)); }
                case "plus" -> { g.draw(new Line2D.Double(8, 3.5, 8, 12.5)); g.draw(new Line2D.Double(3.5, 8, 12.5, 8)); }
                case "keyboard" -> { g.draw(new RoundRectangle2D.Double(1.5, 4, 13, 8, 2.5, 2.5)); for (double x : new double[]{4.5, 8, 11.5}) g.fill(new Ellipse2D.Double(x - .8, 6.2, 1.6, 1.6)); g.draw(new Line2D.Double(5.5, 9.8, 10.5, 9.8)); }
                case "palette" -> { Path2D p = new Path2D.Double(); p.moveTo(8, 1.8); p.curveTo(3.5, 1.8, 1.8, 5, 1.8, 8); p.curveTo(1.8, 11.5, 4.5, 14.2, 8, 14.2); p.curveTo(9.5, 14.2, 9.6, 12.8, 9, 12); p.curveTo(8.2, 11, 9, 9.6, 10.4, 9.6); p.lineTo(12, 9.6); p.curveTo(13.6, 9.6, 14.2, 8.4, 14.2, 7.2); p.curveTo(14.2, 4, 11.4, 1.8, 8, 1.8); p.closePath(); g.draw(p); for (double[] d : new double[][]{{5, 6.5}, {8, 4.6}, {11, 6.5}}) g.fill(new Ellipse2D.Double(d[0] - 1, d[1] - 1, 2, 2)); }
                case "people" -> { g.draw(new Ellipse2D.Double(3, 2.5, 5, 5)); g.draw(new Arc2D.Double(1, 9, 9, 8, 0, 180, Arc2D.OPEN)); g.draw(new Arc2D.Double(9, 3, 4.5, 4.5, -90, 230, Arc2D.OPEN)); g.draw(new Arc2D.Double(10, 9, 5.5, 7, 0, 90, Arc2D.OPEN)); }
            }
            g.dispose();
            ImageIO.write(image, "png", icons.resolve(name + ".png").toFile());
        }
        System.out.println("Baked " + NAMES.length + " GUI icons into " + icons);
    }

    private static Path2D star() {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? 6.6 : 3.1;
            double x = 8 + r * Math.cos(a), y = 8.4 + r * Math.sin(a);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }
}
