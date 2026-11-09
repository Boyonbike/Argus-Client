package dev.argus.client.events;

public final class ConnectScreenConnectEvent extends CancellableEvent {
   private final String serverAddress;

   public String getServerAddress() {
      return this.serverAddress;
   }

   public ConnectScreenConnectEvent(String serverAddress) {
      this.serverAddress = serverAddress;
   }
}
