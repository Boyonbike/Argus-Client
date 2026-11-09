package dev.argus.client.gui.mixin;

import dev.argus.client.modules.misc.ChatToolsModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Left-clicking a player's name in open chat opens Chat Tools on that player. Names count whether the server styled
 * them (insertion, hover or click) or wrote them as plain text, as long as the player is online.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenPlayerClickMixin {
    @Shadow private CommandSuggestions commandSuggestions;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void argus$openChatTools(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ChatToolsModule tools = ChatToolsModule.get();
        if (tools == null || !tools.isToggled() || event.button() != 0 || commandSuggestions.isVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        var finder = new PlayerNameStyleFinder(mc.font, (int) event.x(), (int) event.y(), argus$onlineNames(mc));
        finder.includeInsertions(true);
        mc.gui.getChat().captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.getGuiTicks(), true);
        String player = tools.playerFor(finder.result());
        if (player != null && tools.openWindow(player)) cir.setReturnValue(true);
    }

    @Unique
    private static Map<String, String> argus$onlineNames(Minecraft mc) {
        Map<String, String> names = new HashMap<>();
        if (mc.getConnection() == null) return names;
        mc.getConnection().getOnlinePlayers().forEach(info -> {
            String name = info.getProfile().name();
            names.put(name.toLowerCase(Locale.ROOT), name);
        });
        return names;
    }

    /** Marks plain, unstyled online names as clickable for the search only; what chat draws doesn't change. */
    @Unique
    private static final class PlayerNameStyleFinder extends ActiveTextCollector.ClickableStyleFinder {
        private final Map<String, String> onlineNames;

        private PlayerNameStyleFinder(Font font, int x, int y, Map<String, String> onlineNames) {
            super(font, x, y);
            this.onlineNames = onlineNames;
        }

        @Override
        public void accept(TextAlignment alignment, int x, int y, ActiveTextCollector.Parameters parameters, FormattedCharSequence text) {
            List<Glyph> glyphs = new ArrayList<>();
            text.accept((index, style, codePoint) -> { glyphs.add(new Glyph(style, codePoint)); return true; });
            String[] insertions = new String[glyphs.size()];
            for (int start = 0; start < glyphs.size();) {
                if (!nameCharacter(glyphs.get(start).codePoint())) { start++; continue; }
                int end = start + 1;
                StringBuilder token = new StringBuilder().appendCodePoint(glyphs.get(start).codePoint());
                while (end < glyphs.size() && nameCharacter(glyphs.get(end).codePoint())) token.appendCodePoint(glyphs.get(end++).codePoint());
                String name = onlineNames.get(token.toString().toLowerCase(Locale.ROOT));
                if (name != null) for (int i = start; i < end; i++) insertions[i] = name;
                start = end;
            }
            FormattedCharSequence decorated = sink -> {
                for (int i = 0; i < glyphs.size(); i++) {
                    Glyph glyph = glyphs.get(i);
                    Style style = insertions[i] == null ? glyph.style() : glyph.style().withInsertion(insertions[i]);
                    if (!sink.accept(i, style, glyph.codePoint())) return false;
                }
                return true;
            };
            super.accept(alignment, x, y, parameters, decorated);
        }

        private static boolean nameCharacter(int c) {
            return c == '_' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
        }

        private record Glyph(Style style, int codePoint) {}
    }
}
