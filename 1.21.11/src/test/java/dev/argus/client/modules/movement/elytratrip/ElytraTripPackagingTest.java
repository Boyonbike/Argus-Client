package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class ElytraTripPackagingTest {
   private static final String MODULE = "dev/argus/client/modules/movement/elytratrip/ElytraTripModule";

   @Test
   void moduleIsPackagedAndRegistered() throws Exception {
      try (ZipFile jar = new ZipFile(Path.of(System.getProperty("argus.release.jar")).toFile())) {
         assertNotNull(jar.getEntry(MODULE + ".class"));
         ClassNode registry = new ClassNode();
         new ClassReader(jar.getInputStream(jar.getEntry("dev/argus/client/managers/ModuleRegistry.class"))).accept(registry, 0);
         boolean constructs = false;
         for (MethodNode method : registry.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
               if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(MODULE)) constructs = true;
            }
         }
         assertTrue(constructs, "ModuleRegistry never constructs ElytraTripModule");
      }
   }
}
