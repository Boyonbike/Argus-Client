package dev.argus.client.utils.chat;

import com.mojang.logging.LogUtils;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.NotificationsModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.DynamicColorStyle;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import org.slf4j.Logger;

public final class ChatUtils implements ClientAccess {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final MessageSignature DEFAULT_SIGNATURE = createSignature(-1);
   private static final NotificationsModule NOTIFICATIONS = ClientContext.getModule(NotificationsModule.class);
   public static final GuiMessageTag MESSAGE_TAG = new GuiMessageTag(-1, null, Component.empty(), "Argus Client");
   private static final Pattern ARROW_WHISPER_PATTERN = Pattern.compile("^(\\w{3,16}) -> (\\w{3,16}): (.*)");
   public static final List<Pattern> INCOMING_WHISPER_PATTERNS = List.of(
      Pattern.compile("^From (\\w{3,16}): (.*)"),
      Pattern.compile("^from (\\w{3,16}): (.*)"),
      Pattern.compile("^(\\w{3,16}) whispers: (.*)"),
      Pattern.compile("^(\\w{3,16}) -> me (.*)"),
      Pattern.compile("^(\\w{3,16}) whispers to you: (.*)"),
      Pattern.compile("^(\\w{3,16}) says: (.*)"),
      Pattern.compile("^(\\w{3,16}) пишет: (.*)"),
      ARROW_WHISPER_PATTERN
   );
   public static final List<Pattern> OUTGOING_WHISPER_PATTERNS = List.of(
      Pattern.compile("^To (\\w{3,16}): (.*)"),
      Pattern.compile("^to (\\w{3,16}): (.*)"),
      Pattern.compile("^\\[me -> (\\w{3,16})\\] (.*)"),
      Pattern.compile("^You whisper to (\\w{3,16}): (.*)"),
      Pattern.compile("^К (\\w{3,16}): (.*)"),
      ARROW_WHISPER_PATTERN
   );

   public static boolean isIncomingWhisper(String message) {
      String username = ClientAccess.argusclientMinecraft.getUser().getName().toLowerCase(Locale.ROOT);
      if (message.toLowerCase(Locale.ROOT).startsWith(username)) {
         return false;
      }

      if (!message.contains(" шепчет: ") && !message.contains(" шепчет тебе: ")) {
         for (Pattern pattern : INCOMING_WHISPER_PATTERNS) {
            if (pattern.matcher(message).find()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public static ClickEvent suggestCommand(String command) {
      return new SuggestCommand(CommandRegistry.getPrefix() + command);
   }

   public static Component createPrefix() {
      return Component.empty()
         .append(Component.literal("[").withStyle(style -> withDynamicColor(style, NOTIFICATIONS::getBracketColor)))
         .append(Component.literal("Argus Client").withStyle(style -> withDynamicColor(style, NOTIFICATIONS::getWatermarkColor)))
         .append(Component.literal("]").withStyle(style -> withDynamicColor(style, NOTIFICATIONS::getBracketColor)));
   }

   public static Component bracketedText(String text) {
      return Component.empty()
         .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
         .append(text)
         .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
   }

   public static MessageSignature objectSignature(Object value) {
      return createSignature(-Math.abs(value.hashCode()));
   }

   private ChatUtils() {
   }

   public static void sendChatOrCommand(String message) {
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
      if (message.startsWith("/")) {
         connection.sendCommand(message.substring(1));
      } else {
         connection.sendChat(message);
      }
   }

   public static boolean isOutgoingWhisper(String message) {
      String username = ClientAccess.argusclientMinecraft.getUser().getName().toLowerCase(Locale.ROOT);
      boolean startsWithUsername = message.toLowerCase(Locale.ROOT).startsWith(username);

      for (Pattern pattern : OUTGOING_WHISPER_PATTERNS) {
         if ((pattern != ARROW_WHISPER_PATTERN || startsWithUsername) && pattern.matcher(message).find()) {
            return true;
         }
      }

      return false;
   }

   public static Style withDynamicColor(Style style, Supplier<Integer> colorSupplier) {
      return ((DynamicColorStyle)(Object)style.withColor(ThreadLocalRandom.current().nextInt())).argusclient$withColor(colorSupplier);
   }

   public static Component rainbowText(String text, Supplier<Integer> offsetSupplier, Color baseColor) {
      MutableComponent result = Component.empty();
      float[] hsb = Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);

      for (int index = 0; index < text.length(); index++) {
         int characterIndex = index;
         result.append(
            Component.literal(String.valueOf(text.charAt(index)))
               .withStyle(style -> withDynamicColor(style, () -> ColorUtils.rainbow(characterIndex * offsetSupplier.get(), hsb[1], hsb[2], 255).getRGB()))
         );
      }

      return result;
   }

   public static void printChat(Component message, MessageSignature signature, ChatUtils.MessagePriority priority) {
      MutableComponent decorated = Component.empty()
         .append(Component.literal("[").withStyle(style -> style.withColor(priority.getColor().darker().hashCode())))
         .append(Component.literal("!").withStyle(style -> style.withColor(priority.getColor().hashCode())))
         .append(Component.literal("] ").withStyle(style -> style.withColor(priority.getColor().darker().hashCode())));
      printChat(decorated.append(message), signature);
   }

   public static void printChat(Component message, MessageSignature signature) {
      ClientAccess.argusclientMinecraft
         .execute(
            () -> {
               try {
                  ClientAccess.argusclientMinecraft
                     .gui.hud
                     .getChat()
                     .addPlayerMessage(Component.empty().append(createPrefix()).append(" ").append(message), signature, MESSAGE_TAG);
               } catch (RuntimeException failure) {
                  LOGGER.warn("Could not display chat message", failure);
               }
            }
         );
   }

   public static MessageSignature moduleSignature(Module module) {
      return createSignature(-Math.abs(module.getName().hashCode()));
   }

   public static void printChat(Component message, int id) {
      printChat(message, createSignature(id));
   }

   public static MutableComponent watermarkText(String text) {
      return text == null
         ? Component.empty()
         : Component.literal(text).withStyle(style -> withDynamicColor(style, () -> NOTIFICATIONS.watermarkSetting.getValue().hashCode()));
   }

   public static MessageSignature createSignature(int id) {
      return new MessageSignature(ByteBuffer.allocate(256).putInt(id).array());
   }

   public enum MessagePriority {
      HIGH {
         @Override
         public Color getColor() {
            return new Color(225, 10, 10);
         }
      },
      MID {
         @Override
         public Color getColor() {
            return new Color(225, 170, 10);
         }
      },
      LOW {
         @Override
         public Color getColor() {
            return new Color(225, 225, 225);
         }
      };

      public abstract Color getColor();
   }
}
