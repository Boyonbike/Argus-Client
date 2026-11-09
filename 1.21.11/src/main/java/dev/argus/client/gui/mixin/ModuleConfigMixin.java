package dev.argus.client.gui.mixin;

import com.google.gson.JsonElement;
import dev.argus.client.gui.HudSafeZones;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.HUDModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries an older config's SafeX/SafeY over to the four HUD safe-zone sides. */
@Mixin(value = Module.class, remap = false)
public abstract class ModuleConfigMixin {
    @Inject(method = "fromJson", at = @At("TAIL"), remap = false)
    private void argus$migrateSafeZones(JsonElement json, CallbackInfo callback) {
        if ((Object) this instanceof HUDModule) HudSafeZones.migrate(json);
    }
}
