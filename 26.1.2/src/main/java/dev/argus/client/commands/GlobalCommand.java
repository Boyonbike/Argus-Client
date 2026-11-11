package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;

public final class GlobalCommand extends Command {
   public GlobalCommand() {
      super("global");
      String[] aliases = new String[]{"syncall"};
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder colorCommand = Command.literal("color");
      ArgumentType colorType = BoolArgumentType.bool();
      RequiredArgumentBuilder colorValue = Command.argument("value", colorType);
      com.mojang.brigadier.Command<Object> colorAction = context -> {
         boolean synced = (Boolean)context.getArgument("value", Boolean.class);
         BiConsumer<Module, Setting<?>> syncColor = (module, setting) -> {
            if (!(module instanceof ColorsModule) && !(module instanceof UIModule) && setting instanceof ColorSetting color) {
               color.setSynced(synced);
            }
         };
         this.forEachSetting(syncColor);
         ChatUtils.printChat(Component.literal("Color sync for all modules has been " + (synced ? "enabled." : "disabled.")), ChatUtils.DEFAULT_SIGNATURE);
         return 1;
      };
      ArgumentBuilder colorArguments = colorValue.executes(colorAction);
      ArgumentBuilder colorSubcommand = colorCommand.then(colorArguments);
      builder.then(colorSubcommand);
      LiteralArgumentBuilder lineWidthCommand = Command.literal("linewidth");
      ArgumentType lineWidthType = FloatArgumentType.floatArg(0.1F, 5.0F);
      RequiredArgumentBuilder lineWidthValue = Command.argument("value", lineWidthType);
      com.mojang.brigadier.Command<Object> lineWidthAction = context -> {
         Float argument = (Float)context.getArgument("value", Float.class);
         float lineWidth = argument;
         BiConsumer<Module, Setting<?>> setLineWidth = (module, setting) -> {
            if (setting.getValue() instanceof Float) {
               String name = setting.getName();
               if (name.equalsIgnoreCase("LineWidth")) {
                  NumberSetting<?> number = (NumberSetting<?>)setting;
                  Float value = lineWidth;
                  number.setNumber(value);
               }
            }
         };
         this.forEachSetting(setLineWidth);
         ChatUtils.printChat(
            Component.literal("LineWidth for all modules has been set to ").append(this.formatText(String.valueOf(lineWidth))).append("."),
            ChatUtils.DEFAULT_SIGNATURE
         );
         return 1;
      };
      ArgumentBuilder lineWidthArguments = lineWidthValue.executes(lineWidthAction);
      ArgumentBuilder lineWidthSubcommand = lineWidthCommand.then(lineWidthArguments);
      builder.then(lineWidthSubcommand);
   }

   private void forEachSetting(BiConsumer<Module, Setting<?>> action) {
      ModuleRegistry registry = ClientContext.moduleRegistry;

      for (Module module : (List<Module>)registry.getRegistry()) {
         for (Setting<?> setting : module.getSettings()) {
            action.accept(module, setting);
         }
      }
   }
}
