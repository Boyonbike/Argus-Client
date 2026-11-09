package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.access.VisibleChatLinesAccess;
import dev.argus.client.events.ChatMessageEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.network.chat.Component;

public class AntiSpamModule extends Module {
   private static final Pattern URL_PATTERN = Pattern.compile("(https?://)?([\\w\\-])+\\.{1}([a-zA-Z]{2,63})([/\\w-]*)*/?\\??([^#\\n\\r]*)?#?([^\\n\\r]*)");
   private static final Pattern INVITE_PATTERN = Pattern.compile("gg/([a-zA-Z0-9]*)");
   private final Map<String, AntiSpamModule.MessageStack> messageStacks = new HashMap<>();
   public final Setting<Boolean> indicatorSetting = this.add(new BooleanSetting("Indicator", true));
   private final Setting<Boolean> linksSetting = this.add(new BooleanSetting("Links", false));
   private String pendingMessage;

   public AntiSpamModule() {
      super("AntiSpam", "Stacks similar chat messages with each other.", ModuleCategory.MISC);
      this.setDrawn(false);
   }

   @Override
   public void onToggle() {
      this.messageStacks.clear();
      this.pendingMessage = null;
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.messageStacks.clear();
      this.pendingMessage = null;
   }

   @EventHandler(priority = 200)
   private void onChatLine(ChatMessageEvent event) {
      if (event.getStage() == EventStage.Pre) {
         if (event.getSignature() != null && event.getSignature().asByteBuffer().getInt() < 0) {
            return;
         }

         if (event.getTag() == ChatUtils.MESSAGE_TAG) {
            return;
         }

         this.pendingMessage = event.getMessage().getString().toLowerCase(Locale.ROOT);
         if (this.linksSetting.getValue() && this.containsLink(this.pendingMessage)) {
            event.cancel();
            return;
         }

         if (this.indicatorSetting.getValue()) {
            this.messageStacks.computeIfPresent(this.pendingMessage, (message, stack) -> {
               ((VisibleChatLinesAccess)ClientAccess.argusclientMinecraft.gui.getChat()).getVisible().remove(stack.line());
               return (AntiSpamModule.MessageStack)stack;
            });
            this.messageStacks.putIfAbsent(this.pendingMessage, new AntiSpamModule.MessageStack(1L, event.getLine()));
            long count = this.messageStacks.get(this.pendingMessage).count();
            if (count > 1L) {
               event.setMessage(Component.empty().append(event.getMessage()).append(Component.literal(" [x" + count + "]")));
            }
         }
      } else if (this.pendingMessage != null && this.indicatorSetting.getValue()) {
         this.messageStacks.computeIfPresent(this.pendingMessage, (message, stack) -> new AntiSpamModule.MessageStack(stack.count() + 1L, event.getLine()));
      }
   }

   private boolean containsLink(String message) {
      return URL_PATTERN.matcher(message).find() || INVITE_PATTERN.matcher(message).find();
   }

   private record MessageStack(long count, Line line) {
   }
}
