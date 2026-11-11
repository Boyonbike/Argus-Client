package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.world.WorldUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;

public class CoordLoggerModule extends Module {
   private static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy");
   private final Setting<Boolean> disconnectSetting = this.add(new BooleanSetting("Log", true));
   private final Setting<Boolean> deathSetting = this.add(new BooleanSetting("Death", true));
   private final Setting<Boolean> fileSetting = this.add(new BooleanSetting("File", false));
   private volatile String coordinateText;

   private void appendRetainedEntry(String entry) {
      Path directory = ConfigurationManager.ROOT_DIRECTORY;
      Path logFile = directory.resolve("coordlogger.txt");

      try {
         LinkOption[] existenceOptions = new LinkOption[0];
         Stream<String> existingLines;
         if (Files.exists(logFile, existenceOptions)) {
            existingLines = Files.lines(logFile);
         } else {
            existingLines = Stream.empty();
         }

         try (existingLines) {
            Stream<String> filteredLines = Stream.concat(existingLines, Stream.of(entry)).filter(this::isRetainedEntry);
            List<String> retainedLines = filteredLines.collect(Collectors.toList());
            OpenOption[] writeOptions = new OpenOption[0];
            Files.write(logFile, retainedLines, writeOptions);
         }
      } catch (IOException var12) {
      }
   }

   private boolean isRetainedEntry(String entry) {
      if (entry != null && !entry.isBlank()) {
         String[] fields = entry.split("\\|");
         int fieldCount = fields.length;
         if (fieldCount != 3) {
            return false;
         }

         try {
            LocalDateTime now = LocalDateTime.now();
            String timestampText = fields[2];
            String timestampSnapshot = timestampText.trim();
            DateTimeFormatter parserFormatter = timestampFormatter;
            LocalDateTime entryTime = LocalDateTime.parse(timestampSnapshot, parserFormatter);
            ChronoUnit ageUnit = ChronoUnit.DAYS;
            long ageDays = ageUnit.between(entryTime, now);
            return ageDays < 7L;
         } catch (DateTimeParseException invalidTimestamp) {
            return false;
         }
      } else {
         return false;
      }
   }

   private void saveCoordinates(String reason) {
      if (this.coordinateText != null) {
         StringBuilder entry = new StringBuilder().append(this.coordinateText).append(" | ").append(reason);
         KeyboardHandler keyboard = ClientAccess.argusclientMinecraft.keyboardHandler;
         String clipboardText = entry.toString();
         keyboard.setClipboard(clipboardText);
         Boolean writeFile = this.fileSetting.getValue();
         if (writeFile) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = timestampFormatter;
            String timestamp = now.format(formatter);
            entry.append(" | ").append(timestamp);
            this.appendRetainedEntry(entry.toString());
         }
      }
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      if (ClientAccess.argusclientMinecraft.player != null) {
         Setting<Boolean> disconnectSettingSnapshot = this.disconnectSetting;
         Boolean saveOnDisconnect = disconnectSettingSnapshot.getValue();
         if (saveOnDisconnect) {
            this.saveCoordinates("Disconnect");
            return;
         }
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      Object[] formatArguments = new Object[4];
      LocalPlayer xPlayer = ClientAccess.argusclientMinecraft.player;
      int blockX = xPlayer.getBlockX();
      formatArguments[0] = blockX;
      LocalPlayer yPlayer = ClientAccess.argusclientMinecraft.player;
      int blockY = yPlayer.getBlockY();
      formatArguments[1] = blockY;
      LocalPlayer zPlayer = ClientAccess.argusclientMinecraft.player;
      int blockZ = zPlayer.getBlockZ();
      formatArguments[2] = blockZ;
      DimensionType dimension = WorldUtils.getDimension();
      formatArguments[3] = dimension.getDisplayName();
      this.coordinateText = "X: %d, Y: %d, Z: %d in The %s".formatted(formatArguments);
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         Setting<Boolean> deathSettingSnapshot = this.deathSetting;
         Boolean saveOnDeath = deathSettingSnapshot.getValue();
         if (saveOnDeath) {
            this.saveCoordinates("Death");
         }
      }
   }

   public CoordLoggerModule() {
      super("CoordLogger", "Saves your coordinates whenever you log/die.", ModuleCategory.MISC);
   }
}
