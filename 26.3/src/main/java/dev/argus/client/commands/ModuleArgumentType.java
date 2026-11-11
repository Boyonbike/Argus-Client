package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ModuleArgumentType implements ArgumentType {
   private static final DynamicCommandExceptionType UNKNOWN_MODULE = new DynamicCommandExceptionType(
      argument -> Component.nullToEmpty("Module not found " + argument)
   );

   public Module parse(StringReader reader) throws CommandSyntaxException {
      String moduleName = reader.readString();
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Predicate<Module> moduleMatches = module -> {
         for (String alias : module.getAliases()) {
            String requestedName = moduleName;
            String capturedAlias = alias;
            if (capturedAlias.equalsIgnoreCase(requestedName)) {
               return true;
            }
         }

         return false;
      };
      Optional matchingModule = registry.findFirst(moduleMatches);
      if (matchingModule.isEmpty()) {
         String unmatchedName = moduleName;
         DynamicCommandExceptionType exceptionType = UNKNOWN_MODULE;
         throw exceptionType.create(unmatchedName);
      } else {
         return (Module)matchingModule.get();
      }
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ModuleRegistry modules = ClientContext.moduleRegistry;
      Stream<String> moduleNames = modules.getRegistry().stream().map(NamedFeature::getName);
      return SharedSuggestionProvider.suggest(moduleNames, builder);
   }
}
