package com.jagrosh.discordipc.entities;

public enum DiscordBuild {
   CANARY("//canary.discord.com/api"),
   PTB("//ptb.discord.com/api"),
   STABLE("//discord.com/api"),
   ANY;

   private final String endpoint;

   DiscordBuild(String endpoint) {
      this.endpoint = endpoint;
   }

   DiscordBuild() {
      this(null);
   }

   public static DiscordBuild from(int ordinal) {
      for (DiscordBuild build : values()) {
         if (build.ordinal() == ordinal) {
            return build;
         }
      }

      return ANY;
   }

   public static DiscordBuild from(String endpoint) {
      for (DiscordBuild build : values()) {
         if (build.endpoint != null && build.endpoint.equals(endpoint)) {
            return build;
         }
      }

      return ANY;
   }
}
