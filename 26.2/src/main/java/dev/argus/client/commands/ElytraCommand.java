package dev.argus.client.commands;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class ElytraCommand extends Command {
   public ElytraCommand() {
      super("elytra");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      FancyStringArgument argumentType = FancyStringArgument.fancyString();
      RequiredArgumentBuilder waypointArgument = Command.argument("name", argumentType);
      SuggestionProvider<Object> suggestions = (context, suggestionsBuilder) -> {
         String waypointCategory = WaypointsCommand.getCurrentServerKey();
         return WaypointsCommand.suggestNamesForServer(context, suggestionsBuilder, waypointCategory);
      };
      waypointArgument = waypointArgument.suggests(suggestions);
      com.mojang.brigadier.Command<Object> execution = context -> {
         if (!ClientContext.baritoneBridge.isAvailable()) {
            ChatUtils.printChat(Component.literal("Baritone is not installed."), ChatUtils.DEFAULT_SIGNATURE);
            return 0;
         }

         Vec3 waypoint = WaypointsCommand.resolveWaypointPosition(context);
         if (waypoint == null) {
            ChatUtils.printChat(
               Component.literal("Waypoint ").append(this.formatText((String)context.getArgument("name", String.class))).append(" not found"),
               ChatUtils.DEFAULT_SIGNATURE,
               ChatUtils.MessagePriority.LOW
            );
            return 0;
         }

         IBaritoneProvider provider = BaritoneAPI.getProvider();
         IBaritone baritone = provider.getPrimaryBaritone();
         GoalXZ goal = new GoalXZ((int)waypoint.x(), (int)waypoint.z());
         ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
         customGoalProcess.setGoal(goal);
         IElytraProcess elytraProcess = baritone.getElytraProcess();
         elytraProcess.pathTo(goal);
         ChatUtils.printChat(
            Component.literal("Flying to waypoint ").append(this.formatText((String)context.getArgument("name", String.class))), ChatUtils.DEFAULT_SIGNATURE
         );
         LocalPlayer playerForFlyingCheck = ClientAccess.argusclientMinecraft.player;
         if (playerForFlyingCheck.isFallFlying()) {
            return 1;
         }

         LocalPlayer playerForGroundCheck = ClientAccess.argusclientMinecraft.player;
         if (playerForGroundCheck.onGround()) {
            LocalPlayer playerBeforePush = ClientAccess.argusclientMinecraft.player;
            playerBeforePush.push(0.0, 0.42, 0.0);
         }

         Runnable fallFlyingTask = () -> {
            PacketSender.sendStartFallFlying();
            LocalPlayer playerToStartFlying = ClientAccess.argusclientMinecraft.player;
            playerToStartFlying.startFallFlying();
         };
         this.scheduleRotationTask(fallFlyingTask);
         return 1;
      };
      ArgumentBuilder executableWaypointArgument = waypointArgument.executes(execution);
      builder.then(executableWaypointArgument);
   }
}
