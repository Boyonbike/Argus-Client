package dev.argus.client.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.world.inventory.ClickType;

public class InventoryCommand extends Command {
   private int openInventory(CommandContext context) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      if (gameMode.isServerControlledInventory()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.sendOpenInventory();
         return 1;
      } else {
         InventoryScreen inventoryScreen = new InventoryScreen(ClientAccess.argusclientMinecraft.player);
         this.scheduleScreenChange(inventoryScreen);
         return 1;
      }
   }

   public InventoryCommand() {
      super("inventory");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      LiteralArgumentBuilder openBuilder = Command.literal("open");
      com.mojang.brigadier.Command<Object> openExecutor = this::openInventory;
      ArgumentBuilder openAction = openBuilder.executes(openExecutor);
      root.then(openAction);
      LiteralArgumentBuilder closeBuilder = Command.literal("close");
      com.mojang.brigadier.Command<Object> closeExecutor = this::closeInventory;
      ArgumentBuilder closeAction = closeBuilder.executes(closeExecutor);
      root.then(closeAction);
      LiteralArgumentBuilder clickBuilder = Command.literal("click");
      IntegerArgumentType slotType = IntegerArgumentType.integer();
      RequiredArgumentBuilder slotBuilder = Command.argument("slot", slotType);
      IntegerArgumentType buttonType = IntegerArgumentType.integer(0);
      RequiredArgumentBuilder buttonBuilder = Command.argument("button", buttonType);
      EnumArgumentType clickTypeArgument = new EnumArgumentType(InventoryCommand.ClickAction.class);
      RequiredArgumentBuilder typeBuilder = Command.argument("type", clickTypeArgument);
      com.mojang.brigadier.Command<Object> clickExecutor = this::clickInventory;
      ArgumentBuilder typeAction = typeBuilder.executes(clickExecutor);
      ArgumentBuilder buttonWithType = buttonBuilder.then(typeAction);
      ArgumentBuilder slotWithButton = slotBuilder.then(buttonWithType);
      ArgumentBuilder clickWithSlot = clickBuilder.then(slotWithButton);
      root.then(clickWithSlot);
   }

   private int clickInventory(CommandContext context) {
      int slot = (Integer)context.getArgument("slot", Integer.class);
      int button = (Integer)context.getArgument("button", Integer.class);
      InventoryCommand.ClickAction clickAction = (InventoryCommand.ClickAction)context.getArgument("type", InventoryCommand.ClickAction.class);
      ClickType clickType = clickAction.getClickType();

      try {
         InventoryUtils.clickSlot(slot, button, clickType);
      } catch (Throwable failure) {
         StringBuilder message = new StringBuilder();
         message.append("Failed to click in inventory due to: ");
         if (failure instanceof ReportedException) {
            Throwable cause = failure.getCause();
            String causeMessage = cause.getMessage();
            message.append(causeMessage);
         } else {
            String failureMessage = failure.getMessage();
            message.append(failureMessage);
         }

         String text = message.toString();
         Component failureMessage = Component.nullToEmpty(text);
         MessageSignature failureSignature = ChatUtils.DEFAULT_SIGNATURE;
         ChatUtils.MessagePriority failurePriority = ChatUtils.MessagePriority.HIGH;
         ChatUtils.printChat(failureMessage, failureSignature, failurePriority);
      }

      return 1;
   }

   private int closeInventory(CommandContext context) {
      Minecraft minecraft = ClientAccess.argusclientMinecraft;
      minecraft.setScreen(null);
      return 1;
   }

   public enum ClickAction implements NamedEntry {
      PICKUP(ClickType.PICKUP, "pickup"),
      QUICK_MOVE(ClickType.QUICK_MOVE, "quick_move"),
      SWAP(ClickType.SWAP, "swap"),
      CLONE(ClickType.CLONE, "clone"),
      THROW(ClickType.THROW, "throw"),
      QUICK_CRAFT(ClickType.QUICK_CRAFT, "quick_craft"),
      PICKUP_ALL(ClickType.PICKUP_ALL, "pickup_all");

      private final ClickType clickType;
      private final String argumentName;

      public ClickType getClickType() {
         return this.clickType;
      }

      @Override
      public String getName() {
         return this.argumentName;
      }

      ClickAction(ClickType clickType, String argumentName) {
         this.clickType = clickType;
         this.argumentName = argumentName;
      }
   }
}
