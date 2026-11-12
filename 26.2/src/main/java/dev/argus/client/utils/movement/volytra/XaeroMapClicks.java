package dev.argus.client.utils.movement.volytra;

/*
 * Ported from VolytraFly by Volizray (https://github.com/Volizray/VolytraFly-Addon), CC0 1.0 Universal.
 * See THIRD_PARTY_NOTICES.md and licenses/VolytraFly-CC0.txt.
 */

import com.mojang.blaze3d.platform.InputConstants;
import dev.argus.client.ArgusClient;
import dev.argus.client.utils.KeyUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;

/**
 * Notices a click on Xaero's World Map and reports which block it was on.
 *
 * <p>Nothing here is compiled against Xaero's code, so the client builds and runs with or without Xaero's World
 * Map installed. The map screen is recognised by its class name, the mouse is polled directly, and the block under
 * the cursor is read out of the map screen by reflection (the map keeps it in two int fields it refreshes every
 * frame to draw the coordinates in the corner). If a Xaero update renames those fields the click is reported
 * through {@link #takeError()} and the field names found are logged, rather than flying somewhere wrong.
 */
public final class XaeroMapClicks {
   public record Click(int x, int z) {}

   private static final String MAP_SCREEN_CLASS = "xaero.map.gui.GuiMap";
   private static final String[] HOVER_X_FIELDS = {"mouseBlockPosX"};
   private static final String[] HOVER_Z_FIELDS = {"mouseBlockPosZ"};
   /** A press and release further apart than this (in window pixels) was a drag, which pans the map. */
   private static final double CLICK_SLOP_PIXELS = 6;

   private boolean pressed;
   private String lastLoggedScreen = "";
   private double pressX;
   private double pressY;
   private String error;
   private boolean fieldsLogged;
   private final HashMap<String, Field> fieldCache = new HashMap<>();

   /** The reason the last click could not be read, once, or null. */
   public String takeError() {
      String e = this.error;
      this.error = null;
      return e;
   }

   public void reset() {
      this.pressed = false;
   }

   /**
    * Call once per client tick. Returns the clicked block when the given mouse button was just clicked (pressed and
    * released without dragging) on the map screen, otherwise null.
    */
   public Click poll(Minecraft mc, int button) {
      Screen screen = mc.gui.screen();
      if (screen == null || !isMapScreen(screen)) {
         this.pressed = false;
         this.logUnrecognisedXaeroScreen(screen);
         return null;
      }

      // GLFW numbers the mouse buttons the same way InputConstants does; vanilla's own pressed flags are a backup.
      MouseHandler mouse = mc.mouseHandler;
      boolean glfwDown = KeyUtils.isPhysicalMouseButtonDown(button);
      double cursorX = mouse.xpos();
      double cursorY = mouse.ypos();
      boolean vanillaDown = button == InputConstants.MOUSE_BUTTON_LEFT ? mouse.isLeftPressed()
         : button == InputConstants.MOUSE_BUTTON_RIGHT ? mouse.isRightPressed()
         : mouse.isMiddlePressed();

      if (glfwDown || vanillaDown) {
         if (!this.pressed) {
            this.pressed = true;
            this.pressX = cursorX;
            this.pressY = cursorY;
         }
         return null;
      }

      if (!this.pressed) return null;
      this.pressed = false;
      if (Math.hypot(cursorX - this.pressX, cursorY - this.pressY) > CLICK_SLOP_PIXELS) return null;
      return this.readHoveredBlock(screen);
   }

   /** If Xaero renamed its map screen, say so in the log (once per screen class) instead of failing silently. */
   private void logUnrecognisedXaeroScreen(Screen screen) {
      if (screen == null) return;
      String name = screen.getClass().getName();
      if (!name.toLowerCase(Locale.ROOT).contains("xaero") || name.equals(this.lastLoggedScreen)) return;
      this.lastLoggedScreen = name;
      ArgusClient.LOGGER.warn("Volytra Autonav: a Xaero screen is open ({}) but it is not the world map screen ({}).", name, MAP_SCREEN_CLASS);
   }

   private static boolean isMapScreen(Object screen) {
      for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
         if (c.getName().equals(MAP_SCREEN_CLASS)) return true;
      }
      return false;
   }

   private Click readHoveredBlock(Object screen) {
      Integer x = this.readInt(screen, HOVER_X_FIELDS);
      Integer z = this.readInt(screen, HOVER_Z_FIELDS);
      if (x == null || z == null) {
         this.error = "Couldn't read the clicked spot from Xaero's World Map (this version may have changed); see the log.";
         this.logFieldsOnce(screen);
         return null;
      }
      return new Click(x, z);
   }

   private Integer readInt(Object screen, String[] names) {
      for (String name : names) {
         Field field = this.findField(screen.getClass(), name);
         if (field == null) continue;
         try {
            if (field.get(screen) instanceof Number number) return number.intValue();
         } catch (ReflectiveOperationException | RuntimeException ignored) {
            // try the next name
         }
      }
      return null;
   }

   private Field findField(Class<?> type, String name) {
      String cacheKey = type.getName() + "#" + name;
      if (this.fieldCache.containsKey(cacheKey)) return this.fieldCache.get(cacheKey);

      Field found = null;
      for (Class<?> c = type; c != null && found == null; c = c.getSuperclass()) {
         try {
            Field field = c.getDeclaredField(name);
            field.setAccessible(true);
            found = field;
         } catch (NoSuchFieldException | RuntimeException ignored) {
            // keep looking up the class hierarchy
         }
      }
      this.fieldCache.put(cacheKey, found);
      return found;
   }

   private void logFieldsOnce(Object screen) {
      if (this.fieldsLogged) return;
      this.fieldsLogged = true;
      StringBuilder sb = new StringBuilder("Could not find the hovered-block fields on ").append(screen.getClass().getName()).append(". Fields found:");
      for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
         for (Field field : c.getDeclaredFields()) {
            sb.append("\n  ").append(c.getSimpleName()).append('.').append(field.getName()).append(" : ").append(field.getType().getSimpleName());
         }
      }
      ArgusClient.LOGGER.warn(sb.toString());
   }
}
