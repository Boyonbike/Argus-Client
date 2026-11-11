package dev.argus.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.network.PacketSender;
import java.util.HashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public final class PluginsCommand extends Command {
   private final ElapsedTimer stopwatch = new ElapsedTimer();
   private boolean queryPending;

   public PluginsCommand() {
      super("plugins");
      PluginsCommand command = this;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(command);
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      com.mojang.brigadier.Command<Object> fetchAction = context -> {
         PacketSender.send(new ServerboundCommandSuggestionPacket(0, "/"));
         MutableComponent fetchingMessage = Component.literal("Fetching plugins...");
         ChatUtils.printChat(fetchingMessage, ChatUtils.createSignature(-3));
         this.stopwatch.reset();
         this.queryPending = true;
         return 1;
      };
      root.executes(fetchAction);
   }

   private Component formatPlugins(Iterable<String> commandNames) {
      HashSet<String> namespaces = new HashSet<>();

      for (String commandName : commandNames) {
         String[] parts = commandName.split(":");
         if (parts.length > 1 && !parts[0].isEmpty()) {
            namespaces.add(parts[0]);
         }
      }

      Component pluginList = ComponentUtils.formatList(
         namespaces,
         pluginName -> Component.literal(pluginName).withStyle(style -> ChatUtils.withDynamicColor(style, () -> ColorUtils.getThemeColor().hashCode()))
      );
      MutableComponent message = Component.empty().append("Found plugins [");
      Component count = Component.literal(String.valueOf(namespaces.size())).withStyle(ChatFormatting.GRAY);
      message = message.append(count);
      return message.append("]: ").append(pluginList);
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.queryPending) {
         ElapsedTimer capturedTimer = this.stopwatch;
         if (capturedTimer.hasElapsed(3000L)) {
            this.queryPending = false;
            ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
            CommandDispatcher<ClientSuggestionProvider> dispatcher = connection.getCommands();
            RootCommandNode<ClientSuggestionProvider> commandRoot = dispatcher.getRoot();
            List<String> commandNames = commandRoot.getChildren()
               .stream()
               .<String>map(CommandNode::getName)
               .filter(commandName -> !commandName.contains("fabric-command-api-v2"))
               .toList();
            if (commandNames.isEmpty()) {
               MutableComponent failureMessage = Component.literal("Couldn't get plugins");
               ChatUtils.printChat(failureMessage, ChatUtils.createSignature(-3));
            } else {
               Component fallbackMessage = this.formatPlugins(commandNames);
               ChatUtils.printChat(fallbackMessage, ChatUtils.createSignature(-3));
            }
         }

         if (event.getPacket() instanceof ClientboundCommandSuggestionsPacket suggestionsPacket) {
            this.queryPending = false;
            Suggestions suggestions = suggestionsPacket.toSuggestions();
            List<String> suggestionTexts = suggestions.getList().stream().<String>map(Suggestion::getText).toList();
            Component suggestionMessage = this.formatPlugins(suggestionTexts);
            ChatUtils.printChat(suggestionMessage, ChatUtils.createSignature(-3));
         }
      }
   }
}
