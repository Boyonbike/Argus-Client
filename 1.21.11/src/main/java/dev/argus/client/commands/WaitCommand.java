package dev.argus.client.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.CommandExecuteEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.TaskScheduler;
import java.util.ArrayList;
import java.util.List;

public final class WaitCommand extends Command {
   private final List<String> pendingCommands = new ArrayList<>();
   private boolean hook;
   private int delayTicks;

   public WaitCommand() {
      super("wait");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      IntegerArgumentType tickType = IntegerArgumentType.integer(0);
      RequiredArgumentBuilder ticksArgument = Command.argument("ticks", tickType);
      com.mojang.brigadier.Command<Object> startWaiting = context -> {
         Class<Integer> integerClass = Integer.class;
         Integer ticks = (Integer)context.getArgument("ticks", integerClass);
         this.delayTicks = ticks;
         this.hook = true;
         return 1;
      };
      ArgumentBuilder waitingArgument = ticksArgument.executes(startWaiting);
      builder.then(waitingArgument);
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (this.hook) {
         this.hook = false;
         ArrayList<String> commands = new ArrayList<>(this.pendingCommands);
         this.pendingCommands.clear();
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable executeCommands = () -> {
            for (String command : commands) {
               CommandRegistry.executeCommand(command);
            }
         };
         int ticks = this.delayTicks;
         scheduler.scheduleRotationTask(executeCommands, ticks);
      }
   }

   @EventHandler
   private void onCommandExecute(CommandExecuteEvent event) {
      if (this.hook) {
         List<String> commands = this.pendingCommands;
         String command = event.getCommand();
         commands.add(command);
         event.cancel();
      }
   }
}
