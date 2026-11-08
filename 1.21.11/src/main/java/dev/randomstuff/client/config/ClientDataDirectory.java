package dev.randomstuff.client.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Keeps settings when upgrading to the Random Stuff Client data directory. */
public final class ClientDataDirectory {
    private ClientDataDirectory() {}

    public static Path migrateLegacy(Path target) {
        Path staged = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(target)) throw new IOException("Client data path is not a directory: " + target);
                return target;
            }
            Path previous = null;
            // These old directory names are compatibility inputs, never runtime namespaces.
            for (String name : new String[]{"eggclient", "shuclient", "mio-fabric"}) {
                Path candidate = target.resolveSibling(name);
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    previous = candidate;
                    break;
                }
            }
            if (previous == null) return target;
            Path source = previous;
            staged = Files.createTempDirectory(target.toAbsolutePath().getParent(), ".randomstuffclient-migration-");
            Path destination = staged;
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(destination.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, destination.resolve(source.relativize(file)),
                            StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.move(staged, target);
            return target;
        } catch (IOException failure) {
            if (staged != null) {
                try {
                    Files.walkFileTree(staged, new SimpleFileVisitor<>() {
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
            throw new UncheckedIOException("Could not migrate settings to " + target, failure);
        }
    }
}
