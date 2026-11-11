package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.managers.MacroRegistry;
import dev.argus.client.modules.misc.Macro;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class MacroArgumentType implements ArgumentType {
   private static final String unknownMacroFormat = "Macro %s doesn't exists";

   public Macro parse(StringReader reader) throws CommandSyntaxException {
      String requestedName = reader.readString();
      MacroRegistry macroRegistry = ClientContext.macroRegistry;

      for (Macro macro : (List<Macro>)macroRegistry.getRegistry()) {
         String macroName = macro.getName();
         if (macroName.equalsIgnoreCase(requestedName)) {
            return macro;
         }
      }

      DynamicCommandExceptionType unknownMacroException = new DynamicCommandExceptionType(invalidName -> {
         String capturedErrorFormat = "Macro %s doesn't exists";
         Object[] formatArguments = new Object[]{invalidName};
         String errorMessage = String.format(capturedErrorFormat, formatArguments);
         return Component.literal(errorMessage);
      });
      throw unknownMacroException.create(requestedName);
   }

   public CompletableFuture listSuggestions(CommandContext unusedContext, SuggestionsBuilder suggestions) {
      MacroRegistry macroRegistry = ClientContext.macroRegistry;
      Stream names = macroRegistry.getRegistry().stream().map(NamedFeature::getName);
      return SharedSuggestionProvider.suggest(names, suggestions);
   }
}
