package dev.argus.client.events;

public final class ClientPacketListenerSendChatEvent extends CancellableEvent {
   private String message;

   public ClientPacketListenerSendChatEvent(String message) {
      this.message = message;
   }

   public String getMessage() {
      return this.message;
   }

   public void setMessage(String message) {
      this.message = message;
   }
}
