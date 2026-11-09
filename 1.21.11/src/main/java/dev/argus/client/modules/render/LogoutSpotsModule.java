package dev.argus.client.modules.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.ClientLevelRemoveEntityEvent;
import dev.argus.client.events.ConnectScreenConnectEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RenderStateManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.render.EntityChamsRenderer;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.ServerAddressUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.collections.ObjectPair;
import dev.argus.client.utils.combat.TotemPopTracker;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SidePlayer;
import dev.argus.client.utils.inventory.EquipmentUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.LivingEntityAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class LogoutSpotsModule extends Module {
   private static FontsModule fontsModule = ClientContext.getModule(FontsModule.class);
   private static boolean renderingTexturedModel = false;
   public final Setting<Float> distanceSetting = this.add(new NumberSetting<>("Distance", 128.0F, 16.0F, 301.0F).withUnit("m"));
   final Setting<LogoutSpotsModule.ModelChoice> modelSetting = this.add(new EnumSetting<>("Model", LogoutSpotsModule.ModelChoice.NONE));
   private final Setting<Boolean> texturedSetting;
   private final Setting<Float> alphaSetting;
   private final Setting<Float> modelWidthSetting;
   final Setting<Boolean> tracerSetting;
   private final Setting<Float> tracerWidthSetting;
   final Setting<Boolean> nameTagSetting;
   private final Setting<Float> textScaleSetting;
   private final Setting<LogoutSpotsModule.PositionChoice> positionSetting;
   private final Setting<Boolean> timeSetting;
   private final Setting<Boolean> healthSetting;
   private final Setting<Boolean> totemsSetting;
   public final Setting<Boolean> notifySetting;
   public final Setting<Boolean> loginSetting;
   public final Setting<Boolean> loginSoundSetting;
   public final Setting<NamedIdentifier> loginSoundPathSetting;
   public final Setting<Boolean> logoutSetting;
   public final Setting<Boolean> soundSetting;
   public final Setting<NamedIdentifier> soundPathSetting;
   final Setting<Boolean> colorsSetting;
   private final Setting<Color> modelFillSetting;
   private final Setting<Color> modelLineSetting;
   private final Setting<Color> boxFillSetting;
   private final Setting<Color> boxLineSetting;
   private final Setting<Color> nameTagColorSetting;
   private final Setting<Color> fillColorSetting;
   private final Setting<Boolean> filterSetting;
   private final Setting<Boolean> ignoreFriendsSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final ElapsedTimer spotCleanupTimer;
   private final Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spotsByName;
   private final List<LogoutSpotsModule.PendingRemoval> pendingRemovals;

   private static boolean isPlayerListed(Player player) {
      GameProfile profile = player.getGameProfile();
      String capturedName = profile.name();
      return isNameListed(capturedName);
   }

   private static boolean isNameListed(String playerName) {
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
      return connection.getOnlinePlayers().stream().noneMatch(playerInfo -> {
         PlayerInfo profileInfo = playerInfo;
         GameProfile profile = profileInfo.getProfile();
         return profile.name().equalsIgnoreCase(playerName);
      });
   }

   public LogoutSpotsModule.LogoutSpot getSpot(String playerName) {
      synchronized (this.spotsByName) {
         Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = this.spotsByName;
         return (LogoutSpotsModule.LogoutSpot)spots.getOrDefault(playerName, null);
      }
   }

   public void removeSpot(String playerName) {
      removeSpot(this, playerName);
   }

   private static void removeSpot(LogoutSpotsModule owner, String playerName) {
      synchronized (owner.spotsByName) {
         Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = owner.spotsByName;
         spots.remove(playerName);
      }
   }

   private static String getCurrentServerAddress() {
      return ServerAddressUtils.getCurrentServerAddress();
   }

   @Override
   public void onToggle() {
      Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = this.spotsByName;
      spots.clear();
   }

   public void putSpot(String playerName, LogoutSpotsModule.LogoutSpot spot) {
      synchronized (this.spotsByName) {
         Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = this.spotsByName;
         spots.put(playerName, spot);
      }
   }

   private String formatSpotDetails(LogoutSpotsModule.LogoutSpot spot) {
      StringBuilder details = new StringBuilder();
      Setting<LogoutSpotsModule.PositionChoice> coordinatePositionOption = this.positionSetting;
      if (coordinatePositionOption.getValue() == LogoutSpotsModule.PositionChoice.COORDINATES) {
         Vec3 position = spot.position();
         details.append(String.format(Locale.US, " X: %.1f, Y: %.1f, Z: %.1f", position.x(), position.y(), position.z()));
      }

      Setting<LogoutSpotsModule.PositionChoice> distancePositionOption = this.positionSetting;
      if (distancePositionOption.getValue() == LogoutSpotsModule.PositionChoice.DISTANCE) {
         Vec3 capturedPlayerPosition = ClientAccess.argusclientMinecraft.player.position();
         Vec3 spotPosition = spot.position();
         double distance = capturedPlayerPosition.distanceTo(spotPosition);
         String distanceSeparator = " ";
         details.append(distanceSeparator);
         String formattedDistance = "%.1fm".formatted(distance);
         details.append(formattedDistance);
      }

      Boolean showTime = this.timeSetting.getValue();
      if (showTime) {
         Object[] timeValues = new Object[1];
         SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
         Date logoutTime = new Date(spot.timestamp());
         timeValues[0] = dateFormat.format(logoutTime);
         String formattedTime = " %s".formatted(timeValues);
         details.append(formattedTime);
      }

      Boolean showHealth = this.healthSetting.getValue();
      if (showHealth) {
         String healthPrefix = " (";
         details.append(healthPrefix);
         int health = spot.health;
         details.append(health);
         String healthSuffix = "hp)";
         details.append(healthSuffix);
      }

      if (spot.totemPops > 0) {
         Boolean showTotems = this.totemsSetting.getValue();
         if (showTotems) {
            String totemPrefix = " -";
            details.append(totemPrefix);
            int totemPops = spot.totemPops;
            details.append(totemPops);
         }
      }

      return details.toString();
   }

   @EventHandler
   private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      ClientLevel capturedLevel = ClientAccess.argusclientMinecraft.level;
      int entityId = event.getId();
      if (capturedLevel.getEntity(entityId) instanceof Player removedPlayer) {
         if (removedPlayer instanceof FakeplayerCommand.SimulatedPlayer) {
            return;
         }

         if (removedPlayer.deathTime > 0) {
            return;
         }

         if (EntityUtils.getHealthWithAbsorption(removedPlayer) <= 0.0F) {
            return;
         }

         Boolean ignoreFriends = this.ignoreFriendsSetting.getValue();
         if (ignoreFriends) {
            SocialRegistry friends = ClientContext.socialRegistry;
            if (friends.isFriendPlayer(removedPlayer)) {
               return;
            }
         }

         Boolean ignoreNakeds = this.ignoreNakedsSetting.getValue();
         if (ignoreNakeds && !hasArmor(removedPlayer)) {
            return;
         }

         List<LogoutSpotsModule.PendingRemoval> pending = this.pendingRemovals;
         pending.add(new LogoutSpotsModule.PendingRemoval(removedPlayer, System.currentTimeMillis(), new AtomicBoolean()));
      }
   }

   private static boolean hasArmor(Player player) {
      return PlayerStateUtils.hasArmor(player);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      for (LogoutSpotsModule.PendingRemoval pendingRemoval : this.pendingRemovals) {
         Player removedPlayer = pendingRemoval.player();
         if (isPlayerListed(removedPlayer)) {
            boolean processed = true;
            AtomicBoolean processedFlag = pendingRemoval.processedFlag;
            processedFlag.set(processed);
            Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> capturedSpots = this.spotsByName;
            GameProfile profile = removedPlayer.getGameProfile();
            String capturedPlayerName = profile.name();
            String serverAddress = getCurrentServerAddress();
            LogoutSpotsModule.LogoutSpot snapshot = LogoutSpotsModule.LogoutSpot.capturePlayer(removedPlayer, serverAddress);
            capturedSpots.put(capturedPlayerName, snapshot);
            Boolean notify = this.notifySetting.getValue();
            if (notify) {
               Boolean logoutNotification = this.logoutSetting.getValue();
               if (logoutNotification) {
                  Boolean playSound = this.loginSoundSetting.getValue();
                  if (playSound) {
                     SoundManager capturedSoundManager = ClientContext.soundManager;
                     NamedIdentifier configuredSoundPath = this.soundPathSetting.getValue();
                     capturedSoundManager.playSound(configuredSoundPath, 1.0F);
                  }
               }
            }
         }
      }

      this.pendingRemovals.removeIf(LogoutSpotsModule.PendingRemoval::isReadyForRemoval);
      ElapsedTimer capturedCleanupTimer = this.spotCleanupTimer;
      TimeUnit cleanupUnit = TimeUnit.SECONDS;
      if (capturedCleanupTimer.hasElapsedAndReset(5L, cleanupUnit)) {
         removeFirstUnlistedSpot(this);
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      GuiDrawUtils.flush();
      LineMeshRenderer.begin(event, this.tracerWidthSetting.getValue());
      synchronized (this.spotsByName) {
         ObjectIterator<Entry<String, LogoutSpotsModule.LogoutSpot>> spotIterator = this.spotsByName.entrySet().iterator();

         while (spotIterator.hasNext()) {
            Entry<String, LogoutSpotsModule.LogoutSpot> entry = (Entry<String, LogoutSpotsModule.LogoutSpot>)spotIterator.next();
            LogoutSpotsModule.LogoutSpot serverSpot = entry.getValue();
            String savedServerAddress = serverSpot.serverAddress();
            if (savedServerAddress.equals(getCurrentServerAddress())) {
               LogoutSpotsModule.LogoutSpot dimensionSpot = entry.getValue();
               DimensionType dimension = dimensionSpot.dimension();
               if (dimension == WorldUtils.getDimension()) {
                  AABB distanceBox = entry.getValue().boundingBox;
                  Vec3 capturedBoxCenter = distanceBox.getCenter();
                  Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
                  double distance = capturedBoxCenter.distanceTo(cameraPosition);
                  Float maximumDistance = this.distanceSetting.getValue();
                  if (distance > maximumDistance.floatValue()) {
                     float distanceSentinel = this.distanceSetting.getValue();
                     if (distanceSentinel != 301.0F) {
                        continue;
                     }
                  }

                  PoseStack poses = event.getPoseStack();
                  Setting<LogoutSpotsModule.ModelChoice> simpleModelOption = this.modelSetting;
                  if (simpleModelOption.getValue() == LogoutSpotsModule.ModelChoice.SIMPLE
                     || this.modelSetting.getValue() == LogoutSpotsModule.ModelChoice.BOTH) {
                     LogoutSpotsModule.LogoutSpot boxFillSpot = entry.getValue();
                     AABB fillBox = boxFillSpot.boundingBox();
                     Setting<Color> boxFillOption = this.boxFillSetting;
                     WorldBoxRenderer.fillBox(poses, fillBox, boxFillOption.getValue());
                     LogoutSpotsModule.LogoutSpot boxLineSpot = entry.getValue();
                     AABB lineBox = boxLineSpot.boundingBox();
                     Color boxLineColor = this.boxLineSetting.getValue();
                     Float boxWidth = this.modelWidthSetting.getValue();
                     WorldBoxRenderer.outlineBox(poses, lineBox, boxLineColor, boxWidth);
                  }

                  Setting<LogoutSpotsModule.ModelChoice> complexModelOption = this.modelSetting;
                  if (complexModelOption.getValue() == LogoutSpotsModule.ModelChoice.COMPLEX
                     || this.modelSetting.getValue() == LogoutSpotsModule.ModelChoice.BOTH) {
                     LogoutSpotsModule.LogoutSpot modelSpot = entry.getValue();
                     SidePlayer playerModel = modelSpot.playerModel();
                     Boolean textured = this.texturedSetting.getValue();
                     if (textured) {
                        renderingTexturedModel = true;
                        Float alpha = this.alphaSetting.getValue();
                        RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, alpha);
                        RenderUtils modelRenderer = RenderUtils.INSTANCE;
                        RenderBuffers renderBuffers = ClientAccess.argusclientMinecraft.renderBuffers();
                        modelRenderer.renderEntity(playerModel, 1.0F, poses, renderBuffers.bufferSource());
                        RenderUtils.INSTANCE.flushEntities();
                        RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        renderingTexturedModel = false;
                     }

                     Float modelWidth = this.modelWidthSetting.getValue();
                     EntityChamsRenderer.setLineWidth(modelWidth.floatValue());
                     Color modelLineColor = this.modelLineSetting.getValue();
                     Setting<Color> modelFillOption = this.modelFillSetting;
                     EntityChamsRenderer.setColors(modelLineColor, modelFillOption.getValue());
                     EntityChamsRenderer.renderEntity(poses, playerModel);
                  }

                  Setting<Boolean> tracerOption = this.tracerSetting;
                  Boolean drawTracer = tracerOption.getValue();
                  if (drawTracer) {
                     LogoutSpotsModule.LogoutSpot tracerSpot = entry.getValue();
                     AABB tracerBox = tracerSpot.boundingBox();
                     Vec3 tracerPosition = tracerBox.getCenter();
                     Setting<Color> tracerColorOption = this.modelLineSetting;
                     LineMeshRenderer.addTracer(tracerPosition, tracerColorOption.getValue());
                  }

                  Setting<Boolean> nameTagOption = this.nameTagSetting;
                  Boolean drawNameTag = nameTagOption.getValue();
                  if (drawNameTag) {
                     LogoutSpotsModule.LogoutSpot tagCenterSpot = entry.getValue();
                     AABB tagCenterBox = tagCenterSpot.boundingBox();
                     Vec3 capturedTagCenter = tagCenterBox.getCenter();
                     Axis heightAxis = Axis.Y;
                     LogoutSpotsModule.LogoutSpot tagHeightSpot = entry.getValue();
                     double tagBoxTop = tagHeightSpot.boundingBox().maxY;
                     double tagHeight = tagBoxTop + 0.5;
                     Vec3 tagPosition = capturedTagCenter.with(heightAxis, tagHeight);
                     AliasRegistry capturedNameResolver = ClientContext.aliasRegistry;
                     String capturedTagName = entry.getKey();
                     String label = capturedNameResolver.getPlayerAlias(capturedTagName) + " logout spot";
                     LogoutSpotsModule.LogoutSpot detailsSpot = entry.getValue();
                     String details = this.formatSpotDetails(detailsSpot);
                     FontsModule fontModule = fontsModule;
                     float computedFontOffset;
                     if (fontModule.isToggled()) {
                        computedFontOffset = 1.0F;
                     } else {
                        computedFontOffset = 0.0F;
                     }

                     float fontOffset = computedFontOffset;
                     if (!details.isEmpty()) {
                        String detailsText = String.valueOf(details);
                        label = "\u0001\u0001"
                           .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(label)))
                           .replaceFirst("\u0001", Matcher.quoteReplacement(detailsText));
                     }

                     GameRenderer tagGameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
                     Camera tagCamera = tagGameRenderer.getMainCamera();
                     Vec3 capturedTagCameraPosition = tagCamera.position();
                     Setting<Float> textScaleOption = this.textScaleSetting;
                     Float configuredTextScale = textScaleOption.getValue();
                     double textScaleValue = configuredTextScale.floatValue();
                     double textScale = MathUtils.getDistanceScale(capturedTagCameraPosition, tagPosition, textScaleValue);
                     RenderUtils backgroundRenderer = RenderUtils.INSTANCE;
                     float backgroundTextWidth = FontRenderer.INSTANCE.getWidth(label);
                     float backgroundHalfWidth = -(backgroundTextWidth / 2.0F);
                     float backgroundLeft = backgroundHalfWidth - 2.0F;
                     float backgroundVerticalOffset = 5.0F + fontOffset;
                     double backgroundScale = textScale * 2.0;
                     Setting<Color> fillColorOption = this.fillColorSetting;
                     backgroundRenderer.drawWorldRect(
                        poses, tagPosition, -0.03F, 0.0F, backgroundLeft, backgroundVerticalOffset, backgroundScale, fillColorOption.getValue()
                     );
                     RenderUtils textRenderer = RenderUtils.INSTANCE;
                     float textWidth = FontRenderer.INSTANCE.getWidth(label);
                     float textLeft = -(textWidth / 2.0F);
                     Setting<Color> nameTagColorOption = this.nameTagColorSetting;
                     textRenderer.drawWorldText(poses, label, tagPosition, 0.0F, 0.0F, textLeft, 2.0F, textScale, nameTagColorOption.getValue(), true);
                  }
               }
            }
         }
      }

      LineMeshRenderer.draw();
      RenderStateManager.addStates(RenderStateManager.POLYGON_OFFSET_THROUGH_WALLS);
      GuiDrawUtils.flush();
      RenderStateManager.reset();
   }

   private static void removeFirstUnlistedSpot(LogoutSpotsModule owner) {
      synchronized (owner.spotsByName) {
         String nameToRemove = null;
         Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = owner.spotsByName;
         ObjectIterator<String> names = spots.keySet().iterator();

         while (names.hasNext()) {
            String playerName = (String)names.next();
            if (!isNameListed(playerName)) {
               nameToRemove = playerName;
               break;
            }
         }

         if (nameToRemove != null) {
            String removedName = nameToRemove;
            owner.removeSpot(removedName);
         }
      }
   }

   public static boolean isRenderingTexturedModel() {
      return renderingTexturedModel;
   }

   public List<SidePlayer> getSpotPlayers() {
      synchronized (this.spotsByName) {
         Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = this.spotsByName;
         Stream<SidePlayer> players = spots.values().stream().map(LogoutSpotsModule.LogoutSpot::playerModel);
         return players.collect(Collectors.toUnmodifiableList());
      }
   }

   public ObjectPair raycastSpot() {
      if (this.isWorldUnavailable()) {
         return null;
      }

      LocalPlayer capturedPlayer = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = capturedPlayer.getEyePosition(1.0F);
      capturedPlayer = ClientAccess.argusclientMinecraft.player;
      Vec3 rayOffset = capturedPlayer.getViewVector(1.0F).scale(100.0);
      Vec3 rayEnd = eyePosition.add(rayOffset);
      synchronized (this.spotsByName) {
         ObjectIterator<Entry<String, LogoutSpotsModule.LogoutSpot>> spotIterator = this.spotsByName.entrySet().iterator();

         while (spotIterator.hasNext()) {
            Entry<String, LogoutSpotsModule.LogoutSpot> entry = (Entry<String, LogoutSpotsModule.LogoutSpot>)spotIterator.next();
            AABB bounds = entry.getValue().boundingBox;
            SidePlayer poseModel = entry.getValue().playerModel;
            Pose pose = poseModel.getPose();
            if (pose == Pose.FALL_FLYING || pose == Pose.SWIMMING) {
               SidePlayer lookModel = entry.getValue().playerModel;
               Vec3 look = lookModel.getLookAngle();
               if (pose == Pose.FALL_FLYING) {
                  AABB movingBounds = bounds;
                  bounds = movingBounds.move(look);
               }

               double lookX = look.x;
               double absoluteLookX = Math.abs(lookX);
               double horizontalInflation = absoluteLookX * 0.8;
               double verticalInflation;
               if (pose == Pose.SWIMMING) {
                  verticalInflation = 0.4;
               } else {
                  verticalInflation = 0.6;
               }

               double lookZ = look.z;
               double absoluteLookZ = Math.abs(lookZ);
               double inflateZ = absoluteLookZ * 0.8;
               double inflateY = verticalInflation;
               AABB expandedBounds = bounds;
               bounds = expandedBounds.inflate(horizontalInflation, inflateY, inflateZ);
            }

            AABB clippedBounds = bounds;
            Optional hit = clippedBounds.clip(eyePosition, rayEnd);
            if (hit.isPresent()) {
               return new ObjectPair(entry.getKey(), entry.getValue());
            }
         }

         return null;
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
         for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
            ClientAccess.argusclientMinecraft.executeIfPossible(() -> {
               GameProfile profile = entry.profile();
               String profileName;
               if (profile == null) {
                  profileName = "";
               } else {
                  profileName = profile.name();
               }

               String playerName = profileName;
               this.removeSpot(playerName);
            });
         }
      }
   }

   @EventHandler
   private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      ServerStateManager serverSource = ClientContext.serverStateManager;
      clearForServerConnection(this, serverSource, event);
   }

   private static void clearForServerConnection(LogoutSpotsModule owner, ServerStateManager serverSource, ConnectScreenConnectEvent event) {
      if (serverSource.getServerData() != null) {
         String connectingAddress = event.getServerAddress();
         ServerStateManager capturedServerSource = ClientContext.serverStateManager;
         if (!connectingAddress.equals(capturedServerSource.getServerData().ip)) {
            return;
         }
      }

      Object2ObjectOpenHashMap<String, LogoutSpotsModule.LogoutSpot> spots = owner.spotsByName;
      spots.clear();
   }

   public void executeWithSpotLock(Runnable action) {
      ClientAccess.argusclientMinecraft.executeIfPossible(() -> {
         synchronized (this.spotsByName) {
            action.run();
         }
      });
   }

   public static void bindFontsModule(ModuleRegistry moduleRegistry) {
      fontsModule = (FontsModule)moduleRegistry.getModule(FontsModule.class);
   }

   public LogoutSpotsModule() {
      super("LogoutSpots", "Highlights log out spots.", ModuleCategory.RENDER);
      Predicate<Object> complexModelVisibility = ignored -> {
         Setting<LogoutSpotsModule.ModelChoice> option = this.modelSetting;
         return option.getValue() == LogoutSpotsModule.ModelChoice.COMPLEX || this.modelSetting.getValue() == LogoutSpotsModule.ModelChoice.BOTH;
      };
      this.texturedSetting = this.add(new BooleanSetting("Textured", false, complexModelVisibility).markVisibilityDependency());
      this.alphaSetting = this.add(new NumberSetting<>("Alpha", 0.5F, 0.0F, 1.0F, complexModelVisibility).dependsOn(this.texturedSetting));
      Predicate<Object> modelWidthVisibility = ignored -> {
         Setting<LogoutSpotsModule.ModelChoice> option = this.modelSetting;
         return option.getValue() != LogoutSpotsModule.ModelChoice.NONE;
      };
      this.modelWidthSetting = this.add(new NumberSetting<>("Width", 1.0F, 0.5F, 3.0F, modelWidthVisibility));
      this.tracerSetting = this.add(new BooleanSetting("Tracer", false).markVisibilityDependency());
      Predicate<Object> tracerWidthVisibility = ignored -> {
         Setting<Boolean> option = this.tracerSetting;
         return option.isExpanded();
      };
      this.tracerWidthSetting = this.add(new NumberSetting<>("Width", 1.0F, 0.1F, 1.0F, tracerWidthVisibility));
      this.nameTagSetting = this.add(new BooleanSetting("NameTag", true).markVisibilityDependency());
      Predicate<Object> nameTagOptionsVisible = ignored -> {
         Setting<Boolean> option = this.nameTagSetting;
         return option.isExpanded();
      };
      this.textScaleSetting = this.add(new NumberSetting<>("TextScale", 1.0F, 0.5F, 3.0F, nameTagOptionsVisible));
      this.positionSetting = this.add(new EnumSetting<>("Position", LogoutSpotsModule.PositionChoice.NONE, nameTagOptionsVisible));
      this.timeSetting = this.add(new BooleanSetting("Time", false, nameTagOptionsVisible));
      this.healthSetting = this.add(new BooleanSetting("Health", true, nameTagOptionsVisible));
      this.totemsSetting = this.add(new BooleanSetting("Totems", false).dependsOn(this.nameTagSetting));
      this.notifySetting = this.add(new BooleanSetting("Notify", false).markVisibilityDependency());
      this.loginSetting = this.add(new BooleanSetting("Login", true).markVisibilityDependency().dependsOn(this.notifySetting));
      this.loginSoundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency().dependsOn(this.notifySetting, this.loginSetting));
      this.loginSoundPathSetting = this.add(
         new IdentifierSetting("Sound Path", BuiltinSound.STEAM.getIdentifier()).dependsOn(this.notifySetting, this.loginSetting, this.loginSoundSetting)
      );
      this.logoutSetting = this.add(new BooleanSetting("Logout", true).markVisibilityDependency().dependsOn(this.notifySetting));
      this.soundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency().dependsOn(this.notifySetting, this.logoutSetting));
      this.soundPathSetting = this.add(
         new IdentifierSetting("Sound Path", BuiltinSound.STEAM.getIdentifier()).dependsOn(this.notifySetting, this.logoutSetting, this.soundSetting)
      );
      this.colorsSetting = this.add(new BooleanSetting("Colors", true).markAsGroup());
      this.modelFillSetting = this.add(new ColorSetting("ModelFill", new Color(1019058687, true), complexModelVisibility).dependsOn(this.colorsSetting));
      this.modelLineSetting = this.add(new ColorSetting("ModelLine", new Color(-423781889, true), complexModelVisibility).dependsOn(this.colorsSetting));
      Predicate<Object> boxFillVisibility = ignored -> {
         Setting<LogoutSpotsModule.ModelChoice> option = this.modelSetting;
         return option.getValue() == LogoutSpotsModule.ModelChoice.SIMPLE || this.modelSetting.getValue() == LogoutSpotsModule.ModelChoice.BOTH;
      };
      this.boxFillSetting = this.add(new ColorSetting("BoxFill", new Color(1019058687, true), boxFillVisibility).dependsOn(this.colorsSetting));
      this.boxLineSetting = this.add(new ColorSetting("BoxLine", new Color(-423781889, true), boxFillVisibility).dependsOn(this.colorsSetting));
      Predicate<Object> nameTagColorVisibility = ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      };
      this.nameTagColorSetting = this.add(new ColorSetting("NameTagColor", new Color(-289564161, true), nameTagColorVisibility));
      this.fillColorSetting = this.add(new ColorSetting("FillColor", new Color(1292503562, true), nameTagColorVisibility));
      this.filterSetting = this.add(new BooleanSetting("Filter", false).markAsGroup());
      this.ignoreFriendsSetting = this.add(new BooleanSetting("IgnoreFriends", false).dependsOn(this.filterSetting));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.filterSetting));
      this.spotCleanupTimer = new ElapsedTimer();
      this.spotsByName = new Object2ObjectOpenHashMap();
      this.pendingRemovals = new ArrayList<>();
      this.distanceSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
      this.loginSoundSetting.setConfigName("LoginSoundPath");
      this.soundSetting.setConfigName("LogoutSoundPath");
      this.setDrawn(false);
   }

   public record LogoutSpot(
      long timestamp,
      AABB boundingBox,
      DimensionType dimension,
      int health,
      int totemPops,
      Vec3 position,
      SidePlayer playerModel,
      String serverAddress,
      boolean simulatedPlayer
   ) {
      public static LogoutSpotsModule.LogoutSpot fromBytes(byte[] bytes) {
         try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
            DataInputStream input = new DataInputStream(byteStream);
            long timestamp = input.readLong();
            AABB bounds = new AABB(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
            int health = input.readInt();
            int totemPops = input.readInt();
            Vec3 position = new Vec3(input.readDouble(), input.readDouble(), input.readDouble());
            SidePlayer playerModel = new SidePlayer(ClientAccess.argusclientMinecraft.level);
            playerModel.readSnapshot(input);
            playerModel.hurtTime = playerModel.deathTime = 0;
            return new LogoutSpotsModule.LogoutSpot(
               timestamp, bounds, getSnapshotDimension(), health, totemPops, position, playerModel, getSnapshotServerAddress(), false
            );
         } catch (Throwable failure) {
            return null;
         }
      }

      private static DimensionType getSnapshotDimension() {
         return WorldUtils.getDimension();
      }

      private static String getSnapshotServerAddress() {
         return LogoutSpotsModule.getCurrentServerAddress();
      }

      public byte[] toBytes() {
         try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(byteStream);
            output.writeLong(this.timestamp);
            output.writeDouble(this.boundingBox.minX);
            output.writeDouble(this.boundingBox.minY);
            output.writeDouble(this.boundingBox.minZ);
            output.writeDouble(this.boundingBox.maxX);
            output.writeDouble(this.boundingBox.maxY);
            output.writeDouble(this.boundingBox.maxZ);
            output.writeInt(this.health);
            output.writeInt(this.totemPops);
            output.writeDouble(this.position.x);
            output.writeDouble(this.position.y);
            output.writeDouble(this.position.z);
            SidePlayer tickCountModel = this.playerModel;
            output.writeInt(tickCountModel.getCapturedTickCount());
            output.writeDouble(this.playerModel.getCapturedPosition().x());
            output.writeDouble(this.playerModel.getCapturedPosition().y());
            output.writeDouble(this.playerModel.getCapturedPosition().z());
            output.writeDouble(this.playerModel.getX());
            output.writeDouble(this.playerModel.getY());
            output.writeDouble(this.playerModel.getZ());
            output.writeFloat(this.playerModel.yRotO);
            output.writeFloat(this.playerModel.xRotO);
            output.writeFloat(this.playerModel.getYRot());
            output.writeFloat(this.playerModel.getXRot());
            output.writeFloat(this.playerModel.yBodyRot);
            output.writeFloat(this.playerModel.yBodyRotO);
            output.writeFloat(this.playerModel.yHeadRot);
            output.writeFloat(this.playerModel.yHeadRotO);
            output.writeFloat(this.playerModel.walkAnimation.speed);
            output.writeFloat(this.playerModel.walkAnimation.position);
            SidePlayer poseModel = this.playerModel;
            output.writeInt(poseModel.getPose().id());
            output.writeBoolean(this.playerModel.isShiftKeyDown());
            SidePlayer mainHandModel = this.playerModel;
            ItemStack mainHandStack = mainHandModel.getMainHandItem();
            output.writeUTF(mainHandStack.getItem().toString());
            output.writeUTF(this.playerModel.getOffhandItem().getItem().toString());
            SidePlayer armorModel = this.playerModel;
            List<ItemStack> armor = EquipmentUtils.getArmorStacks(armorModel);
            output.writeInt(armor.size());

            for (ItemStack armorStack : armor) {
               output.writeUTF(armorStack.getItem().toString());
            }

            output.writeFloat(((LivingEntityAccessor)this.playerModel).argusclient$getLeaningPitch());
            output.writeFloat(((LivingEntityAccessor)this.playerModel).argusclient$getLastLeaningPitch());
            output.writeBoolean(this.playerModel.isFallFlying());
            output.writeInt(this.playerModel.getFallFlyingTicks());
            output.close();
            return byteStream.toByteArray();
         } catch (Throwable failure) {
            return null;
         }
      }

      public static LogoutSpotsModule.LogoutSpot capturePlayer(Player player, String serverAddress) {
         SidePlayer playerModel = new SidePlayer(player);
         playerModel.hurtTime = playerModel.deathTime = 0;
         long timestamp = System.currentTimeMillis();
         AABB bounds = player.getBoundingBox();
         DimensionType dimension = WorldUtils.getDimension();
         float rawHealth = EntityUtils.getHealthWithAbsorption(player);
         int health = Math.round(rawHealth);
         TotemPopTracker totemTracker = ClientContext.totemPopTracker;
         int totemPops = totemTracker.getPopCount(player);
         Vec3 position = player.position();
         boolean simulatedPlayer = player instanceof FakeplayerCommand.SimulatedPlayer;
         return new LogoutSpotsModule.LogoutSpot(timestamp, bounds, dimension, health, totemPops, position, playerModel, serverAddress, simulatedPlayer);
      }
   }

   public enum ModelChoice implements NamedEntry {
      NONE("None"),
      SIMPLE("Simple"),
      COMPLEX("Complex"),
      BOTH("Both");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModelChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   private record PendingRemoval(Player player, long removalTime, AtomicBoolean processedFlag) {
      public boolean isReadyForRemoval() {
         long ageMillis = System.currentTimeMillis() - this.removalTime;
         return ageMillis >= 300L || this.processedFlag.get();
      }
   }

   public enum PositionChoice implements NamedEntry {
      NONE("None"),
      COORDINATES("Coordinates"),
      DISTANCE("Distance");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      PositionChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
