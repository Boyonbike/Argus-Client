package dev.argus.client.events;

public final class CommandExecuteEvent extends CancellableEvent {
   private final String command;

   public String getCommand() {
      return this.command;
   }

   public CommandExecuteEvent(String command) {
      this.command = command;
   }
}
