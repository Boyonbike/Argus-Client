package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.modules.render.XrayModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public class BlockMixin {
   private static NoSlowModule noslow = ClientContext.getModule(NoSlowModule.class);

   /** Called for every block face while meshing chunks; a return-value modifier avoids a per-call allocation. */
   @ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"))
   private static boolean overrideXrayFaceVisibility(boolean original, BlockState blockState, BlockState adjacentState, Direction face) {
      XrayModule xray = XrayModule.getInstance();
      return xray.isToggled() && xray.shouldHideUnselectedBlocks() ? xray.shouldRenderBlock(BlockPos.ZERO, blockState.getBlock()) : original;
   }

   @ModifyReturnValue(method = "getFriction", at = @At("RETURN"))
   private float overrideNoSlowFriction(float original) {
      if (noslow.isToggled()) {
         Block block = (Block)(Object)this;
         if (noslow.iceSetting.getValue() && (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE)) {
            return 0.6F;
         }

         if (noslow.slimeSetting.getValue() && block == Blocks.SLIME_BLOCK) {
            return 0.6F;
         }

         if (noslow.honeySetting.getValue() && block == Blocks.HONEY_BLOCK) {
            return 0.6F;
         }
      }

      return original;
   }

   @Unique
   private static void argusclient$bindBlockModules(ModuleRegistry moduleRegistry) {
      noslow = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
