package dev.argus.client.gui.mixin;

import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BetterChat's name highlighter runs on every chat line as it's wrapped, even when its own highlighting is off. Adding
 * clan colours to its result keeps one place that colours names in chat: friends and enemies first (BetterChat), then
 * clan members (Chat Tools), never twice.
 */
@Mixin(value = BetterChatModule.class, remap = false)
public abstract class BetterChatClanHighlightMixin {
    @Inject(method = "highlightNames", at = @At("RETURN"), cancellable = true, remap = false)
    private void argus$highlightClan(FormattedText text, CallbackInfoReturnable<FormattedText> cir) {
        ChatToolsModule tools = ChatToolsModule.get();
        if (tools == null || !tools.isToggled() || !(cir.getReturnValue() instanceof Component line)) return;
        Component highlighted = tools.highlightClan(line);
        if (highlighted != line) cir.setReturnValue(highlighted);
    }
}
