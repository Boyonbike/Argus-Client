package dev.argus.client.config;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ClientDataDirectoryTest {
    @TempDir Path game;

    @Test
    void keepsExistingArgusClientSettings() throws Exception {
        Path target = Files.createDirectory(game.resolve("argusclient"));
        Files.writeString(target.resolve("config.json"), "current");
        assertEquals(target, ClientDataDirectory.migrateLegacy(target));
        assertEquals("current", Files.readString(target.resolve("config.json")));
    }

    @Test
    void returnsMissingDirectoryUnchanged() {
        Path target = game.resolve("argusclient");
        assertEquals(target, ClientDataDirectory.migrateLegacy(target));
    }

    @Test
    void rejectsFileInPlaceOfDirectory() throws Exception {
        Path target = Files.writeString(game.resolve("argusclient"), "not a folder");
        assertThrows(UncheckedIOException.class, () -> ClientDataDirectory.migrateLegacy(target));
    }
}
