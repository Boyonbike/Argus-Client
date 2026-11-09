package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.utils.ElapsedTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

public class NoHitDelayModule extends Module {
   private final ElapsedTimer useKeyTimer = new ElapsedTimer();
   private boolean wasUseKeyDown;

   @EventHandler
   private void onClientTick(MinecraftRunTickEvent event) {
      KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
      boolean useKeyDown = useKey.isDown();
      if (useKeyDown && !this.wasUseKeyDown) {
         this.useKeyTimer.reset();
      }

      this.wasUseKeyDown = useKeyDown;
   }

   public boolean hasUseKeyDelayElapsed() {
      ItemStack offhandStack = ClientAccess.argusclientMinecraft.player.getOffhandItem();
      KeyMapping useKey = ClientAccess.argusclientMinecraft.options.keyUse;
      if (!useKey.isDown()) {
         return false;
      }

      Item crossbowItem = Items.CROSSBOW;
      if (offhandStack.is(crossbowItem)) {
         return false;
      }

      int delayMillis = 25;
      DataComponentType<?> foodComponent = DataComponents.FOOD;
      if (offhandStack.has(foodComponent) || offhandStack.getItem() instanceof PotionItem) {
         delayMillis = 50;
      }

      long requiredDelayMillis = delayMillis;
      return this.useKeyTimer.hasElapsed(requiredDelayMillis);
   }

   public NoHitDelayModule() {
      super("NoHitDelay", "Removes the weapon attack delay. \n" + ChatFormatting.RED + "Only works on pre 1.9 servers.", ModuleCategory.COMBAT);
   }
}
