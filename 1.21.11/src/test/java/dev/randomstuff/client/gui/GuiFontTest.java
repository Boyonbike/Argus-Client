package dev.randomstuff.client.gui;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The GUI font must not introduce a native FreeType provider during resource reload. */
final class GuiFontTest {
    @Test void guiFontUsesACompletePreRenderedAtlasRatherThanNativeTtfLoading() throws Exception {
        try(var source=getClass().getResourceAsStream("/assets/randomstuffclient/font/ui.json")){
            assertNotNull(source);
            var providers=JsonParser.parseReader(new InputStreamReader(source,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
            boolean bitmap=false;StringBuilder supported=new StringBuilder();
            for(var provider:providers){
                var value=provider.getAsJsonObject();String type=value.get("type").getAsString();
                assertNotEquals("ttf",type,"GUI font must not re-enter the crashing native FreeType loader");
                if(type.equals("bitmap")){
                    bitmap=true;
                    for(var row:value.getAsJsonArray("chars"))supported.append(row.getAsString());
                    String file=value.get("file").getAsString().replace(":","/textures/");
                    try(var image=getClass().getResourceAsStream("/assets/"+file)){
                        assertNotNull(image,"Atlas texture missing");var atlas=ImageIO.read(image);assertNotNull(atlas);
                        int rows=value.getAsJsonArray("chars").size(),columns=value.getAsJsonArray("chars").get(0).getAsString().length();
                        assertEquals(0,atlas.getWidth()%columns);assertEquals(0,atlas.getHeight()%rows);
                    }
                }
            }
            assertTrue(bitmap,"GUI font requires a pre-rendered bitmap provider");
            for(char c=32;c<127;c++)assertTrue(supported.indexOf(String.valueOf(c))>=0,"Missing ASCII glyph "+c);
        }
    }
}
