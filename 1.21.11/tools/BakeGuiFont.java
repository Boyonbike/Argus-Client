import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Bake GUI glyphs offline so Minecraft never loads this TTF through native FreeType. */
public final class BakeGuiFont {
    public static void main(String[] args) throws Exception {
        Path fontSource=Path.of(args.length>0?args[0]:"tools/font-source/lexend-deca.ttf");
        Path assets=Path.of(args.length>1?args[1]:"src/main/resources/assets/argusclient");
        Font font=Font.createFont(Font.TRUETYPE_FONT,fontSource.toFile()).deriveFont(Font.PLAIN,96f);
        StringBuilder characters=new StringBuilder();for(char c=32;c<127;c++)characters.append(c);
        for(char c:"…−⌕·×→▸▾✓★♥".toCharArray())if(font.canDisplay(c))characters.append(c);
        int columns=16,cellWidth=128,cellHeight=128,rows=(characters.length()+columns-1)/columns;
        while(characters.length()<rows*columns)characters.append('\0');
        BufferedImage atlas=new BufferedImage(columns*cellWidth,rows*cellHeight,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);g.setFont(font);
        for(int i=0;i<characters.length();i++){
            char c=characters.charAt(i);if(c=='\0'||c==' ')continue;
            g.drawString(String.valueOf(c),(i%columns)*cellWidth+4,(i/columns)*cellHeight+104);
        }
        g.dispose();Path texture=assets.resolve("textures/font/gui.png");Files.createDirectories(texture.getParent());
        ImageIO.write(atlas,"png",texture.toFile());
        StringBuilder json=new StringBuilder("{\n  \"providers\": [\n    {\"type\":\"space\",\"advances\":{\" \":3}},\n    {\"type\":\"bitmap\",\"file\":\"argusclient:font/gui.png\",\"height\":11,\"ascent\":9,\"chars\":[\n");
        for(int row=0;row<rows;row++){
            json.append("      \"");
            for(int column=0;column<columns;column++){
                char c=characters.charAt(row*columns+column);
                if(c=='\0')json.append("\\u0000");else{if(c=='\\'||c=='\"')json.append('\\');json.append(c);}
            }
            json.append(row==rows-1?"\"\n":"\",\n");
        }
        json.append("    ]},\n    {\"type\":\"reference\",\"id\":\"minecraft:default\"}\n  ]\n}\n");
        Path definition=assets.resolve("font/ui.json");Files.createDirectories(definition.getParent());Files.writeString(definition,json);
        System.out.println("Baked "+characters.length()+" glyph cells into "+texture+" ("+atlas.getWidth()+"x"+atlas.getHeight()+")");
    }
}
