package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class PrefixCommand extends Command {
   public PrefixCommand() {
      super("prefix");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      String argumentName = "prefix";
      FancyStringArgument prefixType = FancyStringArgument.fancyString();
      RequiredArgumentBuilder prefixArgument = Command.argument(argumentName, prefixType);
      com.mojang.brigadier.Command<Object> setPrefix = context -> {
         Class<String> prefixClass = String.class;
         String prefix = (String)context.getArgument("prefix", prefixClass);
         CommandRegistry.setPrefix(prefix);
         String messagePrefix = "Prefix set to ";
         MutableComponent message = Component.literal(messagePrefix);
         String currentPrefix = CommandRegistry.getPrefix();
         MutableComponent formattedPrefix = this.formatText(currentPrefix);
         MutableComponent completeMessage = message.append(formattedPrefix);
         ChatUtils.printChat(completeMessage, -1);
         return 1;
      };
      ArgumentBuilder prefixSubcommand = prefixArgument.executes(setPrefix);
      LiteralArgumentBuilder root = (LiteralArgumentBuilder)builder.then(prefixSubcommand);
      com.mojang.brigadier.Command<Object> showPrefix = context -> {
         String messagePrefix = "The current prefix is ";
         MutableComponent message = Component.literal(messagePrefix);
         String currentPrefix = CommandRegistry.getPrefix();
         MutableComponent formattedPrefix = this.formatText(currentPrefix);
         MutableComponent completeMessage = message.append(formattedPrefix);
         ChatUtils.printChat(completeMessage, -1);
         return 1;
      };
      root.executes(showPrefix);
   }
}
