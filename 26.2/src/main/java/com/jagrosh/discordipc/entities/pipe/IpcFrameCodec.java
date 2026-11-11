package com.jagrosh.discordipc.entities.pipe;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

final class IpcFrameCodec {
   static IpcFrameCodec.Frame read(ReadableByteChannel input) throws IOException {
      ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFully(input, header);
      header.flip();
      int opcode = header.getInt();
      int length = header.getInt();
      if (length < 0) {
         throw new IOException("IPC payload length exceeds Java array range");
      }

      ByteBuffer body = ByteBuffer.allocate(length);
      readFully(input, body);
      return new IpcFrameCodec.Frame(opcode, body.array());
   }

   static void readFully(ReadableByteChannel input, ByteBuffer target) throws IOException {
      while (target.hasRemaining()) {
         int count = input.read(target);
         if (count < 0) {
            throw new EOFException("IPC stream ended inside a frame");
         }

         if (count == 0) {
            throw new IOException("Blocking IPC reader made no progress");
         }
      }
   }

   static void writeFully(WritableByteChannel output, byte[] serializedPacket) throws IOException {
      ByteBuffer source = ByteBuffer.wrap(serializedPacket);

      while (source.hasRemaining()) {
         int count = output.write(source);
         if (count < 0) {
            throw new EOFException("IPC stream closed during write");
         }

         if (count == 0) {
            throw new IOException("Blocking IPC writer made no progress");
         }
      }
   }

   record Frame(int opcode, byte[] body) {
   }
}
