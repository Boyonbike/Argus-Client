package dev.argus.mixin;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.argus.client.integrations.ProtocolCompatibility;
import dev.argus.client.integrations.ProtocolVersionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(ProtocolTranslationImpl.class)
public abstract class ProtocolTranslatorMixin {
   @Shadow(remap = false)
   private ProtocolVersion targetVersion;

   @Inject(method = "setTargetVersion(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;Z)V", at = @At("HEAD"), remap = false)
   private void syncTargetProtocolVersion(ProtocolVersion version, boolean ignoredProtocolFlag, CallbackInfo callback) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)version);
   }

   @Inject(method = "<init>", at = @At("TAIL"), remap = false)
   private void initializeTargetProtocolVersion(CallbackInfo callback) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)this.targetVersion);
   }
}
