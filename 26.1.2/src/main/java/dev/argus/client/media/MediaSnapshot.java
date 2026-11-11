package dev.argus.client.media;

import java.util.Objects;

public record MediaSnapshot(int playbackStatus, String artist, String title, long positionMillis, long durationMillis) {
   public static final int PLAYING = 4;

   public MediaSnapshot {
      Objects.requireNonNull(artist, "artist");
      Objects.requireNonNull(title, "title");
   }

   public static MediaSnapshot empty() {
      return new MediaSnapshot(0, "", "", 0L, 0L);
   }
}
