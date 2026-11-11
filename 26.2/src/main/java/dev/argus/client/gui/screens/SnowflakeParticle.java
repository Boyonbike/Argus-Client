package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FrameTiming;
import dev.argus.client.utils.math.MathUtils;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class SnowflakeParticle implements ClientAccess {
   private final float fallSpeed;
   private final float scale;
   private final double initialRelativeX;
   private final double horizontalDrift;
   private float relativeY;

   public boolean isExpired() {
      float currentRelativeY = this.relativeY;
      return currentRelativeY >= 1.0F;
   }

   public SnowflakeParticle() {
      double randomSpeed = Math.random();
      this.fallSpeed = (float)Mth.clamp(randomSpeed, 0.07F, 0.18F);
      float minimumScale = 0.5F;
      this.scale = MathUtils.randomFloat(minimumScale, 1.15F);
      this.initialRelativeX = Math.random();
      this.horizontalDrift = MathUtils.randomFloat(-0.033F, 0.033F);
      this.relativeY = MathUtils.randomFloat(-0.45F, 0.0F);
   }

   public double getInitialRelativeX() {
      return this.initialRelativeX;
   }

   public void render(PoseStack poseStack) {
      Font clientFont = ClientAccess.argusclientMinecraft.font;
      double relativeX = this.initialRelativeX + this.relativeY * this.horizontalDrift;
      int screenWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
      int screenHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
      float screenX = (float)(relativeX * screenWidth);
      float renderValue = this.relativeY * screenHeight;
      renderValue *= 0.5F;
      Objects.requireNonNull(clientFont);
      renderValue -= 9.0F;
      float screenY = renderValue - 1.0F;
      float previousRelativeY = this.relativeY;
      FrameTiming frameTiming = ClientContext.frameTiming;
      this.relativeY = previousRelativeY + frameTiming.getFrameFactor(0.5F) * this.fallSpeed;
      float updatedRelativeY = this.relativeY;
      this.relativeY = Math.min(updatedRelativeY, 1.0F);
      Setting<Boolean> snowSetting = UIModule.INSTANCE.snowSetting;
      Boolean snowEnabled = snowSetting.getValue();
      if (snowEnabled && this.relativeY >= 0.0F) {
         int opacity = Mth.ceil(Math.max(255.0F * (1.0F - this.relativeY), 4.0F));
         poseStack.pushPose();
         float scaleX = this.scale;
         float scaleY = this.scale;
         poseStack.scale(scaleX, scaleY, 1.0F);
         poseStack.translate(screenX / this.scale, screenY / this.scale, 0.0F);
         GuiDrawUtils.drawText(poseStack, "❆", 0, 0, ARGB.color(opacity, -1), true);
         poseStack.popPose();
      }
   }
}
