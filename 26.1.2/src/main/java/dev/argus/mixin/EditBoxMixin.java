package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.utils.inventory.ChestSearchController;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditBox.class)
public class EditBoxMixin {
   @ModifyExpressionValue(
      method = "extractWidgetRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;isBordered()Z", ordinal = 0)
   )
   public boolean argusclient$hideSearchBoxBorder(boolean original) {
      EditBox editBox = (EditBox)(Object)this;
      return editBox == ChestSearchController.searchBox ? false : original;
   }
}
