import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Renames the precompiled base client from Random Stuff Client to Argus Client: Java packages
 * (dev.randomstuff → dev.argus), the main class, the mod ID / resource namespace
 * (randomstuffclient → argusclient), resource paths, member names and display text. Class files are
 * rewritten with ASM so every reference stays consistent; binary assets are copied unchanged.
 *
 * Run from the project directory (ASM jars from the Gradle cache on the class path):
 *   java -cp asm.jar:asm-commons.jar:asm-tree.jar tools/RenameClient.java libs/client-base.jar out.jar
 */
public final class RenameClient {
    /** Applied in order to class names, member names, string constants, paths and text files. */
    private static final String[][] RULES = {
            {"dev/randomstuff/", "dev/argus/"},
            {"dev.randomstuff.", "dev.argus."},
            {"dev$randomstuffclient$", "dev$argus$"},
            {"Random Stuff Client", "Argus Client"},
            {"RandomStuffClient", "ArgusClient"},
            {"randomstuffclient", "argusclient"},
    };

    static String rename(String value) {
        if (value == null) return null;
        for (String[] rule : RULES) value = value.replace(rule[0], rule[1]);
        return value;
    }

    private static final Remapper REMAPPER = new Remapper(org.objectweb.asm.Opcodes.ASM9) {
        @Override public String map(String internalName) { return internalName.startsWith("dev/randomstuff/") ? rename(internalName) : internalName; }
        @Override public String mapMethodName(String owner, String name, String descriptor) { return rename(name); }
        @Override public String mapFieldName(String owner, String name, String descriptor) { return rename(name); }
        @Override public String mapInvokeDynamicMethodName(String name, String descriptor, org.objectweb.asm.Handle bootstrap, Object... arguments) { return rename(name); }
        @Override public String mapRecordComponentName(String owner, String name, String descriptor) { return rename(name); }
        @Override public Object mapValue(Object value) { return value instanceof String text ? rename(text) : super.mapValue(value); }
    };

    private static byte[] renameClass(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, REMAPPER) {
            @Override public void visitSource(String source, String debug) { super.visitSource(rename(source), debug); }
        }, 0);
        return writer.toByteArray();
    }

    private static boolean isText(String name) {
        String lower = name.toLowerCase();
        for (String ext : new String[]{".json", ".ini", ".txt", ".accesswidener", ".mcmeta", ".vsh", ".fsh", ".glsl", ".frag", ".vert", ".properties"})
            if (lower.endsWith(ext)) return true;
        return false;
    }

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]), target = Path.of(args[1]);
        if (Files.isSameFile(source.toAbsolutePath().getParent(), target.toAbsolutePath().getParent()) && source.getFileName().equals(target.getFileName()))
            throw new IllegalArgumentException("Use distinct input and output paths");
        int classes = 0, texts = 0, moved = 0;
        try (ZipFile in = new ZipFile(source.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Enumeration<? extends ZipEntry> entries = in.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName(), renamed = rename(name);
                if (!renamed.equals(name)) moved++;
                ZipEntry copy = new ZipEntry(renamed);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                if (!entry.isDirectory()) {
                    byte[] data;
                    try (InputStream stream = in.getInputStream(entry)) { data = stream.readAllBytes(); }
                    if (name.endsWith(".class")) { data = renameClass(data); classes++; }
                    else if (isText(name)) {
                        String text = new String(data, StandardCharsets.UTF_8), next = rename(text);
                        if (!next.equals(text)) texts++;
                        data = next.getBytes(StandardCharsets.UTF_8);
                    }
                    out.write(data);
                }
                out.closeEntry();
            }
        }
        System.out.printf("Renamed %d classes, %d text files and %d paths into %s%n", classes, texts, moved, target);
    }
}
