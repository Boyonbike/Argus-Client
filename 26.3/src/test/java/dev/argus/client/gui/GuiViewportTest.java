package dev.argus.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GuiViewportTest {
    @Test void canvasSizeDoesNotGrowWithMinecraftGuiZoom(){
        var first=GuiViewport.create(1920,1080,1,1);
        for(double minecraftScale:new double[]{2,3,4}){
            var viewport=GuiViewport.create(1920,1080,minecraftScale,1);
            assertEquals(first.width(),viewport.width());assertEquals(first.height(),viewport.height());
            assertEquals(196,112*viewport.renderScale()*minecraftScale,.001,"Category windows should be compact at about196screen pixels");
        }
    }
    @Test void mouseCoordinatesFollowTheRenderedCanvas(){
        for(double minecraftScale:new double[]{1,2,4}){
            var viewport=GuiViewport.create(1920,1080,minecraftScale,1);
            assertEquals(120,viewport.toLogical(120*viewport.renderScale()),.001);
        }
    }
    @Test void smallWindowsRemainUsable(){
        var viewport=GuiViewport.create(320,240,1,1);
        assertTrue(viewport.width()>=320);assertTrue(viewport.height()>=240);
    }
}
