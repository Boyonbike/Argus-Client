package dev.argus.client.modules.combat;

import com.mojang.blaze3d.platform.InputConstants.Key;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.mixin.MinecraftAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class AutoClickerModule extends Module {
   private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
   private final Setting<AutoClickerModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", AutoClickerModule.ModeChoice.LEFT));
   private final Setting<Float> cpsSetting = this.add(new NumberSetting<>("CPS", 8.0F, 1.0F, 20.0F));
   private final Setting<Float> delaySetting = this.add(
      new NumberSetting<>("Delay", 1.0F, 1.0F, 3.0F).withUnit("x").setBoundaryLabel("Off", SettingLabelCondition.MIN)
   );
   private final Setting<Boolean> friendProtectSetting = this.add(new BooleanSetting("FriendProtect", true));
   private final ElapsedTimer clickTimer = new ElapsedTimer();
   private float delayMultiplier = 1.0F;
   private final List<Long> recentClickTimes = new ArrayList<>();

   @Override
   public String getInfo() {
      int recentClickCount = this.recentClickTimes.size();
      return String.valueOf(recentClickCount);
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.BeforeBufferSource event) {
      this.recentClickTimes.removeIf(timestamp -> System.currentTimeMillis() - timestamp >= 1000L);
      float clicksPerSecond = this.cpsSetting.getValue();
      long clickIntervalMillis = (long)(1000.0F / clicksPerSecond);
      if (!this.delaySetting.isAtMinimum()) {
         clickIntervalMillis *= (long)this.delayMultiplier;
      }

      if (this.clickTimer.hasElapsed(clickIntervalMillis)) {
         this.performClick();
         this.recentClickTimes.add(System.currentTimeMillis());
         this.clickTimer.reset();
         float upperBound = this.delaySetting.getValue();
         this.delayMultiplier = MathUtils.randomFloat(1.0F, upperBound);
      }
   }

   private void performClick() {
      if (!multiTaskModule.isToggled() || !ClientAccess.argusclientMinecraft.player.isUsingItem() && ClientAccess.argusclientMinecraft.gui.screen() == null) {
         Key key = this.modeSetting.getValue().getKeyMapping().key;
         KeyMapping.click(key);
      } else {
         this.modeSetting.getValue().clickDirectly();
      }
   }

   public boolean shouldProtectFriend(Entity target) {
      if (this.isToggled() && this.friendProtectSetting.getValue() && target instanceof Player playerTarget) {
         Player friendTarget = playerTarget;
         if (ClientContext.socialRegistry.isFriendPlayer(friendTarget)) {
            return true;
         }
      }

      return false;
   }

   public static void bindModules(ModuleRegistry registry) {
      multiTaskModule = (MultiTaskModule)registry.getModule(MultiTaskModule.class);
   }

   public AutoClickerModule() {
      super("AutoClicker", "Spam mouse buttons.", ModuleCategory.COMBAT);
   }

   public enum ModeChoice implements NamedEntry {
      LEFT("Left"),
      RIGHT("Right");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public KeyMapping getKeyMapping() {
         return this == LEFT ? ClientAccess.argusclientMinecraft.options.keyAttack : ClientAccess.argusclientMinecraft.options.keyUse;
      }

      public void clickDirectly() {
         if (this == LEFT) {
            ((MinecraftAccessor)ClientAccess.argusclientMinecraft).attack();
         } else {
            ((MinecraftAccessor)ClientAccess.argusclientMinecraft).interact();
         }
      }
   }
}
