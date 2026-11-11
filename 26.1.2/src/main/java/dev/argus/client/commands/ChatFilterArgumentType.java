package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ChatFilterRegistry;
import dev.argus.client.utils.chat.ChatFilterEntry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ChatFilterArgumentType implements ArgumentType<ChatFilterEntry> {
   private static final String UNKNOWN_FILTER_PREFIX = "Filter not found ";

   public ChatFilterEntry parse(StringReader reader) throws CommandSyntaxException {
      String filterId = CommandStringReader.readString(reader);
      ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
      Optional<ChatFilterEntry> matchingFilter = filterRegistry.findById(filterId);
      if (matchingFilter.isEmpty()) {
         DynamicCommandExceptionType unknownFilter = new DynamicCommandExceptionType(argument -> {
            String argumentText = String.valueOf(argument);
            String errorMessage = "Filter not found ".concat(argumentText);
            return Component.nullToEmpty(errorMessage);
         });
         throw unknownFilter.create(filterId);
      } else {
         return matchingFilter.get();
      }
   }

   public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
      Stream<String> filterIds = filterRegistry.streamIds();
      return SharedSuggestionProvider.suggest(filterIds, builder);
   }
}
