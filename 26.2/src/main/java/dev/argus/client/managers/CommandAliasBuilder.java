package dev.argus.client.managers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class CommandAliasBuilder extends LiteralArgumentBuilder {
   public CommandAliasBuilder(String literal) {
      super(literal);
   }

   public LiteralCommandNode build() {
      CommandAliasBuilder.CommandAliasNode node = new CommandAliasBuilder.CommandAliasNode(
         this.getLiteral(), this.getCommand(), this.getRequirement(), this.getRedirect(), this.getRedirectModifier(), this.isFork()
      );

      for (Object child : this.getArguments()) {
         node.addChild((CommandNode)child);
      }

      return node;
   }

   public static class CommandAliasNode extends LiteralCommandNode {
      public CommandAliasNode(String literal, Command command, Predicate requirement, CommandNode redirect, RedirectModifier redirectModifier, boolean forks) {
         super(literal, command, requirement, redirect, redirectModifier, forks);
      }

      public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
         String literalName = this.getName();
         String remaining = builder.getRemainingLowerCase();
         if (literalName.startsWith(remaining)) {
            String remainingForLength = builder.getRemainingLowerCase();
            int typedLength = remainingForLength.length();
            if (typedLength > 3) {
               String literalToSuggest = this.getName();
               SuggestionsBuilder resultBuilder = builder.suggest(literalToSuggest);
               return resultBuilder.buildFuture();
            }
         }

         return Suggestions.empty();
      }
   }
}
