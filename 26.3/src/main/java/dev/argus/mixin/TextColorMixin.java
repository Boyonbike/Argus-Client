package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.render.TextColorSupplierAccess;
import java.util.function.Supplier;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextColor.class)
public class TextColorMixin implements TextColorSupplierAccess {
   @Unique
   private Supplier colorSupplier = null;

   // Called for every styled run of text drawn each frame; a return-value modifier avoids a callback allocation.
   @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
   private int applySuppliedTextColor(int original) {
      Supplier supplier = this.colorSupplier;
      return supplier != null ? (Integer)supplier.get() : original;
   }

   @Override
   public Supplier getSupplier() {
      return this.colorSupplier;
   }

   @Override
   public void setSupplier(Supplier supplier) {
      this.colorSupplier = supplier;
   }
}
