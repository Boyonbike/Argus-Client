package dev.randomstuff.client.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ClientDataDirectoryTest {
    @TempDir Path game;

    @Test
    void importsPreviousClientDataWithoutRemovingTheOriginal() throws Exception {
        Path old = Files.createDirectory(game.resolve("eggclient"));
        Files.createDirectories(old.resolve("presets"));
        Files.writeString(old.resolve("presets/friends.json"), "preserved settings");
        Path target = game.resolve("randomstuffclient");
        assertEquals(target, ClientDataDirectory.migrateLegacy(target));
        assertTrue(Files.exists(target.resolve("presets/friends.json")), "Previous settings were not migrated");
        assertEquals("preserved settings", Files.readString(target.resolve("presets/friends.json")));
        assertTrue(Files.exists(old.resolve("presets/friends.json")), "Original settings must remain intact");
    }

    @Test
    void keepsExistingRandomStuffClientSettings() throws Exception {
        Files.createDirectory(game.resolve("eggclient"));
        Files.writeString(game.resolve("eggclient/config.json"), "old");
        Path target = Files.createDirectory(game.resolve("randomstuffclient"));
        Files.writeString(target.resolve("config.json"), "current");
        ClientDataDirectory.migrateLegacy(target);
        assertEquals("current", Files.readString(target.resolve("config.json")));
    }
}
