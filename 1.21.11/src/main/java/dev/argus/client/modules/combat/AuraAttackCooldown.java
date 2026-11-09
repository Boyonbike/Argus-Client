package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.utils.entity.PlayerState;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class AuraAttackCooldown implements ClientAccess {
   private final AuraModule aura;

   public AuraAttackCooldown(AuraModule aura) {
      this.aura = aura;
   }

   public float getAttackStrength(ItemStack stack) {
      float partialTick = 0.5F;
      if (this.aura.tPSSyncSetting.getValue()) {
         ServerStateManager tickManager = ClientContext.serverStateManager;
         partialTick -= 20.0F - tickManager.getTickRate();
      }

      int ticksSinceAttack = ((PlayerState)ClientAccess.argusclientMinecraft.player).argusclient$getLastAttackedTicksServer();
      LocalPlayer attackSpeedPlayer = ClientAccess.argusclientMinecraft.player;
      Holder attackSpeedAttribute = Attributes.ATTACK_SPEED;
      double attackSpeed = attackSpeedPlayer.getAttributeBaseValue(attackSpeedAttribute);
      double effectiveSpeed = attackSpeed;
      DataComponentType<ItemAttributeModifiers> modifierType = DataComponents.ATTRIBUTE_MODIFIERS;
      ItemAttributeModifiers modifiers = stack.get(modifierType);
      Item mace = Items.MACE;
      if (stack.is(mace) && PlayerStateUtils.getTrackedFallDistance() > 1.0F) {
         effectiveSpeed = attackSpeed;
      } else if (modifiers != null) {
         Holder modifierAttribute = Attributes.ATTACK_SPEED;
         List modifierEntries = modifiers.modifiers();
         effectiveSpeed = ItemAttributeUtils.applyModifiers(modifierAttribute, attackSpeed, modifierEntries);
      }

      double attackPeriod = 1.0 / effectiveSpeed;
      float attackTicks = (float)(attackPeriod * 20.0);
      float strength = (ticksSinceAttack + partialTick) / attackTicks;
      return Mth.clamp(strength, 0.0F, 1.0F);
   }

   public float getSlotAttackStrength(InventoryUtils.SwapSlot slot) {
      ItemStack stack;
      if (!slot.isFound()) {
         stack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      } else {
         stack = slot.getStack();
      }

      return this.getAttackStrength(stack);
   }
}
