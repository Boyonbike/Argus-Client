package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.screens.TradeEditorScreen;
import dev.argus.client.gui.screens.TradeManagerScreen;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.Registry;
import dev.argus.client.managers.TradeRegistry;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.TradeDefinition;
import dev.argus.client.utils.inventory.TradeEnchantmentEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.world.item.Item;

public final class TradeCommand extends Command {
   private int openNewTradeEditor(CommandContext context) {
      String name = (String)context.getArgument("name", String.class);
      TradeEditorScreen editor = new TradeEditorScreen(name);
      TradeCommand command = this;
      return command.scheduleScreenChange(editor);
   }

   private int deleteTrade(CommandContext context) {
      TradeDefinition trade = (TradeDefinition)context.getArgument("trade", TradeDefinition.class);
      Registry registry = ClientContext.tradeRegistry;
      registry.unregisterEntry(trade);
      String message = "Trade %s has been deleted".formatted(trade.getName());
      MutableComponent component = Component.literal(message);
      MessageSignature signature = ChatUtils.createSignature(-1);
      ChatUtils.printChat(component, signature);
      return 1;
   }

   private int clearEnchantments(CommandContext context) {
      TradeDefinition trade = (TradeDefinition)context.getArgument("trade", TradeDefinition.class);
      List<TradeEnchantmentEntry> enchantments = trade.enchants();
      enchantments.clear();
      String message = "Trade %s enchants has been cleared".formatted(trade.getName());
      MutableComponent component = Component.literal(message);
      MessageSignature signature = ChatUtils.createSignature(-1);
      ChatUtils.printChat(component, signature);
      return 1;
   }

   public TradeCommand() {
      super("trade");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      LiteralArgumentBuilder literalBranch = Command.literal("new");
      FancyStringArgument nameArgumentType = FancyStringArgument.fancyString();
      RequiredArgumentBuilder argumentBranch = Command.argument("name", nameArgumentType);
      DefaultedRegistry fromRegistry = BuiltInRegistries.ITEM;
      ArgumentType fromItemType = RegistryEntryArgument.registry(fromRegistry);
      RequiredArgumentBuilder fromArgument = Command.argument("from", fromItemType);
      DefaultedRegistry toRegistry = BuiltInRegistries.ITEM;
      ArgumentType toItemType = RegistryEntryArgument.registry(toRegistry);
      RequiredArgumentBuilder toArgument = Command.argument("to", toItemType);
      IntegerArgumentType priceType = IntegerArgumentType.integer(1, 64);
      RequiredArgumentBuilder priceArgument = Command.argument("max_price", priceType);
      com.mojang.brigadier.Command<Object> priceCallback = this::createTrade;
      ArgumentBuilder priceBranch = priceArgument.executes(priceCallback);
      RequiredArgumentBuilder createTradeBranchBuilder = (RequiredArgumentBuilder)toArgument.then(priceBranch);
      com.mojang.brigadier.Command<Object> createTradeBranchCallback = this::createTrade;
      ArgumentBuilder createTradeBranch = createTradeBranchBuilder.executes(createTradeBranchCallback);
      ArgumentBuilder itemsBranch = fromArgument.then(createTradeBranch);
      RequiredArgumentBuilder newEditorBranchBuilder = (RequiredArgumentBuilder)argumentBranch.then(itemsBranch);
      com.mojang.brigadier.Command<Object> newEditorBranchCallback = this::openNewTradeEditor;
      ArgumentBuilder newEditorBranch = newEditorBranchBuilder.executes(newEditorBranchCallback);
      ArgumentBuilder newBranch = literalBranch.then(newEditorBranch);
      root.then(newBranch);
      literalBranch = Command.literal("edit");
      TradeRegistry capturedEditRegistry = ClientContext.tradeRegistry;
      RegistryArgumentType editArgumentType = RegistryArgumentType.create(capturedEditRegistry);
      RequiredArgumentBuilder editArgument = Command.argument("trade", editArgumentType);
      com.mojang.brigadier.Command<Object> editCallback = this::editTrade;
      ArgumentBuilder editTradeBranch = editArgument.executes(editCallback);
      ArgumentBuilder editBranch = literalBranch.then(editTradeBranch);
      root.then(editBranch);
      literalBranch = Command.literal("delete");
      TradeRegistry capturedDeleteRegistry = ClientContext.tradeRegistry;
      RegistryArgumentType deleteArgumentType = RegistryArgumentType.create(capturedDeleteRegistry);
      RequiredArgumentBuilder deleteArgument = Command.argument("trade", deleteArgumentType);
      com.mojang.brigadier.Command<Object> deleteCallback = this::deleteTrade;
      ArgumentBuilder deleteTradeBranch = deleteArgument.executes(deleteCallback);
      ArgumentBuilder deleteBranch = literalBranch.then(deleteTradeBranch);
      root.then(deleteBranch);
      literalBranch = Command.literal("enchant");
      TradeRegistry capturedEnchantRegistry = ClientContext.tradeRegistry;
      RegistryArgumentType enchantArgumentType = RegistryArgumentType.create(capturedEnchantRegistry);
      argumentBranch = Command.argument("trade", enchantArgumentType);
      LiteralArgumentBuilder clearBuilder = Command.literal("clear");
      com.mojang.brigadier.Command<Object> clearCallback = this::clearEnchantments;
      ArgumentBuilder clearBranch = clearBuilder.executes(clearCallback);
      argumentBranch = (RequiredArgumentBuilder)argumentBranch.then(clearBranch);
      EnchantmentIdArgumentType enchantmentType = new EnchantmentIdArgumentType();
      RequiredArgumentBuilder enchantmentArgument = Command.argument("enchantment", enchantmentType);
      IntegerArgumentType levelType = IntegerArgumentType.integer();
      RequiredArgumentBuilder levelArgument = Command.argument("lvl", levelType);
      com.mojang.brigadier.Command<Object> enchantmentCallback = this::addEnchantment;
      ArgumentBuilder levelBranch = levelArgument.executes(enchantmentCallback);
      ArgumentBuilder addEnchantmentBranch = enchantmentArgument.then(levelBranch);
      ArgumentBuilder enchantsBranch = argumentBranch.then(addEnchantmentBranch);
      ArgumentBuilder enchantBranch = literalBranch.then(enchantsBranch);
      root.then(enchantBranch);
      LiteralArgumentBuilder listBuilder = Command.literal("list");
      com.mojang.brigadier.Command<Object> listCallback = this::listTrades;
      ArgumentBuilder listBranch = listBuilder.executes(listCallback);
      root.then(listBranch);
      com.mojang.brigadier.Command<Object> screenCallback = context -> {
         TradeManagerScreen tradeScreen = new TradeManagerScreen();
         TradeCommand command = this;
         return command.scheduleScreenChange(tradeScreen);
      };
      root.executes(screenCallback);
   }

   private int createTrade(CommandContext context) {
      String name = (String)context.getArgument("name", String.class);
      Item fromItem = (Item)context.getArgument("from", Item.class);
      Item toItem = (Item)context.getArgument("to", Item.class);

      int maxPrice;
      try {
         Integer priceValue = (Integer)context.getArgument("max_price", Integer.class);
         maxPrice = priceValue;
      } catch (IllegalArgumentException missingPrice) {
         maxPrice = 64;
      }

      TradeDefinition createdTrade = new TradeDefinition(name, fromItem, toItem, maxPrice, new ArrayList());
      TradeRegistry registry = ClientContext.tradeRegistry;
      registry.registerTrade(createdTrade);
      Object[] messageArguments = new Object[]{name};
      String message = "Trade %s has been created".formatted(messageArguments);
      MutableComponent component = Component.literal(message);
      MessageSignature signature = ChatUtils.createSignature(-1);
      ChatUtils.printChat(component, signature);
      return 1;
   }

   private int editTrade(CommandContext context) {
      TradeDefinition trade = (TradeDefinition)context.getArgument("trade", TradeDefinition.class);
      TradeEditorScreen editor = new TradeEditorScreen(trade);
      TradeCommand command = this;
      return command.scheduleScreenChange(editor);
   }

   private static Component createTradeListEntry(TradeDefinition trade) {
      String prefix = CommandRegistry.getPrefix();
      String tradeName = trade.getName();
      String editCommand = prefix + "trade edit " + tradeName;
      String name = trade.getName();
      MutableComponent component = Component.literal(name);
      ShowText hoverEvent = new ShowText(Component.literal("Click to edit"));
      RunCommand clickEvent = new RunCommand(editCommand);
      UnaryOperator<Style> entryStyle = style -> {
         Style hoverStyle = style.withHoverEvent(hoverEvent);
         return hoverStyle.withClickEvent(clickEvent);
      };
      component.withStyle(entryStyle);
      return component;
   }

   private int addEnchantment(CommandContext context) {
      TradeDefinition trade = (TradeDefinition)context.getArgument("trade", TradeDefinition.class);
      String enchantment = (String)context.getArgument("enchantment", String.class);
      int level = (Integer)context.getArgument("lvl", Integer.class);
      List<TradeEnchantmentEntry> enchantments = trade.enchants();
      TradeEnchantmentEntry enchantmentEntry = new TradeEnchantmentEntry(enchantment, level);
      enchantments.add(enchantmentEntry);
      String message = "Added enchantment %s with level %d to %s trade".formatted(enchantment, level, trade.getName());
      MutableComponent component = Component.literal(message);
      MessageSignature signature = ChatUtils.createSignature(-1);
      ChatUtils.printChat(component, signature);
      return 1;
   }

   private int listTrades(CommandContext context) {
      TradeRegistry registry = ClientContext.tradeRegistry;
      List<TradeDefinition> trades = (List<TradeDefinition>)registry.getRegistry();
      Stream<TradeDefinition> tradeStream = trades.stream();
      Function<TradeDefinition, Component> entryMapper = TradeCommand::createTradeListEntry;
      Stream<Component> entryStream = tradeStream.map(entryMapper);
      List<Component> entries = entryStream.toList();
      if (entries.isEmpty()) {
         MutableComponent emptyComponent = Component.literal("You don't have any trades yet.");
         ChatUtils.printChat(emptyComponent, -1);
         return 1;
      } else {
         MutableComponent component = Component.empty();
         component.append("Trades list: ");
         MutableComponent separatorComponent = Component.literal(", ");
         Component formattedEntries = ComponentUtils.formatList(entries, separatorComponent);
         component.append(formattedEntries);
         ChatUtils.printChat(component, -1);
         return 1;
      }
   }
}
