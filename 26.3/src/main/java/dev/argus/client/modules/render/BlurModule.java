package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.MaskShaderRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FloatAnimation;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlurModule extends Module {
   private static final Component CLICK_SCREEN_TITLE;
   private final Setting<Integer> radiusSetting;
   private final FloatAnimation openingAnimation;

   public int getRadius() {
      return this.radiusSetting.getValue();
   }

   public BlurModule() {
      super("Blur", "Blurs the background in GUI's.", ModuleCategory.RENDER);
      String radiusKey = new String("Radius");
      this.radiusSetting = this.add(new NumberSetting<>(radiusKey, 6, 1, 16));
      this.openingAnimation = new FloatAnimation();
      this.setToggled(true);
   }

   private static boolean isBlurExcluded(Screen screen) {
      return screen != null && !(screen instanceof ClientScreen) && !(screen instanceof ChatScreen) ? screen.getTitle().equals(CLICK_SCREEN_TITLE) : true;
   }

   @EventHandler
   private void onScreenChange(ScreenChangeEvent.Before event) {
      if (!event.isCancelled()) {
         if (event.getNewScreen() == null) {
            FloatAnimation animation = this.openingAnimation;
            resetOpeningAnimation(animation);
         } else if (event.getPreviousScreen() == null) {
            this.openingAnimation.setTarget(1.0F, 75L);
         }
      }
   }

   private static void resetOpeningAnimation(FloatAnimation animation) {
      animation.setValue(0.0F);
   }

   @EventHandler(priority = -200)
   private void onHudRender(HudRenderEvent.Unconditional event) {
      PoseStack poseStack = event.getPoseStack();
      this.renderBackgroundBlur(poseStack);
   }

   public void renderBackgroundBlur(PoseStack poseStack) {
      Screen currentScreen = ClientAccess.argusclientMinecraft.gui.screen();
      if (!isBlurExcluded(currentScreen)) {
         Runnable drawFullscreenMask = () -> {
            RenderUtils renderer = RenderUtils.INSTANCE;
            float width = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
            float height = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
            renderer.fillRect(poseStack, 0.0F, 0.0F, width, height, 9999, -1);
         };
         float radius = this.getRadius();
         FloatAnimation animationCapture = this.openingAnimation;
         MaskShaderRenderer.renderBlur(drawFullscreenMask, radius * animationCapture.getValue());
      }
   }

   static {
      String clickTitle = "CLICK";
      CLICK_SCREEN_TITLE = Component.literal(clickTitle);
   }
}
