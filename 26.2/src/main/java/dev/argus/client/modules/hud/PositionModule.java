package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.collections.IntPair;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public class PositionModule extends HudWidgetModule {
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private final Setting<Float> safeRangeSetting;
   private final Setting<Boolean> netherSetting;
   private final Setting<Boolean> prefixSetting;
   private final Setting<Boolean> fakeSetting;
   private final ElapsedTimer redactionTimer;
   private IntPair fakeOffset;

   private boolean isRedactionGraceActive() {
      int playerTicks = ClientAccess.argusclientMinecraft.player.tickCount;
      if (playerTicks >= 200) {
         ElapsedTimer timer = this.redactionTimer;
         double delaySeconds = 5.0;
         TimeUnit delayUnit = TimeUnit.SECONDS;
         if (timer.hasElapsed(delaySeconds, delayUnit)) {
            return false;
         }
      }

      return true;
   }

   private String getCoordinatePrefix() {
      Setting<Boolean> prefixOption = this.prefixSetting;
      Boolean showPrefix = prefixOption.getValue();
      return !showPrefix ? "" : "XYZ ";
   }

   @EventHandler
   private void onPlayerPositionPacket(PacketEvent.Receive event) {
      if (!this.isWorldUnavailable() && event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
         PositionMoveRotation positionChange = packet.change();
         Vec3 packetPosition = positionChange.position();
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 playerPosition = player.position();
         double distanceSquared = packetPosition.distanceToSqr(playerPosition);
         if (distanceSquared >= 10000.0) {
            this.redactionTimer.reset();
         }
      }
   }

   public PositionModule() {
      super("Position", "coordinates");
      NumberSetting<Float> safeRangeOption = new NumberSetting<>("SafeRange", 0.0F, 0.0F, 100.0F);
      String safeRangeUnit = "k";
      NumberSetting<Float> safeRangeWithUnit = safeRangeOption.withUnit(safeRangeUnit);
      this.safeRangeSetting = this.add(safeRangeWithUnit);
      this.netherSetting = this.add(new BooleanSetting("Nether", true));
      this.prefixSetting = this.add(new BooleanSetting("Prefix", true));
      this.fakeSetting = this.add(new BooleanSetting("Fake", false));
      this.redactionTimer = new ElapsedTimer();
      Setting<Float> safeRangeForDisplay = this.safeRangeSetting;
      SettingLabelCondition minimumDisplayBound = SettingLabelCondition.MIN;
      String minimumDisplayText = "None";
      safeRangeForDisplay.setBoundaryLabel(minimumDisplayText, minimumDisplayBound);
      Setting<Boolean> fakeCallbackOption = this.fakeSetting;
      Runnable fakeCallback = () -> {
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable offsetTask = () -> {
            boolean negateX = MathUtils.passesRandomThreshold(50);
            boolean negateZ = MathUtils.passesRandomThreshold(50);
            LocalPlayer playerForBaseX = ClientAccess.argusclientMinecraft.player;
            double negatedPlayerX = -playerForBaseX.getX();
            double xBase = negatedPlayerX * 2.0;
            double randomX = Math.random();
            LocalPlayer playerForRandomX = ClientAccess.argusclientMinecraft.player;
            double scaledRandomX = randomX * playerForRandomX.getX();
            int fakeX = (int)(xBase + scaledRandomX * 4.0);
            LocalPlayer playerForBaseZ = ClientAccess.argusclientMinecraft.player;
            double negatedPlayerZ = -playerForBaseZ.getZ();
            double zBase = negatedPlayerZ * 2.0;
            double randomZ = Math.random();
            LocalPlayer playerForRandomZ = ClientAccess.argusclientMinecraft.player;
            double scaledRandomZ = randomZ * playerForRandomZ.getZ();
            int fakeZ = (int)(zBase + scaledRandomZ * 4.0);
            fakeX += 13;
            fakeZ -= 37;
            if (negateX) {
               fakeX = -fakeX;
            }

            if (negateZ) {
               fakeZ = -fakeZ;
            }

            this.fakeOffset = new IntPair(fakeX, fakeZ);
         };
         scheduler.scheduleRotationTask(offsetTask, 0);
      };
      fakeCallbackOption.setChangeCallback(fakeCallback);
      HudTextRow coordinateRow = new HudTextRow(() -> {
         label43:
         if (!this.isWorldUnavailable()) {
            Setting<Float> safeRangeOptionForEnabled = this.safeRangeSetting;
            Float safeRangeEnabledValue = safeRangeOptionForEnabled.getValue();
            if (safeRangeEnabledValue > 0.0F) {
               Vec3 positionForSafeRange = ClientAccess.argusclientMinecraft.player.position();
               Vec3 origin = Vec3.ZERO;
               Setting<Float> safeRangeOptionForDistance = this.safeRangeSetting;
               Float safeRangeValue = safeRangeOptionForDistance.getValue();
               float safeRangeThousands = safeRangeValue;
               double safeRangeDistance = safeRangeThousands * 1000.0F;
               if (!positionForSafeRange.closerThan(origin, safeRangeDistance) && this.isRedactionGraceActive()) {
                  break label43;
               }
            }

            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            ResourceKey<?> dimension = level.dimension();
            Identifier dimensionIdentifier = dimension.identifier();
            String dimensionPath = dimensionIdentifier.getPath();
            boolean inNether = dimensionPath.contains("nether");
            Vec3 displayPosition = ClientAccess.argusclientMinecraft.player.position();
            FreecamModule freecamForEnabledCheck = freecam;
            if (freecamForEnabledCheck.isToggled()) {
               Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
               Vec3 cameraPosition = camera.position();
               double playerHeight = ClientAccess.argusclientMinecraft.player.getBbHeight();
               displayPosition = cameraPosition.subtract(0.0, playerHeight, 0.0);
            }

            Setting<Boolean> fakeDisplayOption = this.fakeSetting;
            Boolean useFakeOffset = fakeDisplayOption.getValue();
            if (useFakeOffset && this.fakeOffset != null) {
               IntPair offsetForXCheck = this.fakeOffset;
               if (offsetForXCheck.first() == 0) {
                  IntPair offsetForZCheck = this.fakeOffset;
                  if (offsetForZCheck.second() == 0) {
                     this.fakeSetting.runChangeCallback();
                  }
               }

               double offsetX = this.fakeOffset.first();
               double offsetZ = this.fakeOffset.second();
               displayPosition = displayPosition.add(offsetX, 0.0, offsetZ);
            }

            BlockPos currentBlockPosition = BlockPos.containing(displayPosition);
            BlockPos convertedBlockPosition;
            if (inNether) {
               Vec3 convertedPosition = displayPosition.multiply(8.0, 1.0, 8.0);
               convertedBlockPosition = BlockPos.containing(convertedPosition);
            } else {
               Vec3 convertedPosition = displayPosition.multiply(0.125, 1.0, 0.125);
               convertedBlockPosition = BlockPos.containing(convertedPosition);
            }

            String coordinatePrefix = this.getCoordinatePrefix();
            ChatFormatting prefixColor = ChatFormatting.WHITE;
            String prefixColorText = String.valueOf(prefixColor);
            String formattedPrefix = coordinatePrefix + prefixColorText;
            int currentX = currentBlockPosition.getX();
            int currentY = currentBlockPosition.getY();
            int currentZ = currentBlockPosition.getZ();
            String coordinateText = currentX + ", " + currentY + ", " + currentZ;
            int convertedX = convertedBlockPosition.getX();
            int convertedZ = convertedBlockPosition.getZ();
            String convertedText = " (" + convertedX + ", " + convertedZ + ")";
            Setting<Boolean> netherDisplayOption = this.netherSetting;
            Boolean showConvertedPosition = netherDisplayOption.getValue();
            if (showConvertedPosition && WorldUtils.getDimension() != DimensionType.THE_END) {
               coordinateText = coordinateText + convertedText;
            }

            String positionText = formattedPrefix + coordinateText;
            return Component.literal(positionText);
         }

         Object[] redactedFormatArguments = new Object[]{this.getCoordinatePrefix(), ChatFormatting.WHITE};
         String redactedText = "%s%sREDACTED".formatted(redactedFormatArguments);
         return Component.literal(redactedText);
      }, () -> true);
      this.setRenderer(new HudTextListRenderer(this, coordinateRow));
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_LEFT);
   }
}
