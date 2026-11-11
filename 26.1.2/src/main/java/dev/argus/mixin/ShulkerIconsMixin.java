package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.render.ShulkerIconRenderState;
import dev.argus.client.render.ShulkerIconRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class ShulkerIconsMixin {
   @ModifyArg(
      method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addItem(Lnet/minecraft/client/gui/render/state/GuiItemRenderState;)V")
   )
   private GuiItemRenderState argus$iconStyle(GuiItemRenderState state) {
      ((ShulkerIconRenderState)(Object)state).argus$iconColour(ShulkerIconRenderer.extractingColour());
      return state;
   }

   // Shared by real and fake GUI items, covering the hotbar, container slots, creative inventory and carried stacks.
   @Inject(
      method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
      at = @At("TAIL")
   )
   private void argus$shulkerIcon(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo info) {
      TooltipsModule tooltips = ClientContext.getModule(TooltipsModule.class);
      if (tooltips != null) {
         tooltips.drawShulkerIcon((GuiGraphicsExtractor)(Object)this, stack, x, y);
      }
   }
}
