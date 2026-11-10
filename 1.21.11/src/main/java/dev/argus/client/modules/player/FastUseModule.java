package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.BlockStateChangeEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FastUseModule extends Module {
   public enum Mode implements NamedEntry {
      ALL("All"),
      SOME("Some");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   public final Setting<Mode> modeSetting = this.add(new EnumSetting<>("Mode", Mode.ALL));
   public final Setting<Set<Item>> itemsSetting = this.add(new RegistrySetSetting<Item>("Items", BuiltInRegistries.ITEM));
   public final Setting<Boolean> blocksSetting = this.add(new BooleanSetting("Blocks", false));
   public final Setting<Integer> cooldownSetting = this.add(new NumberSetting<>("Cooldown", 0, 0, 4));
   public final Setting<Boolean> preventContainerOpenSetting = this.add(new BooleanSetting("PreventContainerOpen", true));
   private final ContainerUseGuard containerGuard = new ContainerUseGuard();
   private ClientLevel guardedWorld;

   public FastUseModule() {
      super("FastUse", "Uses items at high speed without opening containers you just placed.", ModuleCategory.PLAYER);
      this.itemsSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SOME);
      this.blocksSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SOME);
   }

   public boolean appliesTo(ItemStack stack) {
      return this.modeSetting.getValue() == Mode.ALL
         || this.itemsSetting.getValue().contains(stack.getItem())
         || (this.blocksSetting.getValue() && stack.getItem() instanceof BlockItem);
   }

   public int cooldownFor(ItemStack stack) {
      return this.appliesTo(stack) ? this.cooldownSetting.getValue() : 4;
   }

   @Override
   public void onEnable() {
      this.resetGuard();
   }

   @Override
   public void onDisable() {
      this.resetGuard();
   }

   private void resetGuard() {
      this.containerGuard.clear();
      this.guardedWorld = ClientAccess.argusclientMinecraft.level;
   }

   static boolean isContainer(BlockState state, boolean hasMenuProvider) {
      return state.getBlock() instanceof AbstractChestBlock<?> || hasMenuProvider;
   }

   @EventHandler
   private void onPreTick(LocalPlayerPreTickEvent event) {
      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      if (this.guardedWorld != minecraft.level) {
         this.resetGuard();
      }

      if (!this.preventContainerOpenSetting.getValue()) {
         this.containerGuard.clear();
         return;
      }

      if (minecraft.player != null) {
         this.containerGuard.update(minecraft.options.keyUse.isDown());
      }
   }

   @EventHandler
   private void onBlockStateChange(BlockStateChangeEvent event) {
      if (!this.preventContainerOpenSetting.getValue()) {
         return;
      }

      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      if (minecraft.player == null || minecraft.level == null || !minecraft.options.keyUse.isDown()) {
         return;
      }

      boolean container = isContainer(
         event.getNewState(),
         event.getNewState().getMenuProvider(minecraft.level, event.getPos()) != null
      );
      double distanceSq = event.getPos().distToCenterSqr(
         minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()
      );
      if (container && distanceSq <= 64.0) {
         this.containerGuard.placed(event.getPos(), true);
      }
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (!this.preventContainerOpenSetting.getValue()) {
         return;
      }

      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      if (minecraft.player == null || minecraft.level == null) {
         return;
      }

      this.containerGuard.update(minecraft.options.keyUse.isDown());
      BlockPos pos = event.getHitResult().getBlockPos();
      BlockState state = minecraft.level.getBlockState(pos);
      boolean container = isContainer(state, state.getMenuProvider(minecraft.level, pos) != null);
      boolean hasHeldItem = !minecraft.player.getMainHandItem().isEmpty()
         || !minecraft.player.getOffhandItem().isEmpty();
      if (this.containerGuard.shouldBlock(pos, container, minecraft.player.isSecondaryUseActive(), hasHeldItem)) {
         event.cancel();
      }
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.resetGuard();
   }
}
