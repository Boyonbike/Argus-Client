package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.PlayerModelPart;

public class SkinFlickerModule extends Module {
   private static final PlayerModelPart[] HORIZONTAL_PART_ORDER;
   private static final PlayerModelPart[] VERTICAL_PART_ORDER;
   private final Setting<SkinFlickerModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", SkinFlickerModule.ModeChoice.FULL));
   private final Setting<Boolean> capeSetting = this.add(new BooleanSetting("Cape", false));
   private final Setting<Float> delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 10.0F).withUnit("s"));
   private final Map<PlayerModelPart, Boolean> savedModelParts = new HashMap<>();
   private final int modelPartCount = PlayerModelPart.values().length;
   private final ElapsedTimer flickerTimer = new ElapsedTimer();
   private boolean optionsDirty;
   private int sequenceIndex;

   @Override
   public void onEnable() {
      for (PlayerModelPart part : PlayerModelPart.values()) {
         Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
         Options options = ClientAccess.argusclientMinecraft.options;
         boolean enabled = options.isModelPartEnabled(part);
         savedPartsSnapshot.put(part, enabled);
      }
   }

   @Override
   public void onDisable() {
      Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
      Options options = ClientAccess.argusclientMinecraft.options;
      savedPartsSnapshot.forEach(options::setModelPart);
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      ElapsedTimer timerSnapshot = this.flickerTimer;
      Setting<Float> delaySettingSnapshot = this.delaySetting;
      Float delayValue = delaySettingSnapshot.getValue();
      double delaySeconds = delayValue.floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;
      if (timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
         Setting<SkinFlickerModule.ModeChoice> modeSettingSnapshot = this.modeSetting;
         SkinFlickerModule.ModeChoice mode = modeSettingSnapshot.getValue();
         switch (mode) {
            case FULL:
               for (PlayerModelPart part : PlayerModelPart.values()) {
                  if (this.shouldTogglePart(part)) {
                     this.togglePart(part);
                  }
               }
               break;
            case VERTICAL:
            case HORIZONTAL:
               Setting<SkinFlickerModule.ModeChoice> sequenceModeSnapshot = this.modeSetting;
               PlayerModelPart[] partOrder = sequenceModeSnapshot.getValue() == SkinFlickerModule.ModeChoice.HORIZONTAL
                  ? HORIZONTAL_PART_ORDER
                  : VERTICAL_PART_ORDER;
               int indexSnapshot = this.sequenceIndex;
               PlayerModelPart sequencePart = partOrder[indexSnapshot % 6];
               if (this.shouldTogglePart(sequencePart)) {
                  this.togglePart(sequencePart);
                  int nextIndex = this.sequenceIndex + 1;
                  this.sequenceIndex = nextIndex % 6;
               }
               break;
            case RANDOM:
               PlayerModelPart[] allParts = PlayerModelPart.values();
               ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
               int randomBound = this.modelPartCount;
               PlayerModelPart randomPart = allParts[randomSnapshot.nextInt(randomBound)];
               if (this.shouldTogglePart(randomPart)) {
                  this.togglePart(randomPart);
               }
         }

         if (this.optionsDirty) {
            Options options = ClientAccess.argusclientMinecraft.options;
            options.broadcastOptions();
            this.optionsDirty = false;
         }

         timerSnapshot = this.flickerTimer;
         timerSnapshot.reset();
      }
   }

   private void togglePart(PlayerModelPart part) {
      this.optionsDirty = true;
      Options writeOptionsSnapshot = ClientAccess.argusclientMinecraft.options;
      Options readOptions = ClientAccess.argusclientMinecraft.options;
      boolean enabled = !readOptions.isModelPartEnabled(part);
      writeOptionsSnapshot.setModelPart(part, enabled);
   }

   private boolean shouldTogglePart(PlayerModelPart part) {
      Setting<Boolean> capeSettingSnapshot = this.capeSetting;
      Boolean includeCape = capeSettingSnapshot.getValue();
      return includeCape || part != PlayerModelPart.CAPE;
   }

   public SkinFlickerModule() {
      super("SkinFlicker", "Flicks your skin parts.", ModuleCategory.MISC, "skinblinker");
   }

   static {
      PlayerModelPart[] horizontalParts = new PlayerModelPart[]{
         PlayerModelPart.LEFT_SLEEVE,
         PlayerModelPart.JACKET,
         PlayerModelPart.HAT,
         PlayerModelPart.LEFT_PANTS_LEG,
         PlayerModelPart.RIGHT_PANTS_LEG,
         PlayerModelPart.RIGHT_SLEEVE
      };
      HORIZONTAL_PART_ORDER = horizontalParts;
      PlayerModelPart[] verticalParts = new PlayerModelPart[]{
         PlayerModelPart.HAT,
         PlayerModelPart.JACKET,
         PlayerModelPart.LEFT_SLEEVE,
         PlayerModelPart.RIGHT_SLEEVE,
         PlayerModelPart.LEFT_PANTS_LEG,
         PlayerModelPart.RIGHT_PANTS_LEG
      };
      VERTICAL_PART_ORDER = verticalParts;
   }

   public enum ModeChoice implements NamedEntry {
      FULL("Full"),
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal"),
      RANDOM("Random");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
