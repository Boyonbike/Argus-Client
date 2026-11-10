package dev.argus.client.modules.render.targets;

import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;

/**
 * Pairing between ESP's and Tracers' target cards. Per card, either module can:
 * <ul>
 *   <li><b>toggle</b> the card in the other module: switching it on copies this module's settings for that card once;</li>
 *   <li><b>mirror</b> it: both modules keep the card identical until the link is broken.</li>
 * </ul>
 * The mirror flag is a hidden setting saved in both modules. Mirrored cards sync every client tick through
 * {@link LinkSync}, so set settings (edited in place, without change callbacks) stay in step as well.
 */
public final class TargetLinks implements ClientAccess {
   private static TargetLinks instance;
   private final Module esp;
   private final TargetSettings espTargets;
   private final Module tracers;
   private final TargetSettings tracersTargets;
   private final LinkSync[] syncs = new LinkSync[TargetSettings.Kind.values().length];

   private TargetLinks(Module esp, TargetSettings espTargets, Module tracers, TargetSettings tracersTargets) {
      this.esp = esp;
      this.espTargets = espTargets;
      this.tracers = tracers;
      this.tracersTargets = tracersTargets;
   }

   /** Connects the two modules once, after both exist. */
   public static void bind(Module esp, TargetSettings espTargets, Module tracers, TargetSettings tracersTargets) {
      if (instance != null) {
         ClientAccess.eventBus.unsubscribe(instance);
      }

      instance = new TargetLinks(esp, espTargets, tracers, tracersTargets);
      ClientAccess.eventBus.subscribe(instance);
   }

   public static TargetLinks get() {
      return instance;
   }

   public String otherName(boolean fromEsp) {
      return (fromEsp ? this.tracers : this.esp).getName();
   }

   private TargetSettings.Group mine(boolean fromEsp, TargetSettings.Kind kind) {
      return (fromEsp ? this.espTargets : this.tracersTargets).group(kind);
   }

   private TargetSettings.Group other(boolean fromEsp, TargetSettings.Kind kind) {
      return (fromEsp ? this.tracersTargets : this.espTargets).group(kind);
   }

   /** Whether the card is on in the other module (and the other module itself is on). */
   public boolean onInOther(boolean fromEsp, TargetSettings.Kind kind) {
      return (fromEsp ? this.tracers : this.esp).isToggled() && this.other(fromEsp, kind).active();
   }

   public boolean mirrored(TargetSettings.Kind kind) {
      return this.espTargets.group(kind).mirrored();
   }

   /**
    * Switches the card in the other module. Switching it on copies this module's settings for the card once and turns
    * the other module on if it was off; after that the two can be changed separately (unless mirrored).
    */
   public void toggleInOther(boolean fromEsp, TargetSettings.Kind kind) {
      TargetSettings.Group other = this.other(fromEsp, kind);
      Module otherModule = fromEsp ? this.tracers : this.esp;
      if (this.onInOther(fromEsp, kind)) {
         other.enabled.setValue(false);
      } else {
         if (!this.mirrored(kind)) {
            LinkSync.copy(this.pairs(kind), fromEsp);
         }

         other.enabled.setValue(true);
         if (!otherModule.isToggled()) {
            otherModule.enable();
         }
      }

      this.syncNow(kind);
   }

   /** Links the card in both modules, starting from this module's settings. */
   public void mirror(boolean fromEsp, TargetSettings.Kind kind) {
      LinkSync sync = new LinkSync(this.pairs(kind));
      sync.link(fromEsp);
      this.syncs[kind.ordinal()] = sync;
      this.espTargets.group(kind).mirror.setValue(true);
      this.tracersTargets.group(kind).mirror.setValue(true);
   }

   public void breakLink(TargetSettings.Kind kind) {
      this.syncs[kind.ordinal()] = null;
      this.espTargets.group(kind).mirror.setValue(false);
      this.tracersTargets.group(kind).mirror.setValue(false);
   }

   @EventHandler
   private void onTick(MinecraftTickEvent event) {
      for (TargetSettings.Kind kind : TargetSettings.Kind.values()) {
         this.syncNow(kind);
      }
   }

   /** Applies any change on a mirrored card right away (the GUI calls this after a click). */
   public void syncNow(TargetSettings.Kind kind) {
      TargetSettings.Group espCard = this.espTargets.group(kind);
      TargetSettings.Group tracersCard = this.tracersTargets.group(kind);
      if (espCard.mirrored() != tracersCard.mirrored()) {
         // One side was reset (or a config edited by hand): a link needs both sides, so treat it as broken.
         espCard.mirror.setValue(false);
         tracersCard.mirror.setValue(false);
      }

      if (!espCard.mirrored()) {
         this.syncs[kind.ordinal()] = null;
         return;
      }

      LinkSync sync = this.syncs[kind.ordinal()];
      if (sync == null) {
         sync = new LinkSync(this.pairs(kind));
         sync.link(true);
         this.syncs[kind.ordinal()] = sync;
      }

      sync.sync();
   }

   /** The card's settings in both modules, paired by config name (ESP on the left). */
   List<LinkSync.Pair> pairs(TargetSettings.Kind kind) {
      List<Setting<?>> left = this.espTargets.group(kind).shared();
      List<Setting<?>> right = this.tracersTargets.group(kind).shared();
      List<LinkSync.Pair> pairs = new ArrayList<>(left.size());
      for (Setting<?> setting : left) {
         for (Setting<?> candidate : right) {
            if (candidate.getConfigName().equals(setting.getConfigName())) {
               pairs.add(new LinkSync.Pair(field(setting), field(candidate)));
               break;
            }
         }
      }

      return pairs;
   }

   private static LinkSync.Field field(Setting<?> setting) {
      return new LinkSync.Field() {
         @Override
         public String read() {
            return setting.toJson().toString();
         }

         @Override
         public void write(String value) {
            setting.fromJson(JsonParser.parseString(value));
            setting.runChangeCallback();
         }
      };
   }
}
