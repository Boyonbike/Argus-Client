package dev.argus.client.render;

import dev.argus.client.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshBlockContextTest {
   @BeforeAll
   static void bootstrap() {
      TestBootstrap.bootStrap();
   }

   @Test
   void storesAndClearsTheMeshedBlock() {
      BlockState state = Blocks.STONE.defaultBlockState();
      try {
         MeshBlockContext.set(state);
         assertSame(state, MeshBlockContext.get());
      } finally {
         MeshBlockContext.clear();
      }
      assertNull(MeshBlockContext.get());
   }
}
