package dev.argus.client.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Resolves the Argus Client data directory. On first start it imports a sibling legacy settings
 * folder, and on every start it updates old client names inside the saved configs.
 */
public final class ClientDataDirectory {
    /** Legacy settings folders in import priority. */
    static final String[] IMPORT_FOLDERS = {"eggclient", "zio", "randomstuffclient"};

    private ClientDataDirectory() {}

    /** Kept under this name because the base client calls it on startup. */
    public static Path migrateLegacy(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(target))
                throw new UncheckedIOException(new IOException("Client data path is not a directory: " + target));
        } else {
            for (String folder : IMPORT_FOLDERS) {
                Path previous = target.resolveSibling(folder);
                if (Files.isDirectory(previous, LinkOption.NOFOLLOW_LINKS)) {
                    copyInto(previous, target);
                    break;
                }
            }
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
            throw new UncheckedIOException("Could not import settings from " + source + " into " + target, failure);
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
