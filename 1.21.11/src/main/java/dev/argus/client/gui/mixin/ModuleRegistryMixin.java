package dev.argus.client.gui.mixin;

import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.misc.ChatToolsModule;
import dev.argus.client.modules.player.AutoRekitModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register before the core loads module config, so toggles/binds/presets persist normally. */
@Mixin(value = ModuleRegistry.class, remap = false)
public abstract class ModuleRegistryMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void argus$registerModules(CallbackInfo ci) {
        ((ModuleRegistry) (Object) this).registerModule(new AutoRekitModule());
        ChatToolsModule chatTools = new ChatToolsModule();
        DiscordSyncModule discordSync = new DiscordSyncModule();
        discordSync.attach(chatTools);
        ((ModuleRegistry) (Object) this).registerModule(chatTools);
        ((ModuleRegistry) (Object) this).registerModule(discordSync);
    }
}
