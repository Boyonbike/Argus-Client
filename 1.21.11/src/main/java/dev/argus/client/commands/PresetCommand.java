package dev.argus.client.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.config.LegacyBranding;
import dev.argus.client.config.ModulePresetCodec;
import dev.argus.client.config.PresetData;
import dev.argus.client.config.PresetUndoAction;
import dev.argus.client.config.PresetUndoState;
import dev.argus.client.gui.screens.PresetScreen;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.PresetRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.text.ReverseStringFormatter;
import dev.argus.client.settings.options.PresetCategory;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

public final class PresetCommand extends Command {
   public static void reportPresetBinds() {
      MutableComponent message = Component.empty();
      message.append("Preset binds: ");
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

      for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
         Keybind keybind = module.getKeybind();
         if (!keybind.isUnbound()) {
            message.append("\n");
            String moduleName = module.getName();
            message.append(moduleName);
            message.append(" - ");
            String keybindName = keybind.getDisplayName();
            message.append(keybindName);
            message.append(" ");
            Keybind.Mode keybindMode = keybind.mode();
            String modeName = keybindMode.getName();
            message.append(ChatUtils.bracketedText(modeName));
         }
      }

      ChatUtils.printChat(message, -2);
   }

   public PresetCommand() {
      super("preset");
      String[] aliases = new String[]{"config", "cfg"};
      this.setAliases(aliases);
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      registerPresetCommands(this, root);
   }

   private static void registerPresetCommands(PresetCommand command, LiteralArgumentBuilder root) {
      RequiredArgumentBuilder categoryArgument = Command.argument("category", new PresetCategoryArgument());
      LiteralArgumentBuilder operationLiteral = Command.literal("save");
      ArgumentBuilder saveNameBranch = Command.argument("preset", FancyStringArgument.fancyString())
         .suggests(command::suggestPresetNames)
         .executes(PresetCommand::savePreset);
      ArgumentBuilder saveTree = operationLiteral.then(saveNameBranch);
      categoryArgument = (RequiredArgumentBuilder)categoryArgument.then(saveTree);
      operationLiteral = Command.literal("delete");
      ArgumentBuilder deletePresetBranch = Command.argument("preset", new PresetArgumentType()).executes(PresetCommand::deletePreset);
      ArgumentBuilder deleteTree = operationLiteral.then(deletePresetBranch);
      categoryArgument = (RequiredArgumentBuilder)categoryArgument.then(deleteTree);
      operationLiteral = Command.literal("rename");
      RequiredArgumentBuilder operationArgument = Command.argument("preset", new PresetArgumentType());
      ArgumentBuilder renameTargetBranch = Command.argument("name", StringArgumentType.string()).executes(PresetCommand::renamePreset);
      ArgumentBuilder renamePresetBranch = operationArgument.then(renameTargetBranch);
      ArgumentBuilder renameTree = operationLiteral.then(renamePresetBranch);
      categoryArgument = (RequiredArgumentBuilder)categoryArgument.then(renameTree);
      operationLiteral = Command.literal("load");
      ArgumentBuilder loadPresetBranch = Command.argument("preset", new PresetArgumentType()).executes(context -> {
         ConfigurationManager configurationManager = ClientContext.configurationManager;
         return loadPreset(command, context, configurationManager);
      });
      ArgumentBuilder loadTree = operationLiteral.then(loadPresetBranch);
      categoryArgument = (RequiredArgumentBuilder)categoryArgument.then(loadTree);
      operationLiteral = Command.literal("specific");
      operationArgument = Command.argument("preset", new PresetArgumentType());
      ArgumentBuilder moduleCategoryBranch = Command.argument("module_category", new EnumArgumentType(ModuleCategory.class, "Category")).executes(context -> {
         ConfigurationManager configurationManager = ClientContext.configurationManager;
         return loadCategoryPreset(command, context, configurationManager);
      });
      operationArgument = (RequiredArgumentBuilder)operationArgument.then(moduleCategoryBranch);
      ArgumentBuilder modulesBranch = Command.argument("modules", new ArrayArgumentType(new ModuleArgumentType(), Module.class)).executes(context -> {
         ConfigurationManager configurationManager = ClientContext.configurationManager;
         return loadSelectedModulePresets(command, context, configurationManager);
      });
      ArgumentBuilder specificTree = operationArgument.then(modulesBranch);
      ArgumentBuilder categoryTree = operationLiteral.then(specificTree);
      categoryArgument = (RequiredArgumentBuilder)categoryArgument.then(categoryTree);
      ArgumentBuilder listBranch = Command.literal("list").executes(PresetCommand::listPresets);
      ArgumentBuilder listTree = categoryArgument.then(listBranch);
      LiteralArgumentBuilder rootWithCategories = (LiteralArgumentBuilder)root.then(listTree);
      LiteralArgumentBuilder exportLiteralBuilder = Command.literal("export");
      ArgumentBuilder exportModuleBranch = Command.argument("module", new ModuleArgumentType()).executes(PresetCommand::exportModulePreset);
      ArgumentBuilder exportTree = exportLiteralBuilder.then(exportModuleBranch);
      rootWithCategories = (LiteralArgumentBuilder)rootWithCategories.then(exportTree);
      ArgumentBuilder importBranch = Command.literal("import").executes(context -> {
         try {
            KeyboardHandler keyboard = ClientAccess.argusclientMinecraft.keyboardHandler;
            String clipboardConfig = keyboard.getClipboard();
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            importModulePreset(configurationManager, clipboardConfig);
         } catch (Exception importFailure) {
            reportImportFailure(importFailure);
         }

         return 1;
      });
      rootWithCategories.then(importBranch);
      ArgumentBuilder restoreBranch = Command.literal("restore").executes(context -> {
         ConfigurationManager configurationManager = ClientContext.configurationManager;
         return restorePreset(command, configurationManager);
      });
      root.then(restoreBranch);
      root.then(Command.literal("undo").executes(restoreBranch.getCommand()));
      root.executes(context -> {
         command.scheduleRotationTask(() -> {
            Minecraft minecraft = ClientAccess.argusclientMinecraft;
            PresetScreen screen = new PresetScreen();
            minecraft.setScreen(screen);
         });
         return 1;
      });
   }

   private static int savePreset(CommandContext context) throws CommandSyntaxException {
      String presetName = (String)context.getArgument("preset", String.class);
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      PresetUndoAction undoAction = PresetUndoAction.beforeSave(presetManager, presetName);

      try {
         presetManager.savePreset(presetName);
         ClientContext.configurationManager.getPresetUndoState().push(undoAction);
      } catch (IOException saveFailure) {
         ChatUtils.printChat(Component.literal("Couldn't save preset " + presetName + ". " + saveFailure.getMessage()), ChatUtils.createSignature(-1));
         return 0;
      }

      String savedMessage = "Preset " + presetName + " has been saved.";
      MutableComponent savedMessageComponent = Component.literal(savedMessage);
      ChatUtils.printChat(savedMessageComponent, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int deletePreset(CommandContext context) throws CommandSyntaxException {
      PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      String presetName = preset.getName();
      PresetUndoAction undoAction = PresetUndoAction.beforeDelete(presetManager, presetName);
      if (presetManager.deletePreset(presetName)) {
         ClientContext.configurationManager.getPresetUndoState().push(undoAction);
      }

      String deletedMessage = "Preset " + preset.getName() + " has been deleted.";
      MutableComponent deletedMessageComponent = Component.literal(deletedMessage);
      ChatUtils.printChat(deletedMessageComponent, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int renamePreset(CommandContext context) throws CommandSyntaxException {
      PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      String oldName = preset.getName();
      String newName = (String)context.getArgument("name", String.class);
      PresetUndoAction undoAction = PresetUndoAction.beforeRename(presetManager, oldName, newName);
      if (presetManager.renamePreset(oldName, newName)) {
         ClientContext.configurationManager.getPresetUndoState().push(undoAction);
      }

      String reportedOldName = preset.getName();
      String renamedMessage = "The name of " + reportedOldName + " has been set to " + (String)context.getArgument("name", String.class) + ".";
      MutableComponent renamedMessageComponent = Component.literal(renamedMessage);
      ChatUtils.printChat(renamedMessageComponent, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int loadPreset(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws CommandSyntaxException {
      PresetCategory category = (PresetCategory)context.getArgument("category", PresetCategory.class);
      PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
      presetManager.loadPresets();
      String presetName = preset.getName();
      if (!presetManager.applyPreset(presetName)) {
         ChatUtils.printChat(Component.literal("Couldn't load preset " + presetName + "."), ChatUtils.createSignature(-1));
         return 0;
      }
      configurationManager.getPresetUndoState().push(undoAction);

      String loadedMessage = "Preset " + preset.getName() + " has been loaded.";
      MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
      ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
      if (category == PresetCategory.ALL) {
         owner.scheduleRotationTask(PresetCommand::reportPresetBinds);
      }

      return 1;
   }

   private static int loadCategoryPreset(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws CommandSyntaxException {
      PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      ModuleCategory moduleCategory = (ModuleCategory)context.getArgument("module_category", ModuleCategory.class);
      presetManager.loadPresets();
      PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
      owner.loadMatchingModules(preset, candidate -> candidate.getCategory() == moduleCategory);
      configurationManager.getPresetUndoState().push(undoAction);
      String presetName = preset.getName();
      String loadedMessage = "Preset " + presetName + " for " + moduleCategory.getName() + " has been loaded.";
      MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
      ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int loadSelectedModulePresets(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws CommandSyntaxException {
      PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      Module[] modules = (Module[])context.getArgument("modules", Module[].class);
      Stream<Class<?>> moduleClassesStream = Arrays.stream(modules).map(Object::getClass);
      Set<Class<?>> moduleClasses = moduleClassesStream.collect(Collectors.toSet());
      presetManager.loadPresets();
      Predicate<Module> matchesSelectedModule = candidate -> moduleClasses.contains(candidate.getClass());
      PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
      owner.loadMatchingModules(preset, matchesSelectedModule);
      configurationManager.getPresetUndoState().push(undoAction);
      String presetName = preset.getName();
      String loadedMessage = "Preset " + presetName + " for " + owner.joinSelectedModuleNames(matchesSelectedModule) + " has been loaded.";
      MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
      ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int listPresets(CommandContext context) throws CommandSyntaxException {
      PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
      MutableComponent listMessage = Component.literal("Preset list: ");
      listMessage = listMessage.append(ComponentUtils.formatList(presetManager.getRegistry(), preset -> {
         String presetName = preset.getName();
         return Component.literal(presetName);
      }));
      listMessage = listMessage.append(".");
      ChatUtils.printChat(listMessage, ChatUtils.createSignature(-1));
      return 1;
   }

   private static int exportModulePreset(CommandContext context) throws CommandSyntaxException {
      Module module = (Module)context.getArgument("module", Module.class);
      ConfigurationManager configurationManager = ClientContext.configurationManager;
      ModulePresetCodec moduleConfigCodec = configurationManager.getModulePresetCodec();
      KeyboardHandler keyboard = ClientAccess.argusclientMinecraft.keyboardHandler;
      String encodedModuleConfig = moduleConfigCodec.encode(module);
      keyboard.setClipboard(encodedModuleConfig);
      MutableComponent messagePrefix = Component.literal("Successfully copied ");
      String moduleName = module.getName();
      MutableComponent moduleNameComponent = Component.literal(moduleName);
      ChatFormatting nameColor = ChatFormatting.GRAY;
      MutableComponent messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(nameColor));
      MutableComponent exportedMessage = messageWithModuleName.append(" config to your clipboard");
      ChatUtils.printChat(exportedMessage, ChatUtils.createSignature(-1));
      return 1;
   }

   private static void importModulePreset(ConfigurationManager configurationManager, String clipboardConfig) {
      ModulePresetCodec moduleConfigCodec = configurationManager.getModulePresetCodec();
      ModulePresetCodec.DecodedPreset decodedConfig = moduleConfigCodec.decode(clipboardConfig);
      Module module = decodedConfig.module();
      JsonObject moduleConfig = decodedConfig.json();
      module.fromJson(moduleConfig);
      MutableComponent messagePrefix = Component.literal("Successfully loaded ");
      String moduleName = module.getName();
      MutableComponent moduleNameComponent = Component.literal(moduleName);
      ChatFormatting nameColor = ChatFormatting.GRAY;
      MutableComponent messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(nameColor));
      MutableComponent importedMessage = messageWithModuleName.append(" config from your clipboard");
      ChatUtils.printChat(importedMessage, ChatUtils.createSignature(-1));
   }

   private static void reportImportFailure(Exception importFailure) {
      MutableComponent errorMessage = Component.literal("Failed to load module config");
      StackTraceElement failureLocation = importFailure.getStackTrace()[0];
      String failureLocationText = failureLocation.toString();
      if (!failureLocationText.contains("Base64") && !(importFailure instanceof IllegalArgumentException)) {
         errorMessage = errorMessage.append(" due to an unknown reason");
      } else {
         errorMessage = errorMessage.append(" due to invalid input");
      }

      ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.MID);
      importFailure.printStackTrace();
   }

   private static int restorePreset(PresetCommand owner, ConfigurationManager configurationManager) throws CommandSyntaxException {
      PresetUndoState restoreManager = configurationManager.getPresetUndoState();

      PresetUndoAction action;
      try {
         action = restoreManager.undo();
      } catch (IOException undoFailure) {
         undoFailure.printStackTrace();
         ChatUtils.printChat(Component.literal("Failed to undo preset change."), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
         return 1;
      }

      if (action == null) {
         ChatUtils.printChat(Component.literal("Nothing to undo."), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
         return 1;
      } else {
         owner.scheduleRotationTask(() -> ChatUtils.printChat(Component.literal(formatUndoAction(action)), ChatUtils.DEFAULT_SIGNATURE));
         return 1;
      }
   }

   private static String formatUndoAction(PresetUndoAction action) {
      return switch (action) {
         case PresetUndoAction.Load load -> new ReverseStringFormatter().prepend(load.name()).format("Restored config before loading \u0001.");
         case PresetUndoAction.Save save -> new ReverseStringFormatter().prepend(save.name()).format("Restored state before saving \u0001.");
         case PresetUndoAction.Delete delete -> new ReverseStringFormatter().prepend(delete.preset().getName()).format("Restored deleted preset \u0001.");
         case PresetUndoAction.Rename rename -> new ReverseStringFormatter()
            .prepend(rename.newName())
            .prepend(rename.oldName())
            .format("Restored name \u0001 for \u0001.");
         default -> throw new MatchException(null, null);
      };
   }

   private String joinSelectedModuleNames(Predicate<Module> matchesModule) {
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
      Stream<String> moduleNames = moduleRegistry.getRegistry().stream().filter(matchesModule).map(NamedFeature::getName);
      return moduleNames.collect(Collectors.joining(", "));
   }

   private void loadMatchingModules(PresetData preset, Predicate<Module> matchesModule) {
      ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

      for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
         if (!(module instanceof HudWidgetModule) && matchesModule.test(module)) {
            try {
               JsonObject presetContents = preset.contents();
               JsonObject contentsObject = presetContents.getAsJsonObject();
               String moduleConfigName = module.getConfigName();
               JsonElement moduleConfig = LegacyBranding.findModuleConfig(contentsObject, moduleConfigName);
               module.fromJson(moduleConfig);
            } catch (Exception var10) {
            }
         }
      }
   }

   private CompletableFuture suggestPresetNames(CommandContext context, SuggestionsBuilder builder) {
      return new PresetArgumentType().listSuggestions(context, builder);
   }
}
