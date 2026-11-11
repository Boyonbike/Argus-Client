package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.WarpRechargeChoice;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;

public class WarpModule extends Module {
   private static FakeLagModule fakeLag;
   protected final Setting<WarpModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", WarpModule.ModeChoice.PLAIN));
   public final Setting<Integer> boostSetting = this.add(new NumberSetting<>("Boost", 2, 1, 50));
   public final Setting<Integer> chargeSetting = this.add(new NumberSetting<>("Charge", 20, 1, 100));
   protected final Setting<Integer> chargeSpeedSetting;
   protected final Setting<WarpRechargeChoice> rechargeSetting;
   protected final Setting<Boolean> fakeLagSetting;
   protected final Setting<Boolean> disableGroundSetting;
   protected final Setting<Boolean> autoDisableSetting;
   protected final WarpRechargeController rechargeController;
   protected boolean receivedCorrection;
   protected volatile int plainCharge;

   public int getCharge() {
      return this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE ? this.rechargeController.pendingPongs.size() : this.plainCharge;
   }

   private void updatePlainWarp() {
      if (!this.hasDisplacement()) {
         ClientContext.tickRateController.releaseMultiplier(this);
         this.setFakeLagEnabled(false);
         this.receivedCorrection = false;
      } else if (this.plainCharge == 0 || this.receivedCorrection) {
         ClientContext.tickRateController.releaseMultiplier(this);
         if (this.autoDisableSetting.getValue()) {
            this.toggle();
         }

         this.setFakeLagEnabled(false);
      } else if (this.plainCharge > 0) {
         if (this.fakeLagSetting.getValue()) {
            boolean wasFakeLagEnabled = fakeLag.isToggled();
            LocalPlayer groundPlayer = ClientAccess.argusclientMinecraft.player;
            boolean enableFakeLag = true;
            if (!groundPlayer.onGround()) {
               Boolean requireGround = this.disableGroundSetting.getValue();
               enableFakeLag = !requireGround;
            }

            this.setFakeLagEnabled(enableFakeLag);
            if (!wasFakeLagEnabled) {
               return;
            }
         }

         TickRateController boostControllerSnapshot = ClientContext.tickRateController;
         float boost = this.boostSetting.getValue().intValue();
         boostControllerSnapshot.requestMultiplier(this, boost);
      }
   }

   protected boolean hasMovementInputOrDisplacement() {
      if (ClientAccess.argusclientMinecraft.player.input.getMoveVector().length() > 0.0F) {
         return true;
      } else if (ClientAccess.argusclientMinecraft.player.getX() - ClientAccess.argusclientMinecraft.player.xo != 0.0) {
         return true;
      } else {
         return ClientAccess.argusclientMinecraft.player.getY() - ClientAccess.argusclientMinecraft.player.yo != 0.0
            ? true
            : ClientAccess.argusclientMinecraft.player.getZ() - ClientAccess.argusclientMinecraft.player.zo != 0.0;
      }
   }

   private void updateAlternativeWarp() {
      if (!this.rechargeController.pendingPongs.isEmpty() && this.hasMovementInputOrDisplacement()) {
         TickRateController.PlayerTicks boostControllerSnapshot = ClientContext.playerTickController;
         float boost = this.boostSetting.getValue().intValue();
         boostControllerSnapshot.requestMultiplier(this, boost);
         ClientCommonPacketListenerImpl connection = ClientAccess.argusclientMinecraft.player.connection;
         Packet<?> packet = this.rechargeController.pendingPongs.poll();
         connection.send(packet);
      } else {
         if (!this.rechargeController.pendingPongs.isEmpty() && !this.receivedCorrection) {
            ClientContext.playerTickController.releaseMultiplier(this);
         } else {
            ClientContext.playerTickController.releaseMultiplier(this);
            if (this.autoDisableSetting.getValue()) {
               this.toggle();
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return String.valueOf(this.getCharge());
   }

   @Override
   public void onEnable() {
      this.receivedCorrection = false;
      if (this.rechargeSetting.getValue() == WarpRechargeChoice.INSTANT) {
         Integer instantCharge = this.chargeSetting.getValue();
         this.plainCharge = instantCharge;
      }
   }

   @Override
   public void onDisable() {
      this.setFakeLagEnabled(false);
      if (this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE && !this.isWorldUnavailable()) {
         try {
            while (!this.rechargeController.pendingPongs.isEmpty()) {
               ClientCommonPacketListenerImpl connection = ClientAccess.argusclientMinecraft.player.connection;
               Packet<?> packet = this.rechargeController.pendingPongs.poll();
               connection.send(packet);
            }
         } catch (Exception var3) {
         }

         this.rechargeController.pendingPongs.clear();
      }
   }

   private void setFakeLagEnabled(boolean enabled) {
      if (this.fakeLagSetting.getValue() && ClientAccess.argusclientMinecraft.player != null) {
         fakeLag.setToggled(enabled);
      }
   }

   boolean hasDisplacement() {
      Vec3 positionSnapshot = ClientAccess.argusclientMinecraft.player.position();
      double previousX = ClientAccess.argusclientMinecraft.player.xo;
      double previousY = ClientAccess.argusclientMinecraft.player.yo;
      double previousZ = ClientAccess.argusclientMinecraft.player.zo;
      return positionSnapshot.distanceToSqr(previousX, previousY, previousZ) > 0.0;
   }

   @EventHandler
   private void onIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.receivedCorrection = true;
      }
   }

   @EventHandler
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Pre == event.getStage()) {
         if (this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE) {
            this.updateAlternativeWarp();
         } else {
            this.updatePlainWarp();
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      fakeLag = (FakeLagModule)registry.getModule(FakeLagModule.class);
   }

   public WarpModule() {
      super("Warp", "Allows you to dash forward after standing still for a certain amount of time.", ModuleCategory.MOVEMENT, "tickshift");
      Predicate<Object> chargeSpeedVisibility = ignored -> this.modeSetting.getValue() == WarpModule.ModeChoice.PLAIN;
      this.chargeSpeedSetting = this.add(new NumberSetting<>("ChargeSpeed", 1, 1, 4, chargeSpeedVisibility));
      Predicate<Object> rechargeVisibility = ignored -> this.modeSetting.getValue() == WarpModule.ModeChoice.PLAIN;
      this.rechargeSetting = this.add(new EnumSetting<>("Recharge", WarpRechargeChoice.NONE, rechargeVisibility));
      this.fakeLagSetting = this.add(new BooleanSetting("FakeLag", false).markVisibilityDependency());
      this.disableGroundSetting = this.add(new BooleanSetting("DisableGround", false).dependsOn(this.fakeLagSetting));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
      this.rechargeController = new WarpRechargeController(this);
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      ALTERNATIVE("Alternative");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String name) {
         this.displayName = name;
      }
   }
}
