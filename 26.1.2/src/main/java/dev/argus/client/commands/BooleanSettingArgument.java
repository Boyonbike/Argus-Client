package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.settings.BooleanSetting;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BooleanSettingArgument implements ArgumentType {
   private static final Collection EXAMPLES = List.of("true", "false", "toggle");
   private final BooleanSetting setting;

   public BooleanSettingArgument(BooleanSetting setting) {
      this.setting = setting;
   }

   public static BooleanSettingArgument bool(BooleanSetting setting) {
      return new BooleanSettingArgument(setting);
   }

   public static boolean getBool(CommandContext context, String name) {
      return (Boolean)context.getArgument(name, Boolean.class);
   }

   public Boolean parse(StringReader reader) throws CommandSyntaxException {
      String input = reader.readString();
      if (input.equals("0") || input.equalsIgnoreCase("false")) {
         return false;
      }

      if (input.equals("1") || input.equalsIgnoreCase("true")) {
         return true;
      }

      if (input.equalsIgnoreCase("toggle")) {
         return !this.setting.getValue();
      }

      BuiltInExceptionProvider exceptionProvider = CommandSyntaxException.BUILT_IN_EXCEPTIONS;
      DynamicCommandExceptionType invalidBoolean = exceptionProvider.readerInvalidBool();
      throw invalidBoolean.createWithContext(reader, input);
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      String remaining = builder.getRemainingLowerCase();
      if ("true".startsWith(remaining)) {
         builder.suggest("true");
      }

      if ("false".startsWith(remaining)) {
         builder.suggest("false");
      }

      if ("toggle".startsWith(remaining)) {
         builder.suggest("toggle");
      }

      return builder.buildFuture();
   }

   public Collection getExamples() {
      return EXAMPLES;
   }
}
