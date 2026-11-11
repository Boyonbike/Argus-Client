package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.InteractPacketUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.mixin.MinecraftAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class LegacyCrystalModule extends Module {
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 150, 0, 250));
   private final Setting<Boolean> optimizerSetting = this.add(new BooleanSetting("Optimizer", false));
   private final Map<BlockPos, Long> placementTimes = new HashMap<>();
   private final ElapsedTimer attackTimer = new ElapsedTimer();
   private long attackDelayMillis = 150L;

   public LegacyCrystalModule() {
      super("LegacyCrystal", "Legit auto crystal (crystal aura).", ModuleCategory.COMBAT);
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
      synchronized (this.placementTimes) {
         this.placementTimes.entrySet().removeIf(entry -> {
            Long placedAt = entry.getValue();
            long expiryTime = placedAt;
            expiryTime += 15000L;
            return expiryTime < System.currentTimeMillis();
         });
      }

      if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult entityHit) {
         Entity entity = entityHit.getEntity();
         long delay = this.attackDelayMillis;
         if (this.attackTimer.hasElapsed(delay) && entity instanceof EndCrystal && this.placementTimes.containsKey(entity.blockPosition().below())) {
            ((MinecraftAccessor)ClientAccess.argusclientMinecraft).attack();
            double randomDelay = Math.random();
            Integer maximumDelay = this.delaySetting.getValue();
            this.attackDelayMillis = (long)(randomDelay * maximumDelay.intValue());
            this.attackTimer.reset();
            return;
         }
      }
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockHitResult hit = event.getHitResult();
      BlockPos hitPosition = hit.getBlockPos();
      BlockPos abovePosition = hitPosition.above(2);
      BlockState aboveState = level.getBlockState(abovePosition);
      if (aboveState.isAir()) {
         BlockHitResult placementHit = event.getHitResult();
         BlockPos placementPosition = placementHit.getBlockPos();
         boolean canPlaceCrystal = BlockInteractionUtils.canPlaceCrystal(placementPosition, false, false, true, false, false, false);
         ItemStack mainHand = ClientAccess.argusclientMinecraft.player.getMainHandItem();
         DataComponentType<?> weaponComponent = DataComponents.WEAPON;
         if (mainHand.has(weaponComponent)) {
            Item item = canPlaceCrystal ? Items.END_CRYSTAL : Items.OBSIDIAN;
            int slot = InventoryUtils.findHotbarSlot(item);
            InventoryUtils.selectHotbarSlotForPlacement(slot);
         } else {
            ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
            Item obsidian = Items.OBSIDIAN;
            if (heldStack.is(obsidian) && canPlaceCrystal) {
               Item crystal = Items.END_CRYSTAL;
               int slot = InventoryUtils.findHotbarSlot(crystal);
               InventoryUtils.selectHotbarSlot(slot);
            }
         }
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundUseItemOnPacket useItem) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         InteractionHand hand = useItem.hand();
         ItemStack heldStack = player.getItemInHand(hand);
         Item crystal = Items.END_CRYSTAL;
         if (heldStack.is(crystal)) {
            Map<BlockPos, Long> placements = this.placementTimes;
            BlockHitResult hit = useItem.hitResult();
            BlockPos position = hit.getBlockPos();
            placements.compute(position, (ignoredKey, ignoredValue) -> System.currentTimeMillis());
         }
      }

      Entity attackedEntity = event.getPacket() instanceof ServerboundAttackPacket attack ? InteractPacketUtils.getEntity(attack)
         : event.getPacket() instanceof ServerboundInteractPacket interact && InteractPacketUtils.getAction(interact) == InteractPacketUtils.Action.ATTACK ? InteractPacketUtils.getEntity(interact)
         : null;
      if (attackedEntity instanceof EndCrystal crystal && this.optimizerSetting.getValue()) {
         crystal.setRemoved(RemovalReason.KILLED);
      }
   }
}
