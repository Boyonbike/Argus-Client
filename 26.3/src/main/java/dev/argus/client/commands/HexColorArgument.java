package dev.argus.client.commands;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.argus.client.render.ColorUtils;
import java.awt.Color;

public class HexColorArgument implements ArgumentType {
   private static final LiteralMessage INVALID_COLOR_MESSAGE = new LiteralMessage("Invalid color provided.");

   public Color parse(StringReader reader) throws CommandSyntaxException {
      try {
         if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
         }

         String text = reader.readString();
         return text.isEmpty() ? Color.WHITE : ColorUtils.parseHexColor(text);
      } catch (IllegalArgumentException failure) {
         throw new SimpleCommandExceptionType(INVALID_COLOR_MESSAGE).createWithContext(reader);
      }
   }
}
