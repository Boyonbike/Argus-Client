package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class RelativeFloatArgument implements ArgumentType {
   public static RelativeFloatArgument relativeFloatArg() {
      return new RelativeFloatArgument();
   }

   public RelativeFloatArgument.RelativeFloat parse(StringReader reader) throws CommandSyntaxException {
      boolean relative = reader.canRead() && reader.peek() == '~';
      if (relative) {
         reader.skip();
      }

      float value = !relative || reader.canRead() && reader.peek() != ' ' ? reader.readFloat() : 0.0F;
      return new RelativeFloatArgument.RelativeFloat(value, relative);
   }

   public record RelativeFloat(float value, boolean relative) {
   }
}
