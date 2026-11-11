package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.TooltipsModule;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import dev.argus.client.events.ItemUseEvent;
import dev.argus.client.managers.GlintRegistry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
   @Shadow
   public abstract Item getItem();

   @Inject(method = "finishUsingItem", at = @At("HEAD"))
   private void postBeforeItemUseFinish(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> callback) {
      if (entity instanceof LocalPlayer) {
         ItemUseEvent.BeforeFinish event = new ItemUseEvent.BeforeFinish((ItemStack)(Object)this);
         ClientAccess.eventBus.post(event);
      }
   }

   @Inject(method = "addDetailsToTooltip", at = @At("HEAD"))
   private void argusclient$addDurability(
      Item.TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag,
      Consumer<Component> lines, CallbackInfo callback
   ) {
      ItemStack stack = (ItemStack)(Object)this;
      if (!stack.isDamageableItem() || !display.shows(DataComponents.DAMAGE)
         || (flag.isAdvanced() && stack.isDamaged())) return;
      TooltipsModule tooltips = ClientContext.getModule(TooltipsModule.class);
      if (tooltips != null && tooltips.showsDurability()) {
         int max = stack.getMaxDamage();
         lines.accept(Component.translatable("item.durability", max - stack.getDamageValue(), max)
            .withStyle(ChatFormatting.GRAY));
      }
   }

   @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
   private void argusclient$hideContentsText(
      DataComponentType<?> type, net.minecraft.world.item.component.TooltipProvider.Getter getter,
      Item.TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag, CallbackInfo callback
   ) {
      if (type != DataComponents.CONTAINER) return;
      TooltipsModule tooltips = ClientContext.getModule(TooltipsModule.class);
      if (tooltips != null && tooltips.replacesContainerText((ItemStack)(Object)this)) {
         callback.cancel();
      }
   }

   @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
   private boolean overrideItemFoil(boolean original) {
      GlintRegistry.GlintMode glintMode = GlintRegistry.getOverride(this.getItem());
      return glintMode != null ? glintMode == GlintRegistry.GlintMode.ALWAYS : original;
   }
}
