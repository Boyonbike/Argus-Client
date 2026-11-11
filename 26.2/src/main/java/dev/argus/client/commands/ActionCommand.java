package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.InputOverrideManager;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.MinecraftAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public class ActionCommand extends Command {
   private final Map<ActionCommand.SwapMode, List<ActionCommand.SwapHistoryEntry>> swapHistory = new HashMap<>();
   private boolean actionBlocked;

   @Override
   public void exec(LiteralArgumentBuilder root) {
      RequiredArgumentBuilder swapModeArgumentBuilder = Command.argument("swap", new EnumArgumentType(ActionCommand.SwapMode.class));
      DefaultedRegistry<Item> itemRegistry = BuiltInRegistries.ITEM;
      BiPredicate<CommandContext, Item> itemFilter = this::acceptsSwapItem;
      IntFunction<Item[]> itemArrayFactory = Item[]::new;
      ArgumentType itemType = RegistryEntryArgument.aliased(itemRegistry, itemFilter, itemArrayFactory);
      ArgumentBuilder itemArgument = Command.argument("item", itemType).executes(context -> {
         Item[] items = (Item[])context.getArgument("item", Item[].class);
         ActionCommand.SwapMode swapMode = (ActionCommand.SwapMode)context.getArgument("swap", ActionCommand.SwapMode.class);
         ActionSwapStrategy strategyForLookup = swapMode.getStrategy();
         SwapSlots slots = strategyForLookup.findSlots(items);
         if (slots.isInvalid()) {
            this.actionBlocked = true;
         }

         ActionCommand.SwapHistoryEntry entry = new ActionCommand.SwapHistoryEntry(swapMode.getStrategy(), slots);
         this.swapHistory.get(swapMode).add(entry);
         if (!slots.isInvalid()) {
            swapMode.getStrategy().apply(slots);
         }

         return 1;
      });
      swapModeArgumentBuilder = (RequiredArgumentBuilder)swapModeArgumentBuilder.then(itemArgument);
      ArgumentBuilder backArgument = Command.literal("back").executes(context -> {
         ActionCommand.SwapMode swapMode = (ActionCommand.SwapMode)context.getArgument("swap", ActionCommand.SwapMode.class);
         List<ActionCommand.SwapHistoryEntry> history = this.swapHistory.get(swapMode);
         if (history.isEmpty()) {
            return 1;
         }

         ActionCommand.SwapHistoryEntry entry = history.removeLast();
         SwapSlots savedSlots = entry.slots();
         if (savedSlots.isInvalid()) {
            return 1;
         }

         entry.strategy().undo(entry.slots);
         return 1;
      });
      ArgumentBuilder swapWithBack = swapModeArgumentBuilder.then(backArgument);
      root.then(swapWithBack);
      ArgumentBuilder attackArgument = Command.literal("attack").executes(context -> {
         if (this.actionBlocked) {
            return 1;
         }

         Runnable attackAction = () -> ((MinecraftAccessor)ClientAccess.argusclientMinecraft).attack();
         if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ChatScreen) {
            this.scheduleRotationTask(attackAction);
         } else {
            attackAction.run();
         }

         return 1;
      });
      root.then(attackArgument);
      ArgumentBuilder interactArgument = Command.literal("interact").executes(context -> {
         if (this.actionBlocked) {
            return 1;
         }

         Runnable interactAction = () -> ((MinecraftAccessor)ClientAccess.argusclientMinecraft).interact();
         if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ChatScreen) {
            this.scheduleRotationTask(interactAction);
         } else {
            interactAction.run();
         }

         return 1;
      });
      root.then(interactArgument);
      ArgumentBuilder jumpArgument = Command.literal("jump").executes(context -> {
         InputOverrideManager inputOverrides = ClientContext.inputOverrides;
         inputOverrides.requestJump(true);
         return 1;
      });
      root.then(jumpArgument);
      ArgumentBuilder takeoffArgument = Command.literal("takeoff").executes(context -> {
         if (!this.actionBlocked && PlayerStateUtils.isLocalWearingElytra()) {
            LocalPlayer playerForGroundCheck = ClientAccess.argusclientMinecraft.player;
            if (!playerForGroundCheck.onGround()) {
               PacketSender.sendStartFallFlying();
               LocalPlayer playerForTakeoff = ClientAccess.argusclientMinecraft.player;
               playerForTakeoff.startFallFlying();
            }
         }

         return 1;
      });
      root.then(takeoffArgument);
      InventoryCommand inventoryCommand = new InventoryCommand();
      this.addSubcommand(inventoryCommand, root);
   }

   private boolean acceptsSwapItem(CommandContext context, Item item) {
      ActionCommand.SwapMode mode = (ActionCommand.SwapMode)context.getArgument("swap", ActionCommand.SwapMode.class);
      return mode.acceptsItem(item);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.actionBlocked = false;
   }

   public static boolean canEquip(Item item) {
      ItemStack stackForComponent = item.getDefaultInstance();
      DataComponentType<Equippable> equippableType = DataComponents.EQUIPPABLE;
      Equippable equippable = stackForComponent.get(equippableType);
      if (equippable != null) {
         LocalPlayer playerForType = ClientAccess.argusclientMinecraft.player;
         EntityType playerType = playerForType.getType();
         if (equippable.canBeEquippedBy(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(playerType))) {
            return true;
         }
      }

      return false;
   }

   public ActionCommand() {
      super("action");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);

      for (ActionCommand.SwapMode mode : ActionCommand.SwapMode.values()) {
         this.swapHistory.put(mode, new ArrayList<>());
      }
   }

   private record SwapHistoryEntry(ActionSwapStrategy strategy, SwapSlots slots) {
   }

   private enum SwapMode implements NamedEntry {
      SWAP("swap", ignoredItem -> true, new HotbarSwapStrategy()),
      ALTERNATIVE("alternative", ignoredItem -> true, new AlternativeSwapStrategy()),
      ARMOR("armor", ActionCommand::canEquip, new ArmorSwapStrategy()),
      OFFHAND("offhand", ignoredItem -> true, new OffhandSwapStrategy());

      private final String name;
      private final ActionSwapStrategy strategy;
      private final Predicate<Item> itemFilter;

      public ActionSwapStrategy getStrategy() {
         return this.strategy;
      }

      @Override
      public String getName() {
         return this.name;
      }

      SwapMode(String name, Predicate<Item> itemFilter, ActionSwapStrategy strategy) {
         this.name = name;
         this.itemFilter = itemFilter;
         this.strategy = strategy;
      }

      public boolean acceptsItem(Item item) {
         return this.itemFilter.test(item);
      }
   }
}
