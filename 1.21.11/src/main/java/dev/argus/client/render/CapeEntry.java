package dev.argus.client.render;

import net.minecraft.resources.Identifier;

public record CapeEntry(String playerName, String textureFileName) {
   public Identifier getTexture() {
      String texturePath = "capes/" + this.textureFileName;
      return Identifier.fromNamespaceAndPath("argusclient", texturePath);
   }
}
