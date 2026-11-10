package dev.argus.client.modules.hud;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.player.NameProtectModule;
import dev.argus.client.modules.render.NameTagsModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.combat.TotemPopTracker;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.EquipmentUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

public class TextRadarModule extends HudWidgetModule {
   private static final FontsModule fonts = ClientContext.getModule(FontsModule.class);
   private static final NameProtectModule nameProtect = ClientContext.getModule(NameProtectModule.class);
   private static final Color SNEAKING_COLOR = new Color(10945959, false);
   private final Setting<TextRadarModule.SortMode> sortSetting = this.add(new EnumSetting<>("Sort", TextRadarModule.SortMode.DISTANCE));
   private final Setting<Boolean> healthSetting = this.add(new BooleanSetting("Health", true));
   private final Setting<Boolean> distanceSetting = this.add(new BooleanSetting("Distance", false));
   private final Setting<Boolean> totemPopsSetting = this.add(new BooleanSetting("TotemPops", false));
   private final Setting<Boolean> armorSetting = this.add(new BooleanSetting("Armor", false));
   private final Setting<Boolean> headsSetting = this.add(new BooleanSetting("Heads", false));
   private final Setting<Boolean> limitSetting;
   private final Setting<Integer> maxSetting;
   private final Setting<Boolean> colorsSetting;
   private final Setting<Boolean> friendSetting;
   private final Setting<Boolean> enemySetting;
   private final Setting<Boolean> sneakingSetting;
   private final Setting<Boolean> ignoreSetting;
   private final Setting<Boolean> friendsSetting;
   private final Setting<Boolean> nakedsSetting;
   private final Setting<Boolean> botsSetting;
   private final List<Player> radarPlayers;

   @Override
   public void onEnable() {
      this.radarPlayers.clear();
   }

   private Component buildPlayerText(Player player) {
      MutableComponent text = Component.empty();
      float health = EntityUtils.getHealthWithAbsorption(player);
      Setting<Boolean> healthOption = this.healthSetting;
      Boolean showHealth = healthOption.getValue();
      if (showHealth) {
         int roundedHealth = Math.round(health);
         String healthString = roundedHealth + " ";
         MutableComponent healthText = Component.literal(healthString);
         ChatFormatting healthFormatting = this.getHealthFormatting(health);
         healthText.withStyle(healthFormatting);
         text.append(healthText);
      }

      GameProfile profile = player.getGameProfile();
      String playerName = profile.name();
      NameProtectModule nameProtectForToggle = nameProtect;
      if (nameProtectForToggle.isToggled()) {
         boolean previousNameProtectState = NameProtectModule.replacementBypassed;
         NameProtectModule.replacementBypassed = !previousNameProtectState;
         NameProtectModule nameProtectForName = nameProtect;
         playerName = nameProtectForName.protectNames(playerName);
         NameProtectModule.replacementBypassed = previousNameProtectState;
      }

      String displayName = ClientContext.aliasRegistry.getPlayerAlias(playerName);
      text.append(displayName);
      Setting<Boolean> popsOption = this.totemPopsSetting;
      Boolean showPops = popsOption.getValue();
      if (showPops) {
         TotemPopTracker popTracker = ClientContext.totemPopTracker;
         int totemPops = popTracker.getPopCount(player);
         String popString = " -" + totemPops;
         MutableComponent popText = Component.literal(popString);
         int popColor = NameTagsModule.getTotemPopColor(totemPops);
         popText.withColor(popColor);
         if (totemPops > 0) {
            text.append(popText);
         }
      }

      Setting<Boolean> distanceOption = this.distanceSetting;
      Boolean showDistance = distanceOption.getValue();
      if (showDistance) {
         float distance = ClientAccess.argusclientMinecraft.player.distanceTo(player);
         int distanceMeters = Math.round(distance);
         String distanceString = " " + distanceMeters + "m";
         MutableComponent distanceText = Component.literal(distanceString);
         ChatFormatting distanceFormatting = ChatFormatting.WHITE;
         distanceText.withStyle(distanceFormatting);
         text.append(distanceText);
      }

      Setting<Boolean> armorOption = this.armorSetting;
      Boolean showArmor = armorOption.getValue();
      if (showArmor) {
         String armorMarker = "G";

         for (ItemStack armorStack : EquipmentUtils.getArmorStacks(player)) {
            if (armorStack.getItem() == Items.ELYTRA) {
               armorMarker = "W";
               break;
            }

            DataComponentType<Equippable> equippableComponent = DataComponents.EQUIPPABLE;
            if (!armorStack.has(equippableComponent)
               || !EquipmentUtils.isRepairMaterial(armorStack, Items.DIAMOND) && !EquipmentUtils.isRepairMaterial(armorStack, Items.NETHERITE_INGOT)
               || !armorStack.isEnchanted()) {
               armorMarker = "";
            }
         }

         if (!armorMarker.isEmpty()) {
            String armorString = " [" + armorMarker + "]";
            MutableComponent armorText = Component.literal(armorString);
            ChatFormatting armorFormatting = ChatFormatting.WHITE;
            armorText.withStyle(armorFormatting);
            text.append(armorText);
         }
      }

      return text;
   }

   private ChatFormatting getHealthFormatting(double health) {
      if (health >= 20.0) {
         return ChatFormatting.GREEN;
      } else if (health >= 16.0) {
         return ChatFormatting.DARK_GREEN;
      } else if (health >= 10.0) {
         return ChatFormatting.GOLD;
      } else {
         return health >= 4.0 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
      }
   }

   private Comparator<Entity> createPlayerComparator() {
      Setting<TextRadarModule.SortMode> sortOption = this.sortSetting;
      TextRadarModule.SortMode sortMode = sortOption.getValue();
      Comparator<Entity> comparator;
      switch (sortMode) {
         case DISTANCE:
            ToDoubleFunction<Entity> distanceMetric = entity -> {
               LocalPlayer localPlayer = ClientAccess.argusclientMinecraft.player;
               return entity.distanceToSqr(localPlayer);
            };
            comparator = Comparator.comparingDouble(distanceMetric);
            break;
         case ANGLE:
            ToDoubleFunction<Entity> angleMetric = entity -> {
               GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
               Camera camera = gameRenderer.getMainCamera();
               float cameraYaw = camera.yRot();
               float targetYaw = RotationManager.getRotationsToEntity(entity)[0];
               return Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
            };
            comparator = Comparator.comparingDouble(angleMetric);
            break;
         case ALPHABET:
            Function<Entity, String> nameKey = entity -> {
               if (entity instanceof Player player) {
                  GameProfile profile = player.getGameProfile();
                  return profile.name();
               } else {
                  Component entityName = entity.getName();
                  return entityName.getString();
               }
            };
            comparator = Comparator.comparing(nameKey);
            break;
         default:
            throw new MatchException(null, null);
      }

      return comparator;
   }

   @Override
   public float[] calculateDimensions() {
      float totalHeight = 0.0F;
      float maximumWidth = 0.0F;

      for (Player player : this.radarPlayers) {
         FontRenderer textRenderer = FontRenderer.INSTANCE;
         float rowWidth = textRenderer.getWidth(this.buildPlayerText(player));
         Setting<Boolean> headsOption = this.headsSetting;
         Boolean showHeads = headsOption.getValue();
         if (showHeads) {
            float headFontHeight = FontRenderer.INSTANCE.getHeight();
            rowWidth += headFontHeight + 0.5F;
         }

         totalHeight += FontRenderer.INSTANCE.getHeight() + 1;
         if (rowWidth > maximumWidth) {
            maximumWidth = rowWidth;
         }
      }

      return new float[]{maximumWidth, totalHeight};
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      synchronized (this.radarPlayers) {
         this.radarPlayers.clear();
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         List<AbstractClientPlayer> worldPlayers = level.players();
         Stream<AbstractClientPlayer> playerStream = worldPlayers.stream();
         Predicate<AbstractClientPlayer> includedPlayer = this::shouldIncludePlayer;
         Stream<AbstractClientPlayer> players = playerStream.filter(includedPlayer);
         Comparator<Entity> playerComparator = this.createPlayerComparator();
         players = players.sorted(playerComparator);
         Setting<Boolean> limitOption = this.limitSetting;
         Boolean limitEnabled = limitOption.getValue();
         long maximumCount;
         if (limitEnabled) {
            Setting<Integer> maximumOption = this.maxSetting;
            Integer maximumPlayers = maximumOption.getValue();
            maximumCount = maximumPlayers.intValue();
         } else {
            maximumCount = Long.MAX_VALUE;
         }

         players = players.limit(maximumCount);
         List<Player> playerDestination = this.radarPlayers;
         Consumer<AbstractClientPlayer> appendPlayer = playerDestination::add;
         players.forEachOrdered(appendPlayer);
      }
   }

   private void drawHatLayer(PoseStack poses, Identifier skin, int size) {
      float textureU = 40.0F;
      float textureVFloat = 8.0F;
      GuiDrawUtils.drawTexture(poses, skin, 0, 0, textureU, textureVFloat, size, size, 8, 8, 64, 64, -1);
   }

   @Override
   public void renderContent(PoseStack poses) {
      FontsModule fontsForToggle = fonts;
      float headSpacing;
      if (fontsForToggle.isToggled()) {
         headSpacing = 0.0F;
      } else {
         headSpacing = 1.0F;
      }

      float hudScale = hud.getTextScale();
      float rowY = this.renderer.getContentY(FontRenderer.INSTANCE.getHeight() * hudScale) - this.renderer.getY();
      rowY /= hudScale;

      for (Player player : this.radarPlayers) {
         Color rowColor;
         label55: {
            rowColor = this.getHudColor(rowY);
            SocialRegistry friendLookup = ClientContext.socialRegistry;
            if (friendLookup.isFriendPlayer(player)) {
               Setting<Boolean> friendColorOption = this.friendSetting;
               Boolean colorFriends = friendColorOption.getValue();
               if (colorFriends) {
                  SocialRegistry friendColorLookup = ClientContext.socialRegistry;
                  GameProfile friendProfile = player.getGameProfile();
                  String friendProfileName = friendProfile.name();
                  Color fallbackFriendColor = rowColor;
                  rowColor = friendColorLookup.getColor(friendProfileName, fallbackFriendColor);
                  break label55;
               }
            }

            SocialRegistry enemyLookup = ClientContext.socialRegistry;
            if (enemyLookup.isEnemyPlayer(player)) {
               Setting<Boolean> enemyColorOption = this.enemySetting;
               Boolean colorEnemies = enemyColorOption.getValue();
               if (colorEnemies) {
                  SocialRegistry enemyColorLookup = ClientContext.socialRegistry;
                  GameProfile enemyProfile = player.getGameProfile();
                  String enemyProfileName = enemyProfile.name();
                  Color fallbackEnemyColor = rowColor;
                  rowColor = enemyColorLookup.getColor(enemyProfileName, fallbackEnemyColor);
                  break label55;
               }
            }

            if (player.isShiftKeyDown()) {
               Setting<Boolean> sneakingColorOption = this.sneakingSetting;
               Boolean colorSneaking = sneakingColorOption.getValue();
               if (colorSneaking) {
                  rowColor = SNEAKING_COLOR;
               }
            }
         }

         Component text = this.buildPlayerText(player);
         float headWidth = 0.0F;
         float headTextOffset = 0.0F;
         Setting<Boolean> headWidthOption = this.headsSetting;
         Boolean includeHeadWidth = headWidthOption.getValue();
         if (includeHeadWidth) {
            headWidth = FontRenderer.INSTANCE.getHeight() + headSpacing;
            if (this.renderer.getHorizontalDirection() <= 0 && this.renderer.getAnchor() != HudAnchor.TOP_CENTER) {
               headTextOffset = -1.0F;
            } else {
               headTextOffset = headWidth * hudScale;
            }
         }

         float rowWidth = (FontRenderer.INSTANCE.getWidth(text) + headWidth) * hudScale;
         float textX = (this.renderer.getContentX(rowWidth) - this.renderer.getX() + headTextOffset) / hudScale;
         Setting<Boolean> headRenderOption = this.headsSetting;
         Boolean showHead = headRenderOption.getValue();
         if (showHead) {
            float headY = rowY - 0.5F;
            float capturedHeadWidth = headWidth;
            this.renderPlayerHead(poses, player, headY, hudScale, capturedHeadWidth, rowWidth);
         }

         FontRenderer.INSTANCE.drawWithShadow(poses, text, textX, rowY, rowColor);
         rowY += (FontRenderer.INSTANCE.getHeight() + 1) * this.renderer.getVerticalDirection();
      }

      BufferSource bufferSource = RenderUtils.getEntityBuffers();
      bufferSource.endBatch();
   }

   private void renderPlayerHead(PoseStack poses, Player player, float y, float hudScale, float headWidth, float rowWidth) {
      float fontHeight = FontRenderer.INSTANCE.getHeight();
      float headSize = fontHeight + 0.5F;
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
      UUID playerId = player.getUUID();
      PlayerInfo playerInfo = connection.getPlayerInfo(playerId);
      if (playerInfo != null) {
         float headX = this.renderer.getHorizontalDirection() < 0 ? this.renderer.getDimensions()[0] / hudScale - headWidth : 0.0F;
         if (this.renderer.getAnchor() == HudAnchor.TOP_CENTER) {
            float remainingWidth = (this.renderer.getDimensions()[0] - rowWidth) / hudScale;
            headX = remainingWidth / 2.0F;
         }

         poses.translate(headX, y, 0.0F);
         PlayerSkin skin = playerInfo.getSkin();
         Texture bodyTexture = skin.body();
         Identifier skinTexture = bodyTexture.texturePath();
         int pixelSize = (int)(headSize - 1.0F);
         boolean playerShowsHat = playerInfo.showHat();
         this.drawSkinHead(poses, skinTexture, pixelSize, playerShowsHat);
         poses.translate(-headX, -y, 0.0F);
      }
   }

   private void drawSkinHead(PoseStack poses, Identifier skin, int size, boolean showHat) {
      float textureU = 8.0F;
      float textureVFloat = 8.0F;
      GuiDrawUtils.drawTexture(poses, skin, 0, 0, textureU, textureVFloat, size, size, 8, 8, 64, 64, -1);
      if (showHat) {
         this.drawHatLayer(poses, skin, size);
      }
   }

   private boolean shouldIncludePlayer(Player player) {
      if (ClientAccess.argusclientMinecraft.player == player) {
         return this.isInHudEditor();
      }

      Setting<Boolean> friendsOption = this.friendsSetting;
      Boolean ignoreFriends = friendsOption.getValue();
      if (ignoreFriends) {
         SocialRegistry friendLookup = ClientContext.socialRegistry;
         if (friendLookup.isFriendPlayer(player)) {
            return false;
         }
      }

      if (this.isMissingFromPlayerList(player)) {
         Setting<Boolean> botsOption = this.botsSetting;
         Boolean ignoreBots = botsOption.getValue();
         if (ignoreBots) {
            return false;
         }
      }

      Setting<Boolean> nakedsOption = this.nakedsSetting;
      Boolean ignoreNakeds = nakedsOption.getValue();
      return !ignoreNakeds || PlayerStateUtils.hasArmor(player);
   }

   private boolean isMissingFromPlayerList(Player player) {
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;

      for (PlayerInfo playerInfo : connection.getListedOnlinePlayers()) {
         GameProfile listedProfile = playerInfo.getProfile();
         if (listedProfile.equals(player.getGameProfile())) {
            return false;
         }
      }

      return true;
   }

   public TextRadarModule() {
      super("TextRadar", "playerlist");
      BooleanSetting limitOption = new BooleanSetting("Limit", false);
      Setting<Boolean> limitDependency = limitOption.markVisibilityDependency();
      this.limitSetting = this.add(limitDependency);
      NumberSetting<Integer> maximumSetting = new NumberSetting<>("Max", 8, 1, 32);
      this.maxSetting = this.add(maximumSetting.dependsOn(this.limitSetting));
      BooleanSetting colorsOption = new BooleanSetting("Colors", false);
      Setting<Boolean> colorsGroup = colorsOption.markAsGroup();
      this.colorsSetting = this.add(colorsGroup);
      BooleanSetting friendColorSetting = new BooleanSetting("Friend", true);
      this.friendSetting = this.add(friendColorSetting.dependsOn(this.colorsSetting));
      BooleanSetting enemyColorSetting = new BooleanSetting("Enemy", true);
      this.enemySetting = this.add(enemyColorSetting.dependsOn(this.colorsSetting));
      BooleanSetting sneakingColorSetting = new BooleanSetting("Sneaking", true);
      this.sneakingSetting = this.add(sneakingColorSetting.dependsOn(this.colorsSetting));
      BooleanSetting ignoreOption = new BooleanSetting("Ignore", false);
      Setting<Boolean> ignoreGroup = ignoreOption.markAsGroup();
      this.ignoreSetting = this.add(ignoreGroup);
      BooleanSetting ignoreFriendsSetting = new BooleanSetting("Friends", false);
      this.friendsSetting = this.add(ignoreFriendsSetting.dependsOn(this.ignoreSetting));
      BooleanSetting ignoreNakedsSetting = new BooleanSetting("Nakeds", false);
      this.nakedsSetting = this.add(ignoreNakedsSetting.dependsOn(this.ignoreSetting));
      BooleanSetting ignoreBotsSetting = new BooleanSetting("Bots", true);
      this.botsSetting = this.add(ignoreBotsSetting.dependsOn(this.ignoreSetting));
      this.radarPlayers = new ArrayList<>();
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.markAlignsContent();
      widget.enableHudScale();
      this.setRenderer(widget);
   }

   public enum SortMode implements NamedEntry {
      DISTANCE("Distance"),
      ANGLE("Angle"),
      ALPHABET("Alphabet");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SortMode(String displayNameArgument) {
         this.displayName = displayNameArgument;
      }
   }
}
