import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** High-density, antialiased GUI primitives and original category icons. */
public final class BakeGuiArt {
    private static Path sprites=Path.of("src/main/resources/assets/randomstuffclient/textures/gui/sprites");
    public static void main(String[] args) throws Exception {
        if(args.length>0)sprites=Path.of(args[0]);Files.createDirectories(sprites);
        for(int radius=3;radius<=7;radius++){
            int logical=radius*4,size=logical*4;BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=graphics(image);g.setColor(Color.WHITE);g.fill(new RoundRectangle2D.Double(0,0,size,size,radius*8,radius*8));g.dispose();
            Path path=sprites.resolve("rounded_"+radius+".png");ImageIO.write(image,"png",path.toFile());
            Files.writeString(path.resolveSibling(path.getFileName()+".mcmeta"),"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":"+logical+",\"height\":"+logical+",\"border\":"+radius+",\"stretch_inner\":true}}}\n");
        }
        for(String name:new String[]{"combat","movement","render","player","exploit","client","misc","favourites","search"}){
            BufferedImage image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);Graphics2D g=graphics(image);g.scale(8,8);
            g.setColor(Color.WHITE);g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(name){
                case "search"->{g.draw(new Ellipse2D.Double(2.5,2.5,8,8));g.draw(new Line2D.Double(10,10,14,14));}
                case "combat"->{g.draw(new Line2D.Double(3,2,13,12));g.draw(new Line2D.Double(13,2,3,12));g.draw(new Line2D.Double(1.5,10.5,5,14));g.draw(new Line2D.Double(11,14,14.5,10.5));}
                case "movement"->{Path2D p=new Path2D.Double();p.moveTo(3,8);p.lineTo(8,3);p.lineTo(13,8);p.moveTo(8,3);p.lineTo(8,14);g.draw(p);}
                case "render"->{Path2D p=new Path2D.Double();p.moveTo(1.5,8);p.quadTo(8,-1,14.5,8);p.quadTo(8,17,1.5,8);g.draw(p);g.fill(new Ellipse2D.Double(6,6,4,4));}
                case "player"->{g.draw(new Ellipse2D.Double(5,2,6,6));g.draw(new Arc2D.Double(2,9,12,10,0,180,Arc2D.OPEN));}
                case "exploit"->{Path2D p=new Path2D.Double();p.moveTo(9,1);p.lineTo(3,9);p.lineTo(7,9);p.lineTo(6,15);p.lineTo(13,6);p.lineTo(9,6);p.closePath();g.fill(p);}
                case "client"->{g.draw(new Ellipse2D.Double(4,4,8,8));g.draw(new Ellipse2D.Double(6.5,6.5,3,3));for(int i=0;i<8;i++){double a=i*Math.PI/4;g.draw(new Line2D.Double(8+5*Math.cos(a),8+5*Math.sin(a),8+7*Math.cos(a),8+7*Math.sin(a)));}}
                case "misc"->{for(int x:new int[]{2,9})for(int y:new int[]{2,9})g.draw(new RoundRectangle2D.Double(x,y,5,5,1,1));}
                case "favourites"->{Path2D p=new Path2D.Double();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5,r=i%2==0?6.5:3;double x=8+r*Math.cos(a),y=8+r*Math.sin(a);if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.closePath();g.fill(p);}
            }
            g.dispose();ImageIO.write(image,"png",sprites.resolve(name+".png").toFile());
        }
        System.out.println("Baked high-density rounded panels and9category icons");
    }
    private static Graphics2D graphics(BufferedImage image){Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);return g;}
}
