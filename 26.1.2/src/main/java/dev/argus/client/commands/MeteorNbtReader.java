package dev.argus.client.commands;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class MeteorNbtReader {
   private MeteorNbtReader() {
   }

   public static Map<String, Object> readCompound(Path var0) throws IOException {
      try (BufferedInputStream var1 = new BufferedInputStream(Files.newInputStream(var0))) {
         var1.mark(2);
         int var2 = var1.read();
         int var3 = var1.read();
         var1.reset();
         FilterInputStream var4 = var2 == 31 && var3 == 139 ? new GZIPInputStream(var1) : var1;
         DataInputStream var5 = new DataInputStream(new BufferedInputStream(var4));
         int var6 = var5.readUnsignedByte();
         if (var6 != 10) {
            throw new IOException("Root tag is not a compound");
         }

         var5.readUTF();
         return readCompoundBody(var5);
      }
   }

   private static Map<String, Object> readCompoundBody(DataInputStream var0) throws IOException {
      LinkedHashMap var1 = new LinkedHashMap();

      while (true) {
         int var2 = var0.readUnsignedByte();
         if (var2 == 0) {
            return var1;
         }

         String var3 = var0.readUTF();
         var1.put(var3, readPayload(var0, var2));
      }
   }

   private static Object readPayload(DataInputStream var0, int var1) throws IOException {
      switch (var1) {
         case 1:
            return var0.readByte();
         case 2:
            return var0.readShort();
         case 3:
            return var0.readInt();
         case 4:
            return var0.readLong();
         case 5:
            return var0.readFloat();
         case 6:
            return var0.readDouble();
         case 7:
            byte[] var8 = new byte[var0.readInt()];
            var0.readFully(var8);
            return var8;
         case 8:
            return var0.readUTF();
         case 9:
            int var7 = var0.readUnsignedByte();
            int var10 = var0.readInt();
            ArrayList var4 = new ArrayList(Math.max(0, var10));

            for (int var5 = 0; var5 < var10; var5++) {
               var4.add(readPayload(var0, var7));
            }

            return var4;
         case 10:
            return readCompoundBody(var0);
         case 11:
            int[] var6 = new int[var0.readInt()];

            for (int var9 = 0; var9 < var6.length; var9++) {
               var6[var9] = var0.readInt();
            }

            return var6;
         case 12:
            long[] var2 = new long[var0.readInt()];

            for (int var3 = 0; var3 < var2.length; var3++) {
               var2[var3] = var0.readLong();
            }

            return var2;
         default:
            throw new IOException("Unknown NBT tag type " + var1);
      }
   }
}
