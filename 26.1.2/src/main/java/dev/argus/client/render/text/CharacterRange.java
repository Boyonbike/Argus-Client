package dev.argus.client.render.text;

import dev.argus.client.utils.inventory.CharacterConsumer;
import java.util.Collection;

public interface CharacterRange {
   default void addTo(Collection characters) {
      this.forEachCharacter(characters::add);
   }

   void forEachCharacter(CharacterConsumer var1);
}
