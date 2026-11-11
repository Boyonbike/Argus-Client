package dev.argus.client.modules.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientInitializationEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.AtlasTextRenderer;
import dev.argus.client.render.FontLanguage;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.FontSupport;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.settings.options.FontsStyleChoice;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

public class FontsModule extends Module {
   public static FontsModule INSTANCE;
   private static final Runnable RELOAD_RENDERER = () -> {
      if (ClientAccess.argusclientMinecraft.player != null || ClientAccess.argusclientMinecraft.gui.screen() instanceof ClientScreen) {
         FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
      }
   };
   public final Setting<String> fontSetting = this.add(new StringSetting("Font", "Arial"));
   public final Setting<FontsStyleChoice> styleSetting;
   public final Setting<Integer> sizeSetting;
   public final Setting<Boolean> antiAliasSetting;
   public final Setting<Boolean> chatSetting;
   private final Setting<Boolean> offsetsSetting;
   public final Setting<Integer> shiftSetting;
   public final Setting<Integer> translateSetting;
   public final Setting<Float> shadowSetting;
   private final Setting<Boolean> extraCharsSetting;
   private final Setting<Boolean> simplifiedChineseSetting;
   private final Setting<Boolean> japaneseSetting;
   private static final AtomicBoolean reloadRequested = new AtomicBoolean(false);
   private static int reloadCooldownTicks;
   private final Vector2f transformedPosition;
   private final List<FontsModule.QueuedDraw> queuedDraws;

   public boolean hasReloadCooldown() {
      return reloadCooldownTicks > 0;
   }

   public Set<FontLanguage> getLanguages() {
      FontLanguage cyrillic = FontLanguage.CYRILLIC;
      FontLanguage latin = FontLanguage.LATIN;
      FontLanguage special = FontLanguage.SPECIAL;
      Set<FontLanguage> languages = new HashSet<>(List.of(special, latin, cyrillic));
      Setting<Boolean> japaneseSettingSnapshot = this.japaneseSetting;
      Boolean includeJapanese = japaneseSettingSnapshot.getValue();
      if (includeJapanese) {
         FontLanguage japaneseAlphabet = FontLanguage.JAPANESE_ALPHABET;
         FontLanguage japanese = FontLanguage.JAPANESE;
         languages.addAll(List.of(japanese, japaneseAlphabet));
      }

      Setting<Boolean> chineseSettingSnapshot = this.simplifiedChineseSetting;
      Boolean includeChinese = chineseSettingSnapshot.getValue();
      if (includeChinese) {
         languages.add(FontLanguage.CHINESE);
      }

      return languages;
   }

   @Override
   public void onToggle() {
      reloadCooldownTicks = 3;
      this.validateFont(false);
   }

   private void flushQueuedDraws(PoseStack poseStack) {
      if (!this.queuedDraws.isEmpty()) {
         GuiDrawUtils.flush(true);

         for (FontsModule.QueuedDraw draw : this.queuedDraws) {
            draw.render(poseStack);
         }

         this.queuedDraws.clear();
         GuiDrawUtils.flush(true);
      }
   }

   public void enqueueText(Matrix3x2fc transform, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
      this.transformedPosition.set(0.0F);
      transform.transformPosition(x, y, this.transformedPosition);
      this.queuedDraws.add(new FontsModule.QueuedText(text, this.transformedPosition.x(), this.transformedPosition.y(), color, shadow));
   }

   @EventHandler
   private static void onClientInitialized(ClientInitializationEvent event) {
      INSTANCE.setToggled(true);
      reloadRequested.set(true);
   }

   public void enqueueRectangle(Matrix3x2fc transform, float left, float top, float right, float bottom, int color) {
      this.transformedPosition.set(0.0F);
      transform.transformPosition(left, top, this.transformedPosition);
      left = this.transformedPosition.x();
      top = this.transformedPosition.y();
      transform.transformPosition(right, bottom, this.transformedPosition);
      this.queuedDraws.add(new FontsModule.QueuedRectangle(left, top, this.transformedPosition.x(), this.transformedPosition.y(), color));
   }

   @EventHandler
   private static void onMinecraftTick(MinecraftTickEvent event) {
      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      Window window = minecraft.getWindow();
      long windowHandle = window.handle();
      if (GLFW.glfwGetMouseButton(windowHandle, 0) != 1) {
         AtomicBoolean requestedReloadFlag = reloadRequested;
         if (requestedReloadFlag.get()) {
            FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
            boolean clearRequest = false;
            AtomicBoolean reloadFlag = reloadRequested;
            reloadFlag.set(clearRequest);
            reloadCooldownTicks = 3;
         }
      }

      if (reloadCooldownTicks > 0) {
         reloadCooldownTicks--;
      }
   }

   @EventHandler
   private void onHudRender(HudRenderEvent.Unconditional event) {
      this.flushQueuedDraws(event.getPoseStack());
   }

   public FontsModule() {
      super("Fonts", "Manages the client's font renderer.", ModuleCategory.CLIENT);
      FontsStyleChoice defaultStyle = FontsStyleChoice.PLAIN;
      this.styleSetting = this.add(new EnumSetting<>("Style", defaultStyle));
      this.sizeSetting = this.add(new NumberSetting<>("Size", 16, 12, 20));
      this.antiAliasSetting = this.add(new BooleanSetting("AntiAlias", true));
      this.chatSetting = this.add(new BooleanSetting("Chat", false));
      BooleanSetting offsets = new BooleanSetting("Offsets", false);
      offsets.markAsGroup();
      offsets.markVisibilityDependency();
      this.offsetsSetting = this.add(offsets);
      this.shiftSetting = this.add(new NumberSetting<>("Shift", -2, -4, 4).dependsOn(this.offsetsSetting));
      this.translateSetting = this.add(new NumberSetting<>("Translate", -1, -4, 4).dependsOn(this.offsetsSetting));
      NumberSetting<Float> shadow = new NumberSetting<>("Shadow", 0.7F, 0.5F, 1.0F);
      this.shadowSetting = this.add(shadow.dependsOn(this.offsetsSetting));
      BooleanSetting extraChars = new BooleanSetting("ExtraChars", false);
      extraChars.markAsGroup();
      extraChars.markVisibilityDependency();
      this.extraCharsSetting = this.add(extraChars);
      this.simplifiedChineseSetting = this.add(new BooleanSetting("SimplifiedChinese", false).dependsOn(this.extraCharsSetting));
      this.japaneseSetting = this.add(new BooleanSetting("Japanese", false).dependsOn(this.extraCharsSetting));
      this.transformedPosition = new Vector2f();
      this.queuedDraws = new ArrayList<>();
      this.styleSetting.setChangeCallback(RELOAD_RENDERER);
      this.antiAliasSetting.setChangeCallback(RELOAD_RENDERER);
      this.japaneseSetting.setChangeCallback(() -> this.validateFont(true));
      this.simplifiedChineseSetting.setChangeCallback(() -> this.validateFont(true));
      this.sizeSetting.setChangeCallback(() -> reloadRequested.set(true));
      this.fontSetting.setChangeCallback(() -> this.validateFont(true));
      INSTANCE = this;
      ClientAccess.eventBus.subscribeStatic(FontsModule.class);
      this.setDrawn(false);
   }

   public void validateFont(boolean reload) {
      if (UIModule.INSTANCE.languageSetting.getValue() == UIModule.LanguageChoice.CHINESE && !this.simplifiedChineseSetting.getValue()) {
         this.simplifiedChineseSetting.setValue(true);
      } else {
         boolean chinese = this.simplifiedChineseSetting.getValue();
         boolean japanese = this.japaneseSetting.getValue();
         if ((chinese || japanese) && !FontSupport.supports(this.fontSetting.getValue(), chinese, japanese)) {
            String replacement = null;

            for (String font : chinese ? (japanese ? List.of("SansSerif") : List.of("Microsoft YaHei", "Microsoft JhengHei")) : List.of("MS PGothic")) {
               if (FontSupport.supports(font, chinese, japanese)) {
                  replacement = font;
                  break;
               }
            }

            if (replacement == null) {
               List<String> compatible = FontSupport.getCompatibleFonts(chinese, japanese);
               if (!compatible.isEmpty()) {
                  replacement = compatible.get(ThreadLocalRandom.current().nextInt(compatible.size()));
               }
            }

            if (replacement != null) {
               String key = chinese ? (japanese ? "Both" : "Chinese") : "Japanese";
               String characters = chinese ? (japanese ? "Chinese and Japanese" : "Chinese") : "Japanese";
               String fallback = "Your font has been changed to \"%s\" because your old font does not support " + characters + " characters.";
               String message = ClientContext.languageTable.getTranslationOrDefault("fonts." + key + "Unsupported", fallback);
               ChatUtils.printChat(Component.literal(message.formatted(replacement)), ChatUtils.objectSignature(this), ChatUtils.MessagePriority.MID);
               this.fontSetting.setValue(replacement);
            } else if (reload) {
               RELOAD_RENDERER.run();
            }
         } else {
            if (reload) {
               RELOAD_RENDERER.run();
            }
         }
      }
   }

   interface QueuedDraw {
      void render(PoseStack var1);
   }

   private record QueuedRectangle(float left, float top, float right, float bottom, int color) implements FontsModule.QueuedDraw {
      @Override
      public void render(PoseStack poseStack) {
         GuiDrawUtils.fillRect(poseStack, this.left, this.top, this.right, this.bottom, this.color);
      }
   }

   private record QueuedText(FormattedCharSequence text, float x, float y, int color, boolean shadow) implements FontsModule.QueuedDraw {
      @Override
      public void render(PoseStack poseStack) {
         float chatScale = ClientAccess.argusclientMinecraft.options.chatScale().get().floatValue();
         poseStack.pushPose();
         poseStack.scale(chatScale, chatScale, 1.0F);
         AtlasTextRenderer renderer = FontRenderer.INSTANCE.getRenderer();
         renderer.draw(poseStack, this.text, this.x / chatScale, this.y / chatScale, this.color, this.shadow);
         poseStack.popPose();
      }
   }
}
