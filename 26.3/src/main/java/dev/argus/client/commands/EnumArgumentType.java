package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.settings.NamedEntry;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class EnumArgumentType implements ArgumentType {
   private final Class enumClass;
   private final String argumentLabel;

   public EnumArgumentType(Class enumClass) {
      this(enumClass, "Enum");
   }

   public EnumArgumentType(Class enumClass, String argumentLabel) {
      this.enumClass = enumClass;
      this.argumentLabel = argumentLabel;
   }

   private String getEnumName(Enum value) {
      return value instanceof NamedEntry namedEntry ? namedEntry.getName() : value.name().toLowerCase(Locale.ROOT);
   }

   public Enum parse(StringReader reader) throws CommandSyntaxException {
      String requestedName = reader.readString();
      Class enumType = this.enumClass;

      for (Enum enumConstant : (Enum[])enumType.getEnumConstants()) {
         String candidateName = this.getEnumName(enumConstant);
         if (candidateName.equalsIgnoreCase(requestedName)) {
            return enumConstant;
         }
      }

      DynamicCommandExceptionType unknownValueException = new DynamicCommandExceptionType(invalidValue -> {
         Object[] errorArguments = new Object[]{this.argumentLabel, invalidValue};
         String errorMessage = String.format("%s %s doesn't exist", errorArguments);
         return Component.literal(errorMessage);
      });
      throw unknownValueException.create(requestedName);
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      Class enumType = this.enumClass;
      Enum[] enumConstants = (Enum[])enumType.getEnumConstants();
      Stream<String> enumNames = Arrays.stream(enumConstants).map(this::getEnumName);
      return SharedSuggestionProvider.suggest(enumNames, builder);
   }
}
