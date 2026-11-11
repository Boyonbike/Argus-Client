package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.mixin.MinecraftAccessor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public class TriggerModule extends Module {
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 0.0F, 5.0F).withUnit("m"));
   private final Setting<Boolean> onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
   private final Setting<Double> delaySetting = this.add(new NumberSetting<>("Delay", 1.0, 0.0, 5.0).withUnit("s"));
   private final Setting<Integer> missChanceSetting = this.add(new NumberSetting<>("MissChance", 10, 0, 50).withUnit("%"));
   final Setting<Boolean> targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency());
   final Setting<Boolean> playersSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> crystalsSetting;
   private final ElapsedTimer attackTimer;

   public TriggerModule() {
      super("Trigger", "Attacks entities under your crosshair.", ModuleCategory.COMBAT);
      String playersName = "Players";
      TriggerPlayersVisibilityPredicate playersVisibility = new TriggerPlayersVisibilityPredicate(this);
      this.playersSetting = this.add(new BooleanSetting(playersName, true, playersVisibility).markVisibilityDependency());
      String ignoreNakedsName = "IgnoreNakeds";
      TriggerIgnoreNakedsVisibilityPredicate ignoreNakedsVisibility = new TriggerIgnoreNakedsVisibilityPredicate(this);
      this.ignoreNakedsSetting = this.add(new BooleanSetting(ignoreNakedsName, false, ignoreNakedsVisibility));
      String animalsName = "Animals";
      TriggerAnimalsVisibilityPredicate animalsVisibility = new TriggerAnimalsVisibilityPredicate(this);
      this.animalsSetting = this.add(new BooleanSetting(animalsName, false, animalsVisibility));
      String hostilesName = "Hostiles";
      TriggerHostilesVisibilityPredicate hostilesVisibility = new TriggerHostilesVisibilityPredicate(this);
      this.hostilesSetting = this.add(new BooleanSetting(hostilesName, true, hostilesVisibility));
      String crystalsName = "Crystals";
      TriggerCrystalsVisibilityPredicate crystalsVisibility = new TriggerCrystalsVisibilityPredicate(this);
      this.crystalsSetting = this.add(new BooleanSetting(crystalsName, false, crystalsVisibility));
      this.attackTimer = new ElapsedTimer();
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() != EventStage.Post) {
         if (!this.isWorldUnavailable()) {
            if (!(ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem() instanceof AxeItem)) {
               ItemStack componentStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
               DataComponentType<?> weaponComponent = DataComponents.WEAPON;
               if (!componentStack.has(weaponComponent) && this.onlyWeaponSetting.getValue()) {
                  return;
               }
            }

            if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult) {
               Entity target = ((EntityHitResult)ClientAccess.argusclientMinecraft.hitResult).getEntity();
               boolean eligiblePlayer = false;
               if (target instanceof Player) {
                  SocialRegistry socialRegistry = ClientContext.socialRegistry;
                  Component targetName = target.getName();
                  String playerName = targetName.getString();
                  if (!socialRegistry.isFriendName(playerName) && this.playersSetting.getValue()) {
                     eligiblePlayer = true;
                  }
               }

               if (eligiblePlayer && this.ignoreNakedsSetting.getValue() && !PlayerStateUtils.hasArmor((Player)target)) {
                  eligiblePlayer = false;
               }

               if (target.isAlive()) {
                  if (!eligiblePlayer
                     && (!(target instanceof AgeableMob) || !this.animalsSetting.getValue())
                     && (!(target instanceof Enemy) || !this.hostilesSetting.getValue())) {
                     if (!(target instanceof EndCrystal)) {
                        return;
                     }

                     if (!this.crystalsSetting.getValue()) {
                        return;
                     }
                  }

                  float attackStrength = ClientAccess.argusclientMinecraft.player.getAttackStrengthScale(0.5F);
                  if (attackStrength >= 1.0F) {
                     ElapsedTimer delayTimer = this.attackTimer;
                     double delaySeconds = this.delaySetting.getValue();
                     double randomizedDelay = delaySeconds * Math.random();
                     TimeUnit delayUnit = TimeUnit.SECONDS;
                     if (delayTimer.hasElapsed(randomizedDelay, delayUnit)) {
                        float targetDistance = ClientAccess.argusclientMinecraft.player.distanceTo(target);
                        Float rangeValue = this.rangeSetting.getValue();
                        if (targetDistance <= rangeValue) {
                           int missThreshold = this.missChanceSetting.getValue();
                           boolean miss = MathUtils.passesRandomThreshold(missThreshold);
                           this.attackOrMiss(miss);
                           this.attackTimer.reset();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void attackOrMiss(boolean miss) {
      if (miss) {
         LocalPlayer swingPlayer = ClientAccess.argusclientMinecraft.player;
         InteractionHand hand = InteractionHand.MAIN_HAND;
         swingPlayer.swing(hand);
         ClientAccess.argusclientMinecraft.player.resetOnlyAttackStrengthTicker();
      } else {
         ((MinecraftAccessor)ClientAccess.argusclientMinecraft).attack();
      }
   }
}
