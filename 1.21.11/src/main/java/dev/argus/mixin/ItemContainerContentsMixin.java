package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.TooltipsModule;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the vanilla "Item x64 / and N more..." lines when Tooltips already draws the contents as a grid. */
@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
   @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
   private void argusclient$hideContentsText(
      Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag, DataComponentGetter components, CallbackInfo callback
   ) {
      TooltipsModule tooltips = ClientContext.getModule(TooltipsModule.class);
      if (tooltips != null && tooltips.replacesContainerText()) {
         callback.cancel();
      }
   }
}
