package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.InputPressEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Shift + right-click a block to copy its position to the clipboard as "x y z".
 */
public final class CopyBlockPosModule extends Module {
   private static final int RIGHT_MOUSE = 1;
   private static final int LEFT_SHIFT = 340;
   private static final int RIGHT_SHIFT = 344;

   private final Setting<Boolean> blockClickSetting = this.add(new BooleanSetting("BlockClick", true));
   private final Setting<Boolean> dimensionSetting = this.add(new BooleanSetting("Dimension", false));
   private final Setting<Boolean> messageSetting = this.add(new BooleanSetting("Message", true));

   public CopyBlockPosModule() {
      super("CopyBlockPos", "Shift + right-click a block to copy its coordinates.", ModuleCategory.MISC, "CopyCoords");
   }

   @EventHandler
   private void onInputPress(InputPressEvent event) {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (!event.isMouse() || event.getCode() != RIGHT_MOUSE || mc.screen != null || mc.player == null) {
         return;
      }

      if (!KeyUtils.isPhysicalKeyDown(LEFT_SHIFT) && !KeyUtils.isPhysicalKeyDown(RIGHT_SHIFT)) {
         return;
      }

      HitResult hit = mc.hitResult;
      if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
         return;
      }

      BlockPos pos = blockHit.getBlockPos();
      String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
      if (this.dimensionSetting.getValue()) {
         text += " " + WorldUtils.getDimension().getDisplayName();
      }

      mc.keyboardHandler.setClipboard(text);
      if (this.messageSetting.getValue()) {
         ChatUtils.printChat(Component.literal("Copied " + text), ChatUtils.moduleSignature(this));
      }

      // Stops the click from also placing a block or opening a container.
      if (this.blockClickSetting.getValue()) {
         event.cancel();
      }
   }
}
