package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class NoInteractModule extends Module {
   public final Setting<NoInteractModule.TargetsChoice> targetsSetting;
   public final Setting<NoInteractModule.ModeChoice> modeSetting;
   public final Setting<Boolean> onlyFoodSetting;
   public boolean shouldStopSneaking;

   public static InteractionHand selectUsableHand() {
      ItemStack mainHandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      ItemStack offhandStack = ClientAccess.argusclientMinecraft.player.getOffhandItem();
      return !isFoodOrProjectile(mainHandStack) && isFoodOrProjectile(offhandStack) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
   }

   private boolean isSpawnPointTarget(BlockState state) {
      if (ClientContext.worldStateManager.getDimension().isOverworld()) {
         return state.getBlock() instanceof BedBlock;
      } else {
         return ClientContext.worldStateManager.getDimension().isNether() ? state.getBlock() instanceof RespawnAnchorBlock : false;
      }
   }

   public boolean shouldBlockInteraction(ItemStack stack, BlockPos position) {
      if (!this.isToggled()) {
         return false;
      }

      if (this.onlyFoodSetting.getValue()) {
         DataComponentType<?> foodComponent = DataComponents.FOOD;
         if (!stack.has(foodComponent)) {
            return false;
         }
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      if (this.targetsSetting.getValue() == NoInteractModule.TargetsChoice.SPAWN_POINT) {
         return this.isSpawnPointTarget(state);
      } else {
         return this.targetsSetting.getValue() == NoInteractModule.TargetsChoice.BLOCKS
            ? this.isBlockInteractionTarget(state)
            : this.isBlockInteractionTarget(state) || this.isSpawnPointTarget(state);
      }
   }

   public static boolean isFoodOrProjectile(ItemStack stack) {
      DataComponentType<?> foodComponent = DataComponents.FOOD;
      return stack.has(foodComponent)
         ? true
         : stack.getItem() instanceof EnderpearlItem || stack.getItem() instanceof ProjectileItem || stack.getItem() instanceof ProjectileWeaponItem;
   }

   private boolean isBlockInteractionTarget(BlockState state) {
      return state.getBlock() instanceof AnvilBlock ? true : state.hasBlockEntity() && !(state.getBlock() instanceof BedBlock);
   }

   public NoInteractModule() {
      super("NoInteract", "Prevents you from interacting with tile entities.", ModuleCategory.PLAYER);
      String targetsName = "Targets";
      NoInteractModule.TargetsChoice defaultTargets = NoInteractModule.TargetsChoice.BLOCKS;
      EnumSetting<NoInteractModule.TargetsChoice> targetsOption = new EnumSetting<>(targetsName, defaultTargets);
      this.targetsSetting = this.add(targetsOption);
      String modeName = "Mode";
      NoInteractModule.ModeChoice defaultMode = NoInteractModule.ModeChoice.CANCEL;
      EnumSetting<NoInteractModule.ModeChoice> modeOption = new EnumSetting<>(modeName, defaultMode);
      this.modeSetting = this.add(modeOption);
      String onlyFoodName = "OnlyFood";
      Boolean defaultOnlyFood = false;
      BooleanSetting onlyFoodOption = new BooleanSetting(onlyFoodName, defaultOnlyFood);
      this.onlyFoodSetting = this.add(onlyFoodOption);
   }

   public enum ModeChoice implements NamedEntry {
      CANCEL("Cancel"),
      SHIFT("Shift");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum TargetsChoice implements NamedEntry {
      BLOCKS("Blocks"),
      SPAWN_POINT("SpawnPoint"),
      BOTH("Both");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TargetsChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
