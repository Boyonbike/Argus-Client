package com.jagrosh.discordipc.entities.pipe;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Objects;

final class JavaUnixSocketTransport implements AutoCloseable {
   private final SocketChannel socket;
   private final Object writeLock = new Object();

   JavaUnixSocketTransport(SocketChannel socket) {
      this.socket = Objects.requireNonNull(socket);
   }

   static JavaUnixSocketTransport connect(Path endpoint) throws IOException {
      return new JavaUnixSocketTransport(SocketChannel.open(UnixDomainSocketAddress.of(endpoint)));
   }

   IpcFrameCodec.Frame read() throws IOException {
      return IpcFrameCodec.read(this.socket);
   }

   void write(byte[] packet) throws IOException {
      synchronized (this.writeLock) {
         IpcFrameCodec.writeFully(this.socket, packet);
      }
   }

   @Override
   public void close() throws IOException {
      this.socket.close();
   }
}
