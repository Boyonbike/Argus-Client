package dev.argus.client.gui.mixin;

import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.utils.chat.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BetterChat's private-message sound asks Chat Tools what counts as a DM, so the sound, the window and notifications
 * agree, including servers with custom DM patterns. Without Chat Tools on, BetterChat's own check is used.
 */
@Mixin(value = BetterChatModule.class, remap = false)
public abstract class BetterChatDmSoundMixin {
    @Redirect(method = "playPrivateMessageSound", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Ldev/argus/client/utils/chat/ChatUtils;isIncomingWhisper(Ljava/lang/String;)Z"))
    private boolean argus$chatToolsDetectsDms(String text) {
        ChatToolsModule tools = ChatToolsModule.get();
        return tools != null && tools.isToggled() ? tools.isIncomingPrivateMessage(text) : ChatUtils.isIncomingWhisper(text);
    }
}
