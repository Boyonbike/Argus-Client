package dev.argus.client.gui.mixin;

import dev.argus.client.gui.HudSafeZones;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.settings.Setting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits the HUD's safe zones into four sides and hides the old HudEditor switch, which only
 * reopened the legacy editor (the HUD module's own on/off switch turns the whole HUD on and off).
 */
@Mixin(value = HUDModule.class, remap = false)
public abstract class HUDModuleMixin {
    @Shadow(remap = false) @Final private Setting<Boolean> hudEditorSetting;
    @Shadow(remap = false) @Final private Setting<Boolean> safeZonesSetting;
    @Shadow(remap = false) @Final private Setting<Integer> safeXSetting;
    @Shadow(remap = false) @Final private Setting<Integer> safeYSetting;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void argus$fourSafeZones(CallbackInfo callback) {
        HudSafeZones.install((Module) (Object) this, safeZonesSetting, safeXSetting, safeYSetting);
        hudEditorSetting.addVisibilityPredicate(value -> false);
    }
}
