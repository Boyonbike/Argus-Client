package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.KitRegistry;
import dev.argus.client.utils.inventory.Kit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class KitArgumentType implements ArgumentType {
   private static final String unknownKitFormat = "Kit %s doesn't exist";

   public Kit parse(StringReader reader) throws CommandSyntaxException {
      String requestedKitName = CommandStringReader.readString(reader);
      KitRegistry kitRegistry = ClientContext.kitRegistry;

      for (Kit kit : (List<Kit>)kitRegistry.getRegistry()) {
         String kitName = kit.getName();
         if (kitName.equalsIgnoreCase(requestedKitName)) {
            return kit;
         }
      }

      DynamicCommandExceptionType unknownKitException = new DynamicCommandExceptionType(argument -> {
         String format = "Kit %s doesn't exist";
         Object[] formatArguments = new Object[]{argument};
         String errorMessage = String.format(format, formatArguments);
         return Component.literal(errorMessage);
      });
      throw unknownKitException.create(requestedKitName);
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      KitRegistry kitRegistry = ClientContext.kitRegistry;
      Stream<String> kitNames = kitRegistry.getRegistry().stream().map(Kit::getName);
      return SharedSuggestionProvider.suggest(kitNames, builder);
   }
}
