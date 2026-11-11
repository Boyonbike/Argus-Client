package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Draws a banner with its patterns, the way the loom screen does.
 * Feature ported from Meteor Client's BetterTooltips banner preview (GPL-3.0); see THIRD_PARTY_NOTICES.md.
 */
public record BannerTooltipComponent(DyeColor baseColor, BannerPatternLayers patterns) implements ClientAccess, ClientTooltipComponent {
   private static final int WIDTH = 20;
   private static final int HEIGHT = 40;
   private static BannerFlagModel flagModel;

   @Override
   public int getHeight(Font font) {
      return HEIGHT + 2;
   }

   @Override
   public int getWidth(Font font) {
      return WIDTH;
   }

   @Override
   public void renderImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphics graphics) {
      if (flagModel == null) {
         flagModel = new BannerFlagModel(argusclientMinecraft.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
      }

      int left = x + (tooltipWidth - WIDTH) / 2;
      graphics.submitBannerPatternRenderState(flagModel, this.baseColor, this.patterns, left, y, left + WIDTH, y + HEIGHT);
   }
}
