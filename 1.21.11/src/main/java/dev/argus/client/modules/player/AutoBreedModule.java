package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AutoBreedModule extends Module {
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F).withUnit("m"));
   private final Setting<Integer> frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
   private final Setting<Float> delaySetting = this.add(new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F).withUnit("s"));
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> dogsSetting;
   private final Setting<Boolean> catsSetting;
   private final Setting<Boolean> parrotsSetting;
   private final Setting<Boolean> donkeysSetting;
   private final Setting<Boolean> horsesSetting;
   private final Setting<Boolean> allaysSetting;
   private final Setting<Boolean> othersSetting;
   private final ElapsedTimer interactionTimer;
   private final List<AutoBreedModule.BreedingCooldown> breedingCooldowns;

   @Override
   public void onEnable() {
      this.breedingCooldowns.clear();
   }

   private static Predicate<ItemStack> getBreedingFoodPredicate(Entity entity) {
      if (entity instanceof Allay) {
         return stack -> {
            Item foodItem = Items.AMETHYST_SHARD;
            return stack.is(foodItem);
         };
      } else if (entity instanceof Parrot) {
         return stack -> List.of(Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS).contains(stack.getItem());
      } else {
         return entity instanceof Animal animal ? animal::isFood : stack -> false;
      }
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      this.breedingCooldowns.forEach(cooldown -> cooldown.elapsedTicks++);
      this.breedingCooldowns.removeIf(cooldownEntry -> {
         int elapsedTicks = cooldownEntry.elapsedTicks;
         return elapsedTicks >= 6000;
      });
      ElapsedTimer delayTimer = this.interactionTimer;
      double delaySecondsValue = this.delaySetting.getValue().floatValue();
      TimeUnit delayUnit = TimeUnit.SECONDS;
      if (delayTimer.hasElapsed(delaySecondsValue, delayUnit)) {
         int interactions = 0;
         ClientLevel level = ClientAccess.argusclientMinecraft.level;

         label48:
         for (Object cooldowns : level.entitiesForRendering()) {
            if (cooldowns instanceof LivingEntity livingEntity) {
               for (AutoBreedModule.BreedingCooldown cooldown : this.breedingCooldowns) {
                  if (livingEntity == cooldown.entity) {
                     continue label48;
                  }
               }

               if (this.isTargetEnabled(livingEntity)) {
                  LocalPlayer player = ClientAccess.argusclientMinecraft.player;
                  float distance = livingEntity.distanceTo(player);
                  Float rangeValue = this.rangeSetting.getValue();
                  if (distance <= rangeValue) {
                     Predicate<ItemStack> foodPredicate = getBreedingFoodPredicate(livingEntity);
                     int foodSlot = InventoryUtils.findHotbarSlot(foodPredicate);
                     if (foodSlot == -1) {
                        return;
                     }

                     Vec3 targetCenter = livingEntity.getBoundingBox().getCenter();
                     if (targetCenter != null) {
                        InventoryUtils.selectHotbarSlotForPlacement(foodSlot);
                        InteractionHand hand = InteractionHand.MAIN_HAND;
                        BlockInteractionUtils.interactWithEntity(livingEntity, hand);
                        this.breedingCooldowns.add(new AutoBreedModule.BreedingCooldown(livingEntity));
                        interactions++;
                        if (this.rotateSetting.getValue()) {
                           RotationManager rotationManagerForRequest = ClientContext.rotationManager;
                           float[] rotations = RotationManager.getRotationsToPosition(targetCenter);
                           rotationManagerForRequest.requestRotation(rotations, 5);
                        }

                        Integer frequencyValue = this.frequencySetting.getValue();
                        if (interactions >= frequencyValue) {
                           break;
                        }
                     }
                  }
               }
            }
         }

         delayTimer = this.interactionTimer;
         delayTimer.reset();
      }
   }

   private boolean isTargetEnabled(LivingEntity entity) {
      if (entity.isBaby()) {
         return false;
      }

      return switch (entity) {
         case Wolf wolf -> this.dogsSetting.getValue();
         case Cat cat -> this.catsSetting.getValue();
         case Parrot parrot -> this.parrotsSetting.getValue();
         case Donkey donkey -> this.donkeysSetting.getValue();
         case Horse horse -> this.horsesSetting.getValue();
         case Allay allay -> this.allaysSetting.getValue();
         case Animal animal -> this.othersSetting.getValue();
         default -> false;
      };
   }

   public AutoBreedModule() {
      super("AutoBreed", "Breeds animals in between each other automatically.", ModuleCategory.PLAYER);
      Boolean rotateDefault = true;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", rotateDefault));
      Boolean targetsDefault = false;
      BooleanSetting targetsOption = new BooleanSetting("Targets", targetsDefault);
      targetsOption.markAsGroup();
      targetsOption.markVisibilityDependency();
      this.targetsSetting = this.add(targetsOption);
      Boolean dogsDefault = true;
      BooleanSetting dogsOption = new BooleanSetting("Dogs", dogsDefault);
      this.dogsSetting = this.add(dogsOption.dependsOn(this.targetsSetting));
      Boolean catsDefault = true;
      BooleanSetting catsOption = new BooleanSetting("Cats", catsDefault);
      this.catsSetting = this.add(catsOption.dependsOn(this.targetsSetting));
      Boolean parrotsDefault = true;
      BooleanSetting parrotsOption = new BooleanSetting("Parrots", parrotsDefault);
      this.parrotsSetting = this.add(parrotsOption.dependsOn(this.targetsSetting));
      Boolean donkeysDefault = true;
      BooleanSetting donkeysOption = new BooleanSetting("Donkeys", donkeysDefault);
      this.donkeysSetting = this.add(donkeysOption.dependsOn(this.targetsSetting));
      Boolean horsesDefault = true;
      BooleanSetting horsesOption = new BooleanSetting("Horses", horsesDefault);
      this.horsesSetting = this.add(horsesOption.dependsOn(this.targetsSetting));
      Boolean allaysDefault = false;
      BooleanSetting allaysOption = new BooleanSetting("Allays", allaysDefault);
      this.allaysSetting = this.add(allaysOption.dependsOn(this.targetsSetting));
      Boolean othersDefault = true;
      BooleanSetting othersOption = new BooleanSetting("Others", othersDefault);
      this.othersSetting = this.add(othersOption.dependsOn(this.targetsSetting));
      this.interactionTimer = new ElapsedTimer();
      this.breedingCooldowns = new ArrayList<>();
   }

   private static final class BreedingCooldown {
      private final LivingEntity entity;
      private int elapsedTicks;

      private BreedingCooldown(LivingEntity entity) {
         this.entity = entity;
      }
   }
}
