package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.ItemUtils;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AimAssistModule extends Module {
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F).withUnit("m"));
   private final Setting<Float> speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 15.0F));
   private final Setting<Float> fovSetting = this.add(new NumberSetting<>("Fov", 90.0F, 1.0F, 360.0F));
   private final Setting<Boolean> onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
   private final Setting<Boolean> ignoreInvisibleSetting = this.add(new BooleanSetting("IgnoreInvisible", true));
   private final Setting<Boolean> lockTargetSetting = this.add(new BooleanSetting("LockTarget", true));
   private final Setting<Boolean> friendsSetting = this.add(new BooleanSetting("Friends", true));
   private final Setting<Boolean> instantSetting = this.add(new BooleanSetting("Instant", false));
   private AbstractClientPlayer targetPlayer;

   public AimAssistModule() {
      super("AimAssist", "Helps you aiming at players.", ModuleCategory.COMBAT);
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (ClientAccess.argusclientMinecraft.gui.screen() == null) {
         if (ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem() instanceof AxeItem
            || ItemUtils.isSword(ClientAccess.argusclientMinecraft.player.getMainHandItem())
            || !this.onlyWeaponSetting.getValue()) {
            boolean keepTarget = false;
            if (this.targetPlayer != null) {
               keepTarget = this.lockTargetSetting.getValue();
            }

            if (!keepTarget) {
               Stream<AbstractClientPlayer> players = ClientAccess.argusclientMinecraft.level.players().stream();
               Comparator<AbstractClientPlayer> byDistance = Comparator.comparing(candidate -> ClientAccess.argusclientMinecraft.player.distanceTo(candidate));
               this.targetPlayer = players.sorted(byDistance).filter(this::isValidTarget).findFirst().orElse(null);
            }

            AbstractClientPlayer checkedTarget = this.targetPlayer;
            if (!this.isValidTarget(checkedTarget)) {
               this.targetPlayer = null;
            } else if (ClientAccess.argusclientMinecraft.crosshairPickEntity != this.targetPlayer) {
               if (this.instantSetting.getValue()) {
                  AbstractClientPlayer aimedTarget = this.targetPlayer;
                  AABB bounds = aimedTarget.getBoundingBox();
                  Vec3 center = bounds.getCenter();
                  float[] rotations = RotationManager.getRotationsToPosition(center);
                  float yaw = rotations[0];
                  LocalPlayer yawPlayer = ClientAccess.argusclientMinecraft.player;
                  yawPlayer.setYRot(yaw);
                  float pitch = rotations[1];
                  ClientAccess.argusclientMinecraft.player.setXRot(pitch);
               } else {
                  AbstractClientPlayer aimedTarget = this.targetPlayer;
                  double yawDifference = yawDifference(aimedTarget);
                  if (!(yawDifference > 1.0) && !(yawDifference < -1.0)) {
                     return;
                  }

                  Float speedValue = this.speedSetting.getValue();
                  double scaledDifference = yawDifference * speedValue.floatValue();
                  float yawStep = (float)(scaledDifference * 0.01F);
                  LocalPlayer yawPlayer = ClientAccess.argusclientMinecraft.player;
                  LocalPlayer viewPlayer = ClientAccess.argusclientMinecraft.player;
                  float partialTick = RenderUtils.getPartialTick();
                  float nextYaw = viewPlayer.getViewYRot(partialTick) - yawStep;
                  yawPlayer.setYRot(nextYaw);
               }
            }
         }
      }
   }

   private boolean isValidTarget(Entity entity) {
      if (!(entity instanceof Player) || entity == ClientAccess.argusclientMinecraft.player) {
         return false;
      }

      if (!entity.isAlive()) {
         return false;
      }

      if (this.ignoreInvisibleSetting.getValue() && entity.isInvisible()) {
         return false;
      }

      double fieldOfView = this.fovSetting.getValue().floatValue();
      if (!isWithinFov(entity, fieldOfView)) {
         return false;
      }

      float distance = ClientAccess.argusclientMinecraft.player.distanceTo(entity);
      Float rangeValue = this.rangeSetting.getValue();
      if (!(distance <= rangeValue)) {
         return false;
      }

      if (this.friendsSetting.getValue()) {
         SocialRegistry socialRegistry = ClientContext.socialRegistry;
         String name = ((Player)entity).getGameProfile().name();
         if (socialRegistry.isFriendName(name)) {
            return false;
         }
      }

      return this.hasLineOfSight(entity);
   }

   private boolean hasLineOfSight(Entity entity) {
      Vec3 start = new Vec3(
         ClientAccess.argusclientMinecraft.player.getX(),
         ClientAccess.argusclientMinecraft.player.getY() + ClientAccess.argusclientMinecraft.player.getEyeHeight(),
         ClientAccess.argusclientMinecraft.player.getZ()
      );
      Vec3 feet = new Vec3(entity.getX(), entity.getY(), entity.getZ());
      ClientLevel feetWorld = ClientAccess.argusclientMinecraft.level;
      ClipContext feetRay = new ClipContext(start, feet, Block.COLLIDER, Fluid.NONE, ClientAccess.argusclientMinecraft.player);
      BlockHitResult feetHit = feetWorld.clip(feetRay);
      if (feetHit.getType() == Type.MISS) {
         return true;
      }

      Vec3 eyes = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
      ClientLevel eyesWorld = ClientAccess.argusclientMinecraft.level;
      ClipContext eyesRay = new ClipContext(start, eyes, Block.COLLIDER, Fluid.NONE, ClientAccess.argusclientMinecraft.player);
      BlockHitResult eyesHit = eyesWorld.clip(eyesRay);
      return eyesHit.getType() == Type.MISS;
   }

   private static double yawDifference(Entity entity) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float partialTick = RenderUtils.getPartialTick();
      float viewYaw = player.getViewYRot(partialTick);
      double difference = (viewYaw - yawToEntity(entity)) % 360.0F;
      double wrapped = (difference + 540.0) % 360.0;
      return wrapped - 180.0;
   }

   private static boolean isWithinFov(Entity entity, double fieldOfView) {
      fieldOfView = (float)(fieldOfView * 0.5);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float partialTick = RenderUtils.getPartialTick();
      float viewYaw = player.getViewYRot(partialTick);
      double difference = (viewYaw - yawToEntity(entity)) % 360.0F;
      double wrapped = (difference + 540.0) % 360.0;
      double yawDifference = wrapped - 180.0;
      return yawDifference > 0.0 && yawDifference < fieldOfView || -fieldOfView < yawDifference && yawDifference < 0.0;
   }

   private static float yawToEntity(Entity entity) {
      double targetX = entity.getX();
      LocalPlayer xPlayer = ClientAccess.argusclientMinecraft.player;
      double deltaX = targetX - xPlayer.getX();
      double targetZ = entity.getZ();
      LocalPlayer zPlayer = ClientAccess.argusclientMinecraft.player;
      double deltaZ = targetZ - zPlayer.getZ();
      double angle = Math.atan2(deltaX, deltaZ);
      double degrees = angle * 57.2957795;
      return (float)(degrees * -1.0);
   }
}
