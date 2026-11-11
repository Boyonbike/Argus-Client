package dev.argus.client.modules.misc;

import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.FileUtils;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

public class SpammerModule extends Module {
   private final Setting<String> pathSetting = this.add(new StringSetting("Path", "spammer.txt"));
   private final Setting<Float> delaySetting = this.add(new NumberSetting<>("Delay", 5.0F, 0.0F, 300.0F).withUnit("s"));
   private final Setting<Boolean> refreshSetting = this.add(new BooleanSetting("Refresh", false));
   private final Setting<Boolean> smartDelaySetting = this.add(new BooleanSetting("SmartDelay", true));
   private final Setting<Boolean> randomSetting = this.add(new BooleanSetting("Random", false));
   private final Setting<Boolean> autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
   private final Random messageRandom = new Random();
   private final List<String> messages = new ArrayList<>();
   private final ElapsedTimer sendTimer = new ElapsedTimer();
   private int nextMessageIndex = 0;

   private void loadMessages() {
      try {
         Path resolvedPath = ConfigurationManager.SPAMMER_DIRECTORY;
         Setting<String> pathSnapshot = this.pathSetting;
         String selectedPath = pathSnapshot.getValue();
         resolvedPath = resolvedPath.resolve(selectedPath);
         String[] extensions = new String[]{".txt"};
         Path filePath = FileUtils.findExistingWithSuffixes(resolvedPath, extensions);
         File existenceCheckFile = filePath.toFile();
         if (!existenceCheckFile.exists()) {
            throw new RuntimeException("Invalid spammer file path");
         }

         this.nextMessageIndex = 0;
         this.messages.clear();
         List<String> destinationMessages = this.messages;
         File readFile = filePath.toFile();
         Charset charset = StandardCharsets.UTF_8;
         destinationMessages.addAll(org.apache.commons.io.FileUtils.readLines(readFile, charset));
      } catch (Exception exception) {
         if (!this.isWorldUnavailable()) {
            Setting<String> failedPathSnapshot = this.pathSetting;
            String errorMessage = "Failed to open spammer file " + failedPathSnapshot.getValue();
            MutableComponent literalComponent = Component.literal(errorMessage);
            UnaryOperator<Style> errorStyle = style -> {
               ChatFormatting errorColor = ChatFormatting.RED;
               return style.withColor(errorColor);
            };
            MutableComponent errorComponent = literalComponent.withStyle(errorStyle);
            String exceptionText = exception.toString();
            int exceptionHash = exceptionText.hashCode();
            MessageSignature errorSignature = ChatUtils.createSignature(exceptionHash);
            ChatUtils.MessagePriority errorPriority = ChatUtils.MessagePriority.LOW;
            ChatUtils.printChat(errorComponent, errorSignature, errorPriority);
         }
      }
   }

   @Override
   public void onEnable() {
      this.loadMessages();
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundChatPacket) {
         Setting<Boolean> smartDelaySnapshot = this.smartDelaySetting;
         Boolean smartDelayEnabled = smartDelaySnapshot.getValue();
         if (smartDelayEnabled) {
            this.sendTimer.reset();
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      ElapsedTimer timerCapture = this.sendTimer;
      Setting<Float> delaySnapshot = this.delaySetting;
      Float delayValue = delaySnapshot.getValue();
      double delaySeconds = delayValue.floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;
      if (timerCapture.hasElapsed(delaySeconds, delayUnit) && !this.messages.isEmpty()) {
         List<String> messagesSnapshot = this.messages;
         Setting<Boolean> randomSnapshot = this.randomSetting;
         Boolean randomEnabled = randomSnapshot.getValue();
         int selectedIndex;
         if (randomEnabled && this.messages.size() > 1) {
            Random random = this.messageRandom;
            int randomBound = this.messages.size() - 1;
            selectedIndex = random.nextInt(randomBound);
         } else {
            selectedIndex = this.nextMessageIndex;
         }

         String message = messagesSnapshot.get(selectedIndex);
         ChatUtils.sendChatOrCommand(message);
         this.nextMessageIndex = (this.nextMessageIndex + 1) % this.messages.size();
         timerCapture = this.sendTimer;
         timerCapture.reset();
         Setting<Boolean> autoDisableSnapshot = this.autoDisableSetting;
         Boolean autoDisableEnabled = autoDisableSnapshot.getValue();
         if (autoDisableEnabled) {
            this.toggle();
         }
      }
   }

   public SpammerModule() {
      super("Spammer", "Spams messages from a selected text file.", ModuleCategory.MISC);
      this.pathSetting.setChangeCallback(this::loadMessages);
      this.refreshSetting.setChangeCallback(() -> {
         Boolean refreshRequested = this.refreshSetting.getValue();
         if (refreshRequested) {
            this.refreshSetting.setValue(false);
            this.loadMessages();
         }
      });
   }
}
