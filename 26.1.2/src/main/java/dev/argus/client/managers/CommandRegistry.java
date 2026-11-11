package dev.argus.client.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.commands.ActionCommand;
import dev.argus.client.commands.AliasCommand;
import dev.argus.client.commands.AlloffCommand;
import dev.argus.client.commands.AngleCommand;
import dev.argus.client.commands.AutopotCommand;
import dev.argus.client.commands.AutopresetCommand;
import dev.argus.client.commands.BaritoneCommand;
import dev.argus.client.commands.BindCommand;
import dev.argus.client.commands.ChatfilterCommand;
import dev.argus.client.commands.CheststealerCommand;
import dev.argus.client.commands.ClipCommand;
import dev.argus.client.commands.ColorCommand;
import dev.argus.client.commands.Command;
import dev.argus.client.commands.CoordsCommand;
import dev.argus.client.commands.DefaultsCommand;
import dev.argus.client.commands.DisconnectCommand;
import dev.argus.client.commands.DrawnCommand;
import dev.argus.client.commands.EnemyFriendCommand;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.commands.FireworksCommand;
import dev.argus.client.commands.FolderCommand;
import dev.argus.client.commands.GlintCommand;
import dev.argus.client.commands.GlobalCommand;
import dev.argus.client.commands.HelpCommand;
import dev.argus.client.commands.HighlightCommand;
import dev.argus.client.commands.KitCommand;
import dev.argus.client.commands.MacroCommand;
import dev.argus.client.commands.MeteorCommand;
import dev.argus.client.commands.ModuleCommand;
import dev.argus.client.commands.NamehistoryCommand;
import dev.argus.client.commands.NbtCommand;
import dev.argus.client.commands.PanicCommand;
import dev.argus.client.commands.PitchCommand;
import dev.argus.client.commands.PlaytimeCommand;
import dev.argus.client.commands.PluginsCommand;
import dev.argus.client.commands.PrefixCommand;
import dev.argus.client.commands.PresetCommand;
import dev.argus.client.commands.PrintCommand;
import dev.argus.client.commands.QueueCommand;
import dev.argus.client.commands.ReloadCommand;
import dev.argus.client.commands.SayCommand;
import dev.argus.client.commands.SeenCommand;
import dev.argus.client.commands.SessionCommand;
import dev.argus.client.commands.ToggleCommand;
import dev.argus.client.commands.TradeCommand;
import dev.argus.client.commands.WaitCommand;
import dev.argus.client.commands.WaypointsCommand;
import dev.argus.client.commands.YawCommand;
import dev.argus.client.events.CommandExecuteEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.KeyboardHandlerCharTypedEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.SettingsModule;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.SocialRole;
import dev.argus.client.utils.KeyUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

public final class CommandRegistry extends Registry<Command> implements ClientAccess, JsonConfigurable {
   private static final SettingsModule settingsModule = ClientContext.getModule(SettingsModule.class);
   public static final SharedSuggestionProvider suggestionSource = new ClientSuggestionProvider(null, ClientAccess.argusclientMinecraft, ignored -> true);
   public static final CommandDispatcher commandDispatcher = new CommandDispatcher();
   private static String commandPrefix = ";";

   public static char getPrefixCharacter() {
      return commandPrefix.charAt(0);
   }

   private void registerRelationshipCommands() {
      this.registerCommand(new EnemyFriendCommand(SocialRole.FRIEND));
      this.registerCommand(new EnemyFriendCommand(SocialRole.ENEMY));
   }

   private void registerPathfindingCommand() {
      this.registerCommand(new BaritoneCommand());
   }

   public CommandRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      this.registerCommand(new ActionCommand());
      this.registerCommand(new AliasCommand());
      this.registerCommand(new AlloffCommand());
      this.registerCommand(new AngleCommand());
      this.registerCommand(new AutopotCommand());
      this.registerCommand(new AutopresetCommand());
      this.registerCommand(new BindCommand());
      this.registerCommand(new ChatfilterCommand());
      this.registerCommand(new CheststealerCommand());
      this.registerCommand(new ClipCommand());
      this.registerCommand(new ColorCommand());
      this.registerCommand(new CoordsCommand());
      this.registerCommand(new DefaultsCommand());
      this.registerCommand(new DisconnectCommand());
      this.registerCommand(new DrawnCommand());
      this.registerCommand(new FakeplayerCommand());
      this.registerCommand(new FireworksCommand());
      this.registerCommand(new FolderCommand());
      this.registerCommand(new GlintCommand());
      this.registerCommand(new GlobalCommand());
      this.registerCommand(new HelpCommand());
      this.registerCommand(new HighlightCommand());
      this.registerCommand(new KitCommand());
      this.registerCommand(new MacroCommand());
      this.registerCommand(new MeteorCommand());
      this.registerCommand(new NamehistoryCommand());
      this.registerCommand(new NbtCommand());
      this.registerCommand(new PanicCommand());
      this.registerCommand(new PitchCommand());
      this.registerCommand(new PlaytimeCommand());
      this.registerCommand(new PluginsCommand());
      this.registerCommand(new PrefixCommand());
      this.registerCommand(new PresetCommand());
      this.registerCommand(new PrintCommand());
      this.registerCommand(new QueueCommand());
      this.registerCommand(new ReloadCommand());
      this.registerCommand(new SayCommand());
      this.registerCommand(new SeenCommand());
      this.registerCommand(new SessionCommand());
      this.registerCommand(new ToggleCommand());
      this.registerCommand(new TradeCommand());
      this.registerCommand(new WaitCommand());
      this.registerCommand(new WaypointsCommand());
      this.registerCommand(new YawCommand());
      this.registerPathfindingCommand();
      this.registerRelationshipCommands();
      this.registerModuleAliasCommands();
      ((List)this.getRegistry()).sort(Comparator.comparing(NamedFeature::getName));
   }

   @Override
   public boolean register(Object entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   public static void executeCommand(String input) {
      CommandExecuteEvent event = new CommandExecuteEvent(input);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.post(event);
      if (!event.isCancelled()) {
         try {
            CommandDispatcher executionDispatcher = commandDispatcher;
            SharedSuggestionProvider source = suggestionSource;
            ParseResults parsed = commandDispatcher.parse(input, source);
            executionDispatcher.execute(parsed);
         } catch (CommandSyntaxException var6) {
         }
      }
   }

   @Override
   public void fromJson(JsonElement config) {
      JsonObject objectForPresence = config.getAsJsonObject();
      if (objectForPresence.has("prefix")) {
         JsonObject objectForValue = config.getAsJsonObject();
         JsonElement prefixElement = objectForValue.get("prefix");
         String prefix = prefixElement.getAsString();
         setPrefix(prefix);
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject config = new JsonObject();
      String prefix = commandPrefix;
      config.addProperty("prefix", prefix);
      return config;
   }

   @Override
   public String getConfigName() {
      return "commands.json";
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      Command command = (Command)entry;
      return this.registerCommand(command);
   }

   @EventHandler
   public void onKeyboardHandlerCharTypedEvent(KeyboardHandlerCharTypedEvent inputEvent) {
      if (!KeyUtils.isShiftDown()) {
         String prefixForLength = getPrefix();
         if (prefixForLength.length() == 1) {
            String prefixForCharacter = getPrefix();
            char prefixCharacter = prefixForCharacter.charAt(0);
            if (prefixCharacter == inputEvent.getCharacter()) {
               Minecraft minecraft = ClientAccess.argusclientMinecraft;
               ChatScreen chatScreen = new ChatScreen("", false);
               minecraft.setScreen(chatScreen);
            }
         }
      }
   }

   private Optional<LiteralArgumentBuilder> createMatchingModuleCommand(Command command) {
      return ClientContext.moduleRegistry.findFirst(module -> {
         String moduleName = module.getName();
         String commandName = command.getName();
         return moduleName.equalsIgnoreCase(commandName);
      }).map(module -> {
         LiteralArgumentBuilder moduleBuilder = LiteralArgumentBuilder.literal("module");
         ModuleCommand moduleCommand = new ModuleCommand(module);
         moduleCommand.exec(moduleBuilder);
         return moduleBuilder;
      });
   }

   public static void setPrefix(String prefix) {
      if (!prefix.isBlank()) {
         String updatedPrefix = prefix.substring(0, 1);
         commandPrefix = updatedPrefix;
         Setting<String> prefixSetting = settingsModule.prefixSetting;
         prefixSetting.setValue(updatedPrefix);
      }
   }

   public boolean registerCommand(Command command) {
      String commandName = command.getName();
      LiteralArgumentBuilder builder = LiteralArgumentBuilder.literal(commandName);
      command.exec(builder);
      Optional<LiteralArgumentBuilder> moduleCommand = this.createMatchingModuleCommand(command);
      moduleCommand.ifPresent(builder::then);
      LiteralCommandNode<Object> canonicalNode = commandDispatcher.register(builder);

      for (String alias : command.getAliases()) {
         CommandAliasBuilder aliasBuilder = new CommandAliasBuilder(alias);

         for (CommandNode child : canonicalNode.getChildren()) {
            aliasBuilder.then(child);
         }

         aliasBuilder.executes(canonicalNode.getCommand());
         commandDispatcher.register(aliasBuilder);
      }

      return this.entries.add(command);
   }

   public static String getPrefix() {
      return commandPrefix;
   }

   private void registerModuleAliasCommands() {
      for (Module module : ClientContext.moduleRegistry.getRegistry()) {
         Optional<Command> matchingCommand = this.findFirst(command -> {
            String commandName = command.getName();
            String moduleName = module.getName();
            return commandName.equalsIgnoreCase(moduleName);
         });
         if (!matchingCommand.isPresent()) {
            for (String alias : module.getAliases()) {
               ModuleAliasBuilder aliasBuilder = new ModuleAliasBuilder(alias.toLowerCase());
               ModuleCommand moduleCommand = new ModuleCommand(module);
               moduleCommand.exec(aliasBuilder);
               commandDispatcher.register(aliasBuilder);
            }
         }
      }
   }
}
