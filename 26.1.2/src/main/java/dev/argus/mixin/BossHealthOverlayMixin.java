package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.SkyboxModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressBossBars(GuiGraphicsExtractor graphics, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.bossBarsSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
   private void suppressBossBarDarkening(CallbackInfoReturnable<Boolean> callback) {
      if (noRenderModule.isToggled() && noRenderModule.bossBarsSetting.getValue() || skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }
}
