package dev.argus.client.modules.render.targets;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.modules.render.EntityCullingOverrides;
import dev.argus.client.render.ColorOutlineBufferSource;
import dev.argus.client.render.QuadSink;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * ESP's block meshes, one per 8x8x8 cell, kept on the GPU between frames (after Comet's Fast Storage ESP). A cell is
 * rebuilt only when its contents or the look settings changed, nearest first and within a per-frame time budget, so a
 * large idle stash costs one visibility test per cell and one draw per visible cell. Block entities use their real
 * models (captured once through the outline buffer); other blocks use their shape bounds.
 * <p>
 * Meshes are drawn into the outline pass's own colour and depth target: they hide each other but never hide behind
 * terrain, and the outline shader then turns that mask into the glow.
 */
public final class BlockMeshCache implements ClientAccess, AutoCloseable {
   /** Decides whether an entry is drawn and in what colour: returns ARGB, or 0 to leave it out. */
   public interface Paint {
      int color(long packed, BlockTargetIndex.Entry entry);
   }

   public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
      .withLocation("pipeline/argusclient_esp_cells")
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
      .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
      .withCull(false)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .build();
   private static final int MAX_MODELS_PER_FRAME = 128;

   private record Mesh(GpuBuffer vertices, int quads, long revision, int look) implements AutoCloseable {
      @Override
      public void close() {
         if (this.vertices != null) {
            this.vertices.close();
         }
      }
   }

   private record Visible(CellIndex.CellKey key, CellIndex.Cell<BlockTargetIndex.Entry> cell, double distanceSquared) {
   }

   private final Map<CellIndex.CellKey, Mesh> meshes = new HashMap<>();
   private final List<Visible> visible = new ArrayList<>();
   private final List<Visible> stale = new ArrayList<>();
   private final QuadSink sink = new QuadSink();
   private final BlockPos.MutableBlockPos neighbourPosition = new BlockPos.MutableBlockPos();
   private final Matrix4f transform = new Matrix4f();
   private ClientLevel level;
   private int drawn;

   /** Containers drawn last frame. */
   public int drawnCount() {
      return this.drawn;
   }

   /**
    * Rebuilds what's stale (within {@code budgetMillis}) and draws every visible cell into the outline target.
    *
    * @param look changes whenever a setting that affects colours or filtering changes; all meshes are then rebuilt
    * @return whether anything was drawn (the caller then lets the outline pass composite)
    */
   public boolean render(PoseStack poses, float partialTick, RenderTarget target, Paint paint, int look, double rangeSquared, double budgetMillis) {
      this.drawn = 0;
      ClientLevel current = argusclientMinecraft.level;
      if (current != this.level) {
         this.close();
         this.level = current;
      }

      if (current == null) {
         return false;
      }

      CellIndex<BlockTargetIndex.Entry> index = BlockTargetIndex.get().index();
      Vec3 camera = argusclientMinecraft.gameRenderer.mainCamera().position();
      this.collect(index, camera, look, rangeSquared);
      this.rebuild(poses, partialTick, paint, look, budgetMillis);
      return this.draw(poses, target, camera);
   }

   private void collect(CellIndex<BlockTargetIndex.Entry> index, Vec3 camera, int look, double rangeSquared) {
      this.visible.clear();
      this.stale.clear();
      Iterator<Map.Entry<CellIndex.CellKey, Mesh>> meshIterator = this.meshes.entrySet().iterator();
      while (meshIterator.hasNext()) {
         Map.Entry<CellIndex.CellKey, Mesh> entry = meshIterator.next();
         CellIndex.Cell<BlockTargetIndex.Entry> cell = index.cell(entry.getKey());
         if (cell == null || cell.size() == 0) {
            entry.getValue().close();
            meshIterator.remove();
         }
      }

      for (CellIndex.Cell<BlockTargetIndex.Entry> cell : index.cells()) {
         if (cell.size() == 0) {
            continue;
         }

         CellIndex.CellKey key = cell.key();
         double dx = key.minX() + 4.0 - camera.x;
         double dy = key.minY() + 4.0 - camera.y;
         double dz = key.minZ() + 4.0 - camera.z;
         double distanceSquared = dx * dx + dy * dy + dz * dz;
         // Cells are 8 blocks across; allow their half-diagonal so edge blocks inside range still draw.
         if (rangeSquared > 0 && distanceSquared > rangeSquared + 14.0 * Math.sqrt(rangeSquared) + 49.0) {
            continue;
         }

         AABB bounds = new AABB(key.minX() - 2, key.minY() - 2, key.minZ() - 2, key.minX() + 10, key.minY() + 10, key.minZ() + 10);
         if (!RotationManager.isVisible(bounds)) {
            continue;
         }

         Visible item = new Visible(key, cell, distanceSquared);
         this.visible.add(item);
         Mesh mesh = this.meshes.get(key);
         if (mesh == null || mesh.revision() != cell.revision() || mesh.look() != look) {
            this.stale.add(item);
         }
      }

      this.stale.sort(Comparator.comparingDouble(Visible::distanceSquared));
      // Front to back, so the mask's own depth rejects dense stash overdraw early.
      this.visible.sort(Comparator.comparingDouble(Visible::distanceSquared));
   }

   private void rebuild(PoseStack poses, float partialTick, Paint paint, int look, double budgetMillis) {
      if (this.stale.isEmpty()) {
         return;
      }

      long deadline = System.nanoTime() + (long)(budgetMillis * 1_000_000.0);
      int models = 0;
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      EntityCullingOverrides.disableCulling();
      try {
         for (Visible item : this.stale) {
            if (models >= MAX_MODELS_PER_FRAME || System.nanoTime() > deadline) {
               break;
            }

            // Only real models (chests, shulkers...) count against the per-frame model limit; plain boxes are cheap.
            for (BlockTargetIndex.Entry entry : item.cell().values()) {
               if (entry.hasBlockEntity()) {
                  models++;
               }
            }

            this.sink.reset();
            buffers.beginCapture(this.sink);
            try {
               this.bake(item.key(), item.cell(), partialTick, paint);
            } finally {
               buffers.endCapture();
               buffers.reset();
            }

            Mesh old = this.meshes.remove(item.key());
            if (old != null) {
               old.close();
            }

            GpuBuffer vertices = null;
            if (this.sink.quadCount() > 0) {
               vertices = RenderSystem.getDevice()
                  .createBuffer(() -> "Argus ESP cell", GpuBuffer.USAGE_VERTEX, this.sink.data());
            }

            this.meshes.put(item.key(), new Mesh(vertices, this.sink.quadCount(), item.cell().revision(), look));
         }
      } finally {
         EntityCullingOverrides.restoreCulling();
      }
   }

   private void bake(CellIndex.CellKey key, CellIndex.Cell<BlockTargetIndex.Entry> cell, float partialTick, Paint paint) {
      ClientLevel current = this.level;
      BlockEntityRenderDispatcher dispatcher = argusclientMinecraft.getBlockEntityRenderDispatcher();
      CameraRenderState cameraState = argusclientMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      for (Long2Entry item : entries(cell)) {
         long packed = item.packed();
         BlockTargetIndex.Entry entry = item.entry();
         int color = paint.color(packed, entry);
         if (color == 0) {
            continue;
         }

         color |= 0xFF000000;
         int x = CellIndex.unpackX(packed);
         int y = CellIndex.unpackY(packed);
         int z = CellIndex.unpackZ(packed);
         position.set(x, y, z);
         float localX = x - key.minX();
         float localY = y - key.minY();
         float localZ = z - key.minZ();
         this.drawn++;
         int before = this.sink.vertexCount();
         if (entry.hasBlockEntity()) {
            BlockEntity blockEntity = current.getBlockEntity(position);
            if (blockEntity != null) {
               BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, null, false);
               if (state != null && dispatcher.getRenderer(state) != null) {
                  PoseStack local = new PoseStack();
                  local.translate(localX, localY, localZ);
                  ShaderRegistry.setRenderColor(color);
                  dispatcher.submit(state, local, ShaderRegistry.getSubmitNodes(), cameraState);
                  ShaderRegistry.finishFeatureRendering();
               }
            }
         }

         if (this.sink.vertexCount() == before) {
            this.box(entry.state(), position, packed, localX, localY, localZ, color, paint);
         }
      }
   }

   private void box(BlockState state, BlockPos position, long packed, float x, float y, float z, int color, Paint paint) {
      VoxelShape shape = state.getShape(this.level, position);
      if (shape == net.minecraft.world.phys.shapes.Shapes.block()) {
         // A face shared with another drawn full block of the same colour is never seen: leave it out. For netherrack
         // or stone this drops most faces, so huge areas stay cheap to build and draw.
         int px = CellIndex.unpackX(packed), py = CellIndex.unpackY(packed), pz = CellIndex.unpackZ(packed);
         int skip = 0;
         if (this.hides(px, py - 1, pz, color, paint)) skip |= QuadSink.DOWN;
         if (this.hides(px, py + 1, pz, color, paint)) skip |= QuadSink.UP;
         if (this.hides(px, py, pz - 1, color, paint)) skip |= QuadSink.NORTH;
         if (this.hides(px, py, pz + 1, color, paint)) skip |= QuadSink.SOUTH;
         if (this.hides(px - 1, py, pz, color, paint)) skip |= QuadSink.WEST;
         if (this.hides(px + 1, py, pz, color, paint)) skip |= QuadSink.EAST;
         this.sink.box(x, y, z, x + 1, y + 1, z + 1, color, skip);
         if (skip == 63) {
            this.drawn--;
         }

         return;
      }

      if (shape.isEmpty() || state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
         this.sink.box(x, y, z, x + 1, y + 1, z + 1, color);
         return;
      }

      AABB bounds = shape.bounds();
      this.sink.box(
         x + (float)bounds.minX, y + (float)bounds.minY, z + (float)bounds.minZ, x + (float)bounds.maxX, y + (float)bounds.maxY, z + (float)bounds.maxZ, color
      );
   }

   /** Whether the block at a position is a drawn full block of this colour (so the face against it is hidden). */
   private boolean hides(int x, int y, int z, int color, Paint paint) {
      long packed = CellIndex.pack(x, y, z);
      BlockTargetIndex.Entry neighbour = BlockTargetIndex.get().index().get(packed);
      if (neighbour == null || neighbour.hasBlockEntity()) {
         return false;
      }

      this.neighbourPosition.set(x, y, z);
      if (neighbour.state().getShape(this.level, this.neighbourPosition) != net.minecraft.world.phys.shapes.Shapes.block()) {
         return false;
      }

      int other = paint.color(packed, neighbour);
      return other != 0 && (other | 0xFF000000) == color;
   }

   private boolean draw(PoseStack poses, RenderTarget target, Vec3 camera) {
      List<GpuBufferSlice> transforms = new ArrayList<>();
      List<Mesh> drawList = new ArrayList<>();
      int maxQuads = 0;
      Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(poses.last().pose());
      for (Visible item : this.visible) {
         Mesh mesh = this.meshes.get(item.key());
         if (mesh == null || mesh.vertices() == null || mesh.quads() == 0) {
            continue;
         }

         CellIndex.CellKey key = item.key();
         this.transform.set(view).translate((float)(key.minX() - camera.x), (float)(key.minY() - camera.y), (float)(key.minZ() - camera.z));
         transforms.add(RenderSystem.getDynamicUniforms().writeTransform(this.transform, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f()));
         drawList.add(mesh);
         maxQuads = Math.max(maxQuads, mesh.quads());
      }

      if (drawList.isEmpty()) {
         return false;
      }

      RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      GpuBuffer indices = quadIndices.getBuffer(maxQuads * 6);
      try (RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "Argus ESP cells", target.getColorTextureView(), java.util.Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
            )) {
         pass.setPipeline(PIPELINE);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setIndexBuffer(indices, quadIndices.type());
         for (int i = 0; i < drawList.size(); i++) {
            Mesh mesh = drawList.get(i);
            pass.setUniform("DynamicTransforms", transforms.get(i));
            pass.setVertexBuffer(0, mesh.vertices().slice());
            pass.drawIndexed(mesh.quads() * 6, 1, 0, 0, 0);
         }
      }

      return true;
   }

   /** Releases every GPU buffer (world change, module off, resource reload). */
   @Override
   public void close() {
      for (Mesh mesh : this.meshes.values()) {
         mesh.close();
      }

      this.meshes.clear();
      this.visible.clear();
      this.stale.clear();
   }

   /** Forces every cell to rebuild on the next frame. */
   public void invalidate() {
      this.close();
   }

   private record Long2Entry(long packed, BlockTargetIndex.Entry entry) {
   }

   private static List<Long2Entry> entries(CellIndex.Cell<BlockTargetIndex.Entry> cell) {
      List<Long2Entry> list = new ArrayList<>(cell.size());
      cell.forEach((packed, entry) -> list.add(new Long2Entry(packed, entry)));
      return list;
   }
}
