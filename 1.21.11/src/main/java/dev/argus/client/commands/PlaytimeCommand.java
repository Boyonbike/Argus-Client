package dev.argus.client.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public class PlaytimeCommand extends TwoB2tApiCommand {
   public PlaytimeCommand() {
      super("playtime");
      this.setAliases("pt");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      StringArgumentType playerNameType = StringArgumentType.greedyString();
      RequiredArgumentBuilder nameBuilder = Command.argument("name", playerNameType);
      com.mojang.brigadier.Command<Object> nameExecutor = commandContext -> {
         String playerName = (String)commandContext.getArgument("name", String.class);
         Component fetchingMessage = Component.nullToEmpty("Fetching data...");
         MessageSignature fetchingSignature = ChatUtils.objectSignature(this);
         ChatUtils.printChat(fetchingMessage, fetchingSignature);
         CompletableFuture<JsonObject> request = this.fetchData("playtime", playerName);
         BiConsumer<JsonObject, Throwable> completion = (playtimeData, requestFailure) -> {
            Object errorDetail = null;
            if (requestFailure != null) {
               errorDetail = requestFailure.getMessage();
            } else {
               try {
                  if (playtimeData.has("playtimeSeconds")) {
                     JsonElement playtimeNullableValue = playtimeData.get("playtimeSeconds");
                     if (playtimeNullableValue.isJsonNull()) {
                        throw new RuntimeException("No data for player.");
                     }

                     JsonElement playtimeValue = playtimeData.get("playtimeSeconds");
                     int totalPlaytimeSeconds = playtimeValue.getAsInt();
                     StringBuilder playtimeSummary = new StringBuilder(playerName);
                     playtimeSummary.append(" has played for ");
                     String durationText = TwoB2tApiCommand.formatDuration(totalPlaytimeSeconds);
                     playtimeSummary.append(durationText);
                     playtimeSummary.append(".");
                     String summaryText = playtimeSummary.toString();
                     Component summaryMessage = Component.nullToEmpty(summaryText);
                     MessageSignature summarySignature = ChatUtils.objectSignature(this);
                     ChatUtils.printChat(summaryMessage, summarySignature);
                     return;
                  }

                  errorDetail = "Response data doesn't have a required field: playtimeSeconds.";
               } catch (Exception responseFailure) {
                  errorDetail = responseFailure.getMessage();
               }
            }

            if (errorDetail != null) {
               ChatFormatting errorColor = ChatFormatting.RED;
               String errorColorText = String.valueOf(errorColor);
               String errorText = errorColorText + errorDetail;
               Component errorMessage = Component.nullToEmpty(errorText);
               MessageSignature errorSignature = ChatUtils.objectSignature(this);
               ChatUtils.MessagePriority errorPriority = ChatUtils.MessagePriority.MID;
               ChatUtils.printChat(errorMessage, errorSignature, errorPriority);
            }
         };
         request.whenComplete(completion);
         return 1;
      };
      ArgumentBuilder nameArgument = nameBuilder.executes(nameExecutor);
      commandBuilder.then(nameArgument);
   }
}
