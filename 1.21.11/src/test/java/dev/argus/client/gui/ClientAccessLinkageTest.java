package dev.argus.client.gui;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Check shared client field names without starting Minecraft or initializing its classes. */
final class ClientAccessLinkageTest {
    @Test
    void allSharedClientFieldReferencesResolveInThePackagedClient() throws Exception {
        String owner = "dev/argus/client/ClientAccess";
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            assertNotNull(jar.getEntry(owner + ".class"), "ClientAccess must use the Argus Client package");
            ClassNode access = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getEntry(owner + ".class"))).accept(access, 0);
            Set<String> declarations = new HashSet<>();
            access.fields.forEach(field -> declarations.add(field.name + ":" + field.desc));
            int references = 0;
            for (var entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode type = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(type, 0);
                for (var method : type.methods) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode field && field.owner.equals(owner)) {
                            references++;
                            assertTrue(declarations.contains(field.name + ":" + field.desc),
                                    type.name + "." + method.name + " refers to missing field " + field.name);
                        }
                    }
                }
            }
            assertTrue(references > 0, "No shared client field references inspected");
        }
    }
}
