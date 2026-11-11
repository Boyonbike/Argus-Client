package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.combat.AutoBowReleaseModule;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class ProjectileTrajectorySimulator implements ClientAccess {
   private static TrajectoriesModule module = ClientContext.getModule(TrajectoriesModule.class);
   private static AutoBowReleaseModule autoBowModule = ClientContext.getModule(AutoBowReleaseModule.class);
   private static final HitResult SIMULATION_LIMIT = new HitResult(Vec3.ZERO) {
      @Override
      public Type getType() {
         return Type.MISS;
      }
   };
   private final MutableBlockPos fluidPosition = new MutableBlockPos();
   private Vec3 segmentEnd = Vec3.ZERO;
   private Vec3 segmentStart = Vec3.ZERO;
   private Entity sourceEntity;
   private Entity ignoredCollisionOwner;
   public final Vector3d position = new Vector3d();
   private final Vector3d velocity = new Vector3d();
   private double gravity;
   private double airDrag;
   private double waterDrag;

   public boolean isEnabledProjectileItem(Item item) {
      if (item instanceof BowItem) {
         return module.bowSetting.getValue();
      } else if (item instanceof CrossbowItem) {
         return module.crossbowSetting.getValue();
      } else if (item instanceof TridentItem) {
         return module.tridentSetting.getValue();
      } else if (item instanceof EnderpearlItem) {
         return module.pearlsSetting.getValue();
      } else {
         return item instanceof ExperienceBottleItem
            ? module.expSetting.getValue()
            : (item instanceof FishingRodItem || item instanceof ThrowablePotionItem || item instanceof SnowballItem || item instanceof EggItem)
               && module.othersSetting.getValue();
      }
   }

   public boolean initializeFromProjectile(Entity entity, double partialTick) {
      if (!(entity instanceof Projectile projectile)) {
         return false;
      } else {
         Vec3 movement = entity.getDeltaMovement();
         if (!(movement.lengthSqr() < 1.0E-10) && finite(movement)) {
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            if (level != null && level.noCollision(entity, entity.getBoundingBox().deflate(1.0E-4))) {
               Vec3 start = entity.getPosition((float)partialTick);
               this.position.set(start.x, start.y, start.z);
               this.velocity.set(movement.x, movement.y, movement.z);
               this.sourceEntity = entity;
               this.ignoredCollisionOwner = projectile.getOwner();
               this.airDrag = 0.99;
               if (entity instanceof ThrownTrident) {
                  this.gravity = 0.05;
                  this.waterDrag = 0.99;
               } else if (entity instanceof AbstractArrow) {
                  this.gravity = 0.05;
                  this.waterDrag = 0.6;
               } else if (entity instanceof ThrownExperienceBottle) {
                  this.gravity = 0.07;
                  this.waterDrag = 0.8;
               } else if (entity instanceof ThrownEnderpearl || entity instanceof Snowball || entity instanceof ThrownEgg) {
                  this.gravity = 0.03;
                  this.waterDrag = 0.8;
               } else if (entity instanceof FireworkRocketEntity) {
                  this.gravity = 0.0;
                  this.airDrag = 1.0;
                  this.waterDrag = 1.0;
               } else if (entity instanceof FishingHook) {
                  this.gravity = 0.03;
                  this.airDrag = 0.92;
                  this.waterDrag = 0.0;
               } else if (entity instanceof ThrowableProjectile) {
                  this.gravity = 0.05;
                  this.waterDrag = 0.8;
               } else {
                  if (!(entity instanceof AbstractHurtingProjectile)) {
                     return false;
                  }

                  this.gravity = 0.0;
                  this.airDrag = 0.95;
                  this.waterDrag = 0.8;
               }

               if (entity.isNoGravity()) {
                  this.gravity = 0.0;
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static boolean finite(Vec3 vector) {
      return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
   }

   public void initializeLaunch(
      Entity entity,
      double pitchOffset,
      double speed,
      double spreadAngle,
      double gravityPerTick,
      double dragInWater,
      boolean addEntityVelocity,
      double partialTick
   ) {
      this.sourceEntity = entity;
      this.ignoredCollisionOwner = entity;
      this.position
         .set(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight(entity.getPose()),
            Mth.lerp(partialTick, entity.zo, entity.getZ())
         );
      double yaw = entity.getViewYRot((float)partialTick);
      double pitch = entity.getViewXRot((float)partialTick);
      double directionX;
      double directionY;
      double directionZ;
      if (spreadAngle == 0.0) {
         double yawRadians = yaw * (float) (Math.PI / 180.0);
         double pitchRadians = pitch * (float) (Math.PI / 180.0);
         directionX = -Math.sin(yawRadians) * Math.cos(pitchRadians);
         directionY = -Math.sin((pitch + pitchOffset) * (float) (Math.PI / 180.0));
         directionZ = Math.cos(yawRadians) * Math.cos(pitchRadians);
      } else {
         Vec3 upVector = entity.getUpVector(1.0F);
         Quaterniond spreadRotation = new Quaterniond().setAngleAxis(spreadAngle, upVector.x, upVector.y, upVector.z);
         Vec3 viewVector = entity.getViewVector(1.0F);
         Vector3d rotatedDirection = new Vector3d(viewVector.x, viewVector.y, viewVector.z);
         rotatedDirection.rotate(spreadRotation);
         directionX = rotatedDirection.x;
         directionY = rotatedDirection.y;
         directionZ = rotatedDirection.z;
      }

      this.velocity.set(directionX, directionY, directionZ).normalize().mul(speed);
      if (addEntityVelocity) {
         Vec3 movement = entity.getDeltaMovement();
         if (entity == ClientAccess.argusclientMinecraft.player) {
            Vec3 previous = module.getPreviousPlayerVelocity();
            movement = new Vec3(
               Mth.lerp(partialTick, previous.x, movement.x), Mth.lerp(partialTick, previous.y, movement.y), Mth.lerp(partialTick, previous.z, movement.z)
            );
         }

         this.velocity.add(movement.x, entity.onGround() ? 0.0 : movement.y, movement.z);
      }

      this.gravity = gravityPerTick;
      this.airDrag = 0.99;
      this.waterDrag = dragInWater;
   }

   public boolean initializeFromItem(Entity entity, ItemStack stack, double spreadAngle, boolean addEntityVelocity, double partialTick) {
      Item item = stack.getItem();
      if (item instanceof TridentItem) {
         ResourceKey riptideKey = Enchantments.RIPTIDE;
         if (EnchantmentUtils.hasEnchantment(riptideKey, stack)) {
            return false;
         }
      }

      if (item instanceof BowItem) {
         Boolean configuredBow = module.bowSetting.getValue();
         if (configuredBow) {
            if (entity instanceof LivingEntity shooter) {
               double var27 = BowItem.getPowerForTime(shooter.getTicksUsingItem());
               double previousPower = entity == ClientAccess.argusclientMinecraft.player ? module.getPreviousBowPower() : var27;
               double bowPower = Mth.lerp(partialTick, previousPower, var27);
               if (bowPower <= 0.0) {
                  bowPower = autoBowModule.isToggled() ? BowItem.getPowerForTime(autoBowModule.getReleaseTicks()) : 1.0;
               }

               double bowSpeed = bowPower * 3.0;
               this.initializeLaunch(entity, 0.0, bowSpeed, spreadAngle, 0.05F, 0.6, addEntityVelocity, partialTick);
               return true;
            }

            return false;
         }
      }

      if (item instanceof CrossbowItem) {
         Boolean configuredCrossbow = module.crossbowSetting.getValue();
         if (configuredCrossbow) {
            if (!CrossbowItem.isCharged(stack)) {
               return false;
            }

            ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (chargedProjectiles == null) {
               return false;
            }

            Item fireworkItem = Items.FIREWORK_ROCKET;
            if (chargedProjectiles.contains(fireworkItem)) {
               double fireworkSpeed = CrossbowItem.getShootingPower(chargedProjectiles);
               this.initializeLaunch(entity, 0.0, fireworkSpeed, spreadAngle, 0.0, 0.6, addEntityVelocity, partialTick);
            } else {
               double crossbowSpeed = CrossbowItem.getShootingPower(chargedProjectiles);
               this.initializeLaunch(entity, 0.0, crossbowSpeed, spreadAngle, 0.05F, 0.6, addEntityVelocity, partialTick);
            }

            return true;
         }
      }

      if (item instanceof FishingRodItem) {
         Boolean configuredOthers = module.othersSetting.getValue();
         if (configuredOthers) {
            this.initializeFishingRod(entity, partialTick);
            return true;
         }
      }

      if (item instanceof TridentItem) {
         Boolean configuredTrident = module.tridentSetting.getValue();
         if (configuredTrident) {
            this.initializeLaunch(entity, 0.0, 2.5, spreadAngle, 0.05F, 0.99, addEntityVelocity, partialTick);
            return true;
         }
      }

      if ((!(item instanceof SnowballItem) && !(item instanceof EggItem) || !module.othersSetting.getValue())
         && (!(item instanceof EnderpearlItem) || !module.pearlsSetting.getValue())) {
         if (item instanceof ExperienceBottleItem) {
            Boolean configuredExp = module.expSetting.getValue();
            if (configuredExp) {
               this.initializeLaunch(entity, -20.0, 0.7, spreadAngle, 0.07, 0.8, addEntityVelocity, partialTick);
               return true;
            }
         }

         if (!(item instanceof ThrowablePotionItem)) {
            return false;
         }

         Boolean configuredOthers3 = module.othersSetting.getValue();
         if (!configuredOthers3) {
            return false;
         }

         double potionSpeed = 0.5;
         this.initializeLaunch(entity, -20.0, potionSpeed, spreadAngle, 0.05, 0.8, addEntityVelocity, partialTick);
         return true;
      } else {
         this.initializeLaunch(entity, 0.0, 1.5, spreadAngle, 0.03, 0.8, addEntityVelocity, partialTick);
         return true;
      }
   }

   public void initializeFishingRod(Entity entity, double partialTick) {
      this.sourceEntity = entity;
      this.ignoredCollisionOwner = entity;
      double yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
      double pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
      double yawRadians = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
      double yawCosine = Math.cos(yawRadians);
      double yawSine = Math.sin(yawRadians);
      double pitchRadians = -pitch * (float) (Math.PI / 180.0);
      double pitchCosine = -Math.cos(pitchRadians);
      double pitchSine = Math.sin(pitchRadians);
      this.position
         .set(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight(entity.getPose()),
            Mth.lerp(partialTick, entity.zo, entity.getZ())
         );
      this.position.sub(yawSine * 0.3, 0.0, yawCosine * 0.3);
      this.velocity.set(-yawSine, Mth.clamp(-(pitchSine / pitchCosine), -5.0, 5.0), -yawCosine);
      double velocityScale = 0.6 / this.velocity.length() + 0.5;
      this.velocity.mul(velocityScale);
      this.gravity = 0.03;
      this.airDrag = 0.92;
      this.waterDrag = 0.0;
   }

   public HitResult step() {
      this.segmentStart = new Vec3(this.position.x, this.position.y, this.position.z);
      this.position.add(this.velocity);
      if (Double.isFinite(this.position.x) && Double.isFinite(this.position.y) && Double.isFinite(this.position.z)) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         if (level != null && !(this.position.y < level.getMinY())) {
            int chunkX = Mth.floor(this.position.x) >> 4;
            int chunkZ = Mth.floor(this.position.z) >> 4;
            if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
               return SIMULATION_LIMIT;
            }

            this.segmentEnd = new Vec3(this.position.x, this.position.y, this.position.z);
            HitResult hit = this.findCollision();
            this.velocity.mul(this.isInWater() ? this.waterDrag : this.airDrag);
            this.velocity.y = this.velocity.y - this.gravity;
            return hit.getType() == Type.MISS ? null : hit;
         } else {
            return SIMULATION_LIMIT;
         }
      } else {
         return SIMULATION_LIMIT;
      }
   }

   private HitResult findCollision() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockHitResult blockHit = level.clip(
         new ClipContext(this.segmentStart, this.segmentEnd, Block.COLLIDER, this.waterDrag == 0.0 ? Fluid.ANY : Fluid.NONE, this.sourceEntity)
      );
      Vec3 end = blockHit.getType() == Type.MISS ? this.segmentEnd : blockHit.getLocation();
      AABB searchBounds = new AABB(this.segmentStart, end).inflate(1.0);
      Predicate<Entity> targets = entity -> entity != this.sourceEntity
         && entity != this.ignoredCollisionOwner
         && !entity.isSpectator()
         && entity.isAlive()
         && entity.isPickable();
      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this.sourceEntity, this.segmentStart, end, searchBounds, targets, 0.3F);
      return entityHit == null ? blockHit : entityHit;
   }

   private boolean isInWater() {
      this.fluidPosition.set(this.position.x, this.position.y, this.position.z);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      FluidState fluid = level.getFluidState(this.fluidPosition);
      if (!fluid.is(FluidTags.WATER)) {
         return false;
      }

      double fractionalY = this.position.y - Math.floor(this.position.y);
      return fractionalY <= fluid.getHeight(level, this.fluidPosition);
   }

   public static void bindModules(ModuleRegistry registry) {
      module = (TrajectoriesModule)registry.getModule(TrajectoriesModule.class);
      autoBowModule = (AutoBowReleaseModule)registry.getModule(AutoBowReleaseModule.class);
   }
}
