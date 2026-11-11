package dev.argus.client.gui.mixin;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import dev.argus.client.network.SessionRecovery;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * If the session server rejects the join because the launcher handed us an expired token, refresh the matching saved
 * Microsoft account and retry once instead of failing with "Invalid session". Runs on Minecraft's login thread.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class HandshakeSessionRecoveryMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(method = "authenticateServer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"))
    private void argus$joinWithSessionRecovery(MinecraftSessionService sessions, UUID profile, String token, String serverId) throws AuthenticationException {
        try {
            sessions.joinServer(profile, token, serverId);
        } catch (InvalidCredentialsException rejected) {
            if (!SessionRecovery.recoverRejectedJoin(minecraft)) throw rejected;
            var user = minecraft.getUser();
            minecraft.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        }
    }
}
