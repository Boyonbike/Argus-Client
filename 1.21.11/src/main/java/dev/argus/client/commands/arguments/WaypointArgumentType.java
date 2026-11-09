package dev.argus.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.utils.world.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class WaypointArgumentType implements ArgumentType<Waypoint> {
   private static final String unknownWaypointFormat = "Waypoint %s doesn't exists";

   public Waypoint parse(StringReader reader) throws CommandSyntaxException {
      String requestedName = reader.readString();
      WaypointRegistry registryForParsing = ClientContext.waypointRegistry;

      for (Waypoint waypoint : (List<Waypoint>)registryForParsing.getRegistry()) {
         if (waypoint != null && waypoint.getName() != null) {
            String waypointName = waypoint.getName();
            if (waypointName.equalsIgnoreCase(requestedName)) {
               return waypoint;
            }
         }
      }

      DynamicCommandExceptionType unknownWaypointError = new DynamicCommandExceptionType(argument -> {
         String errorFormat = unknownWaypointFormat;
         Object[] formatArguments = new Object[]{argument};
         String message = String.format(errorFormat, formatArguments);
         return Component.literal(message);
      });
      throw unknownWaypointError.create(requestedName);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      ArrayList<Waypoint> invalidWaypoints = new ArrayList<>();
      WaypointRegistry registryForSuggestionScan = ClientContext.waypointRegistry;

      for (Waypoint waypoint : (List<Waypoint>)registryForSuggestionScan.getRegistry()) {
         if (waypoint != null && (waypoint.getServer() == null || waypoint.getDimension() == null || waypoint.getName() == null)) {
            invalidWaypoints.add(waypoint);
         }
      }

      for (Waypoint invalidWaypoint : invalidWaypoints) {
         ClientContext.waypointRegistry.unregisterWaypoint(invalidWaypoint);
      }

      WaypointRegistry suggestionRegistry = ClientContext.waypointRegistry;
      Stream<String> waypointNames = suggestionRegistry.getRegistry().stream().map(Waypoint::getName);
      return SharedSuggestionProvider.suggest(waypointNames, builder);
   }
}
