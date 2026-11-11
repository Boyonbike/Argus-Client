package dev.argus.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
   @Accessor("shopItem")
   void argusclient$setSelected(int var1);

   @Invoker("postButtonClick")
   void argusclient$syncRecipe();
}
