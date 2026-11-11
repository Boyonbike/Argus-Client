package dev.argus.client.modules.render.targets;

import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import dev.argus.client.settings.options.ListFilterMode;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

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
   /** Last agreed values of mirrored specific targets, by rule key. */
   private final java.util.Map<String, String> agreedRules = new java.util.HashMap<>();

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

   /** This page's card of the given kind. */
   public TargetSettings.Group group(boolean fromEsp, TargetSettings.Kind kind) {
      return this.mine(fromEsp, kind);
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
      this.syncRules();
   }

   // ---- target groups -------------------------------------------------------------------------------------------------

   public SpecificTargets specific(boolean fromEsp) {
      return (fromEsp ? this.espTargets : this.tracersTargets).specific;
   }

   private SpecificTargets otherSpecific(boolean fromEsp) {
      return this.specific(!fromEsp);
   }

   /** Whether this group is on in the other module (and the other module itself is on). */
   public boolean onInOther(boolean fromEsp, SpecificRules.Rule rule) {
      SpecificRules.Rule other = this.otherSpecific(fromEsp).rules().find(rule.id);
      return (fromEsp ? this.tracers : this.esp).isToggled() && other != null && other.enabled;
   }

   /**
    * Switches the group in the other module. Switching it on copies it there (its things and settings, once, unless
    * linked; things already in another group there move to this one) and turns the other module on if it was off.
    */
   public void toggleInOther(boolean fromEsp, SpecificRules.Rule rule) {
      SpecificTargets theirs = this.otherSpecific(fromEsp);
      Module otherModule = fromEsp ? this.tracers : this.esp;
      SpecificRules.Rule other = theirs.rules().find(rule.id);
      if (this.onInOther(fromEsp, rule)) {
         other.enabled = false;
      } else {
         if (other == null || !rule.mirror) {
            other = theirs.rules().put(rule);
         }

         other.enabled = true;
         if (!otherModule.isToggled()) {
            otherModule.enable();
         }
      }

      theirs.changed();
      this.syncRules();
   }

   /** Links the group in both modules, starting from this page's copy. */
   public void mirror(boolean fromEsp, SpecificRules.Rule rule) {
      SpecificTargets mine = this.specific(fromEsp);
      SpecificTargets theirs = this.otherSpecific(fromEsp);
      SpecificRules.Rule other = theirs.rules().put(rule);
      other.mirror = true;
      rule.mirror = true;
      this.agreedRules.put(rule.id, rule.values());
      mine.changed();
      theirs.changed();
   }

   public void breakLink(SpecificRules.Rule rule) {
      for (boolean esp : new boolean[]{true, false}) {
         SpecificTargets targets = this.specific(esp);
         SpecificRules.Rule found = targets.rules().find(rule.id);
         if (found != null && found.mirror) {
            found.mirror = false;
            targets.changed();
         }
      }

      this.agreedRules.remove(rule.id);
   }

   /** Removes the group from this page, and from the other page too while the two are linked. */
   public void remove(boolean fromEsp, SpecificRules.Rule rule) {
      SpecificTargets mine = this.specific(fromEsp);
      SpecificRules.Rule found = mine.rules().find(rule.id);
      if (found == null) {
         return;
      }

      if (found.mirror) {
         SpecificTargets theirs = this.otherSpecific(fromEsp);
         SpecificRules.Rule other = theirs.rules().find(rule.id);
         if (other != null && other.mirror) {
            theirs.rules().remove(other);
            theirs.changed();
         }
      }

      mine.rules().remove(found);
      mine.changed();
      this.agreedRules.remove(rule.id);
   }

   /** Applies changes on linked groups right away (the GUI calls this after a click). Also runs every tick. */
   public void syncRules() {
      SpecificTargets esp = this.espTargets.specific;
      SpecificTargets tracers = this.tracersTargets.specific;
      migrate(this.espTargets);
      migrate(this.tracersTargets);
      esp.poll();
      tracers.poll();
      int changed = SpecificRules.sync(esp.rules(), tracers.rules(), this.agreedRules);
      if ((changed & SpecificRules.LEFT_CHANGED) != 0) {
         esp.changed();
      }

      if ((changed & SpecificRules.RIGHT_CHANGED) != 0) {
         tracers.changed();
      }
   }

   /**
    * Moves what the old Players/Mobs/Items/Blocks cards showed into the target list, once per module, then switches the
    * cards off (they're no longer shown). Mobs picked by category (hostile, passive, neutral) have no single thing to
    * add, so only a whitelisted mob list carries over.
    */
   static void migrate(TargetSettings targets) {
      if (targets.migrated.getValue()) {
         if (!targets.migratedExtras.getValue()) {
            targets.migratedExtras.setValue(true);
            if (targets.extraMigration != null) {
               targets.extraMigration.run();
            }
         }

         return;
      }

      targets.migrated.setValue(true);
      SpecificRules rules = targets.specific.rules();
      TargetSettings.Players players = targets.players;
      if (players.active()) {
         SpecificRules.Rule all = rules.add(List.of(new SpecificRules.Member(SpecificRules.Type.GROUP, SpecificRules.ALL_PLAYERS, SpecificRules.Category.PLAYERS)));
         if (all != null) {
            all.body.write(colorRange(players.color.getValue(), players.range.getValue(), null));
         }
      }

      TargetSettings.Blocks blocks = targets.blocks;
      if (blocks.active() && !blocks.list.getValue().isEmpty()) {
         List<String> ids = new ArrayList<>();
         for (Block block : blocks.list.getValue()) {
            ids.add(SpecificTargets.idOf(block));
         }

         SpecificRules.Rule group = rules.add(SpecificTargets.members(SpecificRules.Type.BLOCK, ids));
         if (group != null) {
            group.body.write(colorRange(blocks.color.getValue(), blocks.range.getValue(), blocks.colorMode.getValue()));
         }
      }

      TargetSettings.Items items = targets.items;
      if (items.active() && items.filter.getValue() == ListFilterMode.WHITELIST && !items.list.getValue().isEmpty()) {
         List<String> ids = new ArrayList<>();
         for (Item item : items.list.getValue()) {
            ids.add(SpecificTargets.idOf(item));
         }

         SpecificRules.Rule group = rules.add(SpecificTargets.members(SpecificRules.Type.ITEM, ids));
         if (group != null) {
            group.body.write(colorRange(items.color.getValue(), items.range.getValue(), null));
         }
      }

      TargetSettings.Mobs mobs = targets.mobs;
      if (mobs.active() && mobs.filter.getValue() == ListFilterMode.WHITELIST && !mobs.list.getValue().isEmpty()) {
         List<String> ids = new ArrayList<>();
         for (EntityType<?> type : mobs.list.getValue()) {
            ids.add(SpecificTargets.idOf(type));
         }

         SpecificRules.Rule group = rules.add(SpecificTargets.members(SpecificRules.Type.ENTITY, ids));
         if (group != null) {
            group.body.write(colorRange(mobs.hostileColor.getValue(), mobs.range.getValue(), null));
         }
      }

      for (TargetSettings.Group card : targets.groups()) {
         card.enabled.setValue(false);
      }

      targets.specific.changed();
   }

   private static String colorRange(java.awt.Color color, float range, TargetSettings.ColorMode colorMode) {
      com.google.gson.JsonObject object = new com.google.gson.JsonObject();
      object.add("TargetColor", new dev.argus.client.settings.ColorSetting("Color", color).toJson());
      object.addProperty("TargetRange", range);
      if (colorMode != null) {
         object.add("TargetColorMode", new dev.argus.client.settings.EnumSetting<>("ColorMode", colorMode).toJson());
      }

      return object.toString();
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
