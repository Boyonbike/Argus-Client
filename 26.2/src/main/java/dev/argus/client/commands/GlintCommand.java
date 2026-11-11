package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.GlintRegistry;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public final class GlintCommand extends Command {
   public GlintCommand() {
      super("glint");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      EnumArgumentType glintType = new EnumArgumentType(GlintRegistry.GlintMode.class, "Glint");
      RequiredArgumentBuilder glintArgument = Command.argument("glint", glintType);
      LiteralArgumentBuilder clearBuilder = Command.literal("clear");
      com.mojang.brigadier.Command<Object> clearExecutor = this::clearGlintItems;
      ArgumentBuilder clearBranch = clearBuilder.executes(clearExecutor);
      glintArgument = (RequiredArgumentBuilder)glintArgument.then(clearBranch);
      DefaultedRegistry itemRegistry = BuiltInRegistries.ITEM;
      ArgumentType itemType = RegistryEntryArgument.registry(itemRegistry);
      RequiredArgumentBuilder itemBuilder = Command.argument("item", itemType);
      com.mojang.brigadier.Command<Object> itemExecutor = this::setItemGlint;
      ArgumentBuilder itemBranch = itemBuilder.executes(itemExecutor);
      ArgumentBuilder completedGlintArgument = glintArgument.then(itemBranch);
      commandBuilder.then(completedGlintArgument);
   }

   private int setItemGlint(CommandContext commandContext) {
      GlintRegistry.GlintMode glintMode = (GlintRegistry.GlintMode)commandContext.getArgument("glint", GlintRegistry.GlintMode.class);
      Item selectedItem = (Item)commandContext.getArgument("item", Item.class);
      String itemName = selectedItem.getDefaultInstance().getHoverName().getString();
      GlintRegistry glintManager = ClientContext.glintRegistry;
      boolean added = glintManager.toggle(selectedItem, glintMode);
      ChatUtils.printChat(
         Component.literal(added ? "Added " : "Removed ")
            .append(this.formatText(itemName))
            .append(added ? " to glint list \"" : " from glint list \"")
            .append(this.formatText(glintMode.getName()))
            .append("\""),
         ChatUtils.DEFAULT_SIGNATURE
      );
      return 1;
   }

   private int clearGlintItems(CommandContext commandContext) {
      GlintRegistry.GlintMode capturedGlintMode = (GlintRegistry.GlintMode)commandContext.getArgument("glint", GlintRegistry.GlintMode.class);
      GlintRegistry glintManager = ClientContext.glintRegistry;
      RegistrySetSetting<?> configuredItems = glintManager.getItems(capturedGlintMode);
      configuredItems.getValue().clear();
      ChatUtils.printChat(
         Component.literal("Glint list \"").append(this.formatText(capturedGlintMode.getName())).append("\" has been cleared"), ChatUtils.DEFAULT_SIGNATURE
      );
      return 1;
   }
}
