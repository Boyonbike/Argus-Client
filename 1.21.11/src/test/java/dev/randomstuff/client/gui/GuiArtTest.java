package dev.randomstuff.client.gui;
import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GuiArtTest {
    @Test void roundedPanelsHaveHighDensityAntialiasedEdgesAndValidBorders() throws Exception {
        for(int radius=3;radius<=7;radius++){
            String path="/assets/randomstuffclient/textures/gui/sprites/rounded_"+radius+".png";
            try(var source=getClass().getResourceAsStream(path);var metadata=getClass().getResourceAsStream(path+".mcmeta")){
                assertNotNull(source);assertNotNull(metadata);var image=ImageIO.read(source);
                assertTrue(image.getWidth()>=radius*16);boolean smooth=false;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int alpha=image.getRGB(x,y)>>>24;if(alpha>0&&alpha<255)smooth=true;}
                assertTrue(smooth,"Rounded corners need antialiasing");
                var scaling=JsonParser.parseReader(new InputStreamReader(metadata)).getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
                assertEquals("nine_slice",scaling.get("type").getAsString());
                assertTrue(scaling.get("border").getAsInt()*2<scaling.get("width").getAsInt());
            }
        }
    }
}
