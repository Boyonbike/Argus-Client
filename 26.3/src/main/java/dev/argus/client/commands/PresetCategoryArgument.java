package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.PresetRegistry;
import dev.argus.client.settings.options.PresetCategory;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class PresetCategoryArgument implements ArgumentType {
   private static final String INVALID_CATEGORY_PREFIX = "Preset category not found ";

   public PresetCategory parseCategory(StringReader reader) throws CommandSyntaxException {
      String categoryName = reader.readString();
      PresetCategory category = PresetCategory.findByName(categoryName);
      if (category == null) {
         DynamicCommandExceptionType createdExceptionType = new DynamicCommandExceptionType(invalidName -> {
            String invalidNameText = String.valueOf(invalidName);
            String message = "Preset category not found ".concat(invalidNameText);
            return Component.nullToEmpty(message);
         });
         throw createdExceptionType.create(categoryName);
      } else {
         return category;
      }
   }

   public static PresetRegistry getManager(CommandContext context, String argumentName) {
      PresetCategory category = (PresetCategory)context.getArgument(argumentName, PresetCategory.class);
      ConfigurationManager presetManagers = ClientContext.configurationManager;
      return presetManagers.getPresetRegistry(category);
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      PresetCategory[] categories = PresetCategory.values();
      Stream<String> categoryNames = Arrays.stream(categories).map(category -> {
         String categoryName = category.getName();
         Locale locale = Locale.ROOT;
         return categoryName.toLowerCase(locale);
      });
      return SharedSuggestionProvider.suggest(categoryNames, builder);
   }

   public Object parse(StringReader reader) throws CommandSyntaxException {
      return this.parseCategory(reader);
   }
}
