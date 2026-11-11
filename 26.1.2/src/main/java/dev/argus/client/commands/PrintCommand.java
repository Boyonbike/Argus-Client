package dev.argus.client.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class PrintCommand extends Command {
   public PrintCommand() {
      super("print");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      StringArgumentType messageType = StringArgumentType.greedyString();
      RequiredArgumentBuilder messageArgument = Command.argument("message", messageType);
      com.mojang.brigadier.Command<Object> printMessage = context -> {
         Gui gui = ClientAccess.argusclientMinecraft.gui;
         ChatComponent chat = gui.getChat();
         Class<String> messageClass = String.class;
         String message = (String)context.getArgument("message", messageClass);
         Component component = Component.nullToEmpty(message);
         double random = Math.random();
         int signatureId = (int)(random * 10000.0);
         MessageSignature signature = ChatUtils.createSignature(signatureId);
         GuiMessageTag tag = ChatUtils.MESSAGE_TAG;
         chat.addMessage(component, signature, tag);
         return 1;
      };
      ArgumentBuilder messageCommand = messageArgument.executes(printMessage);
      LiteralArgumentBuilder commandWithMessage = (LiteralArgumentBuilder)builder.then(messageCommand);
      com.mojang.brigadier.Command<Object> missingMessage = context -> {
         String message = "Please enter a message to print.";
         MutableComponent component = Component.literal(message);
         MessageSignature signature = ChatUtils.createSignature(-1);
         ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.LOW;
         ChatUtils.printChat(component, signature, priority);
         return 1;
      };
      commandWithMessage.executes(missingMessage);
   }
}
