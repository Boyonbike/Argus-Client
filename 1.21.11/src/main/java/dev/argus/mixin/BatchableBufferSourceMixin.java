package dev.argus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.argus.client.render.BufferSourceCleanup;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import net.raphimc.immediatelyfast.feature.core.ByteBufferBuilderPool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(BatchableBufferSource.class)
public class BatchableBufferSourceMixin implements BufferSourceCleanup {
   @Shadow(remap = false)
   @Final
   protected Map dynamicBuffers;
   @Shadow(remap = false)
   @Final
   protected Set activeRenderTypes;

   @Override
   public void argusclient$cleanAll() {
      for (Object activeRenderType : this.activeRenderTypes) {
         RenderType renderType = (RenderType)activeRenderType;
         ReferenceSet builders = (ReferenceSet)this.dynamicBuffers.remove(renderType);
         if (builders != null) {
            ObjectIterator var5 = builders.iterator();

            while (var5.hasNext()) {
               Object activeBuilder = var5.next();
               BufferBuilder builder = (BufferBuilder)activeBuilder;
               MeshData mesh = builder.build();
               ByteBufferBuilderPool.returnBufferBuilderSafe(builder.buffer);
               if (mesh != null) {
                  mesh.close();
               }
            }

            builders.clear();
         }
      }
   }
}
