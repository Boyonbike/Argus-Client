package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.IllegalDisconnectModule;
import dev.argus.client.modules.misc.AutoReconnectModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.Waypoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoLogModule extends Module {
   private static IllegalDisconnectModule illegalDisconnect = ClientContext.getModule(IllegalDisconnectModule.class);
   private static AutoReconnectModule autoReconnect = ClientContext.getModule(AutoReconnectModule.class);
   private final Setting<Integer> healthSetting = this.add(new NumberSetting<>("Health", 12, 1, 20).withUnit("hp"));
   private final Setting<Float> invincTimeoutSetting = this.add(new NumberSetting<>("InvincTimeout", 0.0F, 0.0F, 4.0F).withUnit("s"));
   private final Setting<Boolean> reconnectSetting = this.add(new BooleanSetting("Reconnect", false));
   private final Setting<Boolean> autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
   private final Setting<Boolean> gearedOnlySetting = this.add(new BooleanSetting("GearedOnly", false));
   private final Setting<Boolean> unfocusedOnlySetting = this.add(new BooleanSetting("UnfocusedOnly", false));
   private final Setting<Boolean> onRenderSetting = this.add(new BooleanSetting("OnRender", false));
   private final Setting<Boolean> crystalSetting = this.add(new BooleanSetting("Crystal", false));
   private final Setting<Boolean> tNTMinecartSetting = this.add(new BooleanSetting("TNTMinecart", false));
   final Setting<Boolean> totemsSetting = this.add(new BooleanSetting("Totems", false).markVisibilityDependency());
   private final Setting<Integer> totemCountSetting;
   private final Setting<Boolean> elytraSetting;
   private final Setting<Integer> durabilitySetting;
   private final Setting<Boolean> assumeInventorySetting;
   public final Setting<Boolean> yLevelSetting;
   private final Setting<Float> ySetting;
   private final Setting<Boolean> waypointsSetting;
   private final Setting<Float> distanceSetting;
   private Connection pendingDisconnect;
   private final ElapsedTimer disconnectTimer;
   private long disconnectTimeMillis;

   private void requestDisconnect(String reason) {
      if (this.autoDisableSetting.getValue()) {
         this.disable();
      }

      ClientPacketListener packetListener = ClientAccess.argusclientMinecraft.player.connection;
      Connection connection = packetListener.getConnection();
      if (connection != null && connection != this.pendingDisconnect) {
         this.disconnectTimeMillis = System.currentTimeMillis();
         if (!this.reconnectSetting.getValue()) {
            ClientContext.serverStateManager.autoLogged = true;
            autoReconnect.disable();
         }

         Setting<Boolean> ignoreProxySettingSource = illegalDisconnect.ignoreProxySetting;
         boolean allowIllegalDisconnect = !ignoreProxySettingSource.getValue() || !IllegalDisconnectModule.isIgnoredProxy();
         if (illegalDisconnect.isToggled() && allowIllegalDisconnect) {
            this.pendingDisconnect = connection;
            this.disconnectTimer.reset();
            Component fallbackReason = this.buildDisconnectReason(reason);
            ClientContext.taskScheduler.scheduleRunTickTask(() -> this.pollPendingDisconnect(connection, fallbackReason));
            IllegalDisconnectModule.sendInvalidChatPacket();
            return;
         }

         connection.disconnect(this.buildDisconnectReason(reason));
      }
   }

   private Component buildDisconnectReason(String reason) {
      SimpleDateFormat formatterSnapshot = new SimpleDateFormat("HH:mm");
      Date disconnectDate = new Date(this.disconnectTimeMillis);
      String formattedTime = formatterSnapshot.format(disconnectDate);
      MutableComponent message = Component.empty();
      MutableComponent componentSnapshot = Component.literal("[AutoLog] ");
      ChatFormatting prefixColor = ChatFormatting.GRAY;
      message.append(componentSnapshot.withStyle(prefixColor));
      componentSnapshot = Component.literal(reason);
      ChatFormatting reasonColor = ChatFormatting.RED;
      message.append(componentSnapshot.withStyle(reasonColor));
      message.append("\n\n");
      String timeText = "Logged out at " + formattedTime;
      componentSnapshot = Component.literal(timeText);
      ChatFormatting timeColor = ChatFormatting.GRAY;
      message.append(componentSnapshot.withStyle(timeColor));
      return message;
   }

   @Override
   public String getInfo() {
      return formatHealthInfo(this);
   }

   private static String formatHealthInfo(AutoLogModule owner) {
      Object[] values = new Object[1];
      Setting<Integer> healthSettingSource = owner.healthSetting;
      values[0] = healthSettingSource.getValue();
      return "%d.0".formatted(values);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable()) {
         if (!ClientAccess.argusclientMinecraft.player.isSpectator()) {
            if (!ClientAccess.argusclientMinecraft.player.hasInfiniteMaterials()) {
               if (!this.gearedOnlySetting.getValue() || PlayerStateUtils.hasArmor(ClientAccess.argusclientMinecraft.player)) {
                  if (this.elytraSetting.getValue() && this.isElytraDurabilityUnsafe()) {
                     this.requestDisconnect("Your elytra durability is too low!");
                  } else {
                     if (this.yLevelSetting.getValue()) {
                        double currentY = ClientAccess.argusclientMinecraft.player.getY();
                        Float heightLimit = this.ySetting.getValue();
                        if (currentY < heightLimit.floatValue()) {
                           Object heightMessageValue = this.ySetting.getValue();
                           this.requestDisconnect("You are below Y level " + heightMessageValue);
                           return;
                        }
                     }

                     List visibleWaypoints = ClientContext.waypointRegistry.getVisible();
                     if (this.waypointsSetting.getValue() && !visibleWaypoints.isEmpty()) {
                        double waypointDistance = ((Waypoint)visibleWaypoints.getFirst()).distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition());
                        Float distanceLimit = this.distanceSetting.getValue();
                        if (waypointDistance <= distanceLimit.floatValue()) {
                           Object[] waypointNameArguments = new Object[1];
                           Waypoint nameWaypoint = (Waypoint)visibleWaypoints.getFirst();
                           waypointNameArguments[0] = nameWaypoint.getName();
                           String waypointMessage = "You are close to %s waypoint!".formatted(waypointNameArguments);
                           this.requestDisconnect(waypointMessage);
                           return;
                        }
                     }

                     float ticksSinceSpawn = ClientAccess.argusclientMinecraft.player.tickCount;
                     float secondsSinceSpawn = ticksSinceSpawn / 20.0F;
                     Float invincibilityTimeout = this.invincTimeoutSetting.getValue();
                     if (!(secondsSinceSpawn < invincibilityTimeout)) {
                        if (this.onRenderSetting.getValue()) {
                           for (Player renderedPlayer : ClientAccess.argusclientMinecraft.level.players()) {
                              if (!(renderedPlayer instanceof FakeplayerCommand.SimulatedPlayer)) {
                                 SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
                                 String renderedPlayerName = renderedPlayer.getName().getString();
                                 if (!friendManagerSnapshot.isFriendName(renderedPlayerName) && renderedPlayer != ClientAccess.argusclientMinecraft.player) {
                                    String renderMessage = renderedPlayer.getName().getString() + " has entered your render distance!";
                                    this.requestDisconnect(renderMessage);
                                 }
                              }
                           }
                        }

                        ClientLevel minecartLevel = ClientAccess.argusclientMinecraft.level;
                        AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
                        Class<MinecartTNT> minecartType = MinecartTNT.class;
                        AABB expandedMinecartBounds = playerBounds.inflate(8.0);
                        Predicate<MinecartTNT> primedMinecart = MinecartTNT::isPrimed;
                        if (!minecartLevel.getEntitiesOfClass(minecartType, expandedMinecartBounds, primedMinecart).isEmpty()
                           && this.tNTMinecartSetting.getValue()) {
                           this.requestDisconnect("You are at risk of getting blown up by a TNT minecart!");
                        }

                        if (this.crystalSetting.getValue()) {
                           ClientAccess.argusclientMinecraft.level.entitiesForRendering().forEach(entity -> {
                              if (entity instanceof EndCrystal) {
                                 Entity distanceCrystal = entity;
                                 double crystalDistanceSquared = ClientAccess.argusclientMinecraft.player.distanceToSqr(distanceCrystal);
                                 if (crystalDistanceSquared <= 128.0) {
                                    Entity positionCrystal = entity;
                                    Vec3 crystalPosition = positionCrystal.position();
                                    LocalPlayer damagePlayer = ClientAccess.argusclientMinecraft.player;
                                    double explosionDamage = DamageUtils.getCrystalDamage(crystalPosition, damagePlayer);
                                    if (explosionDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                                       this.requestDisconnect("You are at risk of getting blown up by an end crystal!");
                                    }
                                 }
                              }
                           });
                        }

                        int health = (int)EntityUtils.getPlayerHealthWithAbsorption();
                        if (!ClientAccess.argusclientMinecraft.player.isDeadOrDying() && health != 0) {
                           if (!ClientAccess.argusclientMinecraft.isWindowActive() || !this.unfocusedOnlySetting.getValue()) {
                              int totemCount = ClientAccess.argusclientMinecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> {
                                 Item totem = Items.TOTEM_OF_UNDYING;
                                 ItemStack totemStack = stack;
                                 return totemStack.is(totem);
                              }).mapToInt(ItemStack::getCount).sum();
                              InventoryMenu inventoryMenu = ClientAccess.argusclientMinecraft.player.inventoryMenu;
                              totemCount += inventoryMenu.getInputGridSlots().stream().filter(this::containsTotem).mapToInt(slot -> {
                                 Slot countSlot = slot;
                                 ItemStack slotStack = countSlot.getItem();
                                 return slotStack.getCount();
                              }).sum();
                              if (ClientAccess.argusclientMinecraft.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                                 ItemStack offhandCountStack = ClientAccess.argusclientMinecraft.player.getOffhandItem();
                                 totemCount += offhandCountStack.getCount();
                              }

                              Integer totemLimit = this.totemCountSetting.getValue();
                              boolean totemConditionMet = totemCount <= totemLimit;
                              if (!this.totemsSetting.getValue()) {
                                 totemConditionMet = true;
                              }

                              Integer healthLimit = this.healthSetting.getValue();
                              if (health <= healthLimit && totemConditionMet) {
                                 String healthMessage = "Your health is too low! (" + health + ").";
                                 this.requestDisconnect(healthMessage);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean containsTotem(Slot slot) {
      ItemStack stackSnapshot = slot.getItem();
      Item totem = Items.TOTEM_OF_UNDYING;
      return stackSnapshot.is(totem);
   }

   private void pollPendingDisconnect(Connection connection, Component reason) {
      if (this.pendingDisconnect != connection) {
         return;
      }

      ClientPacketListener listener = ClientAccess.argusclientMinecraft.getConnection();
      if (!connection.isConnected() || listener == null || listener.getConnection() != connection) {
         this.pendingDisconnect = null;
      } else if (this.disconnectTimer.hasElapsed(1500L)) {
         this.pendingDisconnect = null;
         connection.disconnect(reason);
      } else {
         ClientContext.taskScheduler.scheduleRunTickTask(() -> this.pollPendingDisconnect(connection, reason));
      }
   }

   private boolean isElytraDurabilityUnsafe() {
      Inventory equippedInventorySnapshot = ClientAccess.argusclientMinecraft.player.getInventory();
      EquipmentSlot chestSlotSnapshot = EquipmentSlot.CHEST;
      int chestIndex = chestSlotSnapshot.getIndex(36);
      ItemStack equippedStack = equippedInventorySnapshot.getItem(chestIndex);
      Item elytra = Items.ELYTRA;
      if (equippedStack.is(elytra) && equippedStack.isDamageableItem()) {
         float equippedDurability = InventoryUtils.getDurabilityPercent(equippedStack);
         Integer durabilityLimit = this.durabilitySetting.getValue();
         boolean equippedDurabilitySafe = equippedDurability > durabilityLimit.intValue();
         if (equippedDurabilitySafe) {
            return false;
         }

         if (this.assumeInventorySetting.getValue()) {
            for (ItemStack candidateStack : ClientAccess.argusclientMinecraft.player.getInventory().getNonEquipmentItems()) {
               Item elytraItem = Items.ELYTRA;
               if (candidateStack.is(elytraItem)) {
                  if (!candidateStack.isDamageableItem()) {
                     return false;
                  }

                  float candidateDurability = InventoryUtils.getDurabilityPercent(candidateStack);
                  Integer candidateThreshold = this.durabilitySetting.getValue();
                  if (candidateDurability > candidateThreshold.intValue()) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      illegalDisconnect = (IllegalDisconnectModule)modules.getModule(IllegalDisconnectModule.class);
      autoReconnect = (AutoReconnectModule)modules.getModule(AutoReconnectModule.class);
   }

   public AutoLogModule() {
      super("AutoLog", "Logs you out so you don't have to fight anyone.", ModuleCategory.COMBAT);
      Predicate totemCountVisibility = unused -> this.totemsSetting.isExpanded();
      this.totemCountSetting = this.add(new NumberSetting<>("TotemCount", 1, 0, 16, totemCountVisibility));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", false).markVisibilityDependency());
      this.durabilitySetting = this.add(new NumberSetting<>("Durability", 5, 1, 100).withUnit("%").dependsOn(this.elytraSetting));
      this.assumeInventorySetting = this.add(new BooleanSetting("AssumeInventory", false).dependsOn(this.elytraSetting));
      this.yLevelSetting = this.add(new BooleanSetting("YLevel", false).markVisibilityDependency());
      this.ySetting = this.add(new NumberSetting<>("Y", 64.0F, -64.0F, 300.0F).dependsOn(this.yLevelSetting));
      this.waypointsSetting = this.add(new BooleanSetting("Waypoints", false).markVisibilityDependency());
      this.distanceSetting = this.add(new NumberSetting<>("Distance", 16.0F, 8.0F, 128.0F).withUnit("m").dependsOn(this.waypointsSetting));
      this.disconnectTimer = new ElapsedTimer();
      this.disconnectTimeMillis = -1L;
   }
}
