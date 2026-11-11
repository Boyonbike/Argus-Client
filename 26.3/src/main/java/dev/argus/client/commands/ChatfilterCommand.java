package dev.argus.client.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ChatFilterRegistry;
import dev.argus.client.utils.chat.ChatFilterEntry;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class ChatfilterCommand extends Command {
   public ChatfilterCommand() {
      super("chatfilter");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      LiteralArgumentBuilder branchBuilder = Command.literal("add");
      FancyStringArgument idTypeArgument = FancyStringArgument.fancyString();
      RequiredArgumentBuilder idArgument = Command.argument("id", idTypeArgument);
      StringArgumentType filterTypeArgument = StringArgumentType.greedyString();
      RequiredArgumentBuilder filterBuilder = Command.argument("filter", filterTypeArgument);
      com.mojang.brigadier.Command<Object> addCommand = addContext -> {
         Class<String> idType = String.class;
         String idKey = "id";
         CommandContext idContext = addContext;
         String filterId = (String)idContext.getArgument(idKey, idType);
         Class<String> filterType = String.class;
         String filterKey = "filter";
         CommandContext filterContext = addContext;
         String filterText = (String)filterContext.getArgument(filterKey, filterType);
         ChatFilterRegistry registryCapture = ClientContext.chatFilterRegistry;
         String capturedFilterId = filterId;
         ChatFilterRegistry containsRegistry = registryCapture;
         if (containsRegistry.containsId(capturedFilterId)) {
            Object[] duplicateMessageArguments = new Object[]{filterId};
            String duplicateMessageText = "Chat filter \"%s\" already exists.".formatted(duplicateMessageArguments);
            Component duplicateMessage = Component.nullToEmpty(duplicateMessageText);
            MessageSignature duplicateSignature = ChatUtils.createSignature(-3469856);
            ChatUtils.MessagePriority duplicatePriority = ChatUtils.MessagePriority.MID;
            ChatUtils.printChat(duplicateMessage, duplicateSignature, duplicatePriority);
         } else {
            registryCapture = ClientContext.chatFilterRegistry;
            registryCapture.add(filterId, filterText);
            Object[] addedMessageArguments = new Object[]{filterId};
            String addedMessageText = "Chat filter \"%s\" has been added.".formatted(addedMessageArguments);
            Component addedMessage = Component.nullToEmpty(addedMessageText);
            MessageSignature addedSignature = ChatUtils.createSignature(-3469857);
            ChatUtils.printChat(addedMessage, addedSignature);
         }

         return 1;
      };
      ArgumentBuilder filterArgumentBranch = filterBuilder.executes(addCommand);
      ArgumentBuilder addArguments = idArgument.then(filterArgumentBranch);
      ArgumentBuilder addBranch = branchBuilder.then(addArguments);
      LiteralArgumentBuilder parentBuilder = (LiteralArgumentBuilder)commandBuilder.then(addBranch);
      branchBuilder = Command.literal("remove");
      ChatFilterArgumentType entryTypeArgument = new ChatFilterArgumentType();
      RequiredArgumentBuilder removeArgument = Command.argument("id", entryTypeArgument);
      com.mojang.brigadier.Command<Object> removeCommand = removeContext -> {
         Class<ChatFilterEntry> entryType = ChatFilterEntry.class;
         String entryKey = "id";
         CommandContext entryContext = removeContext;
         ChatFilterEntry selectedEntry = (ChatFilterEntry)entryContext.getArgument(entryKey, entryType);
         ChatFilterEntry entryToRemove = selectedEntry;
         ChatFilterRegistry removeRegistry = ClientContext.chatFilterRegistry;
         removeRegistry.remove(entryToRemove);
         Object[] removedMessageArgumentArray = new Object[]{selectedEntry.id()};
         String removedMessageText = "Chat filter \"%s\" has been removed.".formatted(removedMessageArgumentArray);
         Component removedMessage = Component.nullToEmpty(removedMessageText);
         MessageSignature removedSignature = ChatUtils.createSignature(-3469858);
         ChatUtils.printChat(removedMessage, removedSignature);
         return 1;
      };
      ArgumentBuilder removeArgumentBranch = removeArgument.executes(removeCommand);
      ArgumentBuilder removeBranch = branchBuilder.then(removeArgumentBranch);
      parentBuilder = (LiteralArgumentBuilder)parentBuilder.then(removeBranch);
      LiteralArgumentBuilder listBuilder = Command.literal("list");
      com.mojang.brigadier.Command<Object> listCommand = listContext -> {
         Object[] countMessageArgumentArray = new Object[1];
         ChatFilterRegistry countRegistryCapture = ClientContext.chatFilterRegistry;
         int filterCount = countRegistryCapture.size();
         countMessageArgumentArray[0] = filterCount;
         String countMessageText = "%d chat filter(s)".formatted(countMessageArgumentArray);
         Component countMessage = Component.nullToEmpty(countMessageText);
         MessageSignature countSignature = ChatUtils.createSignature(-3569855);
         ChatUtils.printChat(countMessage, countSignature);
         int rowSignatureId = -3469852;
         ChatFilterRegistry listRegistryCapture = ClientContext.chatFilterRegistry;

         for (String description : listRegistryCapture.describeFilters()) {
            Component descriptionMessage = Component.nullToEmpty(description);
            MessageSignature descriptionSignature = ChatUtils.createSignature(++rowSignatureId);
            ChatUtils.printChat(descriptionMessage, descriptionSignature);
         }

         return 1;
      };
      ArgumentBuilder listBranch = listBuilder.executes(listCommand);
      parentBuilder.then(listBranch);
   }
}
