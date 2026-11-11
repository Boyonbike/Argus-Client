package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.entity.Entity;

public final class ClipCommand extends Command {
   public ClipCommand() {
      super("clip");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder verticalCommand = Command.literal("v");
      ArgumentType verticalType = FloatArgumentType.floatArg();
      RequiredArgumentBuilder verticalValue = Command.argument("value", verticalType);
      com.mojang.brigadier.Command<Object> verticalAction = context -> {
         Float argument = (Float)context.getArgument("value", Float.class);
         float requestedDistance = argument;
         float minimumDistance = -10000.0F;
         float maximumDistance = 10000.0F;
         float distance = Math.clamp(requestedDistance, minimumDistance, maximumDistance);
         this.sendAdditionalMovementPackets(distance, false);
         ChatUtils.printChat(
            Component.literal("Clipped ").append(this.formatText("%.1f".formatted(distance))).append(" blocks vertically."), ChatUtils.DEFAULT_SIGNATURE
         );
         LocalPlayer playerForPassengerCheck = ClientAccess.argusclientMinecraft.player;
         if (playerForPassengerCheck.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.argusclientMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            double x = ClientAccess.argusclientMinecraft.player.getVehicle().getX();
            double y = ClientAccess.argusclientMinecraft.player.getVehicle().getY() + distance;
            double z = ClientAccess.argusclientMinecraft.player.getVehicle().getZ();
            vehicle.setPos(x, y, z);
         } else {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            double x = player.getX();
            double y = ClientAccess.argusclientMinecraft.player.getY() + distance;
            double z = ClientAccess.argusclientMinecraft.player.getZ();
            player.setPos(x, y, z);
            double packetX = ClientAccess.argusclientMinecraft.player.getX();
            double packetY = ClientAccess.argusclientMinecraft.player.getY();
            double packetZ = ClientAccess.argusclientMinecraft.player.getZ();
            PacketSender.sendPosition(packetX, packetY, packetZ, true);
         }

         return 1;
      };
      ArgumentBuilder verticalArguments = verticalValue.executes(verticalAction);
      ArgumentBuilder verticalSubcommand = verticalCommand.then(verticalArguments);
      LiteralArgumentBuilder parent = (LiteralArgumentBuilder)builder.then(verticalSubcommand);
      LiteralArgumentBuilder horizontalCommand = Command.literal("h");
      ArgumentType horizontalType = FloatArgumentType.floatArg();
      RequiredArgumentBuilder horizontalValue = Command.argument("value", horizontalType);
      com.mojang.brigadier.Command<Object> horizontalAction = context -> {
         Float argument = (Float)context.getArgument("value", Float.class);
         float requestedDistance = argument;
         float minimumDistance = -3.0E7F;
         float maximumDistance = 3.0E7F;
         float distance = Math.clamp(requestedDistance, minimumDistance, maximumDistance);
         LocalPlayer playerForCosine = ClientAccess.argusclientMinecraft.player;
         double cosineYaw = playerForCosine.getYRot() + 90.0F;
         double cosine = Math.cos(Math.toRadians(cosineYaw));
         double sineYaw = ClientAccess.argusclientMinecraft.player.getYRot() + 90.0F;
         double sine = Math.sin(Math.toRadians(sineYaw));
         this.sendAdditionalMovementPackets(distance, true);
         ChatUtils.printChat(
            Component.literal("Clipped ").append(this.formatText("%.1f".formatted(distance))).append(" blocks horizontally."), ChatUtils.DEFAULT_SIGNATURE
         );
         if (ClientAccess.argusclientMinecraft.player.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.argusclientMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            double x = ClientAccess.argusclientMinecraft.player.getVehicle().getX() + distance * cosine;
            double y = ClientAccess.argusclientMinecraft.player.getVehicle().getY();
            double z = ClientAccess.argusclientMinecraft.player.getVehicle().getZ() + distance * sine;
            vehicle.setPos(x, y, z);
         } else {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            double x = player.getX() + distance * cosine;
            double y = ClientAccess.argusclientMinecraft.player.getY();
            double z = ClientAccess.argusclientMinecraft.player.getZ() + distance * sine;
            player.setPos(x, y, z);
            double packetX = ClientAccess.argusclientMinecraft.player.getX();
            double packetY = ClientAccess.argusclientMinecraft.player.getY();
            double packetZ = ClientAccess.argusclientMinecraft.player.getZ();
            PacketSender.sendPosition(packetX, packetY, packetZ, true);
         }

         return 1;
      };
      ArgumentBuilder horizontalArguments = horizontalValue.executes(horizontalAction);
      ArgumentBuilder horizontalSubcommand = horizontalCommand.then(horizontalArguments);
      parent.then(horizontalSubcommand);
   }

   private void sendAdditionalMovementPackets(float distance, boolean horizontal) {
      float step;
      if (horizontal) {
         step = 9.0F;
      } else {
         step = 10.0F;
      }

      float steps = distance / step;
      int packetCount = (int)Math.ceil(Math.abs(steps));
      if (packetCount > 20) {
         packetCount = 1;
      }

      for (int packetIndex = 0; packetIndex < packetCount - 1; packetIndex++) {
         LocalPlayer playerForPassengerCheck = ClientAccess.argusclientMinecraft.player;
         if (playerForPassengerCheck.isPassenger()) {
            LocalPlayer playerForVehicle = ClientAccess.argusclientMinecraft.player;
            Entity vehicle = playerForVehicle.getVehicle();
            Packet packet = ServerboundMoveVehiclePacket.fromEntity(vehicle);
            PacketSender.send(packet);
         } else {
            Packet packet = new StatusOnly(true, ClientAccess.argusclientMinecraft.player.horizontalCollision);
            PacketSender.send(packet);
         }
      }
   }
}
