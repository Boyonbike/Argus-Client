package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.SwingModule;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
   private static final SwingModule swing = ClientContext.getModule(SwingModule.class);
   @Shadow
   private ItemStack mainHandItem;
   @Shadow
   private float mainHandHeight;
   @Shadow
   private ItemStack offHandItem;
   @Shadow
   private float offHandHeight;

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 2))
   private float overrideMainHandEquipStep(float originalStep) {
      if (swing.isToggled() && swing.typeSetting.getValue() != SwingModule.TypeChoice.VANILLA) {
         boolean legacySwing = swing.typeSetting.getValue() == SwingModule.TypeChoice.ONE_EIGHT;
         float attackStrength = ClientAccess.argusclientMinecraft.player.getAttackStrengthScale(1.0F);
         if (legacySwing) {
            attackStrength = 1.0F;
         }

         boolean itemChanged = !ItemStack.isSameItemSameComponents(this.mainHandItem, ClientAccess.argusclientMinecraft.player.getMainHandItem());
         float minimumStep = legacySwing ? -0.6F : -0.4F;
         return Mth.clamp((itemChanged ? 0.0F : attackStrength * attackStrength * attackStrength) - this.mainHandHeight, minimumStep, 0.4F);
      } else {
         return originalStep;
      }
   }

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3))
   private float overrideOffHandEquipStep(float originalStep) {
      if (swing.isToggled() && swing.typeSetting.getValue() != SwingModule.TypeChoice.VANILLA) {
         boolean itemChanged = !ItemStack.isSameItemSameComponents(this.offHandItem, ClientAccess.argusclientMinecraft.player.getOffhandItem());
         if (swing.typeSetting.getValue() == SwingModule.TypeChoice.ONE_TWELVE && swing.shakeSetting.getValue() && swing.consumeShakeTick()) {
            itemChanged = true;
         }

         return Mth.clamp((itemChanged ? 0 : 1) - this.offHandHeight, -0.4F, 0.4F);
      } else {
         return originalStep;
      }
   }
}
