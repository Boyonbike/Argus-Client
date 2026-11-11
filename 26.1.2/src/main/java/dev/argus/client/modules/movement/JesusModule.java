package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.BlockCollisionShapeEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class JesusModule extends Module {
   /** Shared; building a new VoxelShape for every water collision query was wasted allocation. */
   private static final VoxelShape WATER_SURFACE_SHAPE = Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 0.98, 1.0));
   final Setting<JesusModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", JesusModule.ModeChoice.SOLID));
   private final Setting<Boolean> strictSetting;
   private final Setting<Float> ascendingSetting;
   private final double[] strictYOffsetSequence;
   private int strictOffsetIndex;

   @Override
   public String getInfo() {
      return this.modeSetting.getValue().getName();
   }

   @Override
   public void onToggle() {
      this.strictOffsetIndex = 0;
   }

   private boolean isStableAboveWater() {
      if (Math.abs(ClientAccess.argusclientMinecraft.player.yo - ClientAccess.argusclientMinecraft.player.getY()) > 0.01) {
         return false;
      }

      for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.argusclientMinecraft.player)) {
         if (WorldUtils.getBlock(position.below()) != Blocks.WATER) {
            return false;
         }
      }

      return true;
   }

   public boolean canWalkOnWater() {
      if (this.isWorldUnavailable() || ClientAccess.argusclientMinecraft.player.input == null) {
         return false;
      } else {
         return ClientAccess.argusclientMinecraft.player.isPassenger() ? false : !ClientAccess.argusclientMinecraft.player.input.keyPresses.shift();
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (this.canWalkOnWater() && ClientAccess.argusclientMinecraft.player.isInWater()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double upwardImpulse = this.modeSetting.getValue() == JesusModule.ModeChoice.DOLPHIN ? 0.0399 : 0.08 * this.ascendingSetting.getValue().floatValue();
         player.push(0.0, upwardImpulse, 0.0);
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (this.modeSetting.getValue() == JesusModule.ModeChoice.SOLID && this.canWalkOnWater()) {
         if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket) {
            if (this.strictSetting.getValue() && this.isStableAboveWater()) {
               ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor)movementPacket;
               packetAccessor.setOnGround(false);
               packetAccessor.setY(movementPacket.getY(0.0) + this.strictYOffsetSequence[this.strictOffsetIndex++]);
               this.strictOffsetIndex = this.strictOffsetIndex % this.strictYOffsetSequence.length;
            }
         }
      }
   }

   @EventHandler
   private void onCollisionShape(BlockCollisionShapeEvent event) {
      if (!this.isWorldUnavailable()) {
         if (!ClientAccess.argusclientMinecraft.player.isInWater()) {
            if (this.modeSetting.getValue() == JesusModule.ModeChoice.SOLID && this.canWalkOnWater()) {
               float blockYAsFloat = event.getPos().getY();
               double liquidSurfaceY = blockYAsFloat + 0.2F;
               if (!(liquidSurfaceY > ClientAccess.argusclientMinecraft.player.getY())) {
                  if (event.getState().is(Blocks.WATER)) {
                     event.setShape(WATER_SURFACE_SHAPE);
                  }
               }
            }
         }
      }
   }

   public JesusModule() {
      super("Jesus", "Allows you to walk on water.", ModuleCategory.MOVEMENT);
      Predicate<Object> showStrict = ignored -> this.modeSetting.getValue() == JesusModule.ModeChoice.SOLID;
      this.strictSetting = this.add(new BooleanSetting("Strict", false, showStrict));
      Predicate<Object> showAscending = ignored -> this.modeSetting.getValue() == JesusModule.ModeChoice.SOLID;
      this.ascendingSetting = this.add(new NumberSetting<>("Ascending", 1.0F, 0.1F, 1.0F, showAscending));
      this.strictYOffsetSequence = new double[]{
         0.08232659236482401, 0.13927999979019162, 0.1654239999427795, 0.11427136035293574, 0.04194693730418576, 0.01236341326161235
      };
   }

   public enum ModeChoice implements NamedEntry {
      SOLID("Solid"),
      DOLPHIN("Dolphin");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
