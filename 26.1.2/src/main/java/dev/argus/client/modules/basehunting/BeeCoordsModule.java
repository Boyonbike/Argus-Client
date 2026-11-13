package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/**
 * Reads the flowers the bees in a hive or nest in your hand last visited. A hive from a base carries the base's
 * flower coordinates with it. Prints them and turns itself off. Ported from Trouser Streak's BeeCoordExploit.
 */
public class BeeCoordsModule extends Module {
   public BeeCoordsModule() {
      super("BeeCoords", "Shows where the bees in a held beehive or nest last found flowers.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
         if (!stack.is(Items.BEEHIVE) && !stack.is(Items.BEE_NEST)) {
            this.say("Hold a beehive or bee nest in your main hand.");
         } else {
            this.read(stack);
         }
      }

      this.disable();
   }

   private void read(ItemStack stack) {
      Bees bees = stack.get(DataComponents.BEES);
      if (bees == null || bees.bees().isEmpty()) {
         this.say("This " + stack.getHoverName().getString() + " has no bees in it.");
         return;
      }

      List<BeehiveBlockEntity.Occupant> occupants = bees.bees();
      for (int index = 0; index < occupants.size(); index++) {
         CompoundTag data = occupants.get(index).entityData().copyTagWithoutId();
         Optional<int[]> flower = data.getIntArray("flower_pos");
         if (flower.isPresent() && flower.get().length >= 3) {
            int[] position = flower.get();
            this.say("Bee " + (index + 1) + " last visited a flower at " + position[0] + " " + position[1] + " " + position[2]);
         } else {
            this.say("Bee " + (index + 1) + " has no flower saved.");
         }
      }
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.createSignature(-Math.abs(message.hashCode())));
   }
}
