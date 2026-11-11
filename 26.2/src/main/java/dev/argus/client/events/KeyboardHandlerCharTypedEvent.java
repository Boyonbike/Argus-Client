package dev.argus.client.events;

public final class KeyboardHandlerCharTypedEvent extends CancellableEvent {
   private final int codePoint;

   public String getText() {
      return Character.toString(this.codePoint);
   }

   public char getCharacter() {
      return this.getText().charAt(0);
   }

   public KeyboardHandlerCharTypedEvent(int codePoint) {
      this.codePoint = codePoint;
   }
}
