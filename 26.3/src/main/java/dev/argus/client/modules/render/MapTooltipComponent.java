package dev.argus.client.modules.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.argus.client.ClientAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

public record MapTooltipComponent(int mapId, MapItemSavedData mapData) implements ClientAccess, ClientTooltipComponent {
   private static final int MAP_SIZE = 96;
   private static final int MAP_TEXTURE_SIZE = 128;
   private static final int BACKGROUND_TEXTURE_SIZE = 64;
   private static final int BORDER = 7;
   private static final int TOOLTIP_SIZE = 110;
   private static final Identifier BACKGROUND_TEXTURE = Identifier.parse("textures/map/map_background.png");
   private static final MapRenderState MAP_RENDER_STATE = new MapRenderState();

   @Override
   public int getHeight(Font font) {
      return 110;
   }

   @Override
   public int getWidth(Font font) {
      return 110;
   }

   @Override
   public void extractImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphicsExtractor graphics) {
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();

      try {
         poses.translate(x, y);
         RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
         graphics.blit(pipeline, BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, 110, 110, 64, 64, -1);
         poses.translate(7.0F, 7.0F);
         poses.scale(0.75F, 0.75F);
         MapRenderer renderer = argusclientMinecraft.getMapRenderer();
         renderer.extractRenderState(new MapId(this.mapId), this.mapData, MAP_RENDER_STATE);
         graphics.map(MAP_RENDER_STATE);
      } finally {
         poses.popMatrix();
      }
   }
}
