package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockOutlineRenderEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RenderStateManager;
import dev.argus.client.managers.RenderTargetRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.modules.render.AmbienceModule;
import dev.argus.client.modules.render.ChamsModule;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.SkyboxModule;
import dev.argus.client.modules.render.ViewClipModule;
import dev.argus.client.render.EntityRenderStateAccess;
import dev.argus.client.render.IrisShaderBridge;
import dev.argus.client.render.LevelRendererFramebufferAccess;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ShaderPass;
import dev.argus.client.render.ShaderPassRenderState;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements ClientAccess, LevelRendererFramebufferAccess {
   @org.spongepowered.asm.mixin.injection.Redirect(
      method = "update",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z")
   )
   private boolean safeIsSpectator(net.minecraft.client.player.LocalPlayer player) {
      // 26.1.2 moved terrain culling's spectator check from renderLevel to update; it also runs during the login
      // transient before the player exists. Freecam / detached ViewClip cull like a spectator (as on 1.21.11).
      if (player == null) {
         return false;
      }

      return freecam.isToggled()
         || viewClip.isToggled() && ClientAccess.argusclientMinecraft.gameRenderer.mainCamera().isDetached()
         || player.isSpectator();
   }

   private static final ViewClipModule viewClip = ClientContext.getModule(ViewClipModule.class);
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
   private static final ChamsModule chamsModule = ClientContext.getModule(ChamsModule.class);
   @Unique
   private PoseStack worldPoseStack;
   @Unique
   private AtomicBoolean savedHideGui;
   @Shadow
   @Nullable
   private ClientLevel level;
   @Shadow
   @Nullable
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private Stack<RenderTarget> entityOutlineTargetStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> entityOutlineHandleStack;

   @WrapWithCondition(
      method = "lambda$addWeatherPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"
      )
   )
   private boolean shouldRenderWeather(WeatherEffectRenderer weatherRenderer, Vec3 cameraPosition, WeatherRenderState weatherState) {
      return !ambienceModule.isToggled()
         || !ambienceModule.worldWeatherSetting.getValue()
         || ambienceModule.weatherSetting.getValue() != AmbienceModule.WeatherChoice.CLEAR;
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void renderWorldEvents(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      CallbackInfo callback
   ) {
      PoseStack poses = new PoseStack();
      float partialTick = RenderUtils.getPartialTick();
      GpuBufferSlice previousFog = RenderSystem.getShaderFog();
      RenderSystem.setShaderFog(ClientAccess.argusclientMinecraft.gameRenderer.fogRenderer.getBuffer(FogMode.NONE));
      poses.pushPose();
      RenderUtils.applyCameraRotation(poses);
      WorldRenderEvent.BeforeDepthClear beforeDepthEvent = WorldRenderEvent.BeforeDepthClear.create(poses, partialTick);
      ClientAccess.eventBus.post(beforeDepthEvent);
      RenderUtils.clearDepth(ClientAccess.argusclientMinecraft.gameRenderer.mainRenderTarget());
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      WorldRenderEvent.Main mainEvent = WorldRenderEvent.Main.create(poses, partialTick);
      ClientAccess.eventBus.post(mainEvent);
      RenderUtils.clearDepth(ClientAccess.argusclientMinecraft.gameRenderer.mainRenderTarget());
      WorldRenderEvent.Overlay overlayEvent = WorldRenderEvent.Overlay.create(poses, partialTick);
      ClientAccess.eventBus.post(overlayEvent);
      poses.popPose();
      RenderSystem.setShaderFog(previousFog);
   }

   @Inject(
      method = "lambda$addMainPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLorg/joml/Matrix4fc;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;",
         shift = Shift.BEFORE
      )
   )
   private void postBeforeBufferSourceEvent(
      GpuBufferSlice inputBuffer,
      LevelRenderState renderState,
      ProfilerFiller profiler,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      ResourceHandle firstTarget,
      ResourceHandle secondTarget,
      ResourceHandle thirdTarget,
      ResourceHandle fourthTarget,
      ResourceHandle fifthTarget,
      boolean renderFlag,
      Matrix4fc inputMatrix,
      CallbackInfo callback
   ) {
      WorldRenderEvent.BeforeBufferSource event = WorldRenderEvent.BeforeBufferSource.create(new PoseStack(), RenderUtils.getPartialTick());
      ClientAccess.eventBus.post(event);
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
         shift = Shift.BEFORE
      )
   )
   private void addSkyShaderPass(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      CallbackInfo callback,
      @Local FrameGraphBuilder frameGraph
   ) {
      if (skyboxModule.isToggled() && skyboxModule.shaderSetting.getValue()) {
         FramePass skyFramePass = frameGraph.addPass("sky_argusclient");
         this.targets.main = skyFramePass.readsAndWrites(this.targets.main);
         skyFramePass.executes(() -> {
            ShaderPass skyShader = ClientContext.shaderRegistry.getPass("sky");
            ShaderPassRenderState.INSTANCE.configure(skyShader);
            RenderStateManager.addStates(ShaderPassRenderState.INSTANCE);
            skyboxModule.drawShaderQuad();
            RenderStateManager.reset();
            RenderUtils.INSTANCE.fillRect(this.worldPoseStack, 0.0F, 0.0F, 1.0F, 1.0F, 0);
         });
      }
   }

   @WrapWithCondition(
      method = "lambda$addMainPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLorg/joml/Matrix4fc;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;)V"
      )
   )
   private boolean shouldRenderBlockOutline(
      LevelRenderer renderer, BufferSource bufferSource, PoseStack poseStack, boolean translucent, LevelRenderState renderState
   ) {
      if (!(ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult)) {
         return true;
      }

      BlockPos blockPos = ((BlockHitResult)ClientAccess.argusclientMinecraft.hitResult).getBlockPos();
      BlockOutlineRenderEvent event = new BlockOutlineRenderEvent(poseStack, null, blockPos, ClientAccess.argusclientMinecraft.level.getBlockState(blockPos));
      ClientAccess.eventBus.post(event);
      return !event.isCancelled();
   }



   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void captureWorldRenderState(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      CallbackInfo callback
   ) {
      RenderUtils.setPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
      this.worldPoseStack = new PoseStack();
   }

   @ModifyExpressionValue(method = "lambda$renderLevel$0(Lorg/joml/Vector4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
   private int applySkyboxBackgroundColor(int originalColor) {
      return skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()
         ? ARGB.color(0, skyboxModule.backgroundSetting.getValue().hashCode())
         : originalColor;
   }

   @ModifyArgs(
      method = "lambda$addSkyPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
   )
   private static void applySkyFogSlice(Args arguments) {
      if (skyboxModule.isToggled() && skyboxModule.splitFogSetting.getValue() && skyboxModule.isCurrentDimensionEnabled()) {
         arguments.set(0, skyboxModule.getSkySlice());
      }
   }

   @WrapWithCondition(
      method = "submitEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
      )
   )
   private boolean shouldSubmitVisibleEntity(
      EntityRenderDispatcher dispatcher,
      EntityRenderState renderState,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      PoseStack poses,
      SubmitNodeCollector collector
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      return (!noRenderModule.entitiesSetting.getValue() || entity == null || !noRenderModule.shouldHideEntity(entity))
         && !chamsModule.shouldRenderThroughWalls(entity);
   }

   @Inject(method = "resize", at = @At("HEAD"))
   private void resizeClientRenderTargets(int width, int height, CallbackInfo callback) {
      RenderTargetRegistry.resizeRenderTargets(width, height);
   }

   @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return freecam.isToggled() || originalDetached;
   }

   @Inject(method = "addWeatherPass", at = @At("HEAD"))
   private void beginWeatherPassColor(FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 0.25F);
   }

   @Inject(method = "addWeatherPass", at = @At("TAIL"))
   private void resetWeatherPassColor(FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @ModifyExpressionValue(
      method = "extractLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
         ordinal = 1
      )
   )
   private Object applyCloudHeight(Object originalHeight) {
      return skyboxModule.getCloudHeight((Float)originalHeight);
   }

   @ModifyExpressionValue(
      method = "extractLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
         ordinal = 0
      )
   )
   private Object applyCloudColor(Object originalColor) {
      return skyboxModule.isToggled() && skyboxModule.cloudsSetting.getValue() ? skyboxModule.cloudsColorSetting.getValue().hashCode() : originalColor;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void hideGuiForFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      CallbackInfo callback
   ) {
      if (freecam.shouldHideHands() && IrisShaderBridge.isHandRendererAvailable()) {
         this.savedHideGui = new AtomicBoolean();
         this.savedHideGui.setPlain(ClientAccess.argusclientMinecraft.gui.hud.isHidden);
         ClientAccess.argusclientMinecraft.gui.hud.isHidden = true;
      }
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void restoreGuiAfterFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      CameraRenderState cameraState,
      Matrix4fc inputMatrix,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
      CallbackInfo callback
   ) {
      if (freecam.shouldHideHands() && this.savedHideGui != null) {
         ClientAccess.argusclientMinecraft.gui.hud.isHidden = this.savedHideGui.getPlain();
         this.savedHideGui = null;
      }
   }

   /** The cull frustum now lives on the Camera and is read in the extract phase. */
   @Inject(method = "extractLevel", at = @At("HEAD"))
   private void captureCullFrustum(DeltaTracker deltaTracker, net.minecraft.client.Camera camera, float partialTick, CallbackInfo callback) {
      RotationManager.setFrustum(camera.getCullFrustum());
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeFramebufferStacks(CallbackInfo callback) {
      this.entityOutlineTargetStack = new ObjectArrayList();
      this.entityOutlineHandleStack = new ObjectArrayList();
   }

   @Override
   public void argusclient$pushFramebuffer(RenderTarget renderTarget) {
      this.entityOutlineTargetStack.push(this.entityOutlineTarget);
      this.entityOutlineTarget = renderTarget;
      this.entityOutlineHandleStack.push(this.targets.entityOutline);
      this.targets.entityOutline = () -> renderTarget;
   }

   @Override
   public void argusclient$popFramebuffer() {
      this.entityOutlineTarget = (RenderTarget)this.entityOutlineTargetStack.pop();
      this.targets.entityOutline = (ResourceHandle<RenderTarget>)this.entityOutlineHandleStack.pop();
   }
}
