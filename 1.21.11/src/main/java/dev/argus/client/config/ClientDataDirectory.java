package dev.argus.client.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

/** Resolves the Argus Client data directory. */
public final class ClientDataDirectory {
    private ClientDataDirectory() {}

    /**
     * Returns the Argus Client data directory. Kept under this name because the base client calls it
     * on startup; settings from other clients are no longer imported.
     */
    public static Path migrateLegacy(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target))
            throw new UncheckedIOException(new IOException("Client data path is not a directory: " + target));
        return target;
    }
}
