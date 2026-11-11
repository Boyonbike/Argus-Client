package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a slowly spinning entity, used for the mob inside a bucket.
 * Feature ported from Meteor Client's BetterTooltips bucket preview (GPL-3.0); see THIRD_PARTY_NOTICES.md.
 */
public record EntityTooltipComponent(LivingEntity entity) implements ClientAccess, ClientTooltipComponent {
   private static final int WIDTH = 64;
   private static final int HEIGHT = 48;
   private static final int FULL_BRIGHT = 15728880;
   private static float spin;

   @Override
   public int getHeight(Font font) {
      return HEIGHT + 4;
   }

   @Override
   public int getWidth(Font font) {
      return WIDTH;
   }

   @Override
   public void renderImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphics graphics) {
      EntityRenderState state = argusclientMinecraft.getEntityRenderDispatcher().getRenderer(this.entity).createRenderState(this.entity, 1.0F);
      state.lightCoords = FULL_BRIGHT;
      state.shadowPieces.clear();
      state.outlineColor = EntityRenderState.NO_OUTLINE;
      if (state instanceof LivingEntityRenderState living) {
         living.bodyRot = spin % 360.0F;
         living.yRot = 0.0F;
         living.xRot = 0.0F;
      }

      int left = x + (tooltipWidth - WIDTH) / 2;
      int top = y + 4;
      float scale = Math.max(WIDTH, HEIGHT) / 2.0F * 1.25F;
      graphics.submitEntityRenderState(
         state, scale, new Vector3f(0.0F, 0.1F, 0.0F), new Quaternionf().rotateZ((float)Math.PI), null, left, top, left + WIDTH, top + HEIGHT
      );
      spin += 3.0F * argusclientMinecraft.getDeltaTracker().getGameTimeDeltaTicks();
   }
}
