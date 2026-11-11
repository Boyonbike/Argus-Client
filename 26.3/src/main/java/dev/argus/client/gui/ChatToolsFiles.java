package dev.argus.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The Chat Tools window's file picker and reader for name lists (.csv or .txt). Both run on a worker thread: blocking
 * the game thread while the player browses would also stop keep-alive replies to the server.
 */
final class ChatToolsFiles {
    static final long MAX_BYTES = 1 << 20;

    private ChatToolsFiles() {}

    /** Asks for one list file; empty when cancelled. Throws if no native picker is available. */
    static List<Path> choose(String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.pointers(stack.UTF8("*.csv"), stack.UTF8("*.txt"));
            String path = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Name lists (.csv, .txt)", false);
            return path == null ? List.of() : List.of(Path.of(path));
        }
    }

    /** Reads every file whole, refusing other types and oversized files so a partial batch is never applied. */
    static List<byte[]> read(List<Path> files) throws IOException {
        List<byte[]> contents = new ArrayList<>();
        for (Path file : files) {
            String name = String.valueOf(file.getFileName());
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) throw new IOException(name + " isn't a .csv or .txt file.");
            if (Files.size(file) > MAX_BYTES) throw new IOException(name + " is too large for a name list.");
            contents.add(Files.readAllBytes(file));
        }
        return contents;
    }
}
