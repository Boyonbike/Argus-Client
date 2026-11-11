package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.Setting;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ModuleSettingArgumentType implements ArgumentType, ClientAccess {
   private final Module module;
   private static final String UNKNOWN_SETTING_PREFIX = "Setting not found ";

   public ModuleSettingArgumentType(Module module) {
      this.module = module;
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public static Setting<?> getSetting(CommandContext context, Module module, String argumentName) throws CommandSyntaxException {
      String requestedName = (String)context.getArgument(argumentName, String.class);
      Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> setting);
      Stream<Setting<?>> matchingSettings = settings.filter(setting -> {
         String configName = setting.getConfigName();
         return configName.equalsIgnoreCase(requestedName);
      });
      Optional<Setting<?>> matchedSetting = matchingSettings.findFirst();
      if (matchedSetting.isEmpty()) {
         DynamicCommandExceptionType unknownSettingError = new DynamicCommandExceptionType(argument -> {
            String argumentText = String.valueOf(argument);
            String message = "Setting not found ".concat(argumentText);
            return Component.literal(message);
         });
         throw unknownSettingError.create(requestedName);
      } else {
         return matchedSetting.get();
      }
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Module module = this.module;
      Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> setting);
      Stream<String> settingNames = settings.filter(setting -> !setting.isGroupOnly()).map(JsonConfigurable::getConfigName);
      return SharedSuggestionProvider.suggest(settingNames, builder);
   }
}
