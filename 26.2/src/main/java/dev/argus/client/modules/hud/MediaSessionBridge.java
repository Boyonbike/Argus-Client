package dev.argus.client.modules.hud;

import dev.argus.client.media.MediaSnapshot;
import dev.argus.client.media.PortableMediaSessionProvider;

public final class MediaSessionBridge {
   public static volatile int playbackStatus;
   public static volatile String artist = "";
   public static volatile String title = "";
   public static volatile long positionMillis;
   public static volatile long durationMillis;
   private static final PortableMediaSessionProvider MEDIA = new PortableMediaSessionProvider(MediaSessionBridge::update);

   private MediaSessionBridge() {
   }

   private static void update(MediaSnapshot state) {
      artist = state.artist();
      title = state.title();
      positionMillis = state.positionMillis();
      durationMillis = state.durationMillis();
      playbackStatus = state.playbackStatus();
   }

   public static String getDisplayText() {
      return artist + " - " + title;
   }

   public static void stopMediaUpdates() {
      MEDIA.close();
   }

   static {
      MEDIA.start();
      Runtime.getRuntime().addShutdownHook(new Thread(MEDIA::close, "argusclient-media-stop"));
   }
}
