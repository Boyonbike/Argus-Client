package dev.argus.client.network;

import com.mojang.authlib.Environment;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.*;
import dev.argus.client.gui.mixin.AccountSessionAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.*;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;

public final class AccountSession {
    private AccountSession() {}
    public record Prepared(AccountAuthenticator.Login login, User user, Services services, UserApiService api) {}
    /** Network-bearing setup occurs before any live Minecraft state is changed. */
    public static Prepared prepare(AccountAuthenticator.Login login, java.net.Proxy proxy, java.io.File gameDir) {
        var account = login.account();
        boolean offline = account.type() == AccountEntry.Type.OFFLINE;
        var auth = account.type() == AccountEntry.Type.ALTENING
                ? new YggdrasilAuthenticationService(proxy, new Environment("http://sessionserver.thealtening.com", "https://api.minecraftservices.com", "https://api.mojang.com", "Altening"))
                : new YggdrasilAuthenticationService(proxy);
        var user = new User(account.username(), AccountAuthenticator.uuid(account.uuid()), login.accessToken(), Optional.empty(), Optional.empty());
        return new Prepared(login, user, Services.create(auth, gameDir), offline ? UserApiService.OFFLINE : auth.createUserApiService(login.accessToken()));
    }
    /** Called on the client thread, once authentication and persistence have succeeded. */
    public static void apply(Minecraft mc, Prepared next) {
        if (mc.level != null || mc.getConnection() != null) throw new IllegalStateException("Disconnect before switching accounts.");
        boolean offline = next.login().account().type() == AccountEntry.Type.OFFLINE;
        var skins = new SkinManager(mc.gameDirectory.toPath().resolve("argusclient/skins"), next.services(), new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc), mc);
        var social = new PlayerSocialManager(mc, next.api());
        var keys = offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : ProfileKeyPairManager.create(next.api(), next.user(), mc.gameDirectory.toPath());
        var reporting = ReportingContext.create(ReportEnvironment.local(), next.api());
        var cache = new PlayerSkinRenderCache(mc.getTextureManager(), skins, next.services().profileResolver());
        var profile = offline ? CompletableFuture.completedFuture(new ProfileResult(new GameProfile(next.user().getProfileId(), next.user().getName()), Set.of()))
                : CompletableFuture.supplyAsync(() -> next.services().sessionService().fetchProfile(next.user().getProfileId(), true), ProfileRuntime.IO);
        var properties = CompletableFuture.supplyAsync(() -> { try { return next.api().fetchProperties(); } catch (Exception ignored) { return UserApiService.OFFLINE_PROPERTIES; } }, ProfileRuntime.IO);
        var access = (AccountSessionAccessor) mc;
        access.argus$setUser(next.user()); access.argus$setServices(next.services()); access.argus$setApi(next.api()); access.argus$setSocial(social);
        access.argus$setKeys(keys); access.argus$setReporting(reporting); access.argus$setSkins(skins); access.argus$setSkinRenderCache(cache);
        access.argus$setProfile(profile); access.argus$setProperties(properties);
    }
}
