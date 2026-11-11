package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.modules.player.NameProtectModule;
import dev.argus.client.modules.render.FreeLookModule;
import dev.argus.client.modules.render.NoBobModule;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.ShaderModule;
import dev.argus.client.modules.render.ViewModelModule;
import dev.argus.client.modules.render.ZoomModule;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.GuiProjectionUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.LockUtils;
import dev.argus.client.utils.math.Vec3Utils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   private static ZoomModule zoom = ClientContext.getModule(ZoomModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static ShaderModule shader = ClientContext.getModule(ShaderModule.class);
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   private static ViewModelModule viewmodel = ClientContext.getModule(ViewModelModule.class);
   private static NoBobModule noBob = ClientContext.getModule(NoBobModule.class);
   private static HUDModule hud = ClientContext.getModule(HUDModule.class);
   @Final
   @Shadow
   private Minecraft minecraft;
   @Shadow
   @Final
   public GuiRenderer guiRenderer;
   @Shadow
   @Final
   public FogRenderer fogRenderer;

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
   private float invertViewBobSine(float originalSine) {
      return RenderUtils.isInverseViewBobbing() ? -originalSine : originalSine;
   }

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
   private float invertViewBobMagnitude(float originalMagnitude) {
      return RenderUtils.isInverseViewBobbing() ? -originalMagnitude : originalMagnitude;
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void skipGameRendererTickWithoutPlayer(CallbackInfo callback) {
      if (ClientAccess.argusclientMinecraft.player == null) {
         callback.cancel();
      }
   }

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void suppressHurtCamera(CameraRenderState cameraState, PoseStack poses, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.hurtCamSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
   private void suppressTotemActivation(ItemStack stack, CallbackInfo callback) {
      if (stack.getItem() == Items.TOTEM_OF_UNDYING && noRenderModule.isToggled() && noRenderModule.totemOverlaySetting.getValue()) {
         callback.cancel();
      }
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"), require = 0)
   private float suppressBlindnessInterpolation(float fraction, float start, float end, Operation original) {
      return noRenderModule.isToggled() && noRenderModule.blindnessSetting.getValue() ? 0.0F : Mth.lerp(fraction, start, end);
   }

   @Inject(
      method = "renderLevel",
      at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=level")
   )
   private void captureWorldProjectionMatrix(
      DeltaTracker deltaTracker,
      CallbackInfo callback,
      @Local(ordinal = 0) Matrix4f projectionMatrix // 26.1.2: the only Matrix4f local (view matrix is now Matrix4fc)
   ) {
      RenderUtils.WORLD_PROJECTION_MATRIX.set(projectionMatrix);
   }

   @WrapWithCondition(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V")
   )
   private boolean shouldRenderHands(GameRenderer renderer, CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix) {
      return !shader.isToggled() || !shader.handsSetting.getValue();
   }

   @WrapWithCondition(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
   private boolean applyNoBobTranslation(PoseStack poseStack, float x, float y, float z) {
      if (!noBob.isToggled()) {
         return true;
      }

      float multiplier = noBob.multiplierSetting.getValue();
      if (multiplier == 0.0F) {
         return true;
      }

      poseStack.translate(x * multiplier, y * multiplier, z * multiplier);
      return false;
   }

   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void prepareHandRendering(
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      Matrix4f mutableProjection = (Matrix4f) projectionMatrix;
      backupMatrix.set(new Matrix4f(mutableProjection));
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, noRenderModule.getHandOpacity());
      if (viewmodel.isToggled() && viewmodel.lighterSetting.getValue()) {
         float handYaw = -95.0F;
         float handPitch = 45.0F;
         Quaternionf handRotation = new Quaternionf().rotationYXZ((float)Math.toRadians(180.0F - handYaw), (float)Math.toRadians(-handPitch), 0.0F).conjugate();
         mutableProjection.set(new Matrix4f().rotation(handRotation));
      }

      if (freecam.shouldHideHands()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderItemInHand", at = @At("RETURN"))
   private void restoreHandRendering(
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (viewmodel.isToggled() && viewmodel.lighterSetting.getValue()) {
         ((Matrix4f)projectionMatrix).set((Matrix4fc)backupMatrix.get());
      }
   }

   @ModifyExpressionValue(
      method = "renderItemInHand",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I"
      )
   )
   private int applyViewModelHandLight(int originalLight) {
      return viewmodel.isToggled() && viewmodel.lighterSetting.getValue() ? 15728864 : originalLight;
   }

   @WrapWithCondition(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submit(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"
      )
   )
   private boolean shouldRenderScreenEffects(ScreenEffectRenderer renderer, boolean ignoredEffectFlag1, boolean ignoredEffectFlag2, float partialTick, SubmitNodeCollector collector, boolean ignoredEffectFlag3) {
      return !ClientContext.shaderRegistry.renderingIntoTarget;
   }

   /** Below the vanilla GUI pass: the Before event, plus the Argus HUD while a screen is open (the screen draws over it). */
   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
   )
   private void renderHudBeforeGui(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
      GuiGraphicsExtractor graphics = dev.argus.client.gui.GuiCapture.hudGraphics;
      if (graphics == null) {
         return;
      }

      if (ClientAccess.argusclientMinecraft.level != null
         && !ClientAccess.argusclientMinecraft.gui.hud.isHidden
         && !ClientAccess.argusclientMinecraft.debugEntries.isOverlayVisible()) {
         PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
         GuiProjectionUtils.beginGuiProjection();
         HudRenderEvent.Before event = HudRenderEvent.Before.create(poses, graphics, RenderUtils.getPartialTick());
         ClientAccess.eventBus.post(event);
         GuiDrawUtils.flush();
         GuiProjectionUtils.restoreProjection();
      }

      if (this.minecraft.gui.screen() != null) {
         this.renderClientHud(graphics, true);
         RenderUtils.clearDepth(this.minecraft.gameRenderer.mainRenderTarget());
      }
   }

   /** On top of the vanilla GUI when no screen is open (the old post-incrementFrameNumber hook). */
   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = Shift.AFTER))
   private void renderHudWithoutScreen(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
      if (this.minecraft.gui.screen() == null && dev.argus.client.gui.GuiCapture.hudGraphics != null) {
         RenderUtils.clearDepth(this.minecraft.gameRenderer.mainRenderTarget());
         this.renderClientHud(dev.argus.client.gui.GuiCapture.hudGraphics, true);
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void updateFrameTimingAndChatHeight(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
      dev.argus.client.render.MeshRenderer.beginFrame();
      hud.updateChatInputHeight(ClientAccess.argusclientMinecraft.gui.screen() instanceof ChatScreen ? 12.0F : 0.0F);
      ClientContext.frameTiming.recordFrame();
   }

   @Unique
   public void renderClientHud(GuiGraphicsExtractor graphics, boolean manageProjection) {
      if (ClientAccess.argusclientMinecraft.level != null) {
         boolean nameReplacementWasBypassed = NameProtectModule.replacementBypassed;
         if (nameReplacementWasBypassed) {
            NameProtectModule.replacementBypassed = false;
         }

         if (manageProjection) {
            GuiProjectionUtils.beginGuiProjection();
         }

         PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
         if (!ClientAccess.argusclientMinecraft.gui.hud.isHidden && !ClientAccess.argusclientMinecraft.debugEntries.isOverlayVisible()) {
            HudRenderEvent hudEvent = HudRenderEvent.create(poses, graphics, RenderUtils.getPartialTick());
            ClientAccess.eventBus.post(hudEvent);
         }

         HudRenderEvent.Unconditional unconditionalEvent = HudRenderEvent.Unconditional.create(poses, graphics, RenderUtils.getPartialTick());
         ClientAccess.eventBus.post(unconditionalEvent);
         GuiDrawUtils.flush();
         if (manageProjection) {
            GuiProjectionUtils.restoreProjection();
         }

         if (nameReplacementWasBypassed) {
            NameProtectModule.replacementBypassed = true;
         }
      }
   }
}
