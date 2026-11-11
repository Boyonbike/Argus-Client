package dev.argus.client.utils;

import dev.argus.client.ClientAccess;
import java.net.SocketAddress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

public final class ServerAddressUtils {
   private static final String fallbackServerAddress = "singleplayer";

   private ServerAddressUtils() {
   }

   public static String getCurrentServerAddress() {
      String address = null;

      try {
         ClientPacketListener packetListener = ClientAccess.argusclientMinecraft.getConnection();
         Connection connection = packetListener.getConnection();
         SocketAddress remoteAddress = connection.getRemoteAddress();
         address = remoteAddress.toString();
         int separatorIndex = address.indexOf(58);
         if (separatorIndex > 0) {
            address = address.substring(0, separatorIndex);
         }

         while (address.endsWith(".")) {
            address = address.substring(0, address.length() - 1);
         }
      } catch (Throwable var5) {
      }

      return address != null && !address.isEmpty() ? address : fallbackServerAddress;
   }
}
