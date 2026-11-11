package dev.argus.client.media;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class DbusSessionConnection implements MprisSessionSource.Connection {
   private static final long CALL_TIMEOUT_NANOS = Duration.ofSeconds(2L).toNanos();
   private final DbusSessionConnection.Transport transport;
   private int nextSerial = 1;

   DbusSessionConnection(DbusSessionConnection.Transport transport) {
      this.transport = transport;
   }

   static DbusSessionConnection connect(String address) throws IOException {
      IOException failure = null;

      for (Path endpoint : endpoints(address)) {
         DbusSessionConnection connection = null;

         try {
            connection = new DbusSessionConnection(DbusSessionConnection.SocketTransport.connect(endpoint));
            connection.authenticate();
            if (connection.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello", List.of()) instanceof String name
               && name.startsWith(":")) {
               return connection;
            }

            throw new IOException("Invalid session-bus Hello reply");
         } catch (IOException unavailable) {
            if (connection != null) {
               try {
                  connection.close();
               } catch (IOException cleanup) {
                  unavailable.addSuppressed(cleanup);
               }
            }

            if (failure == null) {
               failure = unavailable;
            } else {
               failure.addSuppressed(unavailable);
            }
         }
      }

      throw failure == null ? new IOException("No filesystem Unix session-bus endpoint is available") : failure;
   }

   static List<Path> endpoints(String address) throws IOException {
      if (address != null && !address.isBlank()) {
         List<Path> result = new ArrayList<>();

         for (String entry : address.split(";")) {
            if (entry.startsWith("unix:")) {
               String path = null;

               for (String option : entry.substring(5).split(",")) {
                  int equals = option.indexOf(61);
                  if (equals < 1) {
                     throw new IOException("Invalid D-Bus Unix address option");
                  }

                  if (option.substring(0, equals).equals("path")) {
                     if (path != null) {
                        throw new IOException("Duplicate D-Bus path option");
                     }

                     path = unescape(option.substring(equals + 1));
                  }
               }

               if (path != null) {
                  if (!path.startsWith("/")) {
                     throw new IOException("Session-bus path must be absolute");
                  }

                  result.add(Path.of(path));
               }
            }
         }

         return List.copyOf(result);
      } else {
         throw new IOException("DBUS_SESSION_BUS_ADDRESS is unavailable");
      }
   }

   private static String unescape(String value) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      for (int at = 0; at < value.length(); at++) {
         char c = value.charAt(at);
         if (c == '%') {
            if (at + 2 >= value.length()) {
               throw new IOException("Invalid D-Bus address escape");
            }

            int high = Character.digit(value.charAt(++at), 16);
            int low = Character.digit(value.charAt(++at), 16);
            if (high < 0 || low < 0) {
               throw new IOException("Invalid D-Bus address escape");
            }

            bytes.write(high << 4 | low);
         } else {
            if ((c < '0' || c > '9') && (c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && "-_/.*".indexOf(c) < 0) {
               throw new IOException("Invalid unescaped D-Bus address byte");
            }

            bytes.write(c);
         }
      }

      String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
      if (decoded.indexOf(0) >= 0) {
         throw new IOException("Nul session-bus path");
      } else {
         return decoded;
      }
   }

   void authenticate() throws IOException {
      long deadline = System.nanoTime() + CALL_TIMEOUT_NANOS;
      this.transport.writeFully("\u0000AUTH EXTERNAL\r\n".getBytes(StandardCharsets.US_ASCII), deadline);
      String response = this.line(deadline);
      if (response.equals("DATA") || response.equals("DATA ")) {
         this.transport.writeFully("DATA\r\n".getBytes(StandardCharsets.US_ASCII), deadline);
         response = this.line(deadline);
      }

      if (!response.matches("OK [0-9a-fA-F]{32}")) {
         throw new IOException("Session bus did not accept EXTERNAL peer credentials");
      }

      this.transport.writeFully("BEGIN\r\n".getBytes(StandardCharsets.US_ASCII), deadline);
   }

   private String line(long deadline) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] one = new byte[1];

      while (bytes.size() < 4096) {
         this.transport.readFully(one, deadline);
         int value = one[0] & 255;
         if (value == 13) {
            this.transport.readFully(one, deadline);
            if (one[0] != 10) {
               throw new IOException("Invalid auth line terminator");
            }

            return bytes.toString(StandardCharsets.US_ASCII);
         }

         if (value < 32 || value > 126) {
            throw new IOException("Invalid auth line byte");
         }

         bytes.write(value);
      }

      throw new IOException("Auth line exceeds protocol limit");
   }

   @Override
   public Object call(String destination, String path, String iface, String member, List<String> arguments) throws IOException {
      int serial = this.nextSerial++;
      if (this.nextSerial == 0) {
         this.nextSerial = 1;
      }

      long deadline = System.nanoTime() + CALL_TIMEOUT_NANOS;
      this.transport.writeFully(DbusMessageCodec.methodCall(serial, destination, path, iface, member, arguments), deadline);

      for (int messages = 0; messages < 256; messages++) {
         byte[] fixed = new byte[16];
         this.transport.readFully(fixed, deadline);
         DbusMessageCodec.Layout layout = DbusMessageCodec.layout(fixed);
         byte[] fields = new byte[layout.paddedFieldsBytes()];
         byte[] body = new byte[layout.bodyBytes()];
         this.transport.readFully(fields, deadline);
         this.transport.readFully(body, deadline);
         DbusMessageCodec.Message reply = DbusMessageCodec.decode(layout, fields, body);
         if (reply.replySerial() == Integer.toUnsignedLong(serial)) {
            if (reply.type() == 3) {
               throw new DbusSessionConnection.MethodFailure((String)reply.headers().get(4).value());
            }

            if (reply.type() == 2 && reply.arguments().size() == 1) {
               return reply.arguments().get(0);
            }

            throw new IOException("Unexpected read-only D-Bus reply");
         }
      }

      throw new IOException("Session bus flooded unrelated messages");
   }

   @Override
   public void close() throws IOException {
      this.transport.close();
   }

   static final class MethodFailure extends IOException {
      MethodFailure(String name) {
         super("D-Bus method unavailable: " + name);
      }
   }

   private static final class SocketTransport implements DbusSessionConnection.Transport {
      private final SocketChannel channel;
      private final Selector selector;
      private final SelectionKey key;

      private SocketTransport(SocketChannel channel, Selector selector, SelectionKey key) {
         this.channel = channel;
         this.selector = selector;
         this.key = key;
      }

      static DbusSessionConnection.SocketTransport connect(Path endpoint) throws IOException {
         SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
         Selector selector = null;

         try {
            channel.configureBlocking(false);
            selector = Selector.open();
            SelectionKey key = channel.register(selector, 8);
            DbusSessionConnection.SocketTransport transport = new DbusSessionConnection.SocketTransport(channel, selector, key);
            if (!channel.connect(UnixDomainSocketAddress.of(endpoint))) {
               long deadline = System.nanoTime() + DbusSessionConnection.CALL_TIMEOUT_NANOS;

               while (!channel.finishConnect()) {
                  transport.await(8, deadline);
               }
            }

            return transport;
         } catch (IOException | RuntimeException failure) {
            try {
               channel.close();
            } catch (IOException cleanup) {
               failure.addSuppressed(cleanup);
            }

            if (selector != null) {
               try {
                  selector.close();
               } catch (IOException cleanup) {
                  failure.addSuppressed(cleanup);
               }
            }

            throw failure;
         }
      }

      private void await(int interest, long deadline) throws IOException {
         if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Media read interrupted");
         }

         long left = deadline - System.nanoTime();
         if (left <= 0L) {
            throw new IOException("Local media session-bus timeout");
         }

         this.key.interestOps(interest);
         this.selector.select(Math.max(1L, (left + 999999L) / 1000000L));
         this.selector.selectedKeys().clear();
      }

      @Override
      public void readFully(byte[] target, long deadline) throws IOException {
         ByteBuffer bytes = ByteBuffer.wrap(target);

         while (bytes.hasRemaining()) {
            checkDeadline(deadline);
            int count = this.channel.read(bytes);
            if (count < 0) {
               throw new EOFException("Session bus closed");
            }

            if (count == 0) {
               this.await(1, deadline);
            }
         }
      }

      @Override
      public void writeFully(byte[] packet, long deadline) throws IOException {
         ByteBuffer bytes = ByteBuffer.wrap(packet);

         while (bytes.hasRemaining()) {
            checkDeadline(deadline);
            if (this.channel.write(bytes) == 0) {
               this.await(4, deadline);
            }
         }
      }

      private static void checkDeadline(long deadline) throws IOException {
         if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Media operation interrupted");
         }

         if (deadline - System.nanoTime() <= 0L) {
            throw new IOException("Local media session-bus timeout");
         }
      }

      @Override
      public void close() throws IOException {
         IOException failure = null;

         try {
            this.channel.close();
         } catch (IOException error) {
            failure = error;
         }

         try {
            this.selector.close();
         } catch (IOException error) {
            if (failure == null) {
               failure = error;
            } else {
               failure.addSuppressed(error);
            }
         }

         if (failure != null) {
            throw failure;
         }
      }
   }

   interface Transport extends AutoCloseable {
      void readFully(byte[] var1, long var2) throws IOException;

      void writeFully(byte[] var1, long var2) throws IOException;

      @Override
      void close() throws IOException;
   }
}
