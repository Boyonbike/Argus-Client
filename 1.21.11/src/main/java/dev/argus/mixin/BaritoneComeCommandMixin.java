package dev.argus.mixin;

import baritone.api.pathing.goals.GoalBlock;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "baritone/command/defaults/ComeCommand", remap = false)
public class BaritoneComeCommandMixin {
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);

   @ModifyArgs(
      method = "execute",
      at = @At(value = "INVOKE", target = "Lbaritone/api/process/ICustomGoalProcess;setGoalAndPath(Lbaritone/api/pathing/goals/Goal;)V"),
      remap = false
   )
   private void useFreecamGoal(Args args) {
      if (freecam.isToggled()) {
         BlockPos cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().blockPosition();
         if (WorldUtils.isAir(cameraPosition) && WorldUtils.isAir(cameraPosition.below()) && !WorldUtils.isAir(cameraPosition.below(2))) {
            args.set(0, new GoalBlock(cameraPosition.below()));
         } else {
            args.set(0, new GoalBlock(cameraPosition));
         }
      }
   }
}
