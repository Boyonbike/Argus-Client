package dev.argus.client.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Resolves the Argus Client data directory. On first start it imports an Egg Client settings folder
 * found next to it, and on every start it updates old client names inside the saved configs.
 */
public final class ClientDataDirectory {
    /** Folder an Egg Client install keeps its settings in; imported, never written to. */
    static final String EGG_FOLDER = "eggclient";

    private ClientDataDirectory() {}

    /** Kept under this name because the base client calls it on startup. */
    public static Path migrateLegacy(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(target))
                throw new UncheckedIOException(new IOException("Client data path is not a directory: " + target));
        } else {
            Path egg = target.resolveSibling(EGG_FOLDER);
            if (Files.isDirectory(egg, LinkOption.NOFOLLOW_LINKS)) copyInto(egg, target);
        }
        if (Files.isDirectory(target)) LegacyConfigRenamer.renameAll(target);
        return target;
    }

    /** Copies {@code source} to a staging folder, then moves it into place so a failed copy leaves nothing behind. */
    private static void copyInto(Path source, Path target) {
        Path staged = null;
        try {
            staged = Files.createTempDirectory(target.toAbsolutePath().getParent(), ".argusclient-migration-");
            Path destination = staged;
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, destination.resolve(source.relativize(file).toString()),
                            StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.move(staged, target);
        } catch (IOException failure) {
            if (staged != null) deleteQuietly(staged, failure);
            throw new UncheckedIOException("Could not import Egg Client settings into " + target, failure);
        }
    }

    private static void deleteQuietly(Path root, IOException failure) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }
}
