package dev.argus.client.render.text;

import dev.argus.client.utils.inventory.CharacterConsumer;

public class ExplicitCharacterRange implements CharacterRange {
   private final char[] characters;

   public ExplicitCharacterRange(char... characters) {
      this.characters = characters;
   }

   @Override
   public void forEachCharacter(CharacterConsumer consumer) {
      for (char character : this.characters) {
         consumer.accept(character);
      }
   }
}
