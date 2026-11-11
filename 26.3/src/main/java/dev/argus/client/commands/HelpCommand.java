package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class HelpCommand extends Command {
   public HelpCommand() {
      super("help");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      File clientDirectory = new File("argusclient");
      if (!clientDirectory.exists()) {
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable welcomeAction = () -> {
            MutableComponent message = Component.literal("Welcome to Argus Client!");
            MessageSignature welcomeSignature = ChatUtils.createSignature(-1001);
            ChatUtils.printChat(message, welcomeSignature);
            message = Component.literal("The UI bind is ");
            UIModule uiModule = UIModule.INSTANCE;
            Keybind uiKeybind = uiModule.getKeybind();
            String keybindName = uiKeybind.getDisplayName();
            HelpCommand commandForKeybind = this;
            MutableComponent formattedKeybind = commandForKeybind.formatText(keybindName);
            message = message.append(formattedKeybind);
            MessageSignature uiSignature = ChatUtils.createSignature(-1002);
            ChatUtils.printChat(message, uiSignature);
            message = Component.literal("The command prefix is ");
            String commandPrefix = CommandRegistry.getPrefix();
            HelpCommand commandForPrefix = this;
            MutableComponent formattedPrefix = commandForPrefix.formatText(commandPrefix);
            message = message.append(formattedPrefix);
            MessageSignature prefixSignature = ChatUtils.createSignature(-1003);
            ChatUtils.printChat(message, prefixSignature);
         };
         scheduler.scheduleRotationTask(welcomeAction, 0);
      }

      com.mojang.brigadier.Command<Object> helpAction = context -> {
         ArrayList<String> commandNames = new ArrayList<>();
         CommandRegistry registry = ClientContext.commandRegistry;
         List<Command> commands = (List<Command>)registry.getRegistry();
         commands.forEach(command -> commandNames.add(command.getName()));
         MutableComponent message = Component.empty();
         Object[] countArguments = new Object[1];
         int count = commandNames.size();
         countArguments[0] = count;
         String heading = "Commands (%d): ".formatted(countArguments);
         message.append(heading);
         String formattedNames = String.join(", ", commandNames) + ".";
         Component namesComponent = Component.nullToEmpty(formattedNames);
         message.append(namesComponent);
         MessageSignature helpSignature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(message, helpSignature);
         return 1;
      };
      root.executes(helpAction);
   }
}
