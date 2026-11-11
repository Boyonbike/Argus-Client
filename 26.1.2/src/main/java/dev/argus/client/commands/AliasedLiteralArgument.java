package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;

public class AliasedLiteralArgument implements ArgumentType<String> {
   private final String[] aliases;

   public AliasedLiteralArgument(String... aliases) {
      this.aliases = aliases;
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      String input = reader.readString();

      for (String alias : this.aliases) {
         if (alias.equals(input)) {
            return alias;
         }
      }

      BuiltInExceptionProvider exceptionProvider = CommandSyntaxException.BUILT_IN_EXCEPTIONS;
      DynamicCommandExceptionType incorrectLiteral = exceptionProvider.literalIncorrect();
      String[] aliasSnapshot = this.aliases;
      String expectedAliases = Arrays.toString(aliasSnapshot);
      throw incorrectLiteral.createWithContext(reader, expectedAliases);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining();

      for (String alias : this.aliases) {
         if (alias.equals(remaining)) {
            builder.suggest(alias);
            return builder.buildFuture();
         }
      }

      String remainingInput = builder.getRemaining();
      String primaryAlias = this.aliases[0];
      if (SharedSuggestionProvider.matchesSubStr(remainingInput, primaryAlias)) {
         String primarySuggestion = this.aliases[0];
         builder.suggest(primarySuggestion);
      }

      return builder.buildFuture();
   }
}
