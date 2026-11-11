package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Vec3i;

public enum BlockerTargetOffsets implements NamedEntry {
   FULL(
      "Full",
      new Vec3i(1, 0, 0),
      new Vec3i(0, 0, 1),
      new Vec3i(-1, 0, 0),
      new Vec3i(0, 0, -1),
      new Vec3i(1, 1, 0),
      new Vec3i(0, 1, 1),
      new Vec3i(-1, 1, 0),
      new Vec3i(0, 1, -1),
      new Vec3i(0, 2, 0),
      new Vec3i(0, 3, 0)
   ),
   SURROUND("Surround", new Vec3i(1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(0, 0, -1)),
   CEV("Cev", new Vec3i(0, 2, 0), new Vec3i(0, 3, 0)),
   CEV_EXTENDED("CevExtend", new Vec3i(1, 1, 0), new Vec3i(0, 1, 1), new Vec3i(-1, 1, 0), new Vec3i(0, 1, -1));

   private final String displayName;
   private final List<Vec3i> offsets;

   BlockerTargetOffsets(String displayName, Vec3i... targetOffsets) {
      this.displayName = displayName;
      this.offsets = Arrays.asList(targetOffsets);
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   public List<Vec3i> getOffsets() {
      return this.offsets;
   }
}
