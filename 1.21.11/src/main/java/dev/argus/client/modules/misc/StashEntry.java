package dev.argus.client.modules.misc;

import com.google.gson.annotations.SerializedName;
import dev.argus.client.settings.NamedEntry;

public class StashEntry implements NamedEntry {
   @SerializedName("id")
   private final String id;
   @SerializedName("x")
   private final int x;
   @SerializedName("z")
   private final int z;
   @SerializedName("dimension")
   private final String dimension;
   @SerializedName("server")
   private final String server;

   public StashEntry(String id, int x, int z, String dimension, String server) {
      this.id = id;
      this.x = x;
      this.z = z;
      this.dimension = dimension;
      this.server = server;
   }

   public String formatCoordinates() {
      int xCoordinate = this.x;
      int zCoordinate = this.z;
      return xCoordinate + ", " + zCoordinate + ".";
   }

   public double getX() {
      return this.x;
   }

   public double getZ() {
      return this.z;
   }

   public String getDimension() {
      return this.dimension;
   }

   public String getServer() {
      return this.server;
   }

   @Override
   public String getName() {
      return this.id;
   }
}
