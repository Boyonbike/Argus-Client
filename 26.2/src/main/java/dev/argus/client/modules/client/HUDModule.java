package dev.argus.client.modules.client;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.InterpolatedFloat;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class HUDModule extends Module {
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private final Predicate hudEditorVisible = ignored -> ClientAccess.argusclientMinecraft.gui.screen() instanceof ClientScreen screen
      ? screen.getParentScreen() == null
      : true;
   private final Setting<Boolean> hudEditorSetting;
   public final Setting<HUDModule.EffectIconMode> iconsSetting;
   public final Setting<Float> textScaleSetting;
   public final Setting<Boolean> smoothWidthSetting;
   public final Setting<Boolean> parallaxSetting;
   public final Setting<Boolean> bloomSetting;
   public final Setting<Boolean> bloomShadowSetting;
   public final Setting<Integer> bloomRadiusSetting;
   public final Setting<Float> bloomStrengthSetting;
   private final Setting<Boolean> safeZonesSetting;
   private final Setting<Integer> safeXSetting;
   private final Setting<Integer> safeYSetting;
   private final Setting<Boolean> colorsSetting;
   public final Setting<HudColorMode> fadeSetting;
   protected final Setting<Integer> fadeOffsetSetting;
   protected final Setting<Integer> colorAmountSetting;
   protected final Setting<Integer> fadeSpeedSetting;
   protected final Setting<Boolean> mixColorsSetting;
   public final Setting<Color> colorSetting;
   public final Setting<Color> secondColorSetting;
   protected final List<Setting<Color>> rawColorSettings;
   private final InterpolatedFloat effectIconOffset;
   private final InterpolatedFloat clickGuiSearchOffset;
   final InterpolatedFloat paddedChatInputHeight;
   final InterpolatedFloat chatInputHeight;
   private final ChatInputOverlay chatInputOverlay;

   public int getSafeX() {
      return this.safeXSetting.getValue();
   }

   public void updateChatInputHeight(float height) {
      this.paddedChatInputHeight.setTarget(height > 0.0F ? height + 3.0F : 0.0F);
      this.paddedChatInputHeight.updateAndGet();
      this.chatInputHeight.setTarget(height);
      this.chatInputHeight.updateAndGet();
   }

   public float getTextScale() {
      return this.textScaleSetting.getValue();
   }

   public int getSafeY() {
      return this.safeYSetting.getValue();
   }

   public void setSafeY(int value) {
      Setting<Integer> setting = this.safeYSetting;
      Integer boxedValue = value;
      setting.setValue(boxedValue);
   }

   public float getBottomHudOffset() {
      float chatOffset = this.getChatInputOffset();
      float searchOffset = this.clickGuiSearchOffset.currentValue;
      return Math.max(chatOffset, searchOffset);
   }

   public float getEffectIconOffset() {
      return this.effectIconOffset.currentValue;
   }

   public void setSafeX(int value) {
      Setting<Integer> setting = this.safeXSetting;
      Integer boxedValue = value;
      setting.setValue(boxedValue);
   }

   @EventHandler
   private void onHudRender(HudRenderEvent event) {
      int effectRows = 0;
      Setting<HUDModule.EffectIconMode> iconModeSetting = this.iconsSetting;
      if (iconModeSetting.getValue() == HUDModule.EffectIconMode.MOVE && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof InventoryScreen)) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;

         for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            Holder effectHolder = effectInstance.getEffect();
            NoRenderModule noRender = noRenderModule;
            if (noRender.isToggled()) {
               Setting<Boolean> darknessSetting = noRenderModule.darknessSetting;
               Boolean hideDarkness = darknessSetting.getValue();
               if (hideDarkness && effectHolder == MobEffects.DARKNESS) {
                  continue;
               }
            }

            if (effectRows == 0) {
               effectRows++;
            }

            Holder classifiedEffectHolder = effectInstance.getEffect();
            MobEffect effect = (MobEffect)classifiedEffectHolder.value();
            if (!effect.isBeneficial()) {
               effectRows = 2;
            }
         }
      }

      if (ClientAccess.argusclientMinecraft.gui.screen() != null) {
         Screen screen = ClientAccess.argusclientMinecraft.gui.screen();
         if (screen.showsActiveEffects()) {
            effectRows = 0;
         }
      }

      boolean moveForSearch = false;
      int searchHeightBase = FontRenderer.INSTANCE.getHeight();
      HUDModule hud = this;
      searchHeightBase -= hud.getSafeY();
      int searchHeight = searchHeightBase + 2;
      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ClickGuiScreen clickGui) {
         EditBox searchBox = clickGui.getSearchBox();
         moveForSearch = !searchBox.isFocused();
      }

      float searchTargetOffset = moveForSearch ? searchHeight : 0.0F;
      this.clickGuiSearchOffset.setTarget(searchTargetOffset);
      this.clickGuiSearchOffset.updateAndGet();
      InterpolatedFloat capturedEffectAnimation = this.effectIconOffset;
      float effectTargetOffset = 25 * effectRows;
      capturedEffectAnimation.setTarget(effectTargetOffset);
      this.effectIconOffset.updateAndGet();
      HudWidgetRenderer.renderingHud = true;

      try {
         if (!(ClientAccess.argusclientMinecraft.gui.screen() instanceof HudEditorScreen)) {
            HudEditorScreen editor = ClientContext.getHudEditor();
            editor.renderWidgets(event.getPoseStack(), -999.0, -999.0);
            GuiDrawUtils.flush();
         }
      } finally {
         HudWidgetRenderer.renderingHud = false;
      }
   }

   public boolean shouldDrawTextShadow() {
      return !this.bloomSetting.getValue() || this.bloomShadowSetting.getValue();
   }

   public float getChatInputOffset() {
      return this.paddedChatInputHeight.getValue();
   }

   public Vec3 getParallaxOffset() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (this.parallaxSetting.getValue() && player != null && !player.isSpectator()) {
         float partialTick = ClientAccess.argusclientMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         float pitchBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
         float yawBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
         return new Vec3(-(player.getViewYRot(partialTick) - yawBob) * 0.1F, -(player.getViewXRot(partialTick) - pitchBob) * 0.1F, 0.0);
      } else {
         return Vec3.ZERO;
      }
   }

   public static void bindReferencedModules(ModuleRegistry manager) {
      noRenderModule = (NoRenderModule)manager.getModule(NoRenderModule.class);
      HudColorMode.RAINBOW.bindColorsModule(manager);
   }

   public HUDModule() {
      super("HUD", "Draws useful HUD elements on your screen.", ModuleCategory.CLIENT);
      boolean editorEnabled = true;
      BooleanSetting editorSetting = new BooleanSetting("HudEditor", editorEnabled, this.hudEditorVisible);
      this.hudEditorSetting = this.add(editorSetting);
      EnumSetting<HUDModule.EffectIconMode> iconsSetting = new EnumSetting<>("Icons", HUDModule.EffectIconMode.MOVE);
      this.iconsSetting = this.add(iconsSetting);
      NumberSetting<Float> scaleSetting = new NumberSetting<>("TextScale", 1.0F, 0.5F, 2.0F);
      this.textScaleSetting = this.add(scaleSetting);
      BooleanSetting smoothWidthSetting = new BooleanSetting("SmoothWidth", false);
      this.smoothWidthSetting = this.add(smoothWidthSetting);
      BooleanSetting parallaxSetting = new BooleanSetting("Parallax", false);
      this.parallaxSetting = this.add(parallaxSetting);
      this.bloomSetting = this.add(new BooleanSetting("Bloom", false).markVisibilityDependency());
      this.bloomShadowSetting = this.add(new BooleanSetting("Shadow", false).dependsOn(this.bloomSetting));
      this.bloomRadiusSetting = this.add(new NumberSetting<>("Radius", 6, 2, 8).dependsOn(this.bloomSetting));
      this.bloomStrengthSetting = this.add(new NumberSetting<>("Strength", 1.0F, 0.5F, 1.5F).dependsOn(this.bloomSetting));
      BooleanSetting safeZonesSetting = new BooleanSetting("SafeZones", false);
      Setting<Boolean> safeZonesGroup = safeZonesSetting.markAsGroup();
      this.safeZonesSetting = this.add(safeZonesGroup);
      NumberSetting<Integer> safeXOption = new NumberSetting<>("SafeX", 1, 0, 20);
      this.safeXSetting = this.add(safeXOption.dependsOn(this.safeZonesSetting));
      NumberSetting<Integer> safeYOption = new NumberSetting<>("SafeY", 1, 0, 10);
      this.safeYSetting = this.add(safeYOption.dependsOn(this.safeZonesSetting));
      BooleanSetting colorsSetting = new BooleanSetting("Colors", true);
      Setting<Boolean> colorsGroup = colorsSetting.markAsGroup();
      this.colorsSetting = this.add(colorsGroup);
      EnumSetting<HudColorMode> fadeSetting = new EnumSetting<>("Fade", HudColorMode.NONE, ignored -> {
         Setting<Boolean> fadeGroup = this.colorsSetting;
         return fadeGroup.isExpanded();
      });
      this.fadeSetting = this.add(fadeSetting);
      NumberSetting<Integer> fadeOffsetOption = new NumberSetting<>("FadeOffset", 5, 1, 30);
      this.fadeOffsetSetting = this.add(fadeOffsetOption.dependsOn(this.colorsSetting));
      NumberSetting<Integer> colorAmountSetting = new NumberSetting<>("ColorAmount", 5, 2, 9, ignored -> {
         Setting<HudColorMode> amountVisibilityModeSetting = this.fadeSetting;
         return amountVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
      });
      this.colorAmountSetting = this.add(colorAmountSetting);
      NumberSetting<Integer> fadeSpeedSetting = new NumberSetting<>("FadeSpeed", 50, 20, 100, ignored -> {
         Setting<HudColorMode> speedVisibilityModeSetting = this.fadeSetting;
         return speedVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
      });
      this.fadeSpeedSetting = this.add(fadeSpeedSetting);
      boolean mixColorsEnabled = true;
      BooleanSetting mixColorsSetting = new BooleanSetting("MixColors", mixColorsEnabled, ignored -> {
         Setting<HudColorMode> mixVisibilityModeSetting = this.fadeSetting;
         return mixVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
      });
      this.mixColorsSetting = this.add(mixColorsSetting);
      ColorSetting primaryColor = new ColorSetting("Color", new Color(189, 153, 255, 255), ignored -> {
         Setting<HudColorMode> primaryVisibilityModeSetting = this.fadeSetting;
         return primaryVisibilityModeSetting.getValue() != HudColorMode.RAW;
      });
      this.colorSetting = this.add(primaryColor.forceOpaqueColor().dependsOn(this.colorsSetting));
      ColorSetting secondaryColor = new ColorSetting("SecondColor", new Color(105, 86, 143, 255), ignored -> {
         Setting<HudColorMode> secondaryVisibilityModeSetting = this.fadeSetting;
         return secondaryVisibilityModeSetting.getValue() == HudColorMode.PULSE && this.colorsSetting.isExpanded();
      });
      Setting<Color> configuredSecondaryColor = secondaryColor.forceOpaqueColor();
      this.secondColorSetting = this.add(configuredSecondaryColor);
      this.rawColorSettings = new ArrayList<>(this.colorAmountSetting.getMaximumValue());
      this.effectIconOffset = new InterpolatedFloat(2.0F);
      this.clickGuiSearchOffset = new InterpolatedFloat(2.0F);
      this.paddedChatInputHeight = new InterpolatedFloat(1.5F);
      this.chatInputHeight = new InterpolatedFloat(1.5F);
      this.chatInputOverlay = new ChatInputOverlay(this);
      Setting<Integer> fadeOffsetVisibilitySetting = this.fadeOffsetSetting;
      Predicate fadeOffsetVisibility = ignored -> {
         Setting<HudColorMode> offsetVisibilityModeSetting = this.fadeSetting;
         if (offsetVisibilityModeSetting.getValue() == HudColorMode.NONE) {
            ColorSetting rainbowColor = (ColorSetting)this.colorSetting;
            if (!rainbowColor.isRainbow()) {
               return false;
            }
         }

         return true;
      };
      fadeOffsetVisibilitySetting.addVisibilityPredicate(fadeOffsetVisibility);
      Setting<Boolean> callbackSetting = this.hudEditorSetting;
      Runnable editorCallback = () -> {
         Setting<Boolean> editorCallbackCallbackEditorSetting = this.hudEditorSetting;
         Boolean editorCallbackEnabled = editorCallbackCallbackEditorSetting.getValue();
         if (!editorCallbackEnabled) {
            this.hudEditorSetting.setValue(true);
            if (!this.isWorldUnavailable()) {
               Minecraft editorCallbackCapturedMinecraft = ClientAccess.argusclientMinecraft;
               HudEditorScreen editor = ClientContext.getHudEditor();
               editorCallbackCapturedMinecraft.gui.setScreen(editor);
            }
         }
      };
      callbackSetting.setChangeCallback(editorCallback);
      boolean drawn = false;
      HUDModule drawnOwner = this;
      drawnOwner.setDrawn(drawn);
      this.setToggled(true);
      int colorNumber = 1;

      while (true) {
         Setting<Integer> loopAmountSetting = this.colorAmountSetting;
         Integer loopMaximum = loopAmountSetting.getMaximumValue();
         if (colorNumber > loopMaximum) {
            ChatInputOverlay overlay = this.chatInputOverlay;
            EventBus eventBus = ClientAccess.eventBus;
            eventBus.subscribe(overlay);
            return;
         }

         int capturedColorNumber = colorNumber;
         ColorSetting rawColor = new ColorSetting(
            "Color" + colorNumber,
            Color.white,
            ignored -> {
               Setting<HudColorMode> rawColorVisibilityModeSetting = this.fadeSetting;
               return rawColorVisibilityModeSetting.getValue() == HudColorMode.RAW
                  && this.colorsSetting.isExpanded()
                  && capturedColorNumber <= this.colorAmountSetting.getValue();
            }
         );
         Setting<Color> configuredRawColor = rawColor.forceOpaqueColor();
         Setting<Color> registeredColor = this.add(configuredRawColor);
         this.rawColorSettings.add(registeredColor);
         colorNumber++;
      }
   }

   public enum EffectIconMode implements NamedEntry {
      KEEP("Keep"),
      MOVE("Move"),
      HIDE("Hide");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      EffectIconMode(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum ModuleSortMode implements NamedEntry {
      ALPHABET("Alphabet"),
      LENGTH("Length");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModuleSortMode(String displayName) {
         this.displayName = displayName;
      }
   }
}
