package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.CrystalPlacementEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.misc.SwingModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class SwingInteractionManager implements ClientAccess {
   private static final AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static final SwingModule swingModule = ClientContext.getModule(SwingModule.class);
   private final List<SwingInteractionManager.PendingCrystalPlacement> pendingCrystals;

   public SwingInteractionManager() {
      List<SwingInteractionManager.PendingCrystalPlacement> pending = new ArrayList<>(initialCrystalCapacity());
      this.pendingCrystals = Collections.synchronizedList(pending);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static int initialCrystalCapacity() {
      return 10;
   }

   public boolean tryTrackCrystalPlacement(BlockPos position) {
      if (this.isCrystalPlacementLimited()) {
         return false;
      }

      this.pendingCrystals.add(new SwingInteractionManager.PendingCrystalPlacement(position, System.currentTimeMillis() + 300L));
      ClientAccess.eventBus.post(new CrystalPlacementEvent(position));
      return true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.pendingCrystals.removeIf(pending -> {
         long expiresAt = pending.expiresAtMillis();
         return expiresAt <= System.currentTimeMillis();
      });
   }

   public void swing(InteractionHand hand, SwingAction action, boolean packetOnly) {
      AntiCheatModule antiCheatSnapshot = antiCheat;
      if (!action.isEnabled(antiCheatSnapshot)) {
         if (!packetOnly) {
            this.swingClientSide(hand);
         }
      } else if (packetOnly) {
         PacketSender.sendSwing(hand);
      } else {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.swing(hand);
      }
   }

   public void swing(InteractionHand hand, SwingAction action) {
      boolean packetOnly = false;
      this.swing(hand, action, packetOnly);
   }

   public void swingPacket(InteractionHand hand, SwingAction action) {
      boolean packetOnly = true;
      this.swing(hand, action, packetOnly);
   }

   public void swingClientSide(InteractionHand hand) {
      if (ClientAccess.argusclientMinecraft.player.swinging) {
         int swingTime = ClientAccess.argusclientMinecraft.player.swingTime;
         int swingDuration = ((LivingEntityAccessor)ClientAccess.argusclientMinecraft.player).getSwingDuration();
         if (swingTime < swingDuration / 2 && ClientAccess.argusclientMinecraft.player.swingTime >= 0) {
            return;
         }
      }

      ClientAccess.argusclientMinecraft.player.swingTime = -1;
      ClientAccess.argusclientMinecraft.player.swinging = true;
      SwingModule moduleSnapshot = swingModule;
      if (moduleSnapshot.isToggled()) {
         Setting<SwingModule.HandChoice> handSetting = swingModule.handSetting;
         SwingModule.HandChoice handChoice = handSetting.getValue();
         if (handChoice.getHand() != null) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            Setting<SwingModule.HandChoice> overrideSetting = swingModule.handSetting;
            SwingModule.HandChoice overrideChoice = overrideSetting.getValue();
            player.swingingArm = overrideChoice.getHand();
            return;
         }
      }

      ClientAccess.argusclientMinecraft.player.swingingArm = hand;
   }

   public boolean isCrystalPlacementLimited() {
      AntiCheatModule antiCheatSnapshot = antiCheat;
      if (!antiCheatSnapshot.isPaperPlacement()) {
         return false;
      }

      int pendingCount = this.pendingCrystals.size();
      return pendingCount >= 9;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      PacketEvent.Receive packetEvent = event;
      if (packetEvent.getPacket() instanceof ClientboundAddEntityPacket packet && packet.getType() == EntityType.END_CRYSTAL) {
         EntityType spawnTypeSnapshot = packet.getType();
         double spawnXSnapshot = packet.getX();
         double spawnYSnapshot = packet.getY();
         double spawnZ = packet.getZ();
         AABB spawnBoundsSnapshot = spawnTypeSnapshot.getSpawnAABB(spawnXSnapshot, spawnYSnapshot, spawnZ);
         double inset = -BoundingBoxUtils.blockCornerInset;
         AABB crystalBounds = spawnBoundsSnapshot.inflate(inset);
         BlockableEventLoop<?> executor = ClientAccess.argusclientMinecraft;
         Runnable removePlacement = () -> this.pendingCrystals.removeIf(pending -> {
            AABB placementBounds = new AABB(capturePlacementPosition(pending));
            return crystalBounds.intersects(placementBounds);
         });
         executor.execute(removePlacement);
      }
   }

   private static BlockPos capturePlacementPosition(SwingInteractionManager.PendingCrystalPlacement pending) {
      return pending.position();
   }

   private record PendingCrystalPlacement(BlockPos position, long expiresAtMillis) {
   }
}
