package com.jagrosh.discordipc.entities;

public enum PartyPrivacy {
   Private,
   Public;

   public static PartyPrivacy from(int ordinal) {
      for (PartyPrivacy privacy : values()) {
         if (privacy.ordinal() == ordinal) {
            return privacy;
         }
      }

      return Public;
   }
}
