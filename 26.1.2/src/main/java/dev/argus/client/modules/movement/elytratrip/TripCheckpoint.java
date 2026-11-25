package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.GameScreens;
import dev.argus.client.chat.ChatHomeNames;
import dev.argus.client.chat.HomeOverride;
import dev.argus.client.chat.HomeOverrideDialog;
import dev.argus.client.chat.SetHomeScheduler;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;

/**
 * ElytraTrip's rolling checkpoint: every interval while flying it sends /sethome and answers the server's override dialog
 * straight from the packet, as AutoSetHome does, with its own scheduler and state.
 */
public final class TripCheckpoint {
   /** 6b6t's override dialog and its button. */
   private static final String DIALOG_PHRASE = "home";
   private static final String BUTTON_PHRASE = "override";
   private static final long DIALOG_TIMEOUT_MS = 10_000;
   private static final long RETRY_MS = 60_000;

   private final Minecraft mc = Minecraft.getInstance();
   private final Supplier<String> homeName;
   private final LongSupplier intervalMs;
   private final LongSupplier clock;
   private final BooleanSupplier active;
   private SetHomeScheduler scheduler;
   /** Mirrors scheduler.waitingForDialog() for the netty thread. */
   private volatile boolean expectingDialog;
   private Runnable changed = () -> {
   };

   public TripCheckpoint(Supplier<String> homeName, LongSupplier intervalMs, LongSupplier clock, BooleanSupplier active) {
      this.homeName = homeName;
      this.intervalMs = intervalMs;
      this.clock = clock;
      this.active = active;
      this.scheduler = new SetHomeScheduler(clock, SetHomeScheduler.State.empty());
   }

   /** Called whenever the scheduler's state changes, so the trip can save it. */
   public void onChange(Runnable changed) {
      this.changed = changed;
   }

   public void restore(SetHomeScheduler.State state) {
      scheduler = new SetHomeScheduler(clock, state == null ? SetHomeScheduler.State.empty() : state);
      expectingDialog = false;
   }

   public SetHomeScheduler.State state() {
      return scheduler.state();
   }

   /** A new connection: anything in flight belonged to the old one. */
   public void reset() {
      scheduler.cancel();
      expectingDialog = false;
   }

   public void tick(boolean flying, boolean landing) {
      scheduler.expire();
      expectingDialog = scheduler.waitingForDialog();
      if (scheduler.waiting() || !GameScreens.playing(mc)) return;
      if (CheckpointTiming.due(clock.getAsLong(), scheduler.state().lastSentAt(), intervalMs.getAsLong(), serverWaitMs(), landing, flying)) {
         send();
      }
   }

   /** Sends /sethome now if nothing stops it, for a fresh checkpoint before a refill. True if it was sent. */
   public boolean requestNow() {
      scheduler.expire();
      if (scheduler.waiting() || serverWaitMs() > 0) return false;
      return send();
   }

   /** Whether a /sethome is out and its reply or dialog hasn't come yet. */
   public boolean waiting() {
      return scheduler.waiting();
   }

   public long serverWaitMs() {
      SetHomeScheduler.Status status = scheduler.snapshot(false, intervalMs.getAsLong(), 0);
      return Math.max(status.cooldownMillis(), status.retryMillis());
   }

   public long lastSuccessAt() {
      return scheduler.state().lastSuccessAt();
   }

   public SetHomeScheduler.Location lastLocation() {
      var history = scheduler.state().history();
      return history.isEmpty() ? null : history.getFirst().location();
   }

   private boolean send() {
      ClientPacketListener connection = mc.getConnection();
      String name = ChatHomeNames.valid(homeName.get());
      if (connection == null || mc.player == null || mc.level == null || name == null) return false;
      scheduler.sent(name, location(), true, DIALOG_TIMEOUT_MS, intervalMs.getAsLong());
      expectingDialog = true;
      changed.run();
      connection.sendCommand(HomeOverride.command(name));
      return true;
   }

   public void onChat(String text) {
      if (scheduler.message(text, RETRY_MS) == SetHomeScheduler.Reply.IGNORED) return;
      expectingDialog = scheduler.waitingForDialog();
      changed.run();
   }

   /**
    * Runs on the netty thread. A dialog that arrives while our /sethome waits for one, and that matches 6b6t's override
    * dialog, is answered here and never shown. Returns true when the packet should be cancelled.
    */
   public boolean onDialog(ClientboundShowDialogPacket packet) {
      if (!expectingDialog || !active.getAsBoolean()) return false;
      Dialog dialog = packet.dialog().value();
      ActionButton button = HomeOverrideDialog.overrideButton(dialog, DIALOG_PHRASE, BUTTON_PHRASE);
      if (button == null || button.action().isEmpty()) return false;
      expectingDialog = false;
      ClientPacketListener connection = mc.getConnection();
      Holder<Dialog> holder = packet.dialog();
      mc.execute(() -> {
         boolean live = active.getAsBoolean() && connection != null && connection == mc.getConnection() && mc.player != null
            && scheduler.waitingForDialog();
         if (!live || !HomeOverrideDialog.press(connection, button)) {
            // Not ours to answer after all: show it as the server intended.
            if (connection != null && connection == mc.getConnection()) connection.showDialog(holder, mc.screen);
            return;
         }
         scheduler.clicked(location());
         changed.run();
      });
      return true;
   }

   private SetHomeScheduler.Location location() {
      return new SetHomeScheduler.Location(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().identifier().toString());
   }
}
