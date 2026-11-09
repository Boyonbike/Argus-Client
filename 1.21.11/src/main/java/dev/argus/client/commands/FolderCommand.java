package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.settings.options.PresetCategory;
import dev.argus.client.utils.chat.ChatUtils;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class FolderCommand extends Command {
   public FolderCommand() {
      super("folder");
      String[] aliases = new String[]{"openfolder"};
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      attachDirectory(builder, builder, ConfigurationManager.ROOT_DIRECTORY);
      attachDirectory(builder, Command.literal("spammer"), ConfigurationManager.SPAMMER_DIRECTORY);
      attachDirectory(builder, Command.literal("killsay"), ConfigurationManager.KILLSAY_DIRECTORY);
      attachDirectory(builder, Command.literal("textures"), ConfigurationManager.TEXTURES_DIRECTORY);
      attachDirectory(builder, Command.literal("sounds"), ConfigurationManager.SOUNDS_DIRECTORY);
      attachDirectory(builder, Command.literal("shaders"), ConfigurationManager.SHADERS_DIRECTORY);
      String[] presetAliases = new String[]{"configs", "presets"};
      RequiredArgumentBuilder presetsBuilder = Command.aliasedLiteral(presetAliases);

      for (PresetCategory category : PresetCategory.values()) {
         String categoryName = category.getName();
         Locale locale = Locale.ROOT;
         String literalName = categoryName.toLowerCase(locale);
         LiteralArgumentBuilder categoryBuilder = Command.literal(literalName);
         attachDirectory(presetsBuilder, categoryBuilder, category.getDirectory());
      }

      attachDirectory(builder, presetsBuilder, ConfigurationManager.PRESETS_DIRECTORY);
   }

   private static int openDirectory(Path directory) {
      if (GraphicsEnvironment.isHeadless()) {
         Util.getPlatform().openPath(directory);
         return 1;
      }

      try {
         Desktop desktop = Desktop.getDesktop();
         File file = directory.toFile();
         desktop.open(file);
      } catch (IOException failure) {
         failure.printStackTrace();
         ChatUtils.printChat(
            Component.literal("Failed to open folder ").append(ChatUtils.watermarkText(directory.getFileName().toString())).append("."),
            ChatUtils.DEFAULT_SIGNATURE,
            ChatUtils.MessagePriority.MID
         );
      }

      return 1;
   }

   private static void attachDirectory(ArgumentBuilder parent, ArgumentBuilder child, Path directory) {
      com.mojang.brigadier.Command<Object> executor = context -> openDirectory(directory);
      child.executes(executor);
      if (parent != child) {
         parent.then(child);
      }
   }
}
