package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.awt.Color;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemsModule extends HudWidgetModule {
   private ItemStack totemStack;
   private final Setting<Boolean> whiteSetting = this.add(new BooleanSetting("White", false));

   @Override
   public float[] calculateDimensions() {
      return new float[]{16.0F, 16.0F};
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      LocalPlayer localPlayer = ClientAccess.argusclientMinecraft.player;
      if (!localPlayer.isSpectator()) {
         this.totemStack = new ItemStack(Items.TOTEM_OF_UNDYING, InventoryUtils.countItems(candidateStack -> {
            Item totemItemForMatch = Items.TOTEM_OF_UNDYING;
            return candidateStack.is(totemItemForMatch);
         }));
      }
   }

   @Override
   public void renderContent(PoseStack poses) {
      if (this.totemStack != null) {
         ItemStack stackForEmptyCheck = this.totemStack;
         if (stackForEmptyCheck.getCount() != 0) {
            ItemStack stackToRender = this.totemStack;
            GuiDrawUtils.drawItem(poses, stackToRender, 0.0F, 0.0F, -150.0F, true);
            ItemStack stackForOverlayCheck = this.totemStack;
            if (stackForOverlayCheck.getCount() > 1) {
               int totemCount = this.totemStack.getCount();
               String countText = String.valueOf(totemCount);
               poses.pushPose();
               double textDepth = 0.1;
               double textTranslateY = 0.0;
               double textTranslateX = 0.0;
               poses.translate(textTranslateX, textTranslateY, textDepth);
               FontRenderer font = FontRenderer.INSTANCE;
               float textX = 17.0F - FontRenderer.INSTANCE.getWidth(countText);
               float textY = 9.0F;
               Setting<Boolean> whiteOption = this.whiteSetting;
               Boolean useWhite = whiteOption.getValue();
               Color textColor;
               if (useWhite) {
                  textColor = Color.white;
               } else {
                  textColor = this.getHudColor(9.0F);
               }

               font.drawWithShadow(poses, countText, textX, textY, textColor);
               poses.popPose();
            }

            return;
         }
      }
   }

   public TotemsModule() {
      super("Totems");
      this.setRenderer(new ResizableHudWidgetRenderer(this));
   }
}
