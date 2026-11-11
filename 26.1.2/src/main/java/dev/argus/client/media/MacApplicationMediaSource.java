package dev.argus.client.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MacApplicationMediaSource implements MediaSessionSource {
   public static final Set<String> SUPPORTED_APPLICATIONS = Set.of("com.apple.Music", "com.spotify.client");
   static final int READ_SEND_MODE = 131219;
   static final long REPLY_TIMEOUT_TICKS = 120L;
   private final MacApplicationMediaSource.RuntimeApi runtime;
   private volatile boolean closed;
   private Thread activeReader;
   private Integer selectedPid;

   MacApplicationMediaSource(MacApplicationMediaSource.RuntimeApi runtime) {
      this.runtime = Objects.requireNonNull(runtime);
   }

   public static MacApplicationMediaSource connect() throws IOException {
      return new MacApplicationMediaSource(new MacJnaRuntime());
   }

   @Override
   public MediaSnapshot read() throws IOException {
      synchronized (this) {
         if (this.closed || Thread.currentThread().isInterrupted()) {
            throw new IOException("Mac media source closed");
         }

         if (this.activeReader != null) {
            throw new IOException("Concurrent Mac media reads are not supported");
         }

         this.activeReader = Thread.currentThread();
      }

      try (MacApplicationMediaSource.Scope scope = this.runtime.begin()) {
         List<MacApplicationMediaSource.Application> applications = new ArrayList<>();

         for (MacApplicationMediaSource.Application app : this.runtime.alreadyRunning()) {
            if (SUPPORTED_APPLICATIONS.contains(app.bundleId()) && app.pid() > 0) {
               applications.add(app);
            }
         }

         applications.sort(Comparator.comparing(MacApplicationMediaSource.Application::bundleId).thenComparingInt(MacApplicationMediaSource.Application::pid));
         if (this.selectedPid != null) {
            for (int n = 0; n < applications.size(); n++) {
               if (applications.get(n).pid() == this.selectedPid) {
                  applications.add(0, applications.remove(n));
                  break;
               }
            }
         }

         MediaSnapshot fallback = null;
         Integer fallbackPid = null;
         IOException unavailable = null;

         for (MacApplicationMediaSource.Application application : applications) {
            if (this.closed || Thread.currentThread().isInterrupted()) {
               throw new IOException("Mac media source closed");
            }

            try {
               MacScriptingDictionary.Schema schema = MacScriptingDictionary.parse(this.runtime.dictionary(application));
               int permission = this.runtime.permissionForGetData(application, false);
               if (permission != 0) {
                  throw new IOException("Mac media Automation permission unavailable: " + permission);
               }

               MacApplicationMediaSource.Session session = this.runtime.openPidTarget(application, 131219, 120L);
               int state = session.enumValue(schema.playerState());
               int status = state == schema.playing()
                  ? 4
                  : (schema.paused() != null && state == schema.paused() ? 5 : (schema.stopped() != null && state == schema.stopped() ? 3 : 0));
               String title = "";
               String artist = "";
               long position = 0L;
               long duration = 0L;
               if (status == 4 || status == 5) {
                  title = session.text(List.of(schema.currentTrack(), schema.title()));
                  artist = session.text(List.of(schema.currentTrack(), schema.artist()));
                  if (schema.position() != null) {
                     position = MacScriptingDictionary.millis(session.number(List.of(schema.position().code())), schema.position());
                  }

                  if (schema.duration() != null) {
                     duration = MacScriptingDictionary.millis(session.number(List.of(schema.currentTrack(), schema.duration().code())), schema.duration());
                  }
               }

               MediaSnapshot snapshot = new MediaSnapshot(status, artist, title, position, duration);
               if (status == 4) {
                  this.selectedPid = application.pid();
                  return snapshot;
               }

               if (fallback == null) {
                  fallback = snapshot;
                  fallbackPid = application.pid();
               }
            } catch (IOException problem) {
               if (unavailable == null) {
                  unavailable = problem;
               } else {
                  unavailable.addSuppressed(problem);
               }
            }
         }

         this.selectedPid = fallbackPid;
         if (fallback != null) {
            return fallback;
         } else if (unavailable != null) {
            throw unavailable;
         } else {
            return MediaSnapshot.empty();
         }
      } finally {
         synchronized (this) {
            this.activeReader = null;
         }
      }
   }

   @Override
   public void close() {
      Thread reader;
      synchronized (this) {
         this.closed = true;
         reader = this.activeReader;
      }

      if (reader != null) {
         reader.interrupt();
      }
   }

   record Application(int pid, String bundleId, String resourceDirectory, String dictionaryFile) {
   }

   interface RuntimeApi {
      MacApplicationMediaSource.Scope begin() throws IOException;

      List<MacApplicationMediaSource.Application> alreadyRunning() throws IOException;

      byte[] dictionary(MacApplicationMediaSource.Application var1) throws IOException;

      int permissionForGetData(MacApplicationMediaSource.Application var1, boolean var2) throws IOException;

      MacApplicationMediaSource.Session openPidTarget(MacApplicationMediaSource.Application var1, int var2, long var3) throws IOException;
   }

   interface Scope extends AutoCloseable {
      @Override
      void close() throws IOException;
   }

   interface Session {
      int enumValue(int var1) throws IOException;

      String text(List<Integer> var1) throws IOException;

      Double number(List<Integer> var1) throws IOException;
   }
}
