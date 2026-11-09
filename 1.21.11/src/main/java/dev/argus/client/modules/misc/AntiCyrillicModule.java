package dev.argus.client.modules.misc;

import dev.argus.client.events.ClientPacketListenerSendChatEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.mixin.ServerboundChatCommandPacketAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public class AntiCyrillicModule extends Module {
   private static final Map<Character, String> TRANSLITERATIONS = createTransliterations();
   private static final int MESSAGE_LENGTH_LIMIT = 256;
   private final Setting<AntiCyrillicModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", AntiCyrillicModule.ModeChoice.CHAT));

   public AntiCyrillicModule() {
      super("AntiCyrillic", "Replaces cyrillic letters in your messages with latin equivalents.", ModuleCategory.MISC);
   }

   @Override
   public String getInfo() {
      try {
         return FontRenderer.capitalize(this.modeSetting.getValue());
      } catch (Exception ignored) {
         return null;
      }
   }

   @EventHandler
   private void onCommandPacket(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundChatCommandPacket packet && this.modeSetting.getValue() != AntiCyrillicModule.ModeChoice.CHAT) {
         ((ServerboundChatCommandPacketAccessor)(Object)packet).setCommand(this.normalizeMessage(packet.command()));
      }
   }

   @EventHandler
   private void onChatMessage(ClientPacketListenerSendChatEvent event) {
      if (this.modeSetting.getValue() != AntiCyrillicModule.ModeChoice.COMMANDS) {
         String message = event.getMessage();
         event.setMessage(this.normalizeMessage(message));
      }
   }

   private String normalizeMessage(String message) {
      String normalized = message;
      if (!normalized.startsWith("/")) {
         normalized = this.transliterate(normalized);
      } else {
         String[] words = normalized.split(" ");
         if (words.length > 1) {
            StringBuilder command = new StringBuilder();

            for (int index = words.length - 1; index >= 0; index--) {
               String word = index == 0 ? words[0] : this.transliterate(words[index]);
               command.insert(0, word + " ");
            }

            normalized = command.toString();
         }
      }

      return normalized.length() > 256 ? normalized.substring(0, 256) : normalized;
   }

   private String transliterate(String message) {
      String translated = message;

      for (int index = translated.length() - 1; index >= 0; index--) {
         char character = translated.charAt(index);
         if (TRANSLITERATIONS.containsKey(character)) {
            String prefix = translated.substring(0, index);
            String replacement = TRANSLITERATIONS.get(character);
            String suffix = translated.substring(index + 1);
            translated = joinTransliterationParts(prefix, replacement, suffix);
         }
      }

      return translated;
   }

   private static String joinTransliterationParts(String prefix, String replacement, String suffix) {
      String recipe = "\u0001\u0001\u0001";

      for (String part : new String[]{prefix, replacement, suffix}) {
         recipe = recipe.replaceFirst("\u0001", Matcher.quoteReplacement(part));
      }

      return recipe;
   }

   private static Map<Character, String> createTransliterations() {
      Map<Character, String> replacements = new HashMap<>();
      replacements.put('Ё', "E");
      replacements.put('А', "A");
      replacements.put('Б', "6");
      replacements.put('В', "B");
      replacements.put('Г', "r");
      replacements.put('Д', "D");
      replacements.put('Е', "E");
      replacements.put('Ж', ")I(");
      replacements.put('З', "3");
      replacements.put('И', "U");
      replacements.put('Й', "u'");
      replacements.put('К', "K");
      replacements.put('Л', "JI");
      replacements.put('М', "M");
      replacements.put('Н', "H");
      replacements.put('О', "O");
      replacements.put('П', "II");
      replacements.put('Р', "P");
      replacements.put('С', "C");
      replacements.put('Т', "T");
      replacements.put('У', "y");
      replacements.put('Ф', "qp");
      replacements.put('Х', "X");
      replacements.put('Ц', "U,");
      replacements.put('Ч', "4");
      replacements.put('Ш', "LLI");
      replacements.put('Щ', "LLI,");
      replacements.put('Ъ', "'b");
      replacements.put('Ы', "bI");
      replacements.put('Ь', "b");
      replacements.put('Э', "3");
      replacements.put('Ю', "IO");
      replacements.put('Я', "9I");
      replacements.put('а', "a");
      replacements.put('б', "6");
      replacements.put('в', "B");
      replacements.put('г', "r");
      replacements.put('д', "D");
      replacements.put('е', "e");
      replacements.put('ж', ")I(");
      replacements.put('з', "3");
      replacements.put('и', "u");
      replacements.put('й', "u'");
      replacements.put('к', "K");
      replacements.put('л', "JI");
      replacements.put('м', "M");
      replacements.put('н', "H");
      replacements.put('о', "o");
      replacements.put('п', "n");
      replacements.put('р', "p");
      replacements.put('с', "c");
      replacements.put('т', "T");
      replacements.put('у', "y");
      replacements.put('ф', "qp");
      replacements.put('х', "X");
      replacements.put('ц', "u,");
      replacements.put('ч', "4");
      replacements.put('ш', "LLI");
      replacements.put('щ', "LLI,");
      replacements.put('ъ', "'b");
      replacements.put('ы', "bI");
      replacements.put('ь', "b");
      replacements.put('э', "3");
      replacements.put('ю', "IO");
      replacements.put('я', "9I");
      replacements.put('ё', "E");
      return replacements;
   }

   public enum ModeChoice implements NamedEntry {
      CHAT("Chat"),
      COMMANDS("Commands"),
      BOTH("Both");

      private final String displayName;

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
