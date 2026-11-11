package dev.argus.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.RenderUtils;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class CrystalDamageRenderer {
   private final AutoCrystalModule autoCrystal;
   private float damage;
   private long visibleUntilMillis;
   private BlockPos pos;

   public CrystalDamageRenderer(AutoCrystalModule autoCrystal) {
      this.autoCrystal = autoCrystal;
   }

   public void showDamage(BlockPos base, double newDamage) {
      this.pos = base;
      long now = System.currentTimeMillis();
      this.visibleUntilMillis = now + 150L;
      this.damage = (float)newDamage;
   }

   @EventHandler(priority = Integer.MIN_VALUE)
   public void onRenderDamage(WorldRenderEvent.Main event) {
      if (this.autoCrystal.renderDamageSetting.getValue()) {
         Float fadeDuration = this.autoCrystal.fadeTimeSetting.getValue();
         long fadeMillis = (long)(1000.0F * fadeDuration);
         if (!this.autoCrystal.fadeSetting.getValue()) {
            fadeMillis = 1L;
         }

         if (System.currentTimeMillis() < this.visibleUntilMillis + fadeMillis) {
            String text = String.format("%.1f", this.damage);
            long elapsedMillis = System.currentTimeMillis() - this.visibleUntilMillis;
            float elapsed = (float)elapsedMillis;
            float duration = this.autoCrystal.fadeTimeSetting.getValue();
            float alpha = 1.0F - elapsed / (duration * 1000.0F);
            if (!(alpha <= 0.02F)) {
               PoseStack poseStack = event.getPoseStack();
               Vec3 center = Vec3.atCenterOf(this.pos);
               float negativeWidth = -FontRenderer.INSTANCE.getWidth(text);
               float textX = negativeWidth / 2.0F;
               float negativeHeight = -FontRenderer.INSTANCE.getHeight();
               float textY = negativeHeight / 2.0F;
               RenderUtils textRenderer = RenderUtils.INSTANCE;
               textRenderer.drawWorldText(poseStack, text, center, 0.0F, 0.0F, textX, textY, 1.0, ColorUtils.withAlpha(Color.white, alpha), true);
            }
         }
      }
   }
}
