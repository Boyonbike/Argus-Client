package dev.argus.client.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public class QueueCommand extends TwoB2tApiCommand {
   public QueueCommand() {
      super("queue");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      com.mojang.brigadier.Command<Object> executor = ignoredContext -> {
         Component fetchingMessage = Component.nullToEmpty("Fetching data...");
         MessageSignature fetchingSignature = ChatUtils.objectSignature(this);
         ChatUtils.printChat(fetchingMessage, fetchingSignature);
         CompletableFuture<JsonObject> request = this.fetchData("queue", null);
         BiConsumer<JsonObject, Throwable> completion = (queueData, requestFailure) -> {
            Object errorDetail = null;
            if (requestFailure != null) {
               errorDetail = requestFailure.getMessage();
            } else {
               try {
                  int priorityQueue = -1337;
                  int regularQueue = -1337;
                  if (queueData.has("prio")) {
                     priorityQueue = queueData.get("prio").getAsInt();
                  }

                  if (queueData.has("regular")) {
                     regularQueue = queueData.get("regular").getAsInt();
                  }

                  StringBuilder queueSummary = new StringBuilder();
                  if (regularQueue != -1337) {
                     queueSummary.append("%d in normal queue".formatted(regularQueue));
                  }

                  if (regularQueue != -1337 && priorityQueue != -1337) {
                     queueSummary.append(", ");
                  }

                  if (priorityQueue != -1337) {
                     queueSummary.append("%d in priority queue".formatted(priorityQueue));
                  }

                  String summaryText = queueSummary.toString();
                  Component summaryMessage = Component.nullToEmpty(summaryText);
                  MessageSignature summarySignature = ChatUtils.objectSignature(this);
                  ChatUtils.printChat(summaryMessage, summarySignature);
                  return;
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
      commandBuilder.executes(executor);
   }
}
