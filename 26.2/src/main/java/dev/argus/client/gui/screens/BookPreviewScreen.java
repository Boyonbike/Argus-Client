package dev.argus.client.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

/** A read-only book view opened from a tooltip; closing it returns to the screen it was opened from. */
public class BookPreviewScreen extends BookViewScreen {
   private final Screen parentScreen;

   public BookPreviewScreen(BookAccess book, Screen parentScreen) {
      super(book);
      this.parentScreen = parentScreen;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parentScreen);
   }
}
