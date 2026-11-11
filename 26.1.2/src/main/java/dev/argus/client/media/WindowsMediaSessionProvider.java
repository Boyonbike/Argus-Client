package dev.argus.client.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WindowsMediaSessionProvider implements PortableMediaSessionProvider.WindowsBackend {
   public static final int PLAYING = 4;
   private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(3L);
   private static final long POLL_MILLIS = 500L;
   private static final long RETRY_MILLIS = 5000L;
   private final Consumer<WindowsMediaSessionProvider.MediaState> update;
   private final Supplier<WindowsMediaSessionProvider.RuntimeApi> runtime;
   private final AtomicBoolean started = new AtomicBoolean();
   private final AtomicBoolean closed = new AtomicBoolean();
   private volatile WindowsMediaSessionProvider.MediaState latest = WindowsMediaSessionProvider.MediaState.empty();
   private volatile String lastFailure;
   private volatile Thread worker;

   public WindowsMediaSessionProvider(Consumer<WindowsMediaSessionProvider.MediaState> update) {
      this(update, () -> (WindowsMediaSessionProvider.RuntimeApi)Native.load("combase", WindowsMediaSessionProvider.RuntimeApi.class));
   }

   WindowsMediaSessionProvider(Consumer<WindowsMediaSessionProvider.MediaState> update, Supplier<WindowsMediaSessionProvider.RuntimeApi> runtime) {
      this.update = Objects.requireNonNull(update, "Media update consumer");
      this.runtime = Objects.requireNonNull(runtime, "Windows runtime API supplier");
   }

   public WindowsMediaSessionProvider.MediaState latest() {
      return this.latest;
   }

   @Override
   public String lastFailure() {
      return this.lastFailure;
   }

   @Override
   public synchronized void start() {
      if (!this.closed.get() && this.started.compareAndSet(false, true)) {
         if (!System.getProperty("os.name", "").startsWith("Windows")) {
            this.publish(WindowsMediaSessionProvider.MediaState.empty());
         } else {
            Thread thread = new Thread(this::poll, "argusclient-media-session");
            thread.setDaemon(true);
            this.worker = thread;
            thread.start();
         }
      }
   }

   private void publish(WindowsMediaSessionProvider.MediaState state) {
      if (!this.closed.get()) {
         this.latest = state;
         this.update.accept(state);
      }
   }

   private void poll() {
      while (!this.closed.get() && !Thread.currentThread().isInterrupted()) {
         try {
            WindowsMediaSessionProvider.RuntimeApi api = this.runtime.get();

            try (WindowsMediaSessionProvider.SessionReader reader = new WindowsMediaSessionProvider.SessionReader(api, ASYNC_TIMEOUT)) {
               while (!this.closed.get() && !Thread.currentThread().isInterrupted()) {
                  this.publish(reader.read());
                  this.lastFailure = null;
                  Thread.sleep(500L);
               }
            }
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
         } catch (RuntimeException | LinkageError unavailable) {
            this.lastFailure = unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage();
            this.publish(WindowsMediaSessionProvider.MediaState.empty());

            try {
               Thread.sleep(5000L);
            } catch (InterruptedException interrupted) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }
   }

   @Override
   public synchronized void close() {
      if (this.closed.compareAndSet(false, true)) {
         Thread thread = this.worker;
         if (thread != null) {
            thread.interrupt();
         }
      }
   }

   static Memory guid(String text) {
      UUID id = UUID.fromString(text);
      Memory bytes = new Memory(16L);
      long high = id.getMostSignificantBits();
      long low = id.getLeastSignificantBits();
      bytes.setInt(0L, (int)(high >>> 32));
      bytes.setShort(4L, (short)(high >>> 16));
      bytes.setShort(6L, (short)high);

      for (int i = 0; i < 8; i++) {
         bytes.setByte(8 + i, (byte)(low >>> 56 - 8 * i));
      }

      return bytes;
   }

   private static void check(int result, String operation) {
      if (result < 0) {
         throw new WindowsMediaSessionProvider.WinRtException(operation, result);
      }
   }

   static final class ComReference implements AutoCloseable {
      private final Thread owner = Thread.currentThread();
      private Pointer pointer;

      private ComReference(Pointer pointer) {
         this.pointer = pointer;
      }

      static WindowsMediaSessionProvider.ComReference required(Pointer pointer) {
         if (pointer != null && Pointer.nativeValue(pointer) != 0L) {
            return new WindowsMediaSessionProvider.ComReference(pointer);
         } else {
            throw new IllegalStateException("Windows media API returned a null interface");
         }
      }

      private int invoke(int slot, Object... arguments) {
         if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("COM reference used from another thread");
         }

         if (this.pointer == null) {
            throw new IllegalStateException("COM reference is released");
         }

         Pointer table = this.pointer.getPointer(0L);
         Pointer address = table.getPointer((long)slot * Native.POINTER_SIZE);
         if (address == null) {
            throw new IllegalStateException("Null Windows media vtable slot " + slot);
         }

         Object[] call = new Object[arguments.length + 1];
         call[0] = this.pointer;
         System.arraycopy(arguments, 0, call, 1, arguments.length);
         return Function.getFunction(address, 63).invokeInt(call);
      }

      WindowsMediaSessionProvider.ComReference query(String interfaceId) {
         Memory iid = WindowsMediaSessionProvider.guid(interfaceId);

         WindowsMediaSessionProvider.ComReference var4;
         try {
            PointerByReference result = new PointerByReference();
            WindowsMediaSessionProvider.check(this.invoke(0, iid, result), "QueryInterface");
            var4 = required(result.getValue());
         } catch (Throwable var6) {
            if (iid != null) {
               try {
                  iid.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (iid != null) {
            iid.close();
         }

         return var4;
      }

      WindowsMediaSessionProvider.ComReference optionalObject(int slot) {
         PointerByReference result = new PointerByReference();
         WindowsMediaSessionProvider.check(this.invoke(slot, result), "Media interface method " + slot);
         Pointer value = result.getValue();
         return value != null && Pointer.nativeValue(value) != 0L ? required(value) : null;
      }

      WindowsMediaSessionProvider.ComReference object(int slot) {
         WindowsMediaSessionProvider.ComReference result = this.optionalObject(slot);
         if (result == null) {
            throw new IllegalStateException("Windows media method " + slot + " returned no required object");
         } else {
            return result;
         }
      }

      int integer(int slot) {
         IntByReference value = new IntByReference();
         WindowsMediaSessionProvider.check(this.invoke(slot, value), "Media integer method " + slot);
         return value.getValue();
      }

      long longInteger(int slot) {
         LongByReference value = new LongByReference();
         WindowsMediaSessionProvider.check(this.invoke(slot, value), "Media TimeSpan method " + slot);
         return value.getValue();
      }

      String string(WindowsMediaSessionProvider.RuntimeApi api, int slot) {
         PointerByReference value = new PointerByReference();
         WindowsMediaSessionProvider.check(this.invoke(slot, value), "Media string method " + slot);

         try (WindowsMediaSessionProvider.StringHandle string = new WindowsMediaSessionProvider.StringHandle(api, value.getValue())) {
            return string.text();
         }
      }

      void bestEffort(int slot) {
         try {
            this.invoke(slot);
         } catch (RuntimeException var3) {
         }
      }

      @Override
      public void close() {
         if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("COM reference released from another thread");
         }

         if (this.pointer != null) {
            try {
               this.invoke(2);
            } finally {
               this.pointer = null;
            }
         }
      }
   }

   public record MediaState(int playbackStatus, String artist, String title, long positionMillis, long durationMillis) {
      public MediaState {
         Objects.requireNonNull(artist, "Artist");
         Objects.requireNonNull(title, "Title");
      }

      public static WindowsMediaSessionProvider.MediaState empty() {
         return new WindowsMediaSessionProvider.MediaState(0, "", "", 0L, 0L);
      }
   }

   public interface RuntimeApi extends StdCallLibrary {
      int RoInitialize(int var1);

      void RoUninitialize();

      int RoGetActivationFactory(Pointer var1, Pointer var2, PointerByReference var3);

      int WindowsCreateString(Pointer var1, int var2, PointerByReference var3);

      int WindowsDeleteString(Pointer var1);

      Pointer WindowsGetStringRawBuffer(Pointer var1, IntByReference var2);
   }

   static final class SessionReader implements AutoCloseable {
      private static final String RUNTIME_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
      private static final String MANAGER_STATICS = "2050c4ee-11a0-57de-aed7-c97c70338245";
      private static final String ASYNC_INFO = "00000036-0000-0000-c000-000000000046";
      private final WindowsMediaSessionProvider.RuntimeApi api;
      private final Duration timeout;
      private final Thread owner = Thread.currentThread();
      private WindowsMediaSessionProvider.ComReference manager;
      private boolean initialized;
      private boolean stopped;

      SessionReader(WindowsMediaSessionProvider.RuntimeApi api, Duration timeout) throws InterruptedException {
         this.api = Objects.requireNonNull(api, "Windows runtime API");
         this.timeout = Objects.requireNonNull(timeout, "Async timeout");
         if (!timeout.isNegative() && !timeout.isZero()) {
            WindowsMediaSessionProvider.check(api.RoInitialize(1), "RoInitialize(MTA)");
            this.initialized = true;

            try (WindowsMediaSessionProvider.StringHandle name = WindowsMediaSessionProvider.StringHandle.create(
                  api, "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager"
               )) {
               Memory iid = WindowsMediaSessionProvider.guid("2050c4ee-11a0-57de-aed7-c97c70338245");

               try {
                  PointerByReference result = new PointerByReference();
                  WindowsMediaSessionProvider.check(api.RoGetActivationFactory(name.pointer, iid, result), "RoGetActivationFactory");

                  try (
                     WindowsMediaSessionProvider.ComReference factory = WindowsMediaSessionProvider.ComReference.required(result.getValue());
                     WindowsMediaSessionProvider.ComReference operation = factory.object(6);
                  ) {
                     this.manager = this.awaitObject(operation);
                  }
               } catch (Throwable var16) {
                  if (iid != null) {
                     try {
                        iid.close();
                     } catch (Throwable var11) {
                        var16.addSuppressed(var11);
                     }
                  }

                  throw var16;
               }

               if (iid != null) {
                  iid.close();
               }
            } catch (RuntimeException | Error | InterruptedException failure) {
               this.close();
               throw failure;
            }
         } else {
            throw new IllegalArgumentException("Async timeout must be positive");
         }
      }

      WindowsMediaSessionProvider.MediaState read() throws InterruptedException {
         this.requireOwner();
         if (this.stopped) {
            throw new IllegalStateException("Media reader is closed");
         }

         try (WindowsMediaSessionProvider.ComReference session = this.manager.optionalObject(6)) {
            if (session == null) {
               return WindowsMediaSessionProvider.MediaState.empty();
            }

            int status;
            try (WindowsMediaSessionProvider.ComReference info = session.object(9)) {
               status = info.integer(7);
            }

            String title;
            String artist;
            try (
               WindowsMediaSessionProvider.ComReference operation = session.object(7);
               WindowsMediaSessionProvider.ComReference properties = this.awaitObject(operation);
            ) {
               title = properties.string(this.api, 6);
               artist = properties.string(this.api, 9);
            }

            long position = 0L;
            long duration = 0L;

            try (WindowsMediaSessionProvider.ComReference timeline = session.optionalObject(8)) {
               if (timeline != null) {
                  long start = timeline.longInteger(6);
                  long end = timeline.longInteger(7);
                  long current = timeline.longInteger(10);
                  position = (current - start) / 10000L;
                  duration = (end - start) / 10000L;
               }
            }

            return new WindowsMediaSessionProvider.MediaState(status, artist, title, position, duration);
         }
      }

      private WindowsMediaSessionProvider.ComReference awaitObject(WindowsMediaSessionProvider.ComReference operation) throws InterruptedException {
         this.requireOwner();

         try (WindowsMediaSessionProvider.ComReference info = operation.query("00000036-0000-0000-c000-000000000046")) {
            boolean terminal = false;

            try {
               long startedAt = System.nanoTime();
               long limit = this.timeout.toNanos();

               while (!Thread.currentThread().isInterrupted()) {
                  int status = info.integer(7);
                  if (status == 1) {
                     terminal = true;
                     return operation.object(8);
                  }

                  if (status == 2) {
                     terminal = true;
                     throw new WindowsMediaSessionProvider.WinRtException("Media query canceled", -2147023673);
                  }

                  if (status == 3) {
                     terminal = true;
                     throw new WindowsMediaSessionProvider.WinRtException("Media query failed", info.integer(8));
                  }

                  if (status != 0) {
                     throw new IllegalStateException("Unknown async media status " + status);
                  }

                  if (System.nanoTime() - startedAt >= limit) {
                     throw new WindowsMediaSessionProvider.WinRtException("Media query timed out", -2147023436);
                  }

                  Thread.sleep(10L);
               }

               throw new InterruptedException("Media query interrupted");
            } finally {
               if (!terminal) {
                  info.bestEffort(9);
               }

               info.bestEffort(10);
            }
         }
      }

      private void requireOwner() {
         if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("COM media reader used from another thread");
         }
      }

      @Override
      public void close() {
         this.requireOwner();
         if (!this.stopped) {
            this.stopped = true;

            try {
               if (this.manager != null) {
                  this.manager.close();
               }
            } finally {
               if (this.initialized) {
                  this.initialized = false;
                  this.api.RoUninitialize();
               }
            }
         }
      }
   }

   static final class StringHandle implements AutoCloseable {
      private final WindowsMediaSessionProvider.RuntimeApi api;
      private Pointer pointer;
      private boolean released;

      private StringHandle(WindowsMediaSessionProvider.RuntimeApi api, Pointer pointer) {
         this.api = api;
         this.pointer = pointer;
      }

      static WindowsMediaSessionProvider.StringHandle create(WindowsMediaSessionProvider.RuntimeApi api, String text) {
         Memory utf16 = new Memory(Math.max(2L, text.length() * 2L));

         WindowsMediaSessionProvider.StringHandle var4;
         try {
            for (int i = 0; i < text.length(); i++) {
               utf16.setShort(i * 2L, (short)text.charAt(i));
            }

            PointerByReference result = new PointerByReference();
            WindowsMediaSessionProvider.check(api.WindowsCreateString(utf16, text.length(), result), "WindowsCreateString");
            var4 = new WindowsMediaSessionProvider.StringHandle(api, result.getValue());
         } catch (Throwable var6) {
            try {
               utf16.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         utf16.close();
         return var4;
      }

      String text() {
         if (this.released) {
            throw new IllegalStateException("Windows string is released");
         }

         IntByReference length = new IntByReference();
         Pointer raw = this.api.WindowsGetStringRawBuffer(this.pointer, length);
         int count = length.getValue();
         if (count < 0 || count > 1048576) {
            throw new IllegalStateException("Invalid media string length " + count);
         }

         if (count == 0) {
            return "";
         }

         if (raw == null) {
            throw new IllegalStateException("Windows string has no data");
         }

         short[] units = raw.getShortArray(0L, count);
         char[] text = new char[count];

         for (int i = 0; i < count; i++) {
            text[i] = (char)(units[i] & 65535);
         }

         return new String(text);
      }

      @Override
      public void close() {
         if (!this.released) {
            this.released = true;
            this.api.WindowsDeleteString(this.pointer);
            this.pointer = null;
         }
      }
   }

   public static final class WinRtException extends RuntimeException {
      private final int result;

      WinRtException(String operation, int result) {
         super(operation + " failed (HRESULT 0x" + Integer.toHexString(result) + ")");
         this.result = result;
      }

      public int result() {
         return this.result;
      }
   }
}
