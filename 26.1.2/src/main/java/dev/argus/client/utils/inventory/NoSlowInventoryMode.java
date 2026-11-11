package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.InventoryActionEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.movement.NoSlowModule;

public class NoSlowInventoryMode implements ClientAccess {
   protected final NoSlowModule noSlow;

   public NoSlowInventoryMode(NoSlowModule noSlow) {
      this.noSlow = noSlow;
   }

   public void onPacketSend(PacketEvent.Send event) {
   }

   public void onTickStart(LocalPlayerPreTickEvent event) {
   }

   public void onInventoryAction(InventoryActionEvent event) {
   }

   public void beforeMenuClick(MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event) {
   }

   public void onInventoryClickIntercept(MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event) {
   }

   public void onPlayerAiStep() {
   }

   public void onTickBeforePassengerCheck() {
   }
}
