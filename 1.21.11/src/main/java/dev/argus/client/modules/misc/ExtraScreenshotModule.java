package dev.argus.client.modules.misc;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.network.HttpRequests;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import org.slf4j.Logger;

public class ExtraScreenshotModule extends Module {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String imgurAuthorization = "Client-ID e7c0b6a4c926098";
   private static final long uploadCooldownMillis = 1500L;
   public final Setting<ExtraScreenshotModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", ExtraScreenshotModule.ModeChoice.COPY));
   public final Setting<Boolean> deleteFileSetting;
   private final Setting<Boolean> soundSetting;
   private final Setting<NamedIdentifier> pathSetting;
   private final Setting<Float> volumeSetting;
   private final ElapsedTimer uploadTimer;
   private int cooldownMessageIndex;
   private static final HttpClient uploadClient = HttpClient.newHttpClient();

   private void playScreenshotSound() {
      Setting<Boolean> soundOption = this.soundSetting;
      Boolean playSound = soundOption.getValue();
      if (playSound) {
         SoundManager soundManagerSnapshot = ClientContext.soundManager;
         Setting<NamedIdentifier> pathOption = this.pathSetting;
         NamedIdentifier soundId = pathOption.getValue();
         SoundClip soundSnapshot = soundManagerSnapshot.getSound(soundId);
         Setting<Float> volumeOption = this.volumeSetting;
         float soundVolume = volumeOption.getValue();
         soundSnapshot.play(soundVolume);
      }
   }

   private ExtraScreenshotModule.ModeChoice getActiveMode() {
      ExtraScreenshotModule.ModeChoice mode = this.modeSetting.getValue();
      return mode == ExtraScreenshotModule.ModeChoice.COPY && GraphicsEnvironment.isHeadless() ? ExtraScreenshotModule.ModeChoice.NONE : mode;
   }

   public boolean isModeNone() {
      return this.getActiveMode() == ExtraScreenshotModule.ModeChoice.NONE;
   }

   public void processScreenshotBytes(byte[] imageBytes) {
      ExtraScreenshotModule.ModeChoice mode = this.getActiveMode();
      if (mode == ExtraScreenshotModule.ModeChoice.IMGUR) {
         synchronized (this) {
            if (!this.uploadTimer.hasElapsed(1500L)) {
               this.cooldownMessageIndex = (this.cooldownMessageIndex + 1) % 3;
               long remaining = 1500L - this.uploadTimer.elapsedMillis();
               MutableComponent message = Component.literal("Wait " + remaining + "ms before uploading a screenshot").withStyle(ChatFormatting.YELLOW);
               ChatUtils.printChat(message, ChatUtils.createSignature(-34596741 + this.cooldownMessageIndex), ChatUtils.MessagePriority.LOW);
               return;
            }

            this.uploadTimer.reset();
         }
      }

      try {
         this.playScreenshotSound();
      } catch (Exception failure) {
         LOGGER.warn("Could not play screenshot sound", failure);
      }

      if (mode != ExtraScreenshotModule.ModeChoice.IMGUR) {
         this.uploadTimer.reset();
      }

      if (mode != ExtraScreenshotModule.ModeChoice.NONE) {
         try {
            if (mode == ExtraScreenshotModule.ModeChoice.COPY) {
               BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
               if (source == null) {
                  throw new IOException("Unsupported screenshot format");
               }

               BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), 1);
               Graphics2D graphics = image.createGraphics();

               try {
                  graphics.drawImage(source, 0, 0, null);
               } finally {
                  graphics.dispose();
               }

               this.copyImageToClipboard(new ClipboardImage(image));
               ChatUtils.printChat(Component.literal("Copied screenshot to clipboard."), ChatUtils.moduleSignature(this));
               return;
            }

            ChatUtils.printChat(Component.literal("Uploading screenshot..."), ChatUtils.moduleSignature(this));
            String encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(imageBytes), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequests.post("https://api.imgur.com/3/image", BodyPublishers.ofString("image=" + encoded))
               .header("Content-Type", "application/x-www-form-urlencoded")
               .header("Authorization", "Client-ID e7c0b6a4c926098")
               .timeout(Duration.ofSeconds(10L))
               .build();
            HttpResponse<String> response = HttpRequests.send(request);
            if (response.statusCode() / 100 != 2) {
               throw new IOException("Imgur returned HTTP " + response.statusCode());
            }

            JsonObject json = (JsonObject)ClientAccess.gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("success") || !json.get("success").getAsBoolean() || !json.has("data")) {
               throw new IOException("Invalid upload response");
            }

            String link = json.getAsJsonObject("data").get("link").getAsString();
            URI uri = URI.create(link);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
               throw new IOException("Invalid image link");
            }

            ClientAccess.argusclientMinecraft.execute(() -> {
               ClientAccess.argusclientMinecraft.keyboardHandler.setClipboard(link);
               MutableComponent notification = Component.literal("Copied screenshot link.").withStyle(style -> style.withClickEvent(new OpenUrl(uri)));
               ChatUtils.printChat(notification, -43296436);
            });
         } catch (Exception failure) {
            LOGGER.warn("Screenshot {} failed", mode == ExtraScreenshotModule.ModeChoice.COPY ? "copy" : "upload", failure);
            MutableComponent message = Component.literal(
                  mode == ExtraScreenshotModule.ModeChoice.COPY ? "Failed to copy the screenshot!" : "Failed to upload screenshot to Imgur"
               )
               .withStyle(ChatFormatting.RED);
            ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
         }
      }
   }

   @Override
   public String getInfo() {
      Setting<ExtraScreenshotModule.ModeChoice> modeOption = this.modeSetting;
      return FontRenderer.capitalize(modeOption.getValue());
   }

   @Override
   public void onEnable() {
      this.cooldownMessageIndex = 0;
   }

   public boolean shouldDeleteFile() {
      return !this.isModeNone() && this.deleteFileSetting.getValue();
   }

   private void copyImageToClipboard(ClipboardImage image) {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      Clipboard clipboard = toolkit.getSystemClipboard();
      clipboard.setContents(image, null);
   }

   public void processScreenshot(NativeImage image) {
      byte[] imageBytes;
      try {
         imageBytes = this.encodeScreenshot(image);
         if (imageBytes == null) {
            throw new RuntimeException();
         }
      } catch (Exception encodingFailure) {
         Component message = Component.nullToEmpty("Failed to save the screenshot");
         ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
         return;
      } finally {
         if (this.shouldDeleteFile()) {
            image.close();
         }
      }

      ClientAccess.executorService.submit(() -> {
         try {
            this.processScreenshotBytes(imageBytes);
         } catch (Exception processingFailure) {
            LOGGER.warn("Could not process screenshot", processingFailure);
         }
      });
   }

   private byte[] encodeScreenshot(NativeImage image) throws IOException {
      try (
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         WritableByteChannel channel = Channels.newChannel(output);
      ) {
         if (!image.writeToChannel(channel)) {
            throw new IOException("Screenshot encoding failed");
         } else {
            return output.toByteArray();
         }
      }
   }

   public ExtraScreenshotModule() {
      super("ExtraScreenshot", "Uploads your screenshots to Imgur/clipboard.", ModuleCategory.MISC);
      Predicate<Object> deleteFile = ignored -> this.modeSetting.getValue() != ExtraScreenshotModule.ModeChoice.NONE;
      this.deleteFileSetting = this.add(new BooleanSetting("DeleteFile", false, deleteFile));
      BooleanSetting sound = new BooleanSetting("Sound", false);
      sound.markVisibilityDependency();
      this.soundSetting = this.add(sound);
      this.pathSetting = this.add(new IdentifierSetting("Path", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.soundSetting));
      this.volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.soundSetting));
      this.uploadTimer = new ElapsedTimer();
      this.cooldownMessageIndex = 0;
   }

   public enum ModeChoice implements NamedEntry {
      NONE("None"),
      COPY("Copy"),
      IMGUR("Imgur");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
