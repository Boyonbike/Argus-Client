package com.jagrosh.discordipc.entities;

public enum StatusDisplayType {
   Name,
   State,
   Details;

   public static StatusDisplayType from(int ordinal) {
      for (StatusDisplayType type : values()) {
         if (type.ordinal() == ordinal) {
            return type;
         }
      }

      return Name;
   }
}
