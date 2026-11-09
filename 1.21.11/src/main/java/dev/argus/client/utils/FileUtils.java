package dev.argus.client.utils;

import dev.argus.client.render.text.ReverseStringFormatter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;

public final class FileUtils {
   private static final String suffixPathPattern = "\u0001\u0001";

   public static byte[] readBytes(Path path) throws IOException {
      FileInputStream input = null;

      byte[] bytes;
      try {
         input = new FileInputStream(path.toFile());
         bytes = input.readAllBytes();
      } finally {
         if (input != null) {
            try {
               input.close();
            } catch (Exception var9) {
            }
         }
      }

      return bytes;
   }

   public static Path findExistingWithSuffixes(Path path, String... suffixes) {
      File baseFile = path.toFile();
      if (baseFile.exists()) {
         return path;
      }

      for (String suffix : suffixes) {
         String pathText = String.valueOf(path);
         String candidatePathText = new ReverseStringFormatter().prepend(suffix).prepend(pathText).format(suffixPathPattern);
         Path candidatePath = Path.of(candidatePathText);
         File candidateFile = candidatePath.toFile();
         if (candidateFile.exists()) {
            return candidatePath;
         }
      }

      return path;
   }

   public static void writeBytes(Path path, byte[] bytes) throws IOException {
      try (FileOutputStream output = new FileOutputStream(path.toFile())) {
         output.write(bytes);
      }
   }

   public static void writeUtf8(Path path, String text) throws IOException {
      Charset charset = StandardCharsets.UTF_8;
      writeBytes(path, text.getBytes(charset));
   }

   public static void writeUtf8Atomically(Path path, String text) throws IOException {
      Path target = path.toAbsolutePath();
      if (Files.isSymbolicLink(target)) target = target.toRealPath();
      Path staged = Files.createTempFile(target.getParent(), ".argus-config-", ".tmp");
      try {
         Files.writeString(staged, text, StandardCharsets.UTF_8);
         if (Files.exists(target) && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null)
            Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(target));
         try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException failure) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(staged);
      }
   }

   public static String readUtf8(Path path) throws IOException {
      return new String(readBytes(path), StandardCharsets.UTF_8);
   }
}
