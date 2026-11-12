package dev.argus.client.gui.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface AccountSessionAccessor {
    @Mutable @Accessor("user") void argus$setUser(User value);
    @Mutable @Accessor("services") void argus$setServices(Services value);
    @Mutable @Accessor("userApiService") void argus$setApi(UserApiService value);
    @Mutable @Accessor("playerSocialManager") void argus$setSocial(PlayerSocialManager value);
    @Mutable @Accessor("profileKeyPairManager") void argus$setKeys(ProfileKeyPairManager value);
    @Accessor("reportingContext") void argus$setReporting(ReportingContext value);
    @Mutable @Accessor("skinManager") void argus$setSkins(SkinManager value);
    @Mutable @Accessor("playerSkinRenderCache") void argus$setSkinRenderCache(PlayerSkinRenderCache value);
    @Mutable @Accessor("profileFuture") void argus$setProfile(CompletableFuture<ProfileResult> value);
    @Accessor("profileFuture") CompletableFuture<ProfileResult> argus$profile();
    @Mutable @Accessor("userPropertiesFuture") void argus$setProperties(CompletableFuture<UserApiService.UserProperties> value);
}
