package dev.argus.client.audio;

import dev.argus.client.utils.collections.NamedIdentifier;

public enum BuiltinSound {
   NONE("none"),
   KABAN("kaban"),
   NEVERLOSE("neverlose"),
   CSS("css"),
   COD("cod"),
   QUAKE("quake"),
   TOOLBOX("toolbox"),
   WARNING("warning"),
   STEAM("steam"),
   WHATSAPP("whatsapp"),
   VK("vk"),
   ICQ("icq"),
   STALKER("stalker"),
   HOVER("hover"),
   CLICK("click"),
   RCLICK("rclick"),
   BODYSPLAT("bodysplat"),
   BLIP("blip"),
   BLIP2("blip2"),
   HL2("hl2"),
   PICKUP("pickup"),
   PICKUP2("pickup2"),
   HORROR("horror");

   private final NamedIdentifier identifier;

   public NamedIdentifier getIdentifier() {
      return this.identifier;
   }

   BuiltinSound(String name) {
      if (name.equals("none")) {
         this.identifier = NamedIdentifier.NONE;
      } else {
         this.identifier = new NamedIdentifier(name);
      }
   }
}
