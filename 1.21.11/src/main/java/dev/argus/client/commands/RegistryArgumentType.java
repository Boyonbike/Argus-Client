package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.managers.Registry;
import dev.argus.client.settings.NamedEntry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class RegistryArgumentType implements ArgumentType<NamedEntry> {
   private final Registry<?> registry;
   private static final String INVALID_ENTRY_TEMPLATE = "Item %s doesn't exists";

   public RegistryArgumentType(Registry<?> registry) {
      this.registry = registry;
   }

   public NamedEntry parse(StringReader reader) throws CommandSyntaxException {
      String entryName = reader.readString();
      Registry<?> registrySnapshot = this.registry;

      for (NamedEntry entry : (List<NamedEntry>)registrySnapshot.getRegistry()) {
         String entryNameValue = entry.getName();
         if (entryNameValue.equalsIgnoreCase(entryName)) {
            return entry;
         }
      }

      DynamicCommandExceptionType createdExceptionType = new DynamicCommandExceptionType(invalidName -> {
         String formatValue = "Item %s doesn't exists";
         Object[] formatArguments = new Object[]{invalidName};
         String message = String.format(formatValue, formatArguments);
         return Component.literal(message);
      });
      throw createdExceptionType.create(entryName);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      Registry<?> registrySnapshot = this.registry;
      List<NamedEntry> entries = (List<NamedEntry>)registrySnapshot.getRegistry();
      Stream<String> names = entries.stream().map(NamedEntry::getName);
      return SharedSuggestionProvider.suggest(names, builder);
   }

   public static RegistryArgumentType create(Registry<?> registry) {
      return new RegistryArgumentType(registry);
   }
}
