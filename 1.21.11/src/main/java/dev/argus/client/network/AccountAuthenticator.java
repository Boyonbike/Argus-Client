package dev.argus.client.network;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;

/** Authentication is blocking and must only run on ProfileRuntime.IO. No credential logging. */
public final class AccountAuthenticator {
    public record Login(AccountEntry account, String accessToken) {
        @Override public String toString() { return account.toString(); }
    }
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private AccountAuthenticator() {}
    public static Login microsoft(Consumer<String> link) throws Exception {
        var manager = JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(DeviceCodeMsaAuthService::new,
                (Consumer<MsaDeviceCode>) code -> link.accept(code.getDirectVerificationUri()));
        return microsoftResult(manager, UUID.randomUUID().toString());
    }
    public static Login authenticate(AccountEntry entry) throws Exception {
        return switch (entry.type()) {
            case OFFLINE -> new Login(entry, "");
            case MICROSOFT -> microsoftResult(JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(entry.credential()), entry.id());
            case SESSION -> {
                JsonObject profile = request(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile")).header("Authorization", "Bearer " + entry.credential()).GET());
                yield new Login(new AccountEntry(entry.id(), entry.type(), profile.get("name").getAsString(), uuid(profile.get("id").getAsString()).toString(), entry.credential()), entry.credential());
            }
            case ALTENING -> {
                JsonObject payload = new JsonObject(); JsonObject agent = new JsonObject(); agent.addProperty("name", "Minecraft"); agent.addProperty("version", 1); payload.add("agent", agent);
                payload.addProperty("username", entry.credential()); payload.addProperty("password", "Argus"); payload.addProperty("clientToken", UUID.randomUUID().toString()); payload.addProperty("requestUser", true);
                JsonObject response = request(HttpRequest.newBuilder(URI.create("http://authserver.thealtening.com/authenticate")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload.toString())));
                JsonObject profile = response.getAsJsonObject("selectedProfile");
                yield new Login(new AccountEntry(entry.id(), entry.type(), profile.get("name").getAsString(), uuid(profile.get("id").getAsString()).toString(), entry.credential()), response.get("accessToken").getAsString());
            }
        };
    }
    private static Login microsoftResult(JavaAuthManager manager, String id) throws Exception {
        // Read the potentially rotated refresh token after obtaining the complete login chain.
        var token = manager.getMinecraftToken().getUpToDate(); var profile = manager.getMinecraftProfile().getUpToDate();
        return new Login(new AccountEntry(id, AccountEntry.Type.MICROSOFT, profile.getName(), profile.getId().toString(), manager.getMsaToken().getUpToDate().getRefreshToken()), token.getToken());
    }
    private static JsonObject request(HttpRequest.Builder builder) throws Exception {
        var response = HTTP.send(builder.timeout(Duration.ofSeconds(25)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Account authentication was rejected. Check the token or sign in again.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static UUID uuid(String text) {
        if (text.matches("[0-9a-fA-F]{32}")) text = text.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(text);
    }
}
