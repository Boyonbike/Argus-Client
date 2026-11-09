package dev.argus.client.modules.misc;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

public final class ClipboardImage implements Transferable {
   private Image image;

   public ClipboardImage(Image image) {
      this.image = image;
   }

   @Override
   public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{DataFlavor.imageFlavor};
   }

   @Override
   public boolean isDataFlavorSupported(DataFlavor flavor) {
      return DataFlavor.imageFlavor.equals(flavor);
   }

   @Override
   public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!DataFlavor.imageFlavor.equals(flavor)) {
         throw new UnsupportedFlavorException(flavor);
      } else {
         return this.image;
      }
   }
}
