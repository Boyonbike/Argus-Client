package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.ClientLevelRemoveEntityEvent;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);

   @Inject(method = "removeEntity", at = @At(value = "JUMP", shift = Shift.BEFORE, ordinal = 0), cancellable = true)
   private void postRemoveEntityEvent(int entityId, RemovalReason reason, CallbackInfo callback, @Local Entity entity) {
      ClientLevelRemoveEntityEvent event = new ClientLevelRemoveEntityEvent(entityId, entity != null);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
   private void postAddEntityEvent(Entity entity, CallbackInfo callback) {
      ClientLevelAddEntityEvent event = new ClientLevelAddEntityEvent(entity);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
   private void suppressBreakingBlockEffect(BlockPos blockPos, Direction face, CallbackInfo callback) {
      if (noRenderModule.shouldHideBlockBreakingParticles()) {
         callback.cancel();
      }
   }

   @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
   private void suppressDestroyBlockEffect(BlockPos blockPos, BlockState blockState, CallbackInfo callback) {
      if (noRenderModule.shouldHideBlockBreakingParticles()) {
         callback.cancel();
      }
   }

   /** Lets SpeedMine see the server's block state (e.g. air confirming a break) before the client world applies it. */
   @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
   private void notifySpeedMineOfServerBlock(BlockPos blockPos, BlockState blockState, int flags, CallbackInfo callback) {
      if (speedMineModule != null) {
         speedMineModule.onServerBlockUpdate(blockPos, blockState);
      }
   }
}
