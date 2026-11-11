package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.access.LocalPlayerTickAccess;
import dev.argus.mixin.LivingEntityAccessor;
import net.minecraft.world.phys.Vec3;

public final class PlayerTickExecutor implements ClientAccess {
   private static boolean runningExtraTick;

   private PlayerTickExecutor() {
   }

   public static boolean isRunningExtraTick() {
      return runningExtraTick;
   }

   public static void runExtraTick() {
      runningExtraTick = true;
      int useItemRemainingTicks = ClientAccess.argusclientMinecraft.player.getUseItemRemainingTicks();
      float moveDistance = ClientAccess.argusclientMinecraft.player.moveDist;
      float walkAnimationSpeed = ClientAccess.argusclientMinecraft.player.walkAnimation.speed();
      float walkDistance = ClientAccess.argusclientMinecraft.player.avatarState().walkDist;
      float previousWalkDistance = ClientAccess.argusclientMinecraft.player.avatarState().walkDistO;
      float verticalBob = ClientAccess.argusclientMinecraft.player.yBob;
      float previousVerticalBob = ClientAccess.argusclientMinecraft.player.yBobO;
      float horizontalBob = ClientAccess.argusclientMinecraft.player.xBob;
      float previousHorizontalBob = ClientAccess.argusclientMinecraft.player.xBobO;
      Vec3 previousPosition = new Vec3(
         ClientAccess.argusclientMinecraft.player.xo, ClientAccess.argusclientMinecraft.player.yo, ClientAccess.argusclientMinecraft.player.zo
      );
      int attackStrengthTicks = ClientAccess.argusclientMinecraft.player.attackStrengthTicker;
      int swingTime = ClientAccess.argusclientMinecraft.player.swingState.ticks;
      float previousAttackAnimation = ClientAccess.argusclientMinecraft.player.swingState.oldAnimation;
      float attackAnimation = ClientAccess.argusclientMinecraft.player.swingState.animation;
      LocalPlayerTickAccess baseTickPlayer = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
      baseTickPlayer.superTick();
      LocalPlayerTickAccess eventResetPlayer = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
      eventResetPlayer.resetEvent();
      ClientAccess.argusclientMinecraft.player.attackStrengthTicker = attackStrengthTicks;
      ((LivingEntityAccessor)ClientAccess.argusclientMinecraft.player).setItemUseTimeLeft(useItemRemainingTicks);
      ClientAccess.argusclientMinecraft.player.moveDist = moveDistance;
      ClientAccess.argusclientMinecraft.player.walkAnimation.setSpeed(walkAnimationSpeed);
      ClientAccess.argusclientMinecraft.player.avatarState().walkDist = walkDistance;
      ClientAccess.argusclientMinecraft.player.avatarState().walkDistO = previousWalkDistance;
      ClientAccess.argusclientMinecraft.player.yBob = verticalBob;
      ClientAccess.argusclientMinecraft.player.yBobO = previousVerticalBob;
      ClientAccess.argusclientMinecraft.player.xBob = horizontalBob;
      ClientAccess.argusclientMinecraft.player.xBobO = previousHorizontalBob;
      ClientAccess.argusclientMinecraft.player.swingState.ticks = swingTime;
      ClientAccess.argusclientMinecraft.player.swingState.oldAnimation = previousAttackAnimation;
      ClientAccess.argusclientMinecraft.player.swingState.animation = attackAnimation;
      LocalPlayerTickAccess movementPacketPlayer = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
      movementPacketPlayer.sendMovementPacketsWrapper();
      LocalPlayerTickAccess rotationResetPlayer = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
      rotationResetPlayer.resetRotations();
      ClientAccess.argusclientMinecraft.player.xo = previousPosition.x;
      ClientAccess.argusclientMinecraft.player.yo = previousPosition.y;
      ClientAccess.argusclientMinecraft.player.zo = previousPosition.z;
      runningExtraTick = false;
   }
}
