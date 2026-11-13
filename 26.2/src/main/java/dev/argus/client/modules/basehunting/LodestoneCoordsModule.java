package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Reads the coordinates a lodestone compass in your hand points to (a compass from a chest or a kill often points to
 * someone's base), prints them and turns itself off. Ported from Trouser Streak's LodestoneCoords.
 */
public class LodestoneCoordsModule extends Module {
   public LodestoneCoordsModule() {
      super("LodestoneCoords", "Shows the coordinates a lodestone compass in your hand points to.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         String result = decode(player.getMainHandItem());
         if (result == null) {
            result = decode(player.getOffhandItem());
         }

         this.say(result == null ? "Hold a lodestone compass in either hand." : result);
      }

      this.disable();
   }

   private static String decode(ItemStack stack) {
      LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
      if (tracker == null) {
         return null;
      }

      Optional<GlobalPos> target = tracker.target();
      if (target.isEmpty()) {
         return "This compass's lodestone is gone, so it has no coordinates.";
      }

      BlockPos position = target.get().pos();
      return "Lodestone at " + position.getX() + " " + position.getY() + " " + position.getZ() + " in " + target.get().dimension().identifier().getPath();
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this));
   }
}
