package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.PlayerMovementKeys;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gets you somewhere on its own for Spiral and TrailFollower: steer your heading only, walk, fly with ElytraFly's
 * Volytra Fly mode, fly an elytra with rockets (taking off, holding a cruising height, climbing over terrain and
 * boosting when slow), or let Baritone path. Watches elytra durability and rockets, and drops back to steering only
 * rather than let an elytra break mid-flight. Adds its settings to the module that owns it.
 */
final class HuntPilot {
   private final Module module;
   private final String name;
   private final Setting<Travel> travelSetting;
   private final Setting<Float> turnRateSetting;
   private final Setting<Boolean> takeoffSetting;
   private final Setting<Boolean> holdHeightSetting;
   private final Setting<Integer> cruiseSetting;
   private final Setting<Integer> netherCruiseSetting;
   private final Setting<Float> minSpeedSetting;
   private final Setting<Float> rocketDelaySetting;
   private final Setting<Boolean> avoidSetting;
   private final Setting<Integer> minDurabilitySetting;
   private final Setting<Integer> lowRocketsSetting;
   private final ElapsedTimer rocketTimer = new ElapsedTimer();
   private BlockPos lastGoal;
   private boolean enabledElytraFly;
   private ElytraFlyModule.ModeChoice previousElytraMode;
   private boolean warnedDurability;
   private boolean warnedRockets;
   private boolean outOfRockets;
   private Travel fallback;
   /** The pitch you had when it started, which level flight returns to so your view stays where you left it. */
   private float cruisePitch = -2.0F;

   HuntPilot(Module module, Travel defaultTravel, float defaultTurnRate) {
      this.module = module;
      this.name = module.getName();
      this.travelSetting = module.add(new EnumSetting<>("Travel", defaultTravel));
      this.turnRateSetting = module.add(new NumberSetting<>("TurnRate", defaultTurnRate, 0.2F, 45.0F).withUnit("°/tick"));
      this.takeoffSetting = module.add(new BooleanSetting("AutoTakeoff", true));
      this.holdHeightSetting = module.add(new BooleanSetting("HoldHeight", true));
      this.cruiseSetting = module.add(new NumberSetting<>("CruiseY", 200, 0, 320));
      this.netherCruiseSetting = module.add(new NumberSetting<>("NetherCruiseY", 120, 0, 320));
      this.minSpeedSetting = module.add(new NumberSetting<>("MinSpeed", 1.2F, 0.2F, 3.0F).withUnit(" b/t"));
      this.rocketDelaySetting = module.add(new NumberSetting<>("RocketDelay", 2.0F, 0.5F, 10.0F).withUnit("s"));
      this.avoidSetting = module.add(new BooleanSetting("ClimbOverTerrain", true));
      this.minDurabilitySetting = module.add(new NumberSetting<>("MinDurability", 25, 0, 432));
      this.lowRocketsSetting = module.add(new NumberSetting<>("LowRockets", 16, 0, 256));
      this.takeoffSetting.addVisibilityPredicate(value -> this.travelSetting.getValue().flies());
      this.minDurabilitySetting.addVisibilityPredicate(value -> this.travelSetting.getValue().flies());
      this.holdHeightSetting.addVisibilityPredicate(value -> this.travelSetting.getValue().flies());
      this.cruiseSetting.addVisibilityPredicate(value -> this.travelSetting.getValue().flies() && this.holdHeightSetting.getValue());
      this.netherCruiseSetting.addVisibilityPredicate(value -> this.travelSetting.getValue().flies() && this.holdHeightSetting.getValue());
      this.minSpeedSetting.addVisibilityPredicate(value -> this.travelSetting.getValue() == Travel.ROCKETS);
      this.rocketDelaySetting.addVisibilityPredicate(value -> this.travelSetting.getValue() == Travel.ROCKETS);
      this.avoidSetting.addVisibilityPredicate(value -> this.travelSetting.getValue() == Travel.ROCKETS);
      this.lowRocketsSetting.addVisibilityPredicate(value -> this.travelSetting.getValue() == Travel.ROCKETS);
   }

   void start() {
      this.lastGoal = null;
      this.warnedDurability = false;
      this.warnedRockets = false;
      this.outOfRockets = false;
      this.fallback = null;
      this.rocketTimer.reset();
      if (ClientAccess.argusclientMinecraft.player != null) {
         // Kept gentle, so a steep look does not fight the height hold.
         this.cruisePitch = Math.max(-8.0F, Math.min(8.0F, ClientAccess.argusclientMinecraft.player.getXRot()));
      }
   }

   void stop() {
      PlayerMovementKeys.holdForward(this, false);
      PlayerMovementKeys.holdVertical(this, 0);
      if (this.lastGoal != null && ClientContext.baritoneBridge != null && ClientContext.baritoneBridge.isAvailable()) {
         ClientContext.baritoneBridge.cancelPathing();
      }

      this.releaseElytraFly();
      this.lastGoal = null;
   }

   Travel travel() {
      return this.fallback != null ? this.fallback : this.travelSetting.getValue();
   }

   /**
    * One tick of travel towards a heading; {@code goal} is where Baritone should path to, a point some way along it.
    */
   void tick(LocalPlayer player, float wantedYaw, Vec3 goal) {
      if (this.travelSetting.getValue().flies()) {
         this.checkElytra(player);
      }

      Travel travel = this.travel();
      if (travel != Travel.VOLYTRA) {
         this.releaseElytraFly();
         PlayerMovementKeys.holdVertical(this, 0);
      }

      PlayerMovementKeys.holdForward(this, travel == Travel.WALK || travel == Travel.VOLYTRA);
      if (travel == Travel.BARITONE && this.steerWithBaritone(goal)) {
         return;
      }

      player.setYRot(TrailPlanner.approach(TrailPlanner.wrap(player.getYRot()), TrailPlanner.wrap(wantedYaw), this.turnRateSetting.getValue()));
      switch (travel) {
         case WALK -> walk(player);
         case VOLYTRA -> this.flyVolytra(player);
         case ROCKETS -> this.flyRockets(player);
         default -> {
         }
      }
   }

   private static void walk(LocalPlayer player) {
      ClientContext.inputOverrides.requestForwardMovement(1.0F);
      ClientContext.inputOverrides.requestForward(true);
      ClientContext.inputOverrides.requestSprint(true);
      if (player.horizontalCollision && player.onGround()) {
         ClientContext.inputOverrides.requestJump(true);
      }
   }

   /** Jumps, then opens the elytra once falling. */
   private void takeoff(LocalPlayer player) {
      if (!this.takeoffSetting.getValue() || player.isPassenger()) {
         return;
      }

      if (player.onGround()) {
         ClientContext.inputOverrides.requestJump(true);
      } else if (player.getDeltaMovement().y < 0.0 && !PlayerStateUtils.isFallFlying()) {
         PacketSender.sendStartFallFlying();
         player.startFallFlying();
         this.rocketTimer.reset();
         if (this.travel() == Travel.ROCKETS) {
            this.fireRocket(player);
         }
      }
   }

   private void flyVolytra(LocalPlayer player) {
      ElytraFlyModule elytraFly = ClientContext.getModule(ElytraFlyModule.class);
      if (elytraFly == null) {
         this.say("ElytraFly isn't available; flying with rockets instead.");
         this.fallback = Travel.ROCKETS;
         return;
      }

      if (elytraFly.modeSetting.getValue() != ElytraFlyModule.ModeChoice.VOLYTRA) {
         if (this.previousElytraMode == null) {
            this.previousElytraMode = elytraFly.modeSetting.getValue();
         }

         elytraFly.modeSetting.setValue(ElytraFlyModule.ModeChoice.VOLYTRA);
      }

      if (!elytraFly.isToggled()) {
         elytraFly.enable();
         this.enabledElytraFly = true;
      }

      if (!PlayerStateUtils.isFallFlying()) {
         PlayerMovementKeys.holdVertical(this, 0);
         this.takeoff(player);
         return;
      }

      // Volytra Fly climbs on jump and descends on sneak, so hold whichever brings you back to the cruise height.
      int direction = 0;
      if (this.holdHeightSetting.getValue()) {
         double below = this.cruiseHeight() - player.getY();
         if (below > 2.0 || terrainAhead(player)) {
            direction = 1;
         } else if (below < -2.0) {
            direction = -1;
         }
      }

      PlayerMovementKeys.holdVertical(this, direction);
   }

   private int cruiseHeight() {
      return WorldUtils.getDimension() == DimensionType.THE_NETHER ? this.netherCruiseSetting.getValue() : this.cruiseSetting.getValue();
   }

   private void releaseElytraFly() {
      if (!this.enabledElytraFly && this.previousElytraMode == null) {
         return;
      }

      ElytraFlyModule elytraFly = ClientContext.getModule(ElytraFlyModule.class);
      if (elytraFly != null) {
         if (this.enabledElytraFly && elytraFly.isToggled()) {
            elytraFly.disable();
         }

         if (this.previousElytraMode != null) {
            elytraFly.modeSetting.setValue(this.previousElytraMode);
         }
      }

      this.enabledElytraFly = false;
      this.previousElytraMode = null;
   }

   private void flyRockets(LocalPlayer player) {
      if (!PlayerStateUtils.isFallFlying()) {
         this.takeoff(player);
         return;
      }

      // Without HoldHeight, rockets just keep you where you are now.
      double below = this.holdHeightSetting.getValue() ? this.cruiseHeight() - player.getY() : 0.0;
      boolean blocked = this.avoidSetting.getValue() && terrainAhead(player);
      float pitch;
      if (blocked) {
         pitch = -45.0F;
      } else if (below > 6.0) {
         pitch = (float)-Math.min(40.0, 12.0 + below * 0.6);
      } else if (below < -12.0) {
         pitch = (float)Math.min(25.0, 5.0 - below * 0.3);
      } else {
         pitch = this.cruisePitch;
      }

      player.setXRot(TrailPlanner.approach(player.getXRot(), pitch, 4.0F));
      Vec3 motion = player.getDeltaMovement();
      double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
      boolean needBoost = blocked || below > 6.0 || speed < this.minSpeedSetting.getValue();
      if (needBoost && this.rocketTimer.hasElapsed((long)(this.rocketDelaySetting.getValue() * 1000.0F))) {
         this.fireRocket(player);
      }
   }

   /** Whether something solid lies just ahead along your heading, close enough that you need to climb now. */
   private static boolean terrainAhead(LocalPlayer player) {
      Vec3 eye = player.getEyePosition();
      double radians = Math.toRadians(player.getYRot());
      Vec3 ahead = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
      double distance = Math.max(16.0, player.getDeltaMovement().length() * 25.0);
      for (double drop : new double[]{0.0, -3.0}) {
         Vec3 from = eye.add(0.0, drop, 0.0);
         HitResult hit = player.level().clip(new ClipContext(from, from.add(ahead.scale(distance)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
         if (hit.getType() != HitResult.Type.MISS) {
            return true;
         }
      }

      return false;
   }

   private void fireRocket(LocalPlayer player) {
      if (player.isUsingItem()) {
         return;
      }

      int rockets = countRockets(player);
      if (rockets == 0) {
         if (!this.outOfRockets) {
            this.outOfRockets = true;
            this.say("Out of rockets; gliding on the same heading instead.");
         }

         return;
      }

      this.outOfRockets = false;
      if (rockets <= this.lowRocketsSetting.getValue() && !this.warnedRockets) {
         this.warnedRockets = true;
         this.say("Only " + rockets + " rockets left.");
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true);
      if (!rocketSlot.isFound()) {
         return;
      }

      InventoryUtils.swapTo(rocketSlot);
      try {
         ClientAccess.argusclientMinecraft.gameMode.useItem(player, rocketSlot.hand());
         ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
      } finally {
         InventoryUtils.swapBack(selectedSlot, rocketSlot);
      }

      this.rocketTimer.reset();
   }

   private static int countRockets(LocalPlayer player) {
      int count = 0;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (stack.is(Items.FIREWORK_ROCKET)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   /** Warns about a worn-out or missing elytra and drops back to steering only, so it does not break mid-flight. */
   private void checkElytra(LocalPlayer player) {
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      if (!chest.is(Items.ELYTRA)) {
         if (!PlayerStateUtils.isFallFlying() && this.fallback != Travel.STEER) {
            this.say("Put on an elytra to fly; steering only until then.");
            this.fallback = Travel.STEER;
         }

         return;
      }

      int left = chest.getMaxDamage() - chest.getDamageValue();
      if (left <= this.minDurabilitySetting.getValue()) {
         if (!this.warnedDurability) {
            this.warnedDurability = true;
            this.say("Elytra durability is down to " + left + "; stopped flying. Land and swap or mend it.");
         }

         this.fallback = Travel.STEER;
      } else if (this.fallback == Travel.STEER) {
         this.fallback = null;
         this.warnedDurability = false;
      }
   }

   private boolean steerWithBaritone(Vec3 goal) {
      BaritoneBridge baritone = ClientContext.baritoneBridge;
      if (baritone == null || !baritone.isAvailable()) {
         this.say("Baritone isn't available, so steering your heading instead.");
         this.fallback = Travel.STEER;
         return false;
      }

      BlockPos target = BlockPos.containing(goal.x, ClientAccess.argusclientMinecraft.player.getY(), goal.z);
      if (!target.equals(this.lastGoal) || !baritone.isPathingOrCalculating()) {
         this.lastGoal = target;
         if (WorldUtils.getDimension() == DimensionType.THE_NETHER) {
            baritone.pathElytraTo(target);
         } else {
            baritone.pathTo(target);
         }
      }

      return true;
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal(this.name + ": " + message), ChatUtils.moduleSignature(this.module));
   }

   public enum Travel implements NamedEntry {
      STEER("Steer only"),
      WALK("Walk"),
      VOLYTRA("Volytra Fly"),
      ROCKETS("Elytra + rockets"),
      BARITONE("Baritone");

      private final String displayName;

      Travel(String displayName) {
         this.displayName = displayName;
      }

      boolean flies() {
         return this == VOLYTRA || this == ROCKETS;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
