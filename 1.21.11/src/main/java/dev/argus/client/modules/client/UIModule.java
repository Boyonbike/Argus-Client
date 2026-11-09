package dev.argus.client.modules.client;

import com.mojang.blaze3d.platform.Window;
import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.text.LanguageTable;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.world.Season;
import java.awt.Color;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.lwjgl.glfw.GLFW;

public final class UIModule extends Module {
   public static UIModule INSTANCE;
   private final AtomicBoolean panelResetPending = new AtomicBoolean(false);
   private final Setting<Boolean> resetSetting = this.add(new BooleanSetting("Reset", true));
   public final Setting<UIModule.StyleChoice> styleSetting = this.add(new EnumSetting<>("Style", UIModule.StyleChoice.METEOR));
   public final Setting<Float> animSpeedSetting = this.add(new NumberSetting<>("AnimSpeed", 1.0F, 0.5F, 2.5F));
   public final Setting<Float> scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.1F, 2.0F));
   public final Setting<Boolean> snowSetting = this.add(new BooleanSetting("Snow", true));
   public final Setting<Boolean> gamesSetting = this.add(new BooleanSetting("Games", true));
   public final Setting<Boolean> descriptionsSetting;
   final Setting<UIModule.LanguageChoice> languageSetting;
   public final Setting<Boolean> settingsSetting;
   public final Setting<Boolean> elementsSetting;
   public final Setting<Boolean> aeroSetting;
   public final Setting<Boolean> elementGradientSetting;
   public final Setting<Boolean> lineSetting;
   public final Setting<Boolean> bindsSetting;
   public final Setting<Boolean> gearSetting;
   public final Setting<Boolean> soundsSetting;
   public final Setting<Boolean> hoverSetting;
   public final Setting<NamedIdentifier> hoverSoundSetting;
   public final Setting<Float> hoverVolumeSetting;
   public final Setting<Boolean> leftClickSetting;
   public final Setting<NamedIdentifier> leftClickSoundSetting;
   public final Setting<Float> leftClickVolumeSetting;
   public final Setting<Boolean> rightClickSetting;
   public final Setting<NamedIdentifier> rightClickSoundSetting;
   public final Setting<Float> rightClickVolumeSetting;
   public final Setting<Boolean> textSetting;
   public final Setting<Color> textColorSetting;
   public final Setting<Color> enabledColorSetting;
   final Setting<Boolean> colorsSetting;
   public final Setting<Color> colorSetting;
   public final Setting<Color> backgroundSetting;
   public final Setting<Color> buttonSetting;
   public final Setting<Color> enabledSetting;
   public final Setting<Color> tintSetting;
   public final Setting<Color> gradientColorSetting;

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable() && !(ClientAccess.argusclientMinecraft.screen instanceof ClickGuiScreen)) {
         if (ClientAccess.argusclientMinecraft.screen instanceof ChatScreen) {
            TaskScheduler var1 = ClientContext.taskScheduler;
            Runnable var2 = () -> {
               ClickGuiScreen var0 = ClientContext.getClickGui();
               var0.open();
            };
            var1.scheduleRotationTask(var2, 0);
         } else {
            ClickGuiScreen var3 = ClientContext.getClickGui();
            var3.open();
         }
      }
   }

   @Override
   public Keybind getKeybind() {
      Keybind var1 = super.getKeybind();
      if (var1.mode() != Keybind.Mode.TOGGLE) {
         var1 = var1.withMode(Keybind.Mode.TOGGLE);
         this.setKeybind(var1);
      }

      return var1;
   }

   @Override
   public void onDisable() {
      if (ClientAccess.argusclientMinecraft.screen instanceof ClickGuiScreen) {
         ClientContext.getClickGui().closeWithTransition();
      }
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent var1) {
      Minecraft var2 = ClientAccess.argusclientMinecraft;
      Window var3 = var2.getWindow();
      long var4 = var3.handle();
      if (GLFW.glfwGetMouseButton(var4, 0) != 1) {
         AtomicBoolean var6 = this.panelResetPending;
         if (var6.get()) {
            ClickGuiScreen var7 = ClientContext.getClickGui();
            var7.resetPanelPositions();
            this.resetSetting.setValue(true);
            this.panelResetPending.set(false);
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerTickEvent var1) {
      if (!(ClientAccess.argusclientMinecraft.screen instanceof ClientScreen)) {
         this.toggle();
      }
   }

   @EventHandler
   private static void onCursorTick(MinecraftTickEvent var0) {
      if (!(ClientAccess.argusclientMinecraft.screen instanceof ClientScreen)) {
         CursorType var1 = CursorType.STANDARD;
         var1.apply();
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive var1) {
      if (var1.getPacket() instanceof ClientboundDisconnectPacket) {
         this.disable();
      }
   }

   public float getAnimationSpeed() {
      return this.animSpeedSetting.getValue();
   }

   public UIModule() {
      super("UI", "Displays the client's click gui.", ModuleCategory.CLIENT);
      BooleanSetting var1 = new BooleanSetting("Descriptions", true);
      var1.markVisibilityDependency();
      this.descriptionsSetting = this.add(var1);
      this.languageSetting = this.add(new EnumSetting<>("Language", UIModule.LanguageChoice.ENGLISH).dependsOn(this.descriptionsSetting));
      this.settingsSetting = this.add(new BooleanSetting("Settings", true).dependsOn(this.descriptionsSetting));
      BooleanSetting var2 = new BooleanSetting("Elements", true);
      var2.markAsGroup();
      var2.markVisibilityDependency();
      this.elementsSetting = this.add(var2);
      this.aeroSetting = this.add(new BooleanSetting("Aero", true).dependsOn(this.elementsSetting));
      this.elementGradientSetting = this.add(new BooleanSetting("Gradient", false).dependsOn(this.elementsSetting));
      Predicate var3 = var1x -> this.elementsSetting.isExpanded();
      this.lineSetting = this.add(new BooleanSetting("Line", false, var3));
      Predicate var4 = var1x -> this.elementsSetting.isExpanded();
      this.bindsSetting = this.add(new BooleanSetting("Binds", false, var4));
      Predicate var5 = var1x -> this.elementsSetting.isExpanded();
      this.gearSetting = this.add(new BooleanSetting("Gear", true, var5));
      BooleanSetting var6 = new BooleanSetting("Sounds", true);
      var6.markAsGroup();
      var6.markVisibilityDependency();
      this.soundsSetting = this.add(var6);
      Predicate var7 = var1x -> this.soundsSetting.isExpanded();
      BooleanSetting var8 = new BooleanSetting("Hover", true, var7);
      var8.markVisibilityDependency();
      this.hoverSetting = this.add(var8);
      String var9 = "Sound";
      this.hoverSoundSetting = this.add(new IdentifierSetting(var9, BuiltinSound.HOVER.getIdentifier()).dependsOn(this.soundsSetting, this.hoverSetting));
      NumberSetting var10 = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.hoverVolumeSetting = this.add(var10.dependsOn(this.soundsSetting, this.hoverSetting));
      Predicate var11 = var1x -> this.soundsSetting.isExpanded();
      BooleanSetting var12 = new BooleanSetting("LeftClick", true, var11);
      var12.markVisibilityDependency();
      this.leftClickSetting = this.add(var12);
      this.leftClickSoundSetting = this.add(
         new IdentifierSetting("Sound", BuiltinSound.CLICK.getIdentifier()).dependsOn(this.soundsSetting, this.leftClickSetting)
      );
      NumberSetting var13 = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.leftClickVolumeSetting = this.add(var13.dependsOn(this.soundsSetting, this.leftClickSetting));
      Predicate var14 = var1x -> this.soundsSetting.isExpanded();
      BooleanSetting var15 = new BooleanSetting("RightClick", true, var14);
      var15.markVisibilityDependency();
      this.rightClickSetting = this.add(var15);
      this.rightClickSoundSetting = this.add(
         new IdentifierSetting("Sound", BuiltinSound.RCLICK.getIdentifier()).dependsOn(this.soundsSetting, this.rightClickSetting)
      );
      NumberSetting var16 = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
      this.rightClickVolumeSetting = this.add(var16.dependsOn(this.soundsSetting, this.rightClickSetting));
      BooleanSetting var17 = new BooleanSetting("Text", false);
      var17.markAsGroup();
      var17.markVisibilityDependency();
      this.textSetting = this.add(var17);
      String var18 = "TextColor";
      this.textColorSetting = this.add(new ColorSetting(var18, new Color(255, 255, 255, 255)).dependsOn(this.textSetting));
      this.enabledColorSetting = this.add(new ColorSetting("EnabledColor", new Color(185, 120, 255, 255)).dependsOn(this.textSetting));
      BooleanSetting var19 = new BooleanSetting("Colors", false);
      var19.markAsGroup();
      var19.markVisibilityDependency();
      this.colorsSetting = this.add(var19);
      Predicate var20 = var1x -> this.colorsSetting.isExpanded();
      this.colorSetting = this.add(new ColorSetting("Color", new Color(30, 30, 30, 255), var20));
      Predicate var21 = var1x -> this.colorsSetting.isExpanded();
      this.backgroundSetting = this.add(new ColorSetting("Background", new Color(24, 24, 28, 235), var21));
      Predicate var22 = var1x -> this.colorsSetting.isExpanded();
      this.buttonSetting = this.add(new ColorSetting("Button", new Color(0, 0, 0, 0), var22));
      Predicate var23 = var1x -> this.colorsSetting.isExpanded();
      this.enabledSetting = this.add(new ColorSetting("Enabled", new Color(150, 100, 255, 100), var23));
      Predicate var24 = var1x -> this.colorsSetting.isExpanded();
      this.tintSetting = this.add(new ColorSetting("Tint", new Color(1679822128, true), var24));
      Predicate var25 = var1x -> this.colorsSetting.isExpanded();
      this.gradientColorSetting = this.add(new ColorSetting("Gradient", new Color(264738036, true), var25));
      Class<UIModule> var26 = UIModule.class;
      EventBus var27 = ClientAccess.eventBus;
      var27.subscribeStatic(var26);
      this.ignoreState();
      this.setDrawn(false);
      Function<Keybind, Keybind> var28 = var0 -> var0.withCode(344);
      this.modifyKeybind(var28);
      INSTANCE = this;
      Setting var29 = this.gamesSetting;
      Runnable var30 = () -> {
         ClickGuiScreen var1x = ClientContext.clickGui;
         if (!var1x.isGamePanelUnlocked()) {
            var1x = ClientContext.clickGui;
            ArrayList var2x = var1x.getPanels();
            Predicate<ModuleCategory> var3x = var0 -> var0.getName().equals("Game");
            var2x.removeIf(var3x);
            Setting var4x = this.gamesSetting;
            if ((Boolean)var4x.getValue()) {
               var1x = ClientContext.clickGui;
               var1x.addGamePanel();
            }
         }
      };
      var29.setChangeCallback(var30);
      Setting var31 = this.resetSetting;
      Runnable var32 = () -> {
         Setting var1x = this.resetSetting;
         if (!(Boolean)var1x.getValue()) {
            this.resetSetting.setValue(true);
            if (ClientContext.clickGui != null) {
               ClickGuiScreen var2x = ClientContext.getClickGui();
               var2x.resetPanelPositions();
            }
         }
      };
      var31.setChangeCallback(var32);
      Setting var33 = this.styleSetting;
      Runnable var34 = () -> this.panelResetPending.set(true);
      var33.setChangeCallback(var34);
      Setting var35 = this.languageSetting;
      Runnable var36 = () -> {
         LanguageTable var1x = ClientContext.languageTable;
         Setting var2x = this.languageSetting;
         UIModule.LanguageChoice var3x = (UIModule.LanguageChoice)var2x.getValue();
         InputStream var4x = var3x.openStream();
         var1x.load(var4x);
         if (var3x == UIModule.LanguageChoice.CHINESE && FontsModule.INSTANCE.isToggled()) {
            FontsModule.INSTANCE.validateFont(false);
         }
      };
      var35.setChangeCallback(var36);
      this.hoverSoundSetting.setConfigName("HoverSound");
      this.hoverVolumeSetting.setConfigName("HoverVolume");
      this.leftClickSoundSetting.setConfigName("LeftClickSound");
      this.leftClickVolumeSetting.setConfigName("LeftClickVolume");
      this.rightClickSoundSetting.setConfigName("RightClickSound");
      this.rightClickVolumeSetting.setConfigName("RightClickVolume");
      this.backgroundSetting.setConfigName("BgColor");
      this.buttonSetting.setConfigName("BgButton");
      this.enabledSetting.setConfigName("BgEnabled");
      this.gradientColorSetting.setConfigName("GradientColor");
      Season var37 = Season.WINTER;
      LocalDate var38 = LocalDate.now();
      int var39 = var38.getMonthValue();
      if (!var37.containsMonth(var39)) {
         this.snowSetting.setValue(false);
         this.unregister(this.snowSetting);
      }
   }

   public enum LanguageChoice implements NamedEntry {
      ENGLISH("English", "en_us"),
      CHINESE("Chinese", "zh_cn");

      private final String displayName;
      private final String languageCode;

      public InputStream openStream() {
         String var1 = this.languageCode;
         return ArgusClient.class.getResourceAsStream("/assets/argusclient/lang/%s.ini".formatted(var1));
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      LanguageChoice(String nullxx, String nullxxx) {
         this.displayName = nullxx;
         this.languageCode = nullxxx;
      }
   }

   public enum StyleChoice implements NamedEntry {
      PLAIN("Plain"),
      SLIM("Slim"),
      METEOR("Meteor");

      private final String displayName;

      public int getHeightPadding() {
         return this == SLIM ? 3 : (this == METEOR ? 5 : 4);
      }

      public int getWidthPadding() {
         return this == SLIM ? 4 : (this == METEOR ? 14 : 7);
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      StyleChoice(String nullxx) {
         this.displayName = nullxx;
      }
   }
}
