package dev.argus.client.render;

import dev.argus.client.ArgusClient;
import dev.argus.client.render.text.CharacterRange;
import dev.argus.client.render.text.ContiguousCharacterRange;
import dev.argus.client.render.text.DeltaEncodedCharacterRange;
import dev.argus.client.render.text.ExplicitCharacterRange;

public enum FontLanguage {
   SPECIAL(new ExplicitCharacterRange('∞', '♫', '♪')),
   LATIN(new ContiguousCharacterRange(0, 383)),
   CYRILLIC(new ContiguousCharacterRange(1024, 1327)),
   CHINESE(new DeltaEncodedCharacterRange(19968, ArgusClient.class.getResourceAsStream("/assets/argusclient/font/range_cn.bin"))),
   JAPANESE(new DeltaEncodedCharacterRange(19968, ArgusClient.class.getResourceAsStream("/assets/argusclient/font/range_jp.bin"))),
   JAPANESE_ALPHABET(new ContiguousCharacterRange(12288, 12543));

   private final CharacterRange characterRange;

   FontLanguage(CharacterRange characterRange) {
      this.characterRange = characterRange;
   }

   public CharacterRange getRange() {
      return this.characterRange;
   }
}
