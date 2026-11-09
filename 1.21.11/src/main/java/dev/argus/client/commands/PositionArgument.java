package dev.argus.client.commands;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands.ParseFunction;
import net.minecraft.commands.SharedSuggestionProvider.TextCoordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.world.phys.Vec3;

public class PositionArgument implements ArgumentType {
   private static final Collection EXAMPLES = List.of("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "0.1 -0.5 .9", "~0.5 ~1 ~-5");

   public Vec3 parse(StringReader reader) throws CommandSyntaxException {
      int start = reader.getCursor();
      LocalPlayer player = Minecraft.getInstance().player;
      if (reader.canRead() && reader.peek() == '^') {
         double left = readLocal(reader);
         readSeparator(reader, start);
         double up = readLocal(reader);
         readSeparator(reader, start);
         double forward = readLocal(reader);
         Vec3 direction = player.getLookAngle();
         Vec3 upwards = player.getUpVector(1.0F);
         Vec3 leftwards = upwards.cross(direction);
         return player.position().add(leftwards.scale(left)).add(upwards.scale(up)).add(direction.scale(forward));
      } else {
         WorldCoordinate x = WorldCoordinate.parseDouble(reader, false);
         readSeparator(reader, start);
         WorldCoordinate y = WorldCoordinate.parseDouble(reader, false);
         readSeparator(reader, start);
         WorldCoordinate z = WorldCoordinate.parseDouble(reader, false);
         Vec3 position = player.position();
         return new Vec3(x.get(position.x), y.get(position.y), z.get(position.z));
      }
   }

   private static void readSeparator(StringReader reader, int start) throws CommandSyntaxException {
      if (reader.canRead() && reader.peek() == ' ') {
         reader.skip();
      } else {
         reader.setCursor(start);
         throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
      }
   }

   private static double readLocal(StringReader reader) throws CommandSyntaxException {
      if (reader.canRead() && reader.peek() == '^') {
         reader.skip();
         return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
      } else {
         throw new SimpleCommandExceptionType(new LiteralMessage("Cannot mix local and world coordinates.")).createWithContext(reader);
      }
   }

   public Collection getExamples() {
      return EXAMPLES;
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      if (!(context.getSource() instanceof SharedSuggestionProvider)) {
         return Suggestions.empty();
      }

      String remaining = builder.getRemaining();
      if (!remaining.isEmpty()) {
         byte firstCharacterIndex = 0;
         char prefix = remaining.charAt(firstCharacterIndex);
         if (prefix == '^') {
            TextCoordinates localCoordinates = TextCoordinates.DEFAULT_LOCAL;
            Set localSuggestions = Collections.singleton(localCoordinates);
            ParseFunction parser = this::parse;
            Predicate<String> validator = Commands.createValidator(parser);
            return SharedSuggestionProvider.suggestCoordinates(remaining, localSuggestions, builder, validator);
         }
      }

      SharedSuggestionProvider provider = (SharedSuggestionProvider)context.getSource();
      Collection absoluteSuggestions = provider.getAbsoluteCoordinates();
      ParseFunction parser = this::parse;
      Predicate<String> validator = Commands.createValidator(parser);
      return SharedSuggestionProvider.suggestCoordinates(remaining, absoluteSuggestions, builder, validator);
   }
}
