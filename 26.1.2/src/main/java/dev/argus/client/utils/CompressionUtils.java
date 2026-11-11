package dev.argus.client.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CompressionUtils {
   private static final int BUFFER_SIZE = 8192;

   private CompressionUtils() {
   }

   public static byte[] inflate(byte[] input) {
      Inflater inflater = new Inflater();

      try {
         inflater.setInput(input);
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         byte[] buffer = new byte[8192];

         while (!inflater.finished()) {
            int written = inflater.inflate(buffer);
            if (written == 0) {
               break;
            }

            output.write(buffer, 0, written);
         }

         return output.toByteArray();
      } catch (DataFormatException failure) {
         throw new IllegalArgumentException("Invalid compressed data", failure);
      } finally {
         inflater.end();
      }
   }

   public static byte[] deflate(byte[] input, int level) {
      Deflater deflater = new Deflater(level);

      try {
         deflater.setInput(input);
         deflater.finish();
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         byte[] buffer = new byte[8192];

         while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            output.write(buffer, 0, written);
         }

         return output.toByteArray();
      } finally {
         deflater.end();
      }
   }
}
