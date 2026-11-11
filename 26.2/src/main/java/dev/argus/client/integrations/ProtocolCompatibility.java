package dev.argus.client.integrations;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.VarIntType;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public final class ProtocolCompatibility {
   private static final ProtocolVersionAccess NATIVE_VERSION = new ProtocolVersionAccess() {
      @Override
      public boolean olderEq_1_20_6() {
         return false;
      }

      @Override
      public boolean olderEq_1_21_4() {
         return false;
      }

      @Override
      public boolean olderEq_1_21_5() {
         return false;
      }
   };
   private static ProtocolVersionAccess targetVersion = NATIVE_VERSION;

   public static ProtocolVersionAccess getTargetVersion() {
      return targetVersion;
   }

   public static void setSneaking(boolean sneaking) {
      if (getTargetVersion().olderEq_1_21_5()) {
         ProtocolCompatibility.LegacyPackets.setSneaking(sneaking);
      } else {
         Input input = Minecraft.getInstance().player.input.keyPresses;
         input = new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), sneaking, input.sprint());
         ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(input);
         PacketSender.send(packet);
      }
   }

   public static void setTargetVersion(ProtocolVersionAccess version) {
      targetVersion = version == null ? NATIVE_VERSION : version;
   }

   private static final class LegacyPackets {
      private static void setSneaking(boolean sneaking) {
         UserConnection connection = ViaFabricPlusImpl.impl().protocolTranslation().userConnection();
         PacketType packetType = ServerboundPackets1_21_5.PLAYER_COMMAND;
         PacketWrapper packet = PacketWrapper.create(packetType, connection);
         VarIntType entityIdType = Types.VAR_INT;
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         int entityId = player.getId();
         Integer boxedEntityId = entityId;
         packet.write(entityIdType, boxedEntityId);
         VarIntType actionType = Types.VAR_INT;
         int action = sneaking ? 0 : 1;
         packet.write(actionType, action);
         VarIntType dataType = Types.VAR_INT;
         byte data = 0;
         packet.write(dataType, Integer.valueOf(data));
         packet.scheduleSendToServer(Protocol1_21_5To1_21_6.class);
      }
   }
}
