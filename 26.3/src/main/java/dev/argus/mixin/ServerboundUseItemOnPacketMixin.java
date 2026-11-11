package dev.argus.mixin;

import dev.argus.client.utils.network.ImportantPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundUseItemOnPacket.class)
public class ServerboundUseItemOnPacketMixin implements ImportantPacket {
   @Unique
   private boolean important;

   @Override
   public void argusclient$markImportant() {
      this.important = true;
   }

   @Override
   public boolean argusclient$isImportant() {
      return this.important;
   }
}
