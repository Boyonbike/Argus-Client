package dev.argus.client.gui.mixin;
import dev.argus.client.config.PresetData;
import dev.argus.client.config.importing.ImportedPresetStore;
import dev.argus.client.managers.PresetRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Commands and automatic server/world loads share the checked imported Modules loader. */
@Mixin(value=PresetRegistry.class,remap=false)
public abstract class ImportedPresetMixin {
    @Shadow public abstract PresetData getPreset(String name);
    @Inject(method="applyPreset",at=@At("HEAD"),cancellable=true,remap=false)
    private void argus$loadImported(String name,CallbackInfoReturnable<Boolean> cir) {
        PresetData preset=getPreset(name);
        if(preset!=null && ImportedPresetStore.isImported(preset.contents()))cir.setReturnValue(ImportedPresetStore.load(name).success());
    }
}
