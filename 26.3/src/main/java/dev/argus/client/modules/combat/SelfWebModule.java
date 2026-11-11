package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SelfWebModule extends Module {
   private final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", true));
   private final Setting<Boolean> autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
   private final Setting<Boolean> onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false));
   final Setting<Boolean> smartSetting = this.add(new BooleanSetting("Smart", false).markVisibilityDependency());
   protected final Setting<Float> enemyRangeSetting;
   protected final Setting<Boolean> aggressiveSetting;

   private Entity findNearestEnemy(Setting<Float> rangeSetting) {
      Stream<Entity> enemyStream = StreamSupport.stream(ClientAccess.argusclientMinecraft.level.entitiesForRendering().spliterator(), false)
         .filter(candidate -> {
            if (candidate == ClientAccess.argusclientMinecraft.player) {
               return false;
            } else {
               float distanceToEnemy = ClientAccess.argusclientMinecraft.player.distanceTo(candidate);
               Float enemyRange = rangeSetting.getValue();
               if (!(distanceToEnemy <= enemyRange)) {
                  return false;
               } else if (this.meetsHeightPolicy(candidate) && candidate instanceof Player) {
                  SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
                  String candidateName = candidate.getName().getString();
                  return !friendManagerSnapshot.isFriendName(candidateName) && candidate.isAlive();
               } else {
                  return false;
               }
            }
         });
      LocalPlayer comparatorPlayerSnapshot = ClientAccess.argusclientMinecraft.player;
      Optional<Entity> nearestEnemySnapshot = enemyStream.min(Comparator.comparingDouble(comparatorPlayerSnapshot::distanceToSqr));
      return nearestEnemySnapshot.orElse(null);
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      Setting<Float> rangeSettingSnapshot = this.enemyRangeSetting;
      Entity nearestEnemy = this.findNearestEnemy(rangeSettingSnapshot);
      if (!this.smartSetting.getValue() || nearestEnemy != null) {
         this.placeWeb(event);
      }
   }

   private boolean meetsHeightPolicy(Entity candidate) {
      if (this.aggressiveSetting.getValue()) {
         double inclusiveCandidateY = candidate.getY();
         LocalPlayer inclusivePlayer = ClientAccess.argusclientMinecraft.player;
         return inclusiveCandidateY >= inclusivePlayer.getY();
      } else {
         double strictCandidateY = candidate.getY();
         LocalPlayer strictPlayer = ClientAccess.argusclientMinecraft.player;
         return strictCandidateY > strictPlayer.getY();
      }
   }

   private void placeWeb(RotationRequestEvent event) {
      if (!ClientAccess.argusclientMinecraft.player.isPassenger()) {
         if (ClientContext.holeManager.isInHole() || !this.onlyHoleSetting.getValue()) {
            BlockPos feetPosition = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position());
            BlockState feetState = ClientAccess.argusclientMinecraft.level.getBlockState(feetPosition);
            if (feetState.canBeReplaced()) {
               if (!feetState.liquid()) {
                  if (ClientAccess.argusclientMinecraft.player.onGround()) {
                     Item webItemSnapshot = Items.COBWEB;
                     int webSlot = InventoryUtils.findHotbarSlot(webItemSnapshot);
                     int previousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
                     if (webSlot == -1) {
                        String moduleName = this.getName();
                        MutableComponent messageBaseSnapshot = Component.literal(moduleName);
                        MutableComponent outOfBlocksMessage = messageBaseSnapshot.append(" is out of blocks!");
                        ChatUtils.printChat(outOfBlocksMessage, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH);
                        this.disable();
                     } else {
                        Direction placementDirection = BlockInteractionUtils.findSupportDirection(feetPosition);
                        if (placementDirection != null) {
                           InventoryUtils.selectHotbarSlotForPlacement(webSlot);
                           AABB previousBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
                           LocalPlayer clearBoundsPlayerSnapshot = ClientAccess.argusclientMinecraft.player;
                           AABB emptyBounds = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                           clearBoundsPlayerSnapshot.setBoundingBox(emptyBounds);
                           InteractionHand placementHand = InteractionHand.MAIN_HAND;
                           boolean placementFlag = false;
                           Direction placementFace = placementDirection;
                           BlockInteractionUtils.placeBlockIgnoringCrystals(feetPosition, placementFace, placementFlag, placementHand);
                           AABB restoredBounds = previousBounds;
                           ClientAccess.argusclientMinecraft.player.setBoundingBox(restoredBounds);
                           InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
                           if (this.rotateSetting.getValue()) {
                              RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
                              float[] rotations = new float[]{event.getPlayerYaw(), 90.0F};
                              rotationManagerSnapshot.requestRotation(rotations, 5);
                           }

                           if (this.autoDisableSetting.getValue()) {
                              this.disable();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public SelfWebModule() {
      super("SelfWeb", "Places a cobweb at your feet.", ModuleCategory.COMBAT);
      Predicate enemyRangeVisibility = unused -> this.smartSetting.isExpanded();
      this.enemyRangeSetting = this.add(new NumberSetting<>("EnemyRange", 3.0F, 1.0F, 6.0F, enemyRangeVisibility).withUnit("m"));
      this.aggressiveSetting = this.add(new BooleanSetting("Aggressive", false).dependsOn(this.smartSetting));
   }
}
