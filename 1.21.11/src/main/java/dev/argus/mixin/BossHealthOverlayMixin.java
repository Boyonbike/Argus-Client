package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.BetterBossBarsModule;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.SkyboxModule;
import java.util.Iterator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
   private static final BetterBossBarsModule betterBossBarsModule = ClientContext.getModule(BetterBossBarsModule.class);

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressBossBars(GuiGraphics graphics, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.bossBarsSetting.getValue()) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(
      method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;")
   )
   private Iterator<LerpingBossEvent> stackBossBars(Iterator<LerpingBossEvent> bars) {
      return betterBossBarsModule.isToggled() ? betterBossBarsModule.filter(bars) : bars;
   }

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;")
   )
   private Component labelBossBar(Component name, @Local LerpingBossEvent bar) {
      return betterBossBarsModule.isToggled() ? betterBossBarsModule.label(bar, name) : name;
   }

   /** The 10 in {@code yOffset += 10 + font.lineHeight}: the gap under each bar's name. */
   @ModifyConstant(method = "render", constant = @Constant(intValue = 10, ordinal = 0))
   private int spaceBossBars(int gap) {
      return betterBossBarsModule.isToggled() ? betterBossBarsModule.spacing() - 9 : gap;
   }

   @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
   private void suppressBossBarDarkening(CallbackInfoReturnable<Boolean> callback) {
      if (noRenderModule.isToggled() && noRenderModule.bossBarsSetting.getValue() || skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }
}
