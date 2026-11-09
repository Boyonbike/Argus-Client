package com.jagrosh.discordipc.exceptions;

public class NoDiscordClientException extends Exception {
   private static final long serialVersionUID = 1L;

   public NoDiscordClientException() {
      super("No Valid Discord Client was found for this Instance");
   }
}
