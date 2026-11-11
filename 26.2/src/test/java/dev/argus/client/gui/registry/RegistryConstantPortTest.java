package dev.argus.client.gui.registry;

import dev.argus.client.TestBootstrap;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry catalog must keep the same entries after 26.2 moved the constants to EntityTypes. */
class RegistryConstantPortTest {
   @BeforeAll
   static void bootstrap() {
      TestBootstrap.bootStrap();
   }

   @Test
   void catalogUsesEntityTypesConstants() {
      Set<?> entries = RegistryEntryCatalog.entriesFor(BuiltInRegistries.ENTITY_TYPE).entrySet();
      assertTrue(entries.contains(EntityTypes.LLAMA));
      assertTrue(entries.contains(EntityTypes.ENDER_PEARL));
   }
}
