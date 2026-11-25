package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.item.Items;

/**
 * Sends a chat message of your choice when you drop to a chosen number of totems.
 * With Predict on, the message goes out before the pop if a nearby end crystal
 * would be lethal (using Argus's own crystal damage and movement prediction).
 */
public class TotemMessageModule extends Module {
   private final Setting<Integer> totemsSetting = this.add(new NumberSetting<>("Totems", 2, 0, 36));
   private final Setting<String> messageSetting = this.add(new StringSetting("Message", "2 totems left!"));
   private final Setting<Boolean> predictSetting = this.add(new BooleanSetting("Predict", true));

   private final LethalDamageTracker lethalTracker = new LethalDamageTracker(() -> true);
   private int lastCount = -1; // -1 = not yet read
   private volatile boolean sentEarly = false; // already sent (prediction or packet) for this totem

   /**
    * Zero-latency path. The server's totem-pop packet (entity event 35) is seen here the
    * instant it arrives on the network thread. The send is queued on the game thread ahead of
    * the game's own handling of that packet, so the message goes out before the pop is even
    * applied on your client, with no wait for the next tick.
    */
   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != 35) {
         return;
      }

      ClientAccess.argusclientMinecraft.execute(() -> {
         if (this.isWorldUnavailable() || this.sentEarly) {
            return;
         }
         if (packet.getEntity(ClientAccess.argusclientMinecraft.level) != ClientAccess.argusclientMinecraft.player) {
            return;
         }

         // The pop has not been applied yet, so this is the count before losing the totem.
         int before = InventoryUtils.countItems(stack -> stack.is(Items.TOTEM_OF_UNDYING));
         if (before - 1 == this.totemsSetting.getValue()) {
            this.send();
            this.sentEarly = true;
         }
      });
   }

   public TotemMessageModule() {
      super("TotemMessage", "Sends a chat message when you drop to a set number of totems.", ModuleCategory.COMBAT);
   }

   @Override
   public String getInfo() {
      return String.valueOf(this.totemsSetting.getValue());
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.lastCount = -1;
      this.sentEarly = false;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable()) {
         this.lastCount = -1;
         this.sentEarly = false;
         return;
      }

      int count = InventoryUtils.countItems(stack -> stack.is(Items.TOTEM_OF_UNDYING));
      int target = this.totemsSetting.getValue();

      // First read: remember the count, don't send.
      if (this.lastCount == -1) {
         this.lastCount = count;
         return;
      }

      // Predict: one totem above the target and a lethal crystal is near, so a pop is imminent.
      if (this.predictSetting.getValue() && !this.sentEarly
            && count == target + 1
            && this.lethalTracker.hasLethalCrystal()) {
         this.send();
         this.sentEarly = true;
      }

      // Fallback: you lost a totem and landed on the target (skipped if already sent early).
      if (count == target && count < this.lastCount && !this.sentEarly) {
         this.send();
      }

      if (count != this.lastCount) {
         this.sentEarly = false;
      }
      this.lastCount = count;
   }

   private void send() {
      String message = this.messageSetting.getValue();
      if (message == null || message.isBlank() || ClientAccess.argusclientMinecraft.player == null) {
         return;
      }
      ChatUtils.sendChatOrCommand(message);
   }
}
