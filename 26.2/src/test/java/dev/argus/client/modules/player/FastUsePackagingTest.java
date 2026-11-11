package dev.argus.client.modules.player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class FastUsePackagingTest {
    @Test void fastUseMixinIsPackagedAndRegistered() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            assertNotNull(jar.getEntry("dev/argus/mixin/MinecraftFastUseMixin.class"));
            String mixins = new String(
                jar.getInputStream(jar.getEntry("argusclient.mixins.json")).readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(mixins.contains("\"MinecraftFastUseMixin\""));
        }
    }

    @Test void fastUseModuleIsRegisteredAndDescribed() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
            assertNotNull(jar.getEntry("dev/argus/client/modules/player/FastUseModule.class"));

            ClassNode registry = new ClassNode();
            new ClassReader(jar.getInputStream(
                jar.getEntry("dev/argus/client/managers/ModuleRegistry.class"))).accept(registry, 0);
            boolean constructsModule = false;
            outer:
            for (MethodNode method : registry.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW
                        && type.desc.equals("dev/argus/client/modules/player/FastUseModule")) {
                        constructsModule = true;
                        break outer;
                    }
                }
            }
            assertTrue(constructsModule, "ModuleRegistry must instantiate FastUseModule");

            String descriptions = new String(
                jar.getInputStream(jar.getEntry("assets/argusclient/descriptions.tsv"))
                    .readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(descriptions.contains("FastUse\tCooldown\t"));
            assertTrue(descriptions.contains("FastUse\tPreventContainerOpen\t"));
        }
    }
}
