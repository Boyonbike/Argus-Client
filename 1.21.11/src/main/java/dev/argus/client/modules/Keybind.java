package dev.argus.client.modules;

import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.KeyUtils;

public record Keybind(int code, Keybind.Mode mode, boolean mouse) {
   public static final Keybind UNBOUND = new Keybind(-1, Keybind.Mode.TOGGLE, false);

   public boolean isUnbound() {
      return this.code < 0;
   }

   public static Keybind toggle(int code, boolean mouse) {
      return new Keybind(code, Keybind.Mode.TOGGLE, mouse);
   }

   public Keybind withMode(Keybind.Mode mode) {
      return new Keybind(this.code, mode, this.mouse);
   }

   public Keybind withCode(int code) {
      return new Keybind(code, this.mode, this.mouse);
   }

   public String getDisplayName() {
      return KeyUtils.getKeyName(this);
   }

   public Keybind withMouse(boolean mouse) {
      return new Keybind(this.code, this.mode, mouse);
   }

   public enum Mode implements NamedEntry {
      TOGGLE("Toggle", "toggle"),
      HOLD("Hold", "hold"),
      HOLD_REVERSE("ReverseHold", "hold_reverse");

      private final String displayName;
      private final String serializedName;

      @Override
      public String getName() {
         return this.displayName;
      }

      Mode(String displayName, String serializedName) {
         this.displayName = displayName;
         this.serializedName = serializedName;
      }

      public String getSerializedName() {
         return this.serializedName;
      }

      static Keybind.Mode fromSerializedName(String value) {
         return switch (value.trim().toLowerCase()) {
            case "hold" -> HOLD;
            case "hold_reverse" -> HOLD_REVERSE;
            default -> TOGGLE;
         };
      }
   }
}
