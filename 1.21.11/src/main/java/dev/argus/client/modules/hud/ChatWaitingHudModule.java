package dev.argus.client.modules.hud;

import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What Chat Tools is waiting on: one line each for /home, /tpa and /hotspot (warmup, then the cooldown until you can
 * teleport again), incoming request timeouts, unread private messages and a Discord sync problem. Each line appears
 * only while it applies, and each can be moved on its own in the HUD editor.
 */
public class ChatWaitingHudModule extends HudWidgetModule {
    public ChatWaitingHudModule() {
        super("Chat Waiting", new String[] {"chat-waiting", "unread", "requests", "teleport", "countdown", "cooldown", "home", "hotspot"});
        List<HudTextRow> rows = new ArrayList<>();
        for (ChatTeleportState.Kind kind : ChatTeleportState.Kind.values()) rows.add(line(tools -> tools.countdown(kind)));
        rows.add(line(ChatToolsModule::requestCountdown));
        rows.add(line(tools -> {
            int count = tools.waiting().unread();
            return count == 0 ? "" : count + " unread message" + (count == 1 ? "" : "s");
        }));
        rows.add(line(tools -> tools.waiting().problem() == null ? "" : "Discord sync needs attention"));
        setRenderer(new HudTextListRenderer(this, rows).splittable());
        getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
    }

    /** A line shown while its text is non-empty; it keeps its last text while fading out. */
    private static HudTextRow line(Function<ChatToolsModule, String> text) {
        String[] last = {""};
        return new HudTextRow(() -> {
            String now = read(text);
            if (!now.isEmpty()) last[0] = now;
            return Component.literal(valueWhite(last[0]));
        }, () -> !read(text).isEmpty());
    }

    /** The first number or time in a line (and what follows it) is the value: white, with the words before it left as the title colour. */
    private static final Pattern VALUE = Pattern.compile("(?:^| )(~?\\d+[hms]?)(?= |$)");

    private static String valueWhite(String text) {
        Matcher match = VALUE.matcher(text);
        if (!match.find()) return text;
        int at = match.start(1);
        return text.substring(0, at) + ChatFormatting.WHITE + text.substring(at);
    }

    private static String read(Function<ChatToolsModule, String> text) {
        ChatToolsModule tools = ChatToolsModule.get();
        return tools == null || !tools.isToggled() ? "" : text.apply(tools);
    }
}
