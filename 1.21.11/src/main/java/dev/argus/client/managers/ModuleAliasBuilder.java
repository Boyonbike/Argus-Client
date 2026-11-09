package dev.argus.client.managers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.argus.client.ClientContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ModuleAliasBuilder extends LiteralArgumentBuilder {
   public ModuleAliasBuilder(String literal) {
      super(literal);
   }

   public LiteralCommandNode build() {
      ModuleAliasBuilder.ModuleAliasNode node = new ModuleAliasBuilder.ModuleAliasNode(
         this.getLiteral(), this.getCommand(), this.getRequirement(), this.getRedirect(), this.getRedirectModifier(), this.isFork()
      );

      for (Object child : this.getArguments()) {
         node.addChild((CommandNode)child);
      }

      return node;
   }

   public static class ModuleAliasNode extends LiteralCommandNode {
      public ModuleAliasNode(String literal, Command command, Predicate requirement, CommandNode redirect, RedirectModifier redirectModifier, boolean forks) {
         super(literal, command, requirement, redirect, redirectModifier, forks);
      }

      public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
         CommandRegistry registry = ClientContext.commandRegistry;

         for (dev.argus.client.commands.Command command : (List<dev.argus.client.commands.Command>)registry.getRegistry()) {
            String commandName = command.getName();
            String remaining = builder.getRemainingLowerCase();
            if (commandName.startsWith(remaining)) {
               return Suggestions.empty();
            }
         }

         return super.listSuggestions(context, builder);
      }
   }
}
