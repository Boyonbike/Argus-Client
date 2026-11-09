package com.jagrosh.discordipc.entities;

public enum ActivityType {
   Playing,
   Streaming,
   Listening,
   Watching,
   Custom,
   Competing;

   public static ActivityType from(int ordinal) {
      for (ActivityType type : values()) {
         if (type.ordinal() == ordinal) {
            return type;
         }
      }

      return Playing;
   }
}
