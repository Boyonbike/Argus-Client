package dev.argus.client.modules.misc;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.SocialRole;
import dev.argus.client.utils.FloatAnimation;
import java.awt.Color;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.Optionull;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public class ExtraTabModule extends Module {
   public final Setting<ExtraTabModule.DisplayChoice> displaySetting = this.add(new EnumSetting<>("Display", ExtraTabModule.DisplayChoice.ALL));
   public final Setting<ExtraTabModule.LatencyChoice> latencySetting = this.add(new EnumSetting<>("Latency", ExtraTabModule.LatencyChoice.VANILLA));
   public final Setting<Integer> limitSetting = this.add(new NumberSetting<>("Limit", 500, 10, 500));
   public final Setting<Float> scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.5F, 1.5F));
   public final Setting<Boolean> coloredSetting;
   public final Setting<Boolean> smoothSetting;
   public final Setting<Boolean> betterSkinSetting;
   public final Setting<Boolean> highlightSetting;
   public final Setting<Color> selfSetting;
   public final Setting<Boolean> socialSetting;
   public final Setting<Boolean> sortSetting;
   public final Setting<ExtraTabModule.ModeChoice> modeSetting;
   public final Setting<Boolean> reverseSetting;
   private final FloatAnimation openAnimation;

   public float getTabScale() {
      return !this.isToggled() ? 1.0F : this.scaleSetting.getValue();
   }

   public float getOpenProgress() {
      float remainderBase = 1.0F;
      FloatAnimation animationSnapshot = this.openAnimation;
      double remainderCapture = remainderBase - animationSnapshot.getValue();
      return (float)(1.0 - Math.pow(remainderCapture, 5.0));
   }

   public Comparator createPlayerComparator() {
      Comparator<PlayerInfo> comparator = Comparator.<PlayerInfo>comparingInt(spectatorInfo -> {
         int spectatorWeight = spectatorInfo.getGameMode() == GameType.SPECTATOR ? 1 : 0;
         Setting<Boolean> spectatorReverseSnapshot = this.reverseSetting;
         Boolean spectatorReverse = spectatorReverseSnapshot.getValue();
         if (spectatorReverse) {
            spectatorWeight *= -1;
         }

         return spectatorWeight;
      }).thenComparing(teamInfo -> {
         PlayerTeam team = teamInfo.getTeam();
         Function<PlayerTeam, String> teamName = PlayerTeam::getName;
         return Optionull.mapOrDefault(team, teamName, "");
      }).thenComparing(this::getSortWeight).thenComparing(nameInfo -> {
         GameProfile profile = nameInfo.getProfile();
         return profile.name();
      }, String::compareToIgnoreCase);
      Setting<Boolean> reverseSnapshot = this.reverseSetting;
      Boolean reverse = reverseSnapshot.getValue();
      if (reverse) {
         comparator = comparator.reversed();
      }

      return comparator;
   }

   @EventHandler
   private void updateOpenAnimation(HudRenderEvent event) {
      FloatAnimation animationSnapshot = this.openAnimation;
      Setting<Boolean> smoothSnapshot = this.smoothSetting;
      Boolean smoothEnabled = smoothSnapshot.getValue();
      boolean openTarget = false;
      if (smoothEnabled) {
         KeyMapping tabKey = ClientAccess.argusclientMinecraft.options.keyPlayerList;
         if (tabKey.isDown()) {
            Minecraft minecraft = ClientAccess.argusclientMinecraft;
            openTarget = !minecraft.isLocalServer();
         }
      }

      animationSnapshot.setTarget(openTarget, 200L);
   }

   int getSortWeight(PlayerInfo playerInfo) {
      Setting<ExtraTabModule.ModeChoice> modeSnapshot = this.modeSetting;
      ExtraTabModule.ModeChoice mode = modeSnapshot.getValue();

      return switch (mode) {
         case ALPHABET -> 0;
         case SOCIALS -> {
            SocialRegistry socialManagerCapture = ClientContext.socialRegistry;
            GameProfile socialProfile = playerInfo.getProfile();
            String socialName = socialProfile.name();
            SocialRole socialType = socialManagerCapture.getRole(socialName);
            int socialWeight = 0;
            if (socialType == SocialRole.FRIEND) {
               socialWeight = -1;
            }

            if (socialType == SocialRole.ENEMY) {
               socialWeight = -2;
            }

            yield socialWeight;
         }
         case LATENCY -> -playerInfo.getLatency();
         case LENGTH -> {
            Font fontCapture = ClientAccess.argusclientMinecraft.font;
            GameProfile widthProfile = playerInfo.getProfile();
            String widthName = widthProfile.name();
            yield -fontCapture.width(widthName);
         }
      };
   }

   public ExtraTabModule() {
      super("ExtraTab", "Allows you to customize your TAB-screen.", ModuleCategory.MISC);
      Predicate<Object> coloredVisible = ignored -> this.latencySetting.getValue() == ExtraTabModule.LatencyChoice.TEXT;
      this.coloredSetting = this.add(new BooleanSetting("Colored", true, coloredVisible));
      this.smoothSetting = this.add(new BooleanSetting("Smooth", false));
      this.betterSkinSetting = this.add(new BooleanSetting("BetterSkin", true));
      BooleanSetting highlight = new BooleanSetting("Highlight", true);
      highlight.markVisibilityDependency();
      this.highlightSetting = this.add(highlight);
      Predicate<Object> selfVisible = ignored -> this.highlightSetting.isExpanded();
      this.selfSetting = this.add(new ColorSetting("Self", new Color(-65281, true), selfVisible));
      Predicate<Object> socialVisible = ignored -> this.highlightSetting.isExpanded();
      this.socialSetting = this.add(new BooleanSetting("Social", true, socialVisible));
      BooleanSetting sort = new BooleanSetting("Sort", false);
      sort.markVisibilityDependency();
      this.sortSetting = this.add(sort);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ExtraTabModule.ModeChoice.ALPHABET).dependsOn(this.sortSetting));
      this.reverseSetting = this.add(new BooleanSetting("Reverse", false).dependsOn(this.sortSetting));
      this.openAnimation = new FloatAnimation();
      Setting<Integer> limitLabelSetting = this.limitSetting;
      SettingLabelCondition maximumLabelPoint = SettingLabelCondition.MAX;
      limitLabelSetting.setBoundaryLabel("Unlimited", maximumLabelPoint);
      this.setDrawn(false);
   }

   public enum DisplayChoice implements NamedEntry {
      ALL("All"),
      SOCIALS("Socials");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      DisplayChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum LatencyChoice implements NamedEntry {
      VANILLA("Vanilla"),
      TEXT("Text"),
      NONE("None");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      LatencyChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum ModeChoice implements NamedEntry {
      ALPHABET("Alphabet"),
      SOCIALS("Socials"),
      LATENCY("Latency"),
      LENGTH("Length");

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
