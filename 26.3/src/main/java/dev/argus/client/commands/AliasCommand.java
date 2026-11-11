package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

public final class AliasCommand extends Command {
   public AliasCommand() {
      super("alias");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      AliasCommand.AliasTarget moduleTargetKind = AliasCommand.AliasTarget.MODULE;
      ArgumentBuilder moduleCommands = this.buildTargetCommands(moduleTargetKind);
      LiteralArgumentBuilder rootWithModuleCommands = (LiteralArgumentBuilder)root.then(moduleCommands);
      AliasCommand.AliasTarget playerTargetKind = AliasCommand.AliasTarget.PLAYER;
      ArgumentBuilder playerCommands = this.buildTargetCommands(playerTargetKind);
      rootWithModuleCommands.then(playerCommands);
   }

   private ArgumentBuilder buildTargetCommands(AliasCommand.AliasTarget targetKind) {
      String targetKindName = targetKind.name();
      String targetLiteral = targetKindName.toLowerCase();
      LiteralArgumentBuilder targetCommands = Command.literal(targetLiteral);
      LiteralArgumentBuilder operationCommands = Command.literal("set");
      ArgumentType setTargetType = targetKind.argumentType;
      RequiredArgumentBuilder setTargetArgument = Command.argument("target", setTargetType);
      FancyStringArgument aliasParser = FancyStringArgument.fancyString();
      RequiredArgumentBuilder aliasBuilder = Command.argument("alias", aliasParser);
      com.mojang.brigadier.Command<Object> setExecutor = context -> {
         String alias = (String)context.getArgument("alias", String.class);
         String targetName;
         if (targetKind == AliasCommand.AliasTarget.MODULE) {
            AliasRegistry capturedModuleAliasManager = ClientContext.aliasRegistry;
            Module targetModule = (Module)context.getArgument("target", Module.class);
            capturedModuleAliasManager.setModuleAlias(targetModule, alias);
            targetName = targetModule.getName();
         } else {
            AliasRegistry capturedPlayerAliasManager = ClientContext.aliasRegistry;
            String targetPlayerName = (String)context.getArgument("target", String.class);
            capturedPlayerAliasManager.setPlayerAlias(targetPlayerName, alias);
            targetName = targetPlayerName;
         }

         ChatUtils.printChat(
            Component.literal("Alias for ").append(this.formatText(targetName)).append(" has been set to ").append(this.formatText(alias)).append("."),
            ChatUtils.DEFAULT_SIGNATURE
         );
         return 1;
      };
      ArgumentBuilder aliasArgument = aliasBuilder.executes(setExecutor);
      ArgumentBuilder setTargetCommands = setTargetArgument.then(aliasArgument);
      ArgumentBuilder setCommands = operationCommands.then(setTargetCommands);
      targetCommands = (LiteralArgumentBuilder)targetCommands.then(setCommands);
      operationCommands = Command.literal("remove");
      ArgumentType removeTargetType = targetKind.argumentType;
      RequiredArgumentBuilder removeTargetArgument = Command.argument("target", removeTargetType);
      com.mojang.brigadier.Command<Object> removeExecutor = context -> {
         String targetName;
         if (targetKind == AliasCommand.AliasTarget.MODULE) {
            AliasRegistry capturedModuleAliasManager = ClientContext.aliasRegistry;
            Module targetModule = (Module)context.getArgument("target", Module.class);
            capturedModuleAliasManager.removeModuleAlias(targetModule);
            targetName = targetModule.getName();
         } else {
            AliasRegistry capturedPlayerAliasManager = ClientContext.aliasRegistry;
            String targetPlayerName = (String)context.getArgument("target", String.class);
            capturedPlayerAliasManager.removePlayerAlias(targetPlayerName);
            targetName = targetPlayerName;
         }

         ChatUtils.printChat(Component.literal("Alias for ").append(this.formatText(targetName)).append(" has been removed."), ChatUtils.DEFAULT_SIGNATURE);
         return 1;
      };
      ArgumentBuilder removeTargetCommands = removeTargetArgument.executes(removeExecutor);
      ArgumentBuilder removeCommands = operationCommands.then(removeTargetCommands);
      return targetCommands.then(removeCommands);
   }

   private enum AliasTarget {
      MODULE(new ModuleArgumentType()),
      PLAYER(new PlayerNameArgumentType());

      private final ArgumentType argumentType;

      AliasTarget(ArgumentType argumentType) {
         this.argumentType = argumentType;
      }
   }
}
