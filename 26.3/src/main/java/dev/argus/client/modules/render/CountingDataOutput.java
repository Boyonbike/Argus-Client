package dev.argus.client.modules.render;

import java.io.DataOutput;
import org.jetbrains.annotations.NotNull;

public class CountingDataOutput implements DataOutput {
   private int byteCount;

   public int getByteCount() {
      return this.byteCount;
   }

   @Override
   public void write(int value) {
      this.byteCount++;
   }

   @Override
   public void write(byte[] bytes) {
      this.byteCount += bytes.length;
   }

   @Override
   public void write(byte @NotNull [] bytes, int offset, int length) {
      this.byteCount += length;
   }

   @Override
   public void writeBoolean(boolean value) {
      this.byteCount++;
   }

   @Override
   public void writeByte(int value) {
      this.byteCount++;
   }

   @Override
   public void writeShort(int value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 2;
   }

   @Override
   public void writeChar(int value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 2;
   }

   @Override
   public void writeInt(int value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 4;
   }

   @Override
   public void writeLong(long value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 8;
   }

   @Override
   public void writeFloat(float value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 4;
   }

   @Override
   public void writeDouble(double value) {
      int previousCount = this.byteCount;
      this.byteCount = previousCount + 8;
   }

   @Override
   public void writeBytes(String value) {
      int previousCount = this.byteCount;
      String text = value;
      this.byteCount = previousCount + text.length();
   }

   @Override
   public void writeChars(String value) {
      int previousCount = this.byteCount;
      String text = value;
      this.byteCount = previousCount + text.getBytes().length;
   }

   @Override
   public void writeUTF(@NotNull String value) {
      int previousCount = this.byteCount;
      String text = value;
      this.byteCount = previousCount + text.getBytes().length;
   }
}
