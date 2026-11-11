package dev.argus.client.gui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.modules.misc.ChatToolsModule;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Clan colours in the tab list: on the name and, with its own colour, as a bar behind the row. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListClanHighlightMixin {
    /** Wraps the call, not the method, so it colours the name after ExtraTab's friend/enemy colouring has run. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component argus$colourClanName(PlayerTabOverlay overlay, PlayerInfo info, Operation<Component> original) {
        Component name = original.call(overlay, info);
        ChatToolsModule tools = argus$tools();
        return tools != null ? tools.highlightTabName(info, name) : name;
    }

    // 1.21.11 render locals: slot 22 is the row index and slot 5 the ordered PlayerInfo list.
    /** The third fill is each row's own background; the head, name and ping are drawn over it afterwards. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 2))
    private void argus$clanRowBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original,
                                  @Local(index = 22) int index, @Local(index = 5) List<PlayerInfo> infos) {
        original.call(graphics, x0, y0, x1, y1, color);
        ChatToolsModule tools = argus$tools();
        if (tools == null || index >= infos.size()) return;
        int highlight = tools.tabHighlight(infos.get(index));
        if (highlight != 0) graphics.fill(x0, y0, x1, y1, highlight);
    }

    @Unique
    private static ChatToolsModule argus$tools() {
        ChatToolsModule tools = ChatToolsModule.get();
        return tools != null && tools.isToggled() ? tools : null;
    }
}
