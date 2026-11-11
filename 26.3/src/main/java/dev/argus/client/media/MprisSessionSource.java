package dev.argus.client.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MprisSessionSource implements MediaSessionSource {
   private final MprisSessionSource.Connection connection;
   private String selectedPlayer;

   MprisSessionSource(MprisSessionSource.Connection connection) {
      this.connection = Objects.requireNonNull(connection);
   }

   public static MprisSessionSource connect() throws IOException {
      return new MprisSessionSource(DbusSessionConnection.connect(System.getenv("DBUS_SESSION_BUS_ADDRESS")));
   }

   @Override
   public MediaSnapshot read() throws IOException {
      if (this.connection.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames", List.of()) instanceof List<?> names
         && !names.stream().anyMatch(name -> !(name instanceof String))) {
         List<String> players = names.stream().map(String.class::cast).filter(name -> name.startsWith("org.mpris.MediaPlayer2.")).sorted().toList();
         List<String> ordered = new ArrayList<>(players);
         if (this.selectedPlayer != null && ordered.remove(this.selectedPlayer)) {
            ordered.add(0, this.selectedPlayer);
         }

         MediaSnapshot fallback = MediaSnapshot.empty();
         String fallbackPlayer = null;

         for (String player : ordered) {
            Object response;
            try {
               response = this.connection
                  .call(player, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "GetAll", List.of("org.mpris.MediaPlayer2.Player"));
            } catch (DbusSessionConnection.MethodFailure gone) {
               continue;
            }

            MediaSnapshot state = fromProperties(response);
            if (state.playbackStatus() == 4) {
               this.selectedPlayer = player;
               return state;
            }

            if (fallbackPlayer == null) {
               fallback = state;
               fallbackPlayer = player;
            }
         }

         this.selectedPlayer = fallbackPlayer;
         return fallback;
      } else {
         throw new IOException("Invalid D-Bus player list");
      }
   }

   static MediaSnapshot fromProperties(Object response) throws IOException {
      if (response instanceof Map<?, ?> properties) {
         String playback = string(property(properties, "PlaybackStatus", "s"));

         int status = switch (playback) {
            case "Playing" -> 4;
            case "Paused" -> 5;
            case "Stopped" -> 3;
            default -> 0;
         };
         Object metadata = property(properties, "Metadata", "a{sv}");
         if (metadata == null) {
            metadata = Map.of();
         }

         if (!(metadata instanceof Map<?, ?> track)) {
            throw new IOException("Invalid MPRIS track metadata");
         } else {
            String title = string(property(track, "xesam:title", "s"));
            Object artists = property(track, "xesam:artist", "as");
            String artist = "";
            if (artists != null) {
               if (!(artists instanceof List<?> list) || list.stream().anyMatch(value -> !(value instanceof String))) {
                  throw new IOException("Invalid MPRIS artist list");
               }

               artist = String.join(", ", list.stream().map(String.class::cast).toList());
            }

            return new MediaSnapshot(status, artist, title, millis(property(properties, "Position", "x")), millis(property(track, "mpris:length", "x")));
         }
      } else {
         throw new IOException("Invalid MPRIS properties");
      }
   }

   private static Object property(Map<?, ?> values, String key, String signature) throws IOException {
      Object value = values.get(key);
      if (value instanceof DbusMessageCodec.Variant variant) {
         if (!variant.signature().equals(signature)) {
            throw new IOException("Unexpected MPRIS type for " + key);
         } else {
            return variant.value();
         }
      } else {
         return value;
      }
   }

   private static String string(Object value) throws IOException {
      if (value == null) {
         return "";
      } else if (value instanceof String text) {
         return text;
      } else {
         throw new IOException("Invalid MPRIS string property");
      }
   }

   private static long millis(Object value) throws IOException {
      if (value == null) {
         return 0L;
      } else if (value instanceof Long micros) {
         return Math.max(0L, micros / 1000L);
      } else {
         throw new IOException("Invalid MPRIS microsecond property");
      }
   }

   @Override
   public void close() throws IOException {
      this.connection.close();
   }

   interface Connection extends AutoCloseable {
      Object call(String var1, String var2, String var3, String var4, List<String> var5) throws IOException;

      @Override
      void close() throws IOException;
   }
}
