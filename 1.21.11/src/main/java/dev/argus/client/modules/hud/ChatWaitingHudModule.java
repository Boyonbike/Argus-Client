package dev.argus.client.modules.hud;

import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * What Chat Tools is waiting on you for: teleport requests, unread private messages and a Discord sync problem. Each
 * line appears only while it applies, so the widget is empty most of the time. Placed and styled in the HUD editor.
 */
public class ChatWaitingHudModule extends HudWidgetModule {
    public ChatWaitingHudModule() {
        super("Chat Waiting", new String[] {"chat-waiting", "unread", "requests"});
        List<HudTextRow> rows = new ArrayList<>();
        rows.add(new HudTextRow(() -> {
            int count = waiting().requests();
            return Component.literal(count + " teleport request" + (count == 1 ? "" : "s"));
        }, () -> waiting().requests() > 0));
        rows.add(new HudTextRow(() -> {
            int count = waiting().unread();
            return Component.literal(count + " unread message" + (count == 1 ? "" : "s"));
        }, () -> waiting().unread() > 0));
        rows.add(new HudTextRow(() -> Component.literal("Discord sync needs attention"), () -> waiting().problem() != null));
        setRenderer(new HudTextListRenderer(this, rows));
        getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
    }

    private static ChatToolsModule.Waiting waiting() {
        ChatToolsModule tools = ChatToolsModule.get();
        return tools == null || !tools.isToggled() ? new ChatToolsModule.Waiting(0, 0, null) : tools.waiting();
    }
}
