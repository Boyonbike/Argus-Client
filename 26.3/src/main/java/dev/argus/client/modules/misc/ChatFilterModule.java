package dev.argus.client.modules.misc;

import dev.argus.client.ClientContext;
import dev.argus.client.events.ChatMessageEvent;
import dev.argus.client.events.ClientPacketListenerSendChatEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;

public class ChatFilterModule extends Module {
   public final Setting<Boolean> caseSensitiveSetting = this.add(new BooleanSetting("CaseSensitive", false));
   private final Setting<Boolean> ignoreSelfSetting = this.add(new BooleanSetting("IgnoreSelf", true));
   private final Setting<Boolean> ignorePrivateMessagesSetting;
   private String lastSentMessage;

   public ChatFilterModule() {
      super("ChatFilter", "Filters chat messages based on your filters.", ModuleCategory.MISC);
      Boolean ignorePrivateMessages = false;
      this.ignorePrivateMessagesSetting = this.add(new BooleanSetting("IgnorePM", ignorePrivateMessages));
      this.lastSentMessage = null;
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable() && ClientContext.chatFilterRegistry.isEmpty()) {
         String notice = "You don't have any chat filters set. Use the \"%schatfilter add <id> <filter>\" command to add new filters."
            .formatted(CommandRegistry.getPrefix());
         ChatUtils.printChat(Component.nullToEmpty(notice), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.LOW);
      }

      this.lastSentMessage = null;
   }

   @EventHandler(priority = 200)
   private void onIncomingChat(ChatMessageEvent event) {
      if (event.getStage() == EventStage.Pre && event.getMessage() != null) {
         GuiMessageTag tag = event.getTag();
         if (tag == null || tag == GuiMessageTag.system() || tag == GuiMessageTag.systemSinglePlayer() || tag == GuiMessageTag.chatNotSecure()) {
            String text = event.getMessage().getString();
            if (text != null) {
               if (this.ignoreSelfSetting.getValue() && this.lastSentMessage != null && text.contains(this.lastSentMessage)) {
                  this.lastSentMessage = null;
               } else if (!this.ignorePrivateMessagesSetting.getValue() || !ChatUtils.isIncomingWhisper(text)) {
                  if (!ClientContext.chatFilterRegistry.isEmpty() && ClientContext.chatFilterRegistry.matchesMessage(text)) {
                     event.cancel();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onOutgoingChat(ClientPacketListenerSendChatEvent event) {
      if (!event.isCancelled() && event.getMessage() != null && this.ignoreSelfSetting.getValue()) {
         this.lastSentMessage = event.getMessage();
      }
   }
}
