package dev.argus.client.gui;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * File pickers. 26.3 dropped the tinyfd library, so this uses the JDK's
 * cross-platform chooser on the Swing event thread. Callers run it from a
 * worker thread; the dialog does not block the game thread.
 */
public final class FileDialogs {
   public static Path openFile(String title, String description, String... extensions) {
      AtomicReference<Path> selected = new AtomicReference<>();
      try {
         SwingUtilities.invokeAndWait(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
               selected.set(chooser.getSelectedFile().toPath());
            }
         });
      } catch (Exception failure) {
         return null;
      }
      return selected.get();
   }

   private FileDialogs() {
   }
}
