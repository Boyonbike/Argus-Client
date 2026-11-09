package dev.argus.client.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.options.SocialRole;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class EnemyFriendCommand extends Command {
   private final SocialRole role;

   public EnemyFriendCommand(SocialRole role) {
      super(role.name().toLowerCase());
      this.role = role;
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      LiteralArgumentBuilder commandBuilder = (LiteralArgumentBuilder)Command.literal("add").executes(context -> {
         if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player) {
            GameProfile profile = player.getGameProfile();
            String name = profile.name();
            SocialRegistry socials = ClientContext.socialRegistry;
            if (socials.getRole(name) != this.role) {
               if (this.role == SocialRole.ENEMY) {
                  socials = ClientContext.socialRegistry;
                  socials.addEnemy(name);
               } else {
                  socials = ClientContext.socialRegistry;
                  socials.addFriend(name);
               }

               Object[] addedFormatArguments = new Object[]{name, null};
               SocialRole addedRole = this.role;
               addedFormatArguments[1] = addedRole.getPluralName();
               String addedText = "%s has been added to your %s list".formatted(addedFormatArguments);
               Component addedMessage = Component.nullToEmpty(addedText);
               ChatUtils.printChat(addedMessage, ChatUtils.createSignature(-1));
            } else {
               Object[] existingFormatArguments = new Object[]{name, null};
               SocialRole existingRole = this.role;
               existingFormatArguments[1] = existingRole.getPluralName();
               String existingText = "%s is already in your %s list".formatted(existingFormatArguments);
               Component existingMessage = Component.nullToEmpty(existingText);
               ChatUtils.printChat(existingMessage, ChatUtils.createSignature(-1));
            }
         }

         return 1;
      });
      ArgumentBuilder addNamesCommand = Command.argument("names", new ArrayArgumentType(new PlayerNameArgumentType())).executes(context -> {
         String[] names = (String[])context.getArgument("names", String[].class);
         ArrayList<String> addedNames = new ArrayList<>();
         ArrayList<String> existingNames = new ArrayList<>();

         for (String name : names) {
            SocialRegistry socials = ClientContext.socialRegistry;
            if (socials.getRole(name) == this.role) {
               existingNames.add(name);
            } else {
               if (this.role == SocialRole.ENEMY) {
                  socials = ClientContext.socialRegistry;
                  socials.addEnemy(name);
               } else {
                  socials = ClientContext.socialRegistry;
                  socials.addFriend(name);
               }

               addedNames.add(name);
            }
         }

         int messageId = -1;
         if (!addedNames.isEmpty()) {
            String addedVerbChoice = addedNames.size() == 1 ? "has" : "have";
            MutableComponent addedList = this.formatNames(addedNames);
            String addedSuffixTemplate = " " + addedVerbChoice + " been added to your %s list";
            String addedSuffix = this.formatRoleTemplate(addedSuffixTemplate);
            MutableComponent addedMessage = addedList.append(addedSuffix);
            int addedMessageId = messageId;
            ChatUtils.printChat(addedMessage, ChatUtils.createSignature(addedMessageId));
            messageId--;
         }

         if (!existingNames.isEmpty()) {
            String existingVerb = existingNames.size() == 1 ? "is" : "are";
            MutableComponent existingList = this.formatNames(existingNames);
            String existingSuffixTemplate = " " + existingVerb + " already in your %s list";
            String existingSuffix = this.formatRoleTemplate(existingSuffixTemplate);
            MutableComponent existingMessage = existingList.append(existingSuffix);
            int existingMessageId = messageId;
            ChatUtils.printChat(existingMessage, ChatUtils.createSignature(existingMessageId));
         }

         return 1;
      });
      ArgumentBuilder addBranch = commandBuilder.then(addNamesCommand);
      root.then(addBranch);
      String[] aliases = new String[]{"remove", "delete", "del"};
      RequiredArgumentBuilder removeBuilder = (RequiredArgumentBuilder)Command.aliasedLiteral(aliases).executes(context -> {
         if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player) {
            GameProfile profile = player.getGameProfile();
            String name = profile.name();
            SocialRegistry socials = ClientContext.socialRegistry;
            if (socials.getRole(name) == this.role) {
               socials = ClientContext.socialRegistry;
               socials.removeSocial(name);
               Object[] removedFormatArguments = new Object[]{name, null};
               SocialRole removedRole = this.role;
               removedFormatArguments[1] = removedRole.getPluralName();
               String removedTemplate = "%s was removed from your %s list";
               String removedText = removedTemplate.formatted(removedFormatArguments);
               Component removedMessage = Component.nullToEmpty(removedText);
               ChatUtils.printChat(removedMessage, ChatUtils.createSignature(-1));
            } else {
               Object[] missingFormatArguments = new Object[]{name, null};
               SocialRole missingRole = this.role;
               missingFormatArguments[1] = missingRole.getPluralName();
               String missingText = "%s is not in your %s list".formatted(missingFormatArguments);
               Component missingMessage = Component.nullToEmpty(missingText);
               ChatUtils.printChat(missingMessage, ChatUtils.createSignature(-1));
            }
         }

         return 1;
      });
      ArgumentBuilder removeNamesCommand = Command.argument("names", StringArgumentType.greedyString())
         .suggests(this::suggestRegisteredNames)
         .executes(context -> {
            String capturedInput = (String)context.getArgument("names", String.class);
            String[] names = capturedInput.split(" ");
            ArrayList<String> removedNames = new ArrayList<>();
            ArrayList<String> missingNames = new ArrayList<>();

            for (String name : names) {
               SocialRegistry socials = ClientContext.socialRegistry;
               if (socials.getRole(name) == this.role) {
                  SocialRegistry capturedRemover = ClientContext.socialRegistry;
                  capturedRemover.removeSocial(name);
                  removedNames.add(name);
               } else {
                  missingNames.add(name);
               }
            }

            int messageId = -1;
            if (!removedNames.isEmpty()) {
               MutableComponent removedList = this.formatNames(removedNames);
               String removedTemplate = " was removed from your %s list";
               String removedSuffix = this.formatRoleTemplate(removedTemplate);
               MutableComponent removedMessage = removedList.append(removedSuffix);
               int removedMessageId = messageId;
               ChatUtils.printChat(removedMessage, ChatUtils.createSignature(removedMessageId));
               messageId--;
            }

            if (!missingNames.isEmpty()) {
               String missingVerb = missingNames.size() == 1 ? "is" : "are";
               MutableComponent missingList = this.formatNames(missingNames);
               String missingSuffixTemplate = " " + missingVerb + " not your %s";
               String missingSuffix = this.formatRoleTemplate(missingSuffixTemplate);
               MutableComponent missingMessage = missingList.append(missingSuffix);
               int missingMessageId = messageId;
               ChatUtils.printChat(missingMessage, ChatUtils.createSignature(missingMessageId));
            }

            return 1;
         });
      ArgumentBuilder removeBranch = removeBuilder.then(removeNamesCommand);
      root.then(removeBranch);
      ArgumentBuilder listCommand = Command.literal("list").executes(context -> {
         SocialRegistry socials = ClientContext.socialRegistry;
         List<Component> nameComponents = socials.getRegistry().stream().filter(entry -> entry.role() == this.role).map(entry -> {
            String name = entry.getName();
            return Component.nullToEmpty(name);
         }).toList();
         String heading = this.formatRoleTemplate("%S list: ");
         MutableComponent message = Component.literal(heading);
         message = message.append(ComponentUtils.formatList(nameComponents, Component.nullToEmpty(", ")));
         byte messageId = -1;
         ChatUtils.printChat(message, ChatUtils.createSignature(messageId));
         return 1;
      });
      root.then(listCommand);
      if (this.role != SocialRole.ENEMY) {
         commandBuilder = Command.literal("sync");
         ArgumentBuilder importerCommand = Command.argument("importer", new EnumArgumentType(FriendImportFormat.class, "importer")).executes(context -> {
            FriendImportFormat importer = (FriendImportFormat)context.getArgument("importer", FriendImportFormat.class);

            List<String> importedNames;
            try {
               importedNames = importer.readFriendNames();
            } catch (Exception failure) {
               if (failure instanceof FileNotFoundException) {
                  String missingFileText = String.format("Couldn't find %s friends file", importer.getDisplayName());
                  MutableComponent missingFileMessage = Component.literal(missingFileText).withStyle(style -> {
                     ChatFormatting red = ChatFormatting.RED;
                     return style.withColor(red);
                  });
                  ChatUtils.printChat(missingFileMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW);
               } else {
                  failure.printStackTrace();
                  Object[] failureFormatArguments = new Object[]{importer.getDisplayName()};
                  String failureText = String.format("Couldn't import friends from %s due to an unknown error, check your log file", failureFormatArguments);
                  MutableComponent failureMessage = Component.literal(failureText).withStyle(style -> {
                     ChatFormatting red = ChatFormatting.RED;
                     return style.withColor(red);
                  });
                  ChatUtils.printChat(failureMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW);
               }

               return 1;
            }

            int addedCount = 0;

            for (String name : importedNames) {
               SocialRegistry socials = ClientContext.socialRegistry;
               if (!socials.isFriendName(name)) {
                  socials = ClientContext.socialRegistry;
                  socials.addFriend(name);
                  addedCount++;
               }
            }

            Object[] completionFormatArguments = new Object[]{addedCount, importer.getDisplayName()};
            String completionText = String.format("Added %d new friends from %s", completionFormatArguments);
            MutableComponent completionMessage = Component.literal(completionText);
            ChatUtils.printChat(completionMessage, ChatUtils.createSignature(-1));
            return 1;
         });
         ArgumentBuilder syncBranch = commandBuilder.then(importerCommand);
         root.then(syncBranch);
      }
   }

   private String formatRoleTemplate(String template) {
      SocialRole lowercaseRole = this.role;
      String lowercaseFormatted = template.replace("%s", lowercaseRole.getPluralName());
      SocialRole capitalizedRole = this.role;
      return lowercaseFormatted.replace("%S", FontRenderer.capitalize(capitalizedRole.getPluralName()));
   }

   private MutableComponent formatNames(Collection<String> names) {
      List<MutableComponent> nameComponents = names.stream().map(this::formatText).toList();
      return (MutableComponent)ComponentUtils.formatList(nameComponents, Component.nullToEmpty(", "));
   }

   private CompletableFuture<Suggestions> suggestRegisteredNames(CommandContext unusedContext, SuggestionsBuilder builder) {
      int offset;
      label12: {
         String fullInput = builder.getInput();
         offset = fullInput.lastIndexOf(" ");
         if (offset != -1) {
            String capturedInput = builder.getInput();
            if (offset != capturedInput.length()) {
               offset++;
               break label12;
            }
         }

         offset = builder.getStart();
      }

      SuggestionsBuilder offsetBuilder = builder.createOffset(offset);
      SocialRegistry socials = ClientContext.socialRegistry;
      Stream<String> registeredNames = socials.getRegistry().stream().filter(entry -> entry.role() == this.role).map(NamedEntry::getName);
      return SharedSuggestionProvider.suggest(registeredNames, offsetBuilder);
   }
}
