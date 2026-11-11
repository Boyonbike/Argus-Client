package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.TradeRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.TradeOfferSnapshot;
import dev.argus.client.utils.network.InteractPacketUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.mixin.MerchantScreenAccessor;
import dev.argus.mixin.ServerboundInteractPacketAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoTradeModule extends Module {
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 150, 25, 1500).withUnit("ms"));
   private final Setting<Integer> frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 6));
   private final Setting<Boolean> closeSetting;
   private final Setting<Boolean> dropSetting;
   private final Setting<Boolean> autoOpenSetting;
   final Setting<Boolean> onSightSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> rotateSetting;
   private final VillagerOfferCache offerCache;
   private final ElapsedTimer tradeDelayTimer;
   private final ElapsedTimer closeQuietTimer;
   private final ElapsedTimer openTimer;
   private final ElapsedTimer cacheRefreshTimer;
   private boolean tradedInMenu;
   private boolean resetOpenTimer;
   private int villagerId;

   private Entity findVillagerToOpen() {
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera();
      Entity selectedVillager = null;
      float smallestYawDifference = 360.0F;
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof Villager) {
            Vec3 villagerPosition = entity.position();
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            Vec3 eyePosition = player.getEyePosition();
            double openRange = this.rangeSetting.getValue().floatValue();
            if (villagerPosition.closerThan(eyePosition, openRange)) {
               int candidateId = entity.getId();
               if (this.shouldOpenVillager(candidateId)) {
                  if (this.onSightSetting.getValue()) {
                     if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult hitResult && hitResult.getEntity() == entity) {
                        return entity;
                     }
                  } else {
                     float villagerYaw = RotationManager.getRotationsToEntity(entity)[0];
                     float cameraYaw = camera.yRot();
                     float yawDifference = Mth.degreesDifferenceAbs(villagerYaw, cameraYaw);
                     if (yawDifference < smallestYawDifference) {
                        smallestYawDifference = yawDifference;
                        selectedVillager = entity;
                     }
                  }
               }
            }
         }
      }

      return selectedVillager;
   }

   private AbstractVillager getCurrentVillager() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      int currentVillagerId = this.villagerId;
      return level.getEntity(currentVillagerId) instanceof AbstractVillager villager ? villager : null;
   }

   private boolean isTradeExperienceOrb(Entity entity) {
      if (entity.getType() == EntityType.EXPERIENCE_ORB && this.villagerId != -1) {
         AABB orbBounds = entity.getBoundingBox();
         Vec3 position = entity.position();
         return orbBounds.contains(position);
      } else {
         return false;
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundInteractPacket interactPacket
         && InteractPacketUtils.getEntity(interactPacket) instanceof Villager
         && InteractPacketUtils.getAction(interactPacket) == InteractPacketUtils.Action.INTERACT) {
         this.villagerId = ((ServerboundInteractPacketAccessor)interactPacket).getEntityId();
      }
   }

   private void tradeOnce(MerchantMenu menu) {
      AbstractVillager villager = this.getCurrentVillager();
      if (villager != null) {
         VillagerOfferCache cacheReceiver = this.offerCache;
         int queriedVillagerId = villager.getId();
         List<TradeOfferSnapshot> cachedOffers = cacheReceiver.getOffers(queriedVillagerId);
         if (cachedOffers != null && !cachedOffers.isEmpty()) {
            HashMap<TradeItemKey, Integer> offerIndices = new HashMap<>();
            int offerIndex = 0;

            for (MerchantOffer menuOffer : menu.getOffers()) {
               TradeItemKey offerKey = TradeItemKey.fromOffer(menuOffer);
               offerIndices.put(offerKey, offerIndex);
               offerIndex++;
            }

            for (TradeOfferSnapshot cachedOffer : cachedOffers) {
               if (cachedOffer != TradeOfferSnapshot.OUT_OF_STOCK) {
                  TradeRegistry trades = ClientContext.tradeRegistry;
                  if (trades.matches(cachedOffer) && this.canAffordOffer(cachedOffer)) {
                     int selectedIndex = offerIndices.get(TradeItemKey.fromCachedOffer(cachedOffer));
                     this.selectTrade(selectedIndex);
                     ItemStack outputStack = menu.getSlot(2).getItem();
                     ItemStack expectedResult = cachedOffer.result();
                     if (ItemStack.isSameItem(outputStack, expectedResult)) {
                        int containerId = menu.containerId;
                        this.collectTradeResult(containerId);
                     }

                     this.tradedInMenu = true;
                     this.tradeDelayTimer.reset();
                     break;
                  }
               }
            }

            offerIndices.clear();
         }
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      if (!(ClientAccess.argusclientMinecraft.player.containerMenu instanceof MerchantMenu menu)) {
         this.tradedInMenu = false;
      } else {
         ElapsedTimer timer = this.cacheRefreshTimer;
         if (timer.hasElapsedAndReset(500L)
            && ClientAccess.argusclientMinecraft.player.containerMenu instanceof MerchantMenu refreshMenu
            && this.villagerId != -1) {
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            int currentVillagerId = this.villagerId;
            if (level.getEntity(currentVillagerId) instanceof AbstractVillager villager) {
               VillagerOfferCache cache = this.offerCache;
               MerchantOffers refreshedOffers = refreshMenu.getOffers();
               cache.updateOffers(villager, refreshedOffers);
            }
         }

         timer = this.tradeDelayTimer;
         long requiredDelayMillis = this.delaySetting.getValue().intValue();
         if (timer.hasElapsed(requiredDelayMillis)) {
            this.resetOpenTimer = true;
            int tradeAttempt = 0;

            while (true) {
               Integer frequency = this.frequencySetting.getValue();
               if (tradeAttempt >= frequency) {
                  if (this.tradedInMenu) {
                     tradeAttempt = 150;
                  } else {
                     tradeAttempt = 500;
                  }

                  long closeDelayMillis = tradeAttempt;
                  if (this.closeQuietTimer.hasElapsed(closeDelayMillis) && this.closeSetting.getValue()) {
                     Screen screen = ClientAccess.argusclientMinecraft.screen;
                     screen.onClose();
                  }

                  return;
               }

               this.tradeOnce(menu);
               tradeAttempt++;
            }
         }
      }
   }

   @EventHandler
   private void onEntityAdded(ClientLevelAddEntityEvent event) {
      Entity addedEntity = event.getEntity();
      if (this.isTradeExperienceOrb(addedEntity)) {
         this.closeQuietTimer.reset();
      }
   }

   private boolean shouldOpenVillager(int entityId) {
      long cacheAgeMillis = System.currentTimeMillis();
      VillagerOfferCache villagerCache = this.offerCache;
      cacheAgeMillis -= villagerCache.getLastUpdatedMillis(entityId);
      if (cacheAgeMillis > 30000L) {
         return true;
      }

      List<TradeOfferSnapshot> offers = this.offerCache.getOffers(entityId);
      if (offers != null && !offers.isEmpty()) {
         for (TradeOfferSnapshot offer : offers) {
            if (offer != TradeOfferSnapshot.OUT_OF_STOCK && ClientContext.tradeRegistry.matches(offer) && this.canAffordOffer(offer)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private void collectTradeResult(int containerId) {
      if (this.dropSetting.getValue()) {
         MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer dropPlayer = ClientAccess.argusclientMinecraft.player;
         ContainerInput dropClick = ContainerInput.THROW;
         byte dropButton = 0;
         gameMode.handleInventoryMouseClick(containerId, 2, dropButton, dropClick, dropPlayer);
      } else {
         MultiPlayerGameMode quickMoveGameMode = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer movePlayer = ClientAccess.argusclientMinecraft.player;
         ContainerInput moveClick = ContainerInput.QUICK_MOVE;
         byte moveButton = 0;
         quickMoveGameMode.handleInventoryMouseClick(containerId, 2, moveButton, moveClick, movePlayer);
      }
   }

   private void selectTrade(int offerIndex) {
      if (ClientAccess.argusclientMinecraft.screen instanceof MerchantScreen screen) {
         ((MerchantScreenAccessor)screen).argusclient$setSelected(offerIndex);
         ((MerchantScreenAccessor)screen).argusclient$syncRecipe();
      } else {
         ServerboundSelectTradePacket selectedTrade = new ServerboundSelectTradePacket(offerIndex);
         PacketSender.send(selectedTrade);
      }
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      Boolean autoOpen = this.autoOpenSetting.getValue();
      if (autoOpen) {
         if (ClientAccess.argusclientMinecraft.screen == null) {
            if (this.resetOpenTimer) {
               ElapsedTimer timer = this.openTimer;
               timer.reset();
               this.resetOpenTimer = false;
            } else {
               Entity villager = this.findVillagerToOpen();
               if (villager != null) {
                  ElapsedTimer openDelayTimer = this.openTimer;
                  if (openDelayTimer.hasElapsedAndReset(500L)) {
                     InteractionHand hand = InteractionHand.MAIN_HAND;
                     BlockInteractionUtils.interactWithEntity(villager, hand);
                     Boolean rotate = this.rotateSetting.getValue();
                     if (rotate) {
                        if (!this.onSightSetting.getValue()) {
                           float[] rotations = RotationManager.getRotationsToEntity(villager);
                           ClientContext.rotationManager.requestRotation(rotations, 100);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean canAffordOffer(TradeOfferSnapshot offer) {
      int firstCostCount = 0;
      int secondCostCount = 0;

      for (Slot slot : ClientAccess.argusclientMinecraft.player.containerMenu.slots) {
         ItemStack stack = slot.getItem();
         ItemStack firstCostStack = offer.firstCost();
         Item inventoryItem = stack.getItem();
         if (firstCostStack.is(inventoryItem)) {
            firstCostCount += stack.getCount();
         }

         Optional optionalSecondCost = offer.secondCost();
         if (!optionalSecondCost.isEmpty()) {
            ItemCost secondCost = (ItemCost)offer.secondCost().get();
            if (secondCost.test(stack)) {
               secondCostCount += secondCost.count();
            }
         }
      }

      ItemStack requiredFirstCost = offer.firstCost();
      if (firstCostCount < requiredFirstCost.getCount()) {
         return false;
      }

      Optional requiredSecondCost = offer.secondCost();
      if (!requiredSecondCost.isEmpty()) {
         ItemStack requiredCostStack = ((ItemCost)offer.secondCost().get()).itemStack();
         if (secondCostCount < requiredCostStack.getCount()) {
            return false;
         }
      }

      return true;
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundMerchantOffersPacket offersPacket) {
         VillagerOfferCache cache = this.offerCache;
         AbstractVillager villager = this.getCurrentVillager();
         MerchantOffers packetOffers = offersPacket.getOffers();
         cache.updateOffers(villager, packetOffers);
         this.closeQuietTimer.reset();
      }
   }

   public AutoTradeModule() {
      super(
         "AutoTrade",
         "Trades with villagers for you.\n" + "%sUse ;trade command to access the trade gui".formatted(ChatFormatting.YELLOW),
         ModuleCategory.PLAYER
      );
      Boolean closeDefault = false;
      this.closeSetting = this.add(new BooleanSetting("Close", closeDefault));
      Boolean dropDefault = false;
      this.dropSetting = this.add(new BooleanSetting("Drop", dropDefault));
      Boolean autoOpenDefault = false;
      BooleanSetting autoOpenOption = new BooleanSetting("AutoOpen", autoOpenDefault);
      autoOpenOption.markVisibilityDependency();
      this.autoOpenSetting = this.add(autoOpenOption);
      Boolean sightDefault = false;
      BooleanSetting sightOption = new BooleanSetting("OnSight", sightDefault);
      this.onSightSetting = this.add(sightOption.dependsOn(this.autoOpenSetting));
      Predicate rangeVisibility = ignoredValue -> {
         Setting<Boolean> onSight = this.onSightSetting;
         return !onSight.getValue();
      };
      NumberSetting<Float> rangeOption = new NumberSetting<>("Range", 3.0F, 0.1F, 6.0F, rangeVisibility);
      rangeOption.withUnit("m");
      this.rangeSetting = this.add(rangeOption.dependsOn(this.autoOpenSetting));
      Predicate rotateVisibility = ignoredValue -> {
         Setting<Boolean> onSight = this.onSightSetting;
         return !onSight.getValue();
      };
      Boolean rotateDefault = true;
      BooleanSetting rotateOption = new BooleanSetting("Rotate", rotateDefault, rotateVisibility);
      this.rotateSetting = this.add(rotateOption.dependsOn(this.autoOpenSetting));
      this.offerCache = new VillagerOfferCache();
      this.tradeDelayTimer = new ElapsedTimer();
      this.closeQuietTimer = new ElapsedTimer();
      this.openTimer = new ElapsedTimer();
      this.cacheRefreshTimer = new ElapsedTimer();
      this.villagerId = -1;
   }
}
