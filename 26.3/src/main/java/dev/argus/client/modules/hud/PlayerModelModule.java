package dev.argus.client.modules.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.PostProcessShaderPass;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModelModule extends HudWidgetModule {
   public PlayerModelModule() {
      super("PlayerModel");
      ResizableHudWidgetRenderer widgetController = new ResizableHudWidgetRenderer(this);
      widgetController.enableResizing(this);
      this.setRenderer(widgetController);
   }

   @Override
   public float[] calculateDimensions() {
      return new float[]{50.0F, 80.0F};
   }

   @Override
   public void renderContent(PoseStack poses) {
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
      if (camera.position() != null) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Quaternionf quaternion = new Quaternionf();
         float rotationZ = (float) Math.PI;
         Quaternionf rotation = quaternion.rotateZ(rotationZ);
         Vector3f modelTranslation = new Vector3f(0.0F, player.getBbHeight() / 2.0F, 0.0F);
         float x = 25.0F;
         float y = 40.0F;
         float scaleArgument = 35.0F;
         renderEntityPreview(poses, x, y, scaleArgument, modelTranslation, rotation, player);
      }
   }

   public static void renderEntityPreview(PoseStack poses, float x, float y, float scale, Vector3f modelTranslation, Quaternionf rotation, LivingEntity entity) {
      RenderUtils.clearColor(PostProcessShaderPass.POST_TARGET, Color.black);
      RenderUtils.clearDepth(PostProcessShaderPass.POST_TARGET);
      RenderTargetUtils.overrideRenderTargets(PostProcessShaderPass.POST_TARGET);
      poses.pushPose();
      poses.translate(x, y, 50.0);
      float scaleZ = -scale;
      poses.scale(scale, scale, scaleZ);
      float modelZ = modelTranslation.z;
      float modelY = modelTranslation.y;
      float modelX = modelTranslation.x;
      poses.translate(modelX, modelY, modelZ);
      poses.mulPose(rotation);
      Lighting lighting = ClientAccess.argusclientMinecraft.gameRenderer.lighting();
      Entry entityLightingEntry = Entry.ENTITY_IN_UI;
      lighting.setupFor(entityLightingEntry);
      SubmitNodeStorage submitNodes = new SubmitNodeStorage();
      CameraRenderState cameraState = ClientAccess.argusclientMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
      Minecraft clientForEntityRenderer = Minecraft.getInstance();
      EntityRenderDispatcher entityRenderer = clientForEntityRenderer.getEntityRenderDispatcher();
      float partialTick = RenderUtils.getPartialTick();
      EntityRenderState entityState = entityRenderer.extractEntity(entity, partialTick);
      entityState.shadowPieces.clear();
      entityRenderer.submit(entityState, cameraState, 0.0, 0.0, 0.0, poses, submitNodes);
      dev.argus.client.render.ImmediateSubmitRenderer.flush(submitNodes);
      submitNodes.getSubmitsPerOrder().clear();
      poses.popPose();
      GameRenderer rendererForItemLighting = ClientAccess.argusclientMinecraft.gameRenderer;
      lighting = rendererForItemLighting.lighting();
      Entry itemLightingEntry = Entry.ITEMS_FLAT;
      lighting.setupFor(itemLightingEntry);
      RenderTargetUtils.restoreRenderTargets();
      RenderUtils.clearDepth(PostProcessShaderPass.POST_TARGET);
      RenderUtils.blitToMain(PostProcessShaderPass.POST_TARGET);
   }
}
