package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.lang.reflect.Array;
import java.util.concurrent.CompletableFuture;

public class ArrayArgumentType implements ArgumentType<Object[]> {
   private final ArgumentType<?> elementArgument;
   private final Class<?> elementType;

   public ArrayArgumentType(ReifiedArgumentType elementArgument) {
      this.elementArgument = elementArgument;
      this.elementType = elementArgument.getReifiedType();
   }

   public ArrayArgumentType(ArgumentType<?> elementArgument, Class<?> elementType) {
      this.elementArgument = elementArgument;
      this.elementType = elementType;
   }

   public Object[] parse(StringReader reader) throws CommandSyntaxException {
      String remainingInput = reader.getRemaining();
      int inputLength = reader.getTotalLength();
      reader.setCursor(inputLength);
      String[] elements = remainingInput.split(" ");
      Class<?> componentType = this.elementType;
      Object[] parsedElements = (Object[])Array.newInstance(componentType, elements.length);

      for (int index = 0; index < elements.length; index++) {
         ArgumentType<?> argument = this.elementArgument;
         StringReader elementReader = new StringReader(elements[index]);
         parsedElements[index] = argument.parse(elementReader);
      }

      return parsedElements;
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String[] elements = builder.getRemaining().split(" ");
      if (elements.length == 0) {
         ArgumentType<?> argument = this.elementArgument;
         return argument.listSuggestions(context, builder);
      } else {
         int suggestionStart = builder.getStart();
         String remainingInput = builder.getRemaining();
         int remainingEnd = suggestionStart + remainingInput.length();
         String lastElement = elements[elements.length - 1];
         int lastElementStart = remainingEnd - lastElement.length();
         ArgumentType<?> argument = this.elementArgument;
         SuggestionsBuilder lastElementSuggestions = new SuggestionsBuilder(builder.getInput(), lastElementStart);
         return argument.listSuggestions(context, lastElementSuggestions);
      }
   }
}
