package dev.argus.client.modules.misc;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Window;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.managers.InputOverrideManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.RocketExtenderModule;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class MiddleClickModule extends Module {
   private static FireworksModule fireworksModule = ClientContext.getModule(FireworksModule.class);
   private static RocketExtenderModule rocketExtenderModule = ClientContext.getModule(RocketExtenderModule.class);
   private final Setting<Boolean> friendsSetting = this.add(new BooleanSetting("Friends", true));
   private final Setting<Boolean> fireworksSetting;
   private final Setting<Boolean> onGroundSetting;
   private final Setting<Boolean> alternativeSetting;
   private boolean middleButtonHeld;
   private boolean launchAgainNextTick;

   private void handleMiddleClick() {
      HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
      boolean canLaunch = true;
      if (!PlayerStateUtils.isFallFlying()) {
         FireworksModule fireworksSnapshot = fireworksModule;
         boolean forceFastLaunch = false;
         if (!fireworksSnapshot.canFastLaunch(forceFastLaunch)) {
            canLaunch = false;
            Setting<Boolean> fastLaunchOption = fireworksModule.fastLaunchSetting;
            Boolean fastLaunch = fastLaunchOption.getValue();
            if (fastLaunch) {
               canLaunch = PlayerStateUtils.isLocalWearingElytra();
            }
         }
      }

      LocalPlayer groundPlayer = ClientAccess.argusclientMinecraft.player;
      if (groundPlayer.onGround()) {
         Setting<Boolean> onGroundOption = this.onGroundSetting;
         Boolean allowOnGround = onGroundOption.getValue();
         if (!allowOnGround) {
            canLaunch = false;
         }
      }

      if (canLaunch) {
         Setting<Boolean> fireworksOption = this.fireworksSetting;
         Boolean useFireworks = fireworksOption.getValue();
         if (useFireworks) {
            LocalPlayer launchGroundPlayer = ClientAccess.argusclientMinecraft.player;
            if (launchGroundPlayer.onGround()) {
               InputOverrideManager jumpManagerSnapshot = ClientContext.inputOverrides;
               jumpManagerSnapshot.requestJump(true);
               this.launchFirework();
               this.launchAgainNextTick = true;
            } else {
               this.launchFirework();
            }

            return;
         }
      }

      if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player targetPlayer) {
         Setting<Boolean> friendsOption = this.friendsSetting;
         Boolean toggleFriends = friendsOption.getValue();
         if (toggleFriends) {
            GameProfile profile = targetPlayer.getGameProfile();
            String playerName = profile.name();
            SocialRegistry friendCheckerSnapshot = ClientContext.socialRegistry;
            if (friendCheckerSnapshot.isFriendName(playerName)) {
               SocialRegistry friendRemoverSnapshot = ClientContext.socialRegistry;
               friendRemoverSnapshot.removeSocial(playerName);
            } else {
               SocialRegistry friendAdderSnapshot = ClientContext.socialRegistry;
               friendAdderSnapshot.addFriend(playerName);
            }
         }
      }
   }

   public void launchFirework() {
      Item rocketItem = Items.FIREWORK_ROCKET;
      InteractionHand heldRocketHand = InventoryUtils.getHandForItem(rocketItem);
      LocalPlayer selectionPlayer = ClientAccess.argusclientMinecraft.player;
      Inventory selectionInventory = selectionPlayer.getInventory();
      int previousHotbarSlot = selectionInventory.getSelectedSlot();
      Item inventoryRocketItem = Items.FIREWORK_ROCKET;
      int inventoryRocketSlot = InventoryUtils.findMenuSlot(inventoryRocketItem);
      rocketItem = Items.FIREWORK_ROCKET;
      int hotbarRocketSlot = InventoryUtils.findHotbarSlot(rocketItem);
      if (heldRocketHand != null) {
         MultiPlayerGameMode heldGameMode = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer heldUsePlayer = ClientAccess.argusclientMinecraft.player;
         heldGameMode.useItem(heldUsePlayer, heldRocketHand);
      } else if (inventoryRocketSlot != -1) {
         boolean useInventorySwap = true;
         if (hotbarRocketSlot != -1) {
            Setting<Boolean> alternativeOption = this.alternativeSetting;
            Boolean alternative = alternativeOption.getValue();
            useInventorySwap = alternative;
         }

         this.selectOrSwapRocket(hotbarRocketSlot, inventoryRocketSlot, useInventorySwap);
         MultiPlayerGameMode inventoryGameMode = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer inventoryUsePlayer = ClientAccess.argusclientMinecraft.player;
         InteractionHand inventoryUseHand = InteractionHand.MAIN_HAND;
         inventoryGameMode.useItem(inventoryUsePlayer, inventoryUseHand);
         RocketExtenderModule extender = rocketExtenderModule;
         LocalPlayer launchPositionPlayer = ClientAccess.argusclientMinecraft.player;
         extender.launchPosition = launchPositionPlayer.position();
         this.selectOrSwapRocket(previousHotbarSlot, inventoryRocketSlot, useInventorySwap);
      }
   }

   private void selectOrSwapRocket(int hotbarSlot, int inventorySlot, boolean useInventorySwap) {
      if (useInventorySwap) {
         InventoryUtils.swapIntoSelectedSlot(inventorySlot);
      } else {
         InventoryUtils.selectHotbarSlotForPlacement(hotbarSlot);
      }
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      if (this.launchAgainNextTick) {
         this.launchFirework();
         this.launchAgainNextTick = false;
      }

      if (ClientAccess.argusclientMinecraft.screen == null) {
         Minecraft pressClient = ClientAccess.argusclientMinecraft;
         Window pressWindow = pressClient.getWindow();
         long windowHandle = pressWindow.handle();
         if (GLFW.glfwGetMouseButton(windowHandle, 2) == 1 && !this.middleButtonHeld) {
            this.middleButtonHeld = true;
            this.handleMiddleClick();
         } else {
            Minecraft releaseClient = ClientAccess.argusclientMinecraft;
            Window releaseWindow = releaseClient.getWindow();
            windowHandle = releaseWindow.handle();
            if (GLFW.glfwGetMouseButton(windowHandle, 2) == 0) {
               this.middleButtonHeld = false;
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      fireworksModule = (FireworksModule)registry.getModule(FireworksModule.class);
      rocketExtenderModule = (RocketExtenderModule)registry.getModule(RocketExtenderModule.class);
   }

   public MiddleClickModule() {
      super("MiddleClick", "Middle click actions.", ModuleCategory.MISC);
      BooleanSetting fireworks = new BooleanSetting("Fireworks", true);
      fireworks.markVisibilityDependency();
      this.fireworksSetting = this.add(fireworks);
      this.onGroundSetting = this.add(new BooleanSetting("OnGround", true).dependsOn(this.fireworksSetting));
      this.alternativeSetting = this.add(new BooleanSetting("Alternative", false).dependsOn(this.fireworksSetting));
      this.setDrawn(false);
   }
}
