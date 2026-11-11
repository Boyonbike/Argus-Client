package dev.argus.client.media;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class PortableMediaSessionProvider implements AutoCloseable {
   private static final long POLL_MILLIS = 500L;
   private static final long RETRY_MILLIS = 5000L;
   private final Consumer<MediaSnapshot> update;
   private final PortableMediaSessionProvider.SourceFactory sourceFactory;
   private final PortableMediaSessionProvider.SourceFactory macSourceFactory;
   private final PortableMediaSessionProvider.WindowsFactory windowsFactory;
   private final PortableMediaSessionProvider.Backend backend;
   private volatile MediaSnapshot latest = MediaSnapshot.empty();
   private volatile PortableMediaSessionProvider.Availability availability = PortableMediaSessionProvider.Availability.NOT_STARTED;
   private volatile String lastFailure;
   private volatile MediaSessionSource source;
   private volatile Thread worker;
   private volatile PortableMediaSessionProvider.WindowsBackend windows;
   private volatile boolean closed;
   private boolean started;

   public PortableMediaSessionProvider(Consumer<MediaSnapshot> update) {
      this(
         update,
         System.getProperty("os.name", ""),
         MprisSessionSource::connect,
         MacApplicationMediaSource::connect,
         sink -> new WindowsMediaSessionProvider(
            state -> sink.accept(new MediaSnapshot(state.playbackStatus(), state.artist(), state.title(), state.positionMillis(), state.durationMillis()))
         )
      );
   }

   PortableMediaSessionProvider(
      Consumer<MediaSnapshot> update,
      String platform,
      PortableMediaSessionProvider.SourceFactory sourceFactory,
      PortableMediaSessionProvider.WindowsFactory windowsFactory
   ) {
      this(update, platform, sourceFactory, MacApplicationMediaSource::connect, windowsFactory);
   }

   PortableMediaSessionProvider(
      Consumer<MediaSnapshot> update,
      String platform,
      PortableMediaSessionProvider.SourceFactory sourceFactory,
      PortableMediaSessionProvider.SourceFactory macSourceFactory,
      PortableMediaSessionProvider.WindowsFactory windowsFactory
   ) {
      this.update = Objects.requireNonNull(update);
      this.sourceFactory = Objects.requireNonNull(sourceFactory);
      this.macSourceFactory = Objects.requireNonNull(macSourceFactory);
      this.windowsFactory = Objects.requireNonNull(windowsFactory);
      String os = Objects.requireNonNull(platform).toLowerCase(Locale.ROOT);
      this.backend = os.startsWith("windows")
         ? PortableMediaSessionProvider.Backend.WINDOWS_GSMTC
         : (
            os.contains("linux")
               ? PortableMediaSessionProvider.Backend.LINUX_MPRIS
               : (
                  !os.contains("mac") && !os.contains("darwin")
                     ? PortableMediaSessionProvider.Backend.UNSUPPORTED_OS
                     : PortableMediaSessionProvider.Backend.MACOS_APPLE_EVENTS
               )
         );
   }

   public MediaSnapshot latest() {
      return this.latest;
   }

   public PortableMediaSessionProvider.Backend backend() {
      return this.backend;
   }

   public PortableMediaSessionProvider.Availability availability() {
      return !this.closed && this.windows != null && this.windows.lastFailure() != null
         ? PortableMediaSessionProvider.Availability.UNAVAILABLE
         : this.availability;
   }

   public String lastFailure() {
      PortableMediaSessionProvider.WindowsBackend w = this.windows;
      return w != null ? w.lastFailure() : this.lastFailure;
   }

   public synchronized void start() {
      if (!this.closed && !this.started) {
         this.started = true;
         if (this.backend == PortableMediaSessionProvider.Backend.WINDOWS_GSMTC) {
            this.windows = this.windowsFactory.create(this::publish);
            this.windows.start();
         } else if (this.backend != PortableMediaSessionProvider.Backend.LINUX_MPRIS && this.backend != PortableMediaSessionProvider.Backend.MACOS_APPLE_EVENTS
            )
          {
            this.availability = PortableMediaSessionProvider.Availability.UNSUPPORTED;
            this.lastFailure = "No local-media adapter for this operating system";
         } else {
            this.worker = new Thread(
               this::poll, this.backend == PortableMediaSessionProvider.Backend.LINUX_MPRIS ? "argusclient-mpris-session" : "argusclient-mac-media"
            );
            this.worker.setDaemon(true);
            this.worker.start();
         }
      }
   }

   private synchronized void publish(MediaSnapshot state) {
      if (!this.closed) {
         this.latest = Objects.requireNonNull(state);
         this.availability = PortableMediaSessionProvider.Availability.AVAILABLE;
         this.update.accept(state);
      }
   }

   private synchronized void publishFailure(Throwable failure) {
      if (!this.closed) {
         this.lastFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
         this.latest = MediaSnapshot.empty();
         this.availability = PortableMediaSessionProvider.Availability.UNAVAILABLE;
         this.update.accept(this.latest);
      }
   }

   private void poll() {
      while (!this.closed && !Thread.currentThread().isInterrupted()) {
         MediaSessionSource opened = null;

         try {
            opened = (this.backend == PortableMediaSessionProvider.Backend.MACOS_APPLE_EVENTS ? this.macSourceFactory : this.sourceFactory).open();
            synchronized (this) {
               if (this.closed) {
                  return;
               }

               this.source = opened;
            }

            while (!this.closed && !Thread.currentThread().isInterrupted()) {
               this.publish(opened.read());
               this.lastFailure = null;
               Thread.sleep(500L);
            }
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         } catch (IOException | RuntimeException | LinkageError unavailable) {
            this.publishFailure(unavailable);
         } finally {
            synchronized (this) {
               if (this.source == opened) {
                  this.source = null;
               }
            }

            if (opened != null) {
               try {
                  opened.close();
               } catch (IOException cleanup) {
                  if (!this.closed) {
                     this.lastFailure = "Media close failed: " + cleanup.getMessage();
                  }
               }
            }
         }

         if (!this.closed && !Thread.currentThread().isInterrupted()) {
            try {
               Thread.sleep(5000L);
            } catch (InterruptedException interrupted) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }

   @Override
   public void close() {
      MediaSessionSource current;
      PortableMediaSessionProvider.WindowsBackend w;
      Thread thread;
      synchronized (this) {
         if (this.closed) {
            return;
         }

         this.closed = true;
         this.availability = PortableMediaSessionProvider.Availability.CLOSED;
         current = this.source;
         w = this.windows;
         thread = this.worker;
      }

      if (thread != null) {
         thread.interrupt();
      }

      if (current != null) {
         try {
            current.close();
         } catch (IOException cleanup) {
            this.lastFailure = "Media close failed: " + cleanup.getMessage();
         }
      }

      if (w != null) {
         w.close();
      }
   }

   public enum Availability {
      NOT_STARTED,
      AVAILABLE,
      UNAVAILABLE,
      UNSUPPORTED,
      CLOSED;
   }

   public enum Backend {
      WINDOWS_GSMTC,
      LINUX_MPRIS,
      MACOS_APPLE_EVENTS,
      UNSUPPORTED_OS;
   }

   @FunctionalInterface
   interface SourceFactory {
      MediaSessionSource open() throws IOException;
   }

   interface WindowsBackend extends AutoCloseable {
      void start();

      String lastFailure();

      @Override
      void close();
   }

   @FunctionalInterface
   interface WindowsFactory {
      PortableMediaSessionProvider.WindowsBackend create(Consumer<MediaSnapshot> var1);
   }
}
