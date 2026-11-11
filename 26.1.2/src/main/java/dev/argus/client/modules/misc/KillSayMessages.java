package dev.argus.client.modules.misc;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FileUtils;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

public final class KillSayMessages {
   private final KillEffectsModule module;
   private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
   private int lineIndex = 0;

   private String nextMessage() {
      try {
         if (this.lines.isEmpty()) {
            return null;
         } else {
            Setting<Boolean> randomOption = this.module.randomSetting;
            Boolean random = randomOption.getValue();
            if (random) {
               List<String> messageLines = this.lines;
               ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
               int lineCount = this.lines.size();
               return messageLines.get(randomSnapshot.nextInt(lineCount));
            } else {
               return this.lines.get(this.lineIndex++ % this.lines.size());
            }
         }
      } catch (Exception selectionFailure) {
         return null;
      }
   }

   public KillSayMessages(KillEffectsModule module) {
      this.module = module;
   }

   void resetSequence() {
      this.lineIndex = 0;
   }

   void sendKillMessage(Player player, String playerName) {
      Setting<Boolean> killSayOption = this.module.killSaySetting;
      Boolean killSay = killSayOption.getValue();
      if (killSay) {
         Setting<Boolean> onlyEnemiesOption = this.module.onlyEnemiesSetting;
         Boolean onlyEnemies = onlyEnemiesOption.getValue();
         if (onlyEnemies) {
            SocialRegistry enemyManagerSnapshot = ClientContext.socialRegistry;
            Player enemyPlayer = player;
            if (!enemyManagerSnapshot.isEnemyPlayer(enemyPlayer)) {
               return;
            }
         }

         String message = this.nextMessage();
         if (message != null) {
            message = message.replace("{name}", playerName);
            String messageToSend = message;
            Runnable sendMessage = () -> ChatUtils.sendChatOrCommand(messageToSend);
            Setting<Float> delayOptionSnapshot = this.module.delaySetting;
            if (delayOptionSnapshot.isAtMinimum()) {
               sendMessage.run();
            } else {
               TaskScheduler scheduler = ClientContext.taskScheduler;
               Setting<Float> delayReadOption = this.module.delaySetting;
               float delaySeconds = delayReadOption.getValue();
               int delayTicks = (int)(delaySeconds / 0.05F);
               scheduler.scheduleRotationTask(sendMessage, delayTicks);
            }
         }
      }
   }

   void reloadMessages(boolean force) {
      Setting<Boolean> killSayOption = this.module.killSaySetting;
      Boolean killSay = killSayOption.getValue();
      if (killSay || force) {
         Path resolvedPath = ConfigurationManager.KILLSAY_DIRECTORY;
         Setting<String> pathOption = this.module.pathSetting;
         resolvedPath = resolvedPath.resolve(pathOption.getValue());
         String[] extensions = new String[]{".txt"};
         Path path = FileUtils.findExistingWithSuffixes(resolvedPath, extensions);

         try {
            File checkFile = path.toFile();
            if (!checkFile.exists()) {
               Object[] missingFileFormatArguments = new Object[1];
               Setting<String> missingPathOption = this.module.pathSetting;
               missingFileFormatArguments[0] = missingPathOption.getValue();
               throw new IOException("KillSay file %s not found".formatted(missingFileFormatArguments));
            }

            this.lines.clear();
            List<String> messageLines = this.lines;
            File readFileSnapshot = path.toFile();
            Charset encoding = StandardCharsets.UTF_8;
            messageLines.addAll(org.apache.commons.io.FileUtils.readLines(readFileSnapshot, encoding));
            this.lineIndex = 0;
         } catch (Exception loadFailure) {
            Setting<Boolean> failureKillSayOption = this.module.killSaySetting;
            Boolean showFailure = failureKillSayOption.getValue();
            if (showFailure) {
               String failureMessage = "Failed to update KillSay lines: " + loadFailure;
               MutableComponent notification = Component.literal(failureMessage);
               UnaryOperator<Style> notificationStyler = style -> {
                  ChatFormatting notificationColor = ChatFormatting.RED;
                  Style notificationStyle = style;
                  return notificationStyle.withColor(notificationColor);
               };
               notification = notification.withStyle(notificationStyler);
               MessageSignature signature = ChatUtils.createSignature(-9634482);
               ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.LOW;
               ChatUtils.printChat(notification, signature, priority);

               try {
                  FileAttribute<?>[] attributes = new FileAttribute[0];
                  Files.createFile(path, attributes);
               } catch (IOException var17) {
               }
            }
         }
      }
   }
}
