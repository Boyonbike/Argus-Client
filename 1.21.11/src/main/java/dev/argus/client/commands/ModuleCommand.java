package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.NotificationsModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.SetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.collections.NamedRegistryEntry;
import dev.argus.client.utils.collections.RegistryGroupMatcher;
import java.awt.Color;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import org.jetbrains.annotations.NotNull;

public final class ModuleCommand extends Command {
   private static NotificationsModule notifications = ClientContext.getModule(NotificationsModule.class);
   private final Module module;

   @NotNull
   private MutableComponent createModuleTitle() {
      String moduleName = this.module.getName();
      MutableComponent title = Component.literal(moduleName);
      UnaryOperator<Style> titleColorStyle = titleStyle -> {
         Supplier<Integer> titleDynamicColor = () -> {
            Module moduleForState = this.module;
            Integer stateColor;
            if (moduleForState.isToggled()) {
               ChatFormatting enabledColor = ChatFormatting.GREEN;
               stateColor = enabledColor.getColor();
            } else {
               ChatFormatting disabledColor = ChatFormatting.RED;
               stateColor = disabledColor.getColor();
            }

            return stateColor;
         };
         return ChatUtils.withDynamicColor(titleStyle, titleDynamicColor);
      };
      title.withStyle(titleColorStyle);
      UnaryOperator<Style> titleDescriptionStyle = hoverStyle -> {
         HoverEvent descriptionHover = new ShowText(this.moduleDescriptionText());
         return hoverStyle.withHoverEvent(descriptionHover);
      };
      title.withStyle(titleDescriptionStyle);
      UnaryOperator<Style> titleCommandStyle = clickStyle -> {
         ClickEvent toggleClick = new RunCommand(toggleCommandText(this));
         return clickStyle.withClickEvent(toggleClick);
      };
      title.withStyle(titleCommandStyle);
      return title;
   }

   private Component moduleDescriptionText() {
      String description = this.module.getDescription();
      String firstDescriptionLine = description.split("\n")[0];
      return Component.literal(firstDescriptionLine);
   }

   private static String toggleCommandText(ModuleCommand owner) {
      String commandPrefix = CommandRegistry.getPrefix();
      Module moduleForCommand = owner.module;
      String moduleName = moduleForCommand.getName();
      return commandPrefix + "toggle " + moduleName;
   }

   public ModuleCommand(Module module) {
      super(module.getName());
      this.module = module;
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      ArgumentType stateArgumentType = BoolArgumentType.bool();
      RequiredArgumentBuilder stateSuggestionsBuilder = Command.argument("state", stateArgumentType);
      SuggestionProvider<Object> stateSuggestions = (suggestionContext, suggestionBuilder) -> suggestionBuilder.buildFuture();
      ArgumentBuilder stateActionBuilder = stateSuggestionsBuilder.suggests(stateSuggestions);
      com.mojang.brigadier.Command<Object> stateAction = stateContext -> {
         Module moduleForStateChange = this.module;
         Boolean requestedState = (Boolean)stateContext.getArgument("state", Boolean.class);
         boolean stateToSet = requestedState;
         moduleForStateChange.setToggled(stateToSet);
         return 1;
      };
      ArgumentBuilder stateArgument = stateActionBuilder.executes(stateAction);
      LiteralArgumentBuilder rootWithState = (LiteralArgumentBuilder)builder.then(stateArgument);
      ArgumentType settingArgumentType = new ModuleSettingArgumentType(this.module);
      RequiredArgumentBuilder settingArgument = Command.argument("setting", settingArgumentType);
      String[] clearAliases = new String[]{"clear"};
      RequiredArgumentBuilder clearSuggestionsBuilder = Command.aliasedLiteral(clearAliases);
      SuggestionProvider<Object> clearSuggestions = this::suggestClearOperation;
      ArgumentBuilder clearActionBuilder = clearSuggestionsBuilder.suggests(clearSuggestions);
      com.mojang.brigadier.Command<Object> clearAction = clearContext -> {
         Module moduleForLookup = this.module;
         Setting<?> setting = ModuleSettingArgumentType.getSetting(clearContext, moduleForLookup, "setting");
         if (setting instanceof SetSetting<?> setSetting) {
            Set<?> valuesToClear = setSetting.getValue();
            valuesToClear.clear();
            setSetting.runChangeCallback();
            NotificationsModule notificationsForCheck = notifications;
            if (NotificationsModule.shouldNotifyCommands(notificationsForCheck)) {
               Module moduleForName = this.module;
               String moduleName = moduleForName.getName();
               MutableComponent moduleNameText = Component.literal(moduleName);
               MutableComponent spaceText = Component.literal(" ");
               String settingName = setting.getName();
               MutableComponent clearedSettingNameText = spaceText.append(settingName);
               UnaryOperator<Style> clearedSettingNameStyle = grayStyle -> {
                  ChatFormatting grayFormatting = ChatFormatting.GRAY;
                  return grayStyle.applyFormat(grayFormatting);
               };
               Component clearedSettingName = clearedSettingNameText.withStyle(clearedSettingNameStyle);
               MutableComponent settingHeader = moduleNameText.append(clearedSettingName);
               MutableComponent clearedMessage = settingHeader.append(": Cleared");
               Module moduleForSignature = this.module;
               MessageSignature clearedMessageSignature = ChatUtils.moduleSignature(moduleForSignature);
               ChatUtils.MessagePriority clearedMessagePriority = ChatUtils.MessagePriority.LOW;
               ChatUtils.printChat(clearedMessage, clearedMessageSignature, clearedMessagePriority);
            }
         }

         return 1;
      };
      ArgumentBuilder clearSettingBranch = clearActionBuilder.executes(clearAction);
      settingArgument = (RequiredArgumentBuilder)settingArgument.then(clearSettingBranch);
      ArgumentType valueArgumentType = new SettingValueArgument(this.module, "setting");
      ArgumentBuilder valueActionBuilder = Command.argument("value", valueArgumentType);
      com.mojang.brigadier.Command<Object> valueAction = valueContext -> {
         Module moduleForLookup = this.module;
         Setting<?> setting = ModuleSettingArgumentType.getSetting(valueContext, moduleForLookup, "setting");
         String requestedValue = (String)valueContext.getArgument("value", String.class);

         try {
            ChatFormatting changeFormatting = null;
            if (setting instanceof RegistrySetSetting<?> registrySetSetting) {
               Registry registry = registrySetSetting.getRegistry();
               RegistryGroupMatcher registryMatcher = RegistryGroupMatcher.forRegistry(registry);
               Collection matchingEntries = registryMatcher.getGroupEntries(requestedValue);
               if (!matchingEntries.isEmpty()) {
                  Stream matchingEntryStream = matchingEntries.stream();
                  Predicate<NamedRegistryEntry> registryEntriesPresent = entry -> {
                     String entryName = entry.name();
                     return registrySetSetting.containsParsedEntry(entryName);
                  };
                  if (matchingEntryStream.anyMatch(registryEntriesPresent)) {
                     Consumer<NamedRegistryEntry> removeRegistryEntries = entry -> {
                        String entryName = entry.name();
                        registrySetSetting.removeParsedEntry(entryName);
                     };
                     matchingEntries.forEach(removeRegistryEntries);
                     changeFormatting = ChatFormatting.RED;
                  } else {
                     Consumer<NamedRegistryEntry> addRegistryEntries = entry -> {
                        String entryName = entry.name();
                        registrySetSetting.addParsedEntry(entryName);
                     };
                     matchingEntries.forEach(addRegistryEntries);
                     changeFormatting = ChatFormatting.GREEN;
                  }
               }
            }

            if (changeFormatting == null) {
               setting.parseValue(requestedValue);
            }

            Component valueText = this.createSettingValueText(setting);
            if (setting instanceof SetSetting<?> setSetting) {
               if (changeFormatting == null) {
                  changeFormatting = setSetting.containsParsedEntry(requestedValue) ? ChatFormatting.GREEN : ChatFormatting.RED;
               }

               ChatFormatting capturedChangeFormatting = changeFormatting;
               MutableComponent changedEntryText = Component.literal(requestedValue);
               UnaryOperator<Style> changedEntryStyle = entryStyle -> entryStyle.applyFormat(capturedChangeFormatting);
               valueText = changedEntryText.withStyle(changedEntryStyle);
            }

            NotificationsModule notificationsForCheck = notifications;
            if (NotificationsModule.shouldNotifyCommands(notificationsForCheck)) {
               Module moduleForName = this.module;
               String moduleName = moduleForName.getName();
               MutableComponent moduleNameText = Component.literal(moduleName);
               MutableComponent spaceText = Component.literal(" ");
               String settingName = setting.getName();
               MutableComponent changedSettingNameText = spaceText.append(settingName);
               UnaryOperator<Style> changedSettingNameStyle = grayStyle -> {
                  ChatFormatting grayFormatting = ChatFormatting.GRAY;
                  return grayStyle.applyFormat(grayFormatting);
               };
               Component changedSettingName = changedSettingNameText.withStyle(changedSettingNameStyle);
               MutableComponent settingHeader = moduleNameText.append(changedSettingName);
               MutableComponent changedValueHeader = settingHeader.append(": ");
               MutableComponent changedMessage = changedValueHeader.append(valueText);
               Module moduleForSignature = this.module;
               MessageSignature changedMessageSignature = ChatUtils.moduleSignature(moduleForSignature);
               ChatUtils.MessagePriority changedMessagePriority = ChatUtils.MessagePriority.LOW;
               ChatUtils.printChat(changedMessage, changedMessageSignature, changedMessagePriority);
            }
         } catch (Throwable valueFailure) {
            MutableComponent invalidValueMessage = Component.literal("Invalid value: ");
            invalidValueMessage = invalidValueMessage.append(requestedValue);
            Module moduleForErrorSignature = this.module;
            MessageSignature invalidValueMessageSignature = ChatUtils.moduleSignature(moduleForErrorSignature);
            ChatUtils.MessagePriority invalidValueMessagePriority = ChatUtils.MessagePriority.HIGH;
            ChatUtils.printChat(invalidValueMessage, invalidValueMessageSignature, invalidValueMessagePriority);
         }

         return 1;
      };
      ArgumentBuilder valueSettingBranch = valueActionBuilder.executes(valueAction);
      settingArgument = (RequiredArgumentBuilder)settingArgument.then(valueSettingBranch);
      ArgumentBuilder resetSettingActionBuilder = Command.literal("reset");
      com.mojang.brigadier.Command<Object> resetSettingAction = resetSettingContext -> {
         Module moduleForLookup = this.module;
         Setting<?> setting = ModuleSettingArgumentType.getSetting(resetSettingContext, moduleForLookup, "setting");
         setting.reset();
         return 1;
      };
      ArgumentBuilder resetSettingBranch = resetSettingActionBuilder.executes(resetSettingAction);
      ArgumentBuilder querySettingActionBuilder = (RequiredArgumentBuilder)settingArgument.then(resetSettingBranch);
      com.mojang.brigadier.Command<Object> querySettingAction = querySettingContext -> {
         Module moduleForLookup = this.module;
         Setting<?> setting = ModuleSettingArgumentType.getSetting(querySettingContext, moduleForLookup, "setting");
         Module moduleForName = this.module;
         String moduleName = moduleForName.getName();
         MutableComponent queryMessage = Component.literal(moduleName);
         MutableComponent spaceText = Component.literal(" ");
         String settingName = setting.getName();
         MutableComponent querySettingNameText = spaceText.append(settingName);
         UnaryOperator<Style> querySettingNameStyle = grayStyle -> {
            ChatFormatting grayFormatting = ChatFormatting.GRAY;
            return grayStyle.applyFormat(grayFormatting);
         };
         Component querySettingName = querySettingNameText.withStyle(querySettingNameStyle);
         queryMessage = queryMessage.append(querySettingName);
         queryMessage = queryMessage.append(": ");
         Component querySettingValue = this.createSettingValueText(setting);
         queryMessage = queryMessage.append(querySettingValue);
         Module moduleForSignature = this.module;
         MessageSignature queryMessageSignature = ChatUtils.moduleSignature(moduleForSignature);
         ChatUtils.printChat(queryMessage, queryMessageSignature);
         return 1;
      };
      ArgumentBuilder settingQueryBranch = querySettingActionBuilder.executes(querySettingAction);
      rootWithState = (LiteralArgumentBuilder)rootWithState.then(settingQueryBranch);
      LiteralArgumentBuilder enabledBuilder = Command.literal("Enabled");
      ArgumentType enabledStateArgumentType = BoolArgumentType.bool();
      ArgumentBuilder enabledStateActionBuilder = Command.argument("state", enabledStateArgumentType);
      com.mojang.brigadier.Command<Object> enabledStateAction = enabledContext -> {
         Module moduleForStateChange = this.module;
         Boolean requestedState = (Boolean)enabledContext.getArgument("state", Boolean.class);
         boolean stateToSet = requestedState;
         moduleForStateChange.setToggled(stateToSet);
         return 1;
      };
      ArgumentBuilder enabledStateArgument = enabledStateActionBuilder.executes(enabledStateAction);
      ArgumentBuilder enabledQueryActionBuilder = (LiteralArgumentBuilder)enabledBuilder.then(enabledStateArgument);
      com.mojang.brigadier.Command<Object> enabledQueryAction = queryEnabledContext -> {
         MutableComponent enabledMessage = Component.literal("Module ");
         Module moduleForName = this.module;
         String moduleName = moduleForName.getName();
         MutableComponent enabledModuleNameText = Component.literal(moduleName);
         UnaryOperator<Style> enabledModuleNameStyle = grayStyle -> {
            ChatFormatting grayFormatting = ChatFormatting.GRAY;
            return grayStyle.applyFormat(grayFormatting);
         };
         Component enabledModuleName = enabledModuleNameText.withStyle(enabledModuleNameStyle);
         enabledMessage = enabledMessage.append(enabledModuleName);
         enabledMessage = enabledMessage.append(" is ");
         Module moduleForState = this.module;
         String stateText;
         if (moduleForState.isToggled()) {
            stateText = "enabled";
         } else {
            stateText = "disabled";
         }

         MutableComponent enabledStateValueText = Component.literal(stateText);
         UnaryOperator<Style> enabledStateStyle = stateStyle -> {
            Module moduleForStateColor = this.module;
            Style formattedStyle;
            if (moduleForStateColor.isToggled()) {
               ChatFormatting enabledFormatting = ChatFormatting.GREEN;
               formattedStyle = stateStyle.applyFormat(enabledFormatting);
            } else {
               ChatFormatting disabledFormatting = ChatFormatting.RED;
               formattedStyle = stateStyle.applyFormat(disabledFormatting);
            }

            return formattedStyle;
         };
         Component enabledStateText = enabledStateValueText.withStyle(enabledStateStyle);
         enabledMessage = enabledMessage.append(enabledStateText);
         Module moduleForSignature = this.module;
         MessageSignature enabledMessageSignature = ChatUtils.moduleSignature(moduleForSignature);
         ChatUtils.printChat(enabledMessage, enabledMessageSignature);
         return 1;
      };
      ArgumentBuilder enabledQueryBranch = enabledQueryActionBuilder.executes(enabledQueryAction);
      ArgumentBuilder overviewActionBuilder = (LiteralArgumentBuilder)rootWithState.then(enabledQueryBranch);
      com.mojang.brigadier.Command<Object> overviewAction = overviewContext -> {
         MutableComponent overviewText = Component.empty();
         MutableComponent moduleTitle = this.createModuleTitle();
         MutableComponent titleForKeybind = overviewText.append(moduleTitle);
         Object[] keybindArguments = new Object[2];
         Keybind keybindForName = this.module.getKeybind();
         keybindArguments[0] = keybindForName.getDisplayName();
         Keybind capturedKeybind = this.module.getKeybind();
         Keybind.Mode keybindMode = capturedKeybind.mode();
         keybindArguments[1] = keybindMode.getName();
         String keybindText = " [%s - %s]".formatted(keybindArguments);
         titleForKeybind.append(keybindText);
         Module moduleForSettings = this.module;

         for (Setting<?> setting : moduleForSettings.getSettings()) {
            String settingLabel = "\n" + setting.getName() + ": ";
            MutableComponent settingLine = Component.literal(settingLabel);
            switch (setting) {
               case EnumSetting<?> enumSetting:
                  Enum enumValue = enumSetting.getValue();
                  String enumName = FontRenderer.capitalize(enumValue.name());
                  MutableComponent enumText = Component.literal(enumName);
                  ChatFormatting enumFormatting = ChatFormatting.GRAY;
                  Component overviewEnumValue = enumText.withStyle(enumFormatting);
                  settingLine.append(overviewEnumValue);
                  break;
               case SetSetting<?> setSetting:
                  Iterable<? extends CharSequence> formattedSetValues = setSetting.getFormattedValues();
                  String setNames = String.join(", ", formattedSetValues);
                  MutableComponent setText = Component.literal(setNames);
                  ChatFormatting setFormatting = ChatFormatting.GRAY;
                  Component overviewSetValue = setText.withStyle(setFormatting);
                  settingLine.append(overviewSetValue);
                  break;
               case ColorSetting colorSetting:
                  String colorString = ColorUtils.formatRgb(colorSetting.getColor(), !colorSetting.opaqueColor);
                  MutableComponent overviewColorText = Component.literal(colorString);
                  UnaryOperator<Style> overviewColorValueStyle = colorStyle -> {
                     Color currentColor = colorSetting.getColor();
                     int colorRgb = currentColor.hashCode();
                     return colorStyle.withColor(colorRgb);
                  };
                  Component overviewColorValue = overviewColorText.withStyle(overviewColorValueStyle);
                  settingLine.append(overviewColorValue);
                  break;
               default:
                  Object settingValue = setting.getValue();
                  String settingValueString = settingValue.toString();
                  MutableComponent settingValueText = Component.literal(settingValueString);
                  ChatFormatting valueFormatting = ChatFormatting.GRAY;
                  Component overviewOtherValue = settingValueText.withStyle(valueFormatting);
                  settingLine.append(overviewOtherValue);
            }

            String commandPrefix = CommandRegistry.getPrefix();
            String moduleName = this.module.getName();
            String lowercaseModuleName = moduleName.toLowerCase();
            String settingCommand = commandPrefix + lowercaseModuleName + " " + setting.getName() + " ";
            UnaryOperator<Style> overviewSuggestionStyle = suggestionStyle -> {
               ClickEvent settingClick = new SuggestCommand(settingCommand);
               return suggestionStyle.withClickEvent(settingClick);
            };
            settingLine.withStyle(overviewSuggestionStyle);
            overviewText.append(settingLine);
         }

         MessageSignature overviewMessageSignature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(overviewText, overviewMessageSignature);
         return 1;
      };
      overviewActionBuilder.executes(overviewAction);
      ArgumentBuilder resetModuleActionBuilder = Command.literal("reset");
      com.mojang.brigadier.Command<Object> resetModuleAction = resetModuleContext -> {
         Module moduleForSettings = this.module;

         for (Setting<?> setting : moduleForSettings.getSettings()) {
            setting.reset();
         }

         return 1;
      };
      ArgumentBuilder resetModuleBranch = resetModuleActionBuilder.executes(resetModuleAction);
      builder.then(resetModuleBranch);
   }

   private CompletableFuture suggestClearOperation(CommandContext context, SuggestionsBuilder builder) {
      try {
         Module moduleForLookup = this.module;
         Setting<?> setting = ModuleSettingArgumentType.getSetting(context, moduleForLookup, "setting");
         if (setting instanceof SetSetting) {
            String[] clearChoices = new String[]{"clear"};
            return SharedSuggestionProvider.suggest(clearChoices, builder);
         }
      } catch (CommandSyntaxException var6) {
      }

      return Suggestions.empty();
   }

   private Component createSettingValueText(Setting<?> setting) {
      Object value = setting.getValue();
      String valueString = value.toString();
      MutableComponent text = Component.literal(valueString);
      if (setting instanceof ColorSetting colorSetting) {
         String colorString = ColorUtils.toHex(colorSetting.getColor(), true);
         MutableComponent settingColorText = Component.literal(colorString);
         UnaryOperator<Style> settingColorStyle = colorStyle -> {
            int colorRgb = setting.getValue().hashCode();
            return colorStyle.withColor(colorRgb);
         };
         text = settingColorText.withStyle(settingColorStyle);
      } else if (setting instanceof SetSetting<?> setSetting) {
         Set setValues = setSetting.getFormattedValues();
         MutableComponent settingSeparatorText = Component.literal(", ");
         UnaryOperator<Style> settingSeparatorStyle = separatorStyle -> {
            ChatFormatting separatorFormatting = ChatFormatting.GRAY;
            return separatorStyle.applyFormat(separatorFormatting);
         };
         Component formattedSeparator = settingSeparatorText.withStyle(settingSeparatorStyle);
         Function<String, Component> valueToComponent = Component::literal;
         text = ComponentUtils.formatList(setValues, formattedSeparator, valueToComponent);
      } else if (setting instanceof IdentifierSetting identifierSetting) {
         NamedIdentifier identifier = identifierSetting.getValue();
         String identifierName = identifier.getName();
         text = Component.literal(identifierName);
      }

      return text;
   }
}
