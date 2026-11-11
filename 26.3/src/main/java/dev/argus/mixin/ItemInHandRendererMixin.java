package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.combat.NoHitDelayModule;
import dev.argus.client.modules.misc.SwingModule;
import dev.argus.client.modules.render.ViewModelModule;
import dev.argus.client.utils.inventory.ItemUtils;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 9999)
public abstract class ItemInHandRendererMixin {
   private static final ViewModelModule viewModel = ClientContext.getModule(ViewModelModule.class);
   private static final SwingModule swing = ClientContext.getModule(SwingModule.class);
   private static final NoHitDelayModule noHitDelayModule = ClientContext.getModule(NoHitDelayModule.class);
   @Unique
   private static final Vector3f blockingRotation = new Vector3f(120.0F, 15.0F, 100.0F);
   @Unique
   private static final Vector3f blockingTranslation = new Vector3f(0.15F, 0.05F, 0.0F);
   @Unique
   private final Vector3f itemRotation = new Vector3f(1.0F);

   @Inject(
      method = "submitArmWithItem",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      )
   )
   private void applyViewModelItemTransform(
      PlayerRenderState playerState,
      FirstPersonHandsAndItemsRenderState renderState,
      float partialTick,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack itemStack,
      float equipProgress,
      PoseStack pose,
      SubmitNodeCollector submitNodes,
      int light,
      CallbackInfo callback
   ) {
      boolean blockingOffsets = false;
      float offsetX = 0.0F;
      float offsetY = 0.0F;
      float offsetZ = 0.0F;
      this.itemRotation.set(0.0F);
      if (noHitDelayModule.isToggled()
         && noHitDelayModule.hasUseKeyDelayElapsed()
         && hand == InteractionHand.MAIN_HAND
         && (
            !ClientAccess.argusclientMinecraft.player.isUsingItem()
               || !(ClientAccess.argusclientMinecraft.player.getOffhandItem().getItem() instanceof ShieldItem)
         )
         && ItemUtils.isSword(ClientAccess.argusclientMinecraft.player.getMainHandItem())) {
         if (ClientAccess.argusclientMinecraft.player.getMainArm() == HumanoidArm.LEFT) {
            this.itemRotation.set(this.mirrorBlockingRotationInPlace(blockingRotation));
            offsetX += blockingTranslation.x;
         } else {
            this.itemRotation.set(blockingRotation);
            offsetX -= blockingTranslation.x;
         }

         offsetY += blockingTranslation.y;
         blockingOffsets = true;
      }

      if (viewModel.isToggled()) {
         boolean consumable = itemStack.has(DataComponents.FOOD) || itemStack.getItem() instanceof PotionItem;
         boolean activeItemMatches = ClientAccess.argusclientMinecraft.player.getUseItem().getItem() == itemStack.getItem();
         if (consumable && activeItemMatches && ClientAccess.argusclientMinecraft.player.isUsingItem() && !viewModel.eatingSetting.getValue()) {
            return;
         }

         if (hand == InteractionHand.MAIN_HAND) {
            offsetX += consumable && activeItemMatches ? 0.0F : -viewModel.mainXSetting.getValue();
            offsetY += viewModel.mainYSetting.getValue();
            offsetZ = consumable && activeItemMatches ? 0.0F : viewModel.mainZSetting.getValue();
            if (!blockingOffsets) {
               this.itemRotation.set(viewModel.mainRotateXSetting.getValue(), viewModel.mainRotateYSetting.getValue(), viewModel.mainRotateZSetting.getValue());
            }
         } else {
            offsetX += consumable && activeItemMatches ? 0.0F : viewModel.offXSetting.getValue();
            offsetY = viewModel.offYSetting.getValue();
            offsetZ = consumable && activeItemMatches ? 0.0F : viewModel.offZSetting.getValue();
            this.itemRotation.set(viewModel.offRotateXSetting.getValue(), viewModel.offRotateYSetting.getValue(), viewModel.offRotateZSetting.getValue());
         }

         Vector3f scale = viewModel.getScale(hand);
         pose.scale(scale.x, scale.y, scale.z);
      }

      if (ClientAccess.argusclientMinecraft.player.getMainArm() == HumanoidArm.LEFT) {
         offsetX = -offsetX;
      }

      pose.translate(offsetX, offsetY, offsetZ);
      pose.mulPose(new Matrix4f().rotation(Axis.XN.rotationDegrees(this.itemRotation.x)));
      pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(this.itemRotation.y)));
      pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(this.itemRotation.z)));
   }

   @Inject(method = "renderPlayerArm", at = @At("HEAD"))
   private void applyViewModelArmTransform(
      PoseStack pose,
      SubmitNodeCollector submitNodes,
      int light,
      float equipProgress,
      float swingProgress,
      HumanoidArm arm,
      PlayerRenderState playerState,
      CallbackInfo callback
   ) {
      if (viewModel.isToggled() && viewModel.armSetting.getValue()) {
         float offsetX = -viewModel.mainXSetting.getValue();
         if (ClientAccess.argusclientMinecraft.player.getMainArm() == HumanoidArm.LEFT) {
            offsetX = -offsetX;
         }

         pose.translate(offsetX, viewModel.mainYSetting.getValue(), viewModel.mainZSetting.getValue());
         pose.scale(viewModel.mainScaleXSetting.getValue(), viewModel.mainScaleYSetting.getValue(), viewModel.mainScaleZSetting.getValue());
      }
   }

   @ModifyArgs(
      method = "submitHandsWithItems",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
      ),
      require = 0
   )
   private void overrideSwingProgress(Args arguments) {
      InteractionHand hand = (InteractionHand)arguments.get(4);
      float originalProgress = (Float)arguments.get(5);
      arguments.set(5, viewModel.applySwingProgressOverride(hand, originalProgress));
   }

   @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
   private void hideOffhandShieldForNoHitDelay(
      PlayerRenderState playerState,
      FirstPersonHandsAndItemsRenderState renderState,
      float partialTick,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack poses,
      SubmitNodeCollector collector,
      int packedLight,
      CallbackInfo callback
   ) {
      if (noHitDelayModule.isToggled() && hand == InteractionHand.OFF_HAND && stack.is(Items.SHIELD)) {
         callback.cancel();
      }
   }

   @WrapWithCondition(
      method = "submitHandsWithItems",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 0)
   )
   private boolean shouldApplyHandSwayX(PoseStack pose, Axis axis, float degrees) {
      return !viewModel.isToggled() || !viewModel.noSwaySetting.getValue();
   }

   @WrapWithCondition(
      method = "submitHandsWithItems",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 1)
   )
   private boolean shouldApplyHandSwayY(PoseStack pose, Axis axis, float degrees) {
      return !viewModel.isToggled() || !viewModel.noSwaySetting.getValue();
   }

   @ModifyExpressionValue(
      method = "submitArmWithItem",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isAutoSpinAttack:Z"
      )
   )
   private boolean overrideTridentAnimation(boolean originalSpinAttack) {
      return viewModel.isToggled() && viewModel.noTridentAnimSetting.getValue() ? false : originalSpinAttack;
   }

   @ModifyArgs(method = "applyItemArmTransform", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
   private void overrideInstantSwapOffset(Args arguments, @Local(argsOnly = true) HumanoidArm arm) {
      if (viewModel.isToggled() && viewModel.instantSwapSetting.getValue() && !ClientAccess.argusclientMinecraft.player.getItemHeldByArm(arm).is(Items.SHIELD)) {
         arguments.set(1, -0.52F);
      }
   }

   @ModifyArgs(method = "applyEatTransform", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
   private void scaleEatingOffset(Args arguments) {
      if (viewModel.isToggled()) {
         arguments.set(1, (Float)arguments.get(1) * viewModel.eatMultiplierSetting.getValue());
      }
   }

   @Unique
   private Vector3f mirrorBlockingRotationInPlace(Vector3f rotation) {
      return rotation.set(rotation.x, -rotation.y, -rotation.z);
   }
}
