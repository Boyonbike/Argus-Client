package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.CameraSetupEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.InputPressEvent;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MouseHandlerOnScrollEvent;
import dev.argus.client.events.MouseHandlerTurnPlayerEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.InputUtils;
import dev.argus.client.utils.KeyUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class FreecamModule extends Module {
   private static final double DIAGONAL_FACTOR = 1.0 / Math.sqrt(2.0);
   private final Setting<Float> speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 5.0F));
   private final Setting<Float> scrollSensitivitySetting = this.add(new NumberSetting<>("ScrollSensitivity", 0.0F, 0.0F, 2.0F));
   private final Setting<Boolean> staySneakingSetting = this.add(new BooleanSetting("StaySneaking", true));
   private final Setting<Boolean> toggleOnDamageSetting = this.add(new BooleanSetting("ToggleOnDamage", false));
   private final Setting<Boolean> toggleOnDeathSetting = this.add(new BooleanSetting("ToggleOnDeath", false));
   private final Setting<Boolean> toggleOnLogSetting = this.add(new BooleanSetting("ToggleOnLog", true));
   private final Setting<Boolean> reloadChunksSetting = this.add(new BooleanSetting("ReloadChunks", true));
   private final Setting<Boolean> showHandsSetting = this.add(new BooleanSetting("ShowHands", true));
   private final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", false));
   private final Setting<Boolean> staticSetting = this.add(new BooleanSetting("Static", true));
   private final Setting<Boolean> clickToPathSetting = this.add(new BooleanSetting("ClickToPath", false));
   private final Setting<Boolean> doubleClickSetting = this.add(
      new BooleanSetting("DoubleClick", false).dependsOn(this.clickToPathSetting)
   );
   public Vec3 cameraPosition;
   public Vec3 previousCameraPosition;
   public float cameraYaw;
   public float previousCameraYaw;
   public float cameraPitch;
   public float previousCameraPitch;
   private CameraType perspective;
   private double speedValue;
   private double savedFovEffectScale;
   private boolean savedBobView;
   private boolean isSneaking;
   private boolean forwardPressed;
   private boolean backwardPressed;
   private boolean leftPressed;
   private boolean rightPressed;
   private boolean upPressed;
   private boolean downPressed;
   private long clickTs;
   private boolean settingCameraType;

   public FreecamModule() {
      super("Freecam", "Allows the camera to move away from the player.", ModuleCategory.PLAYER);
      this.speedSetting.setChangeCallback(() -> this.speedValue = this.speedSetting.getValue());
   }

   @Override
   public void onEnable() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (this.isWorldUnavailable()) {
         this.disable();
         return;
      }

      LocalPlayer player = mc.player;
      this.savedFovEffectScale = mc.options.fovEffectScale().get();
      this.savedBobView = mc.options.bobView().get();
      if (this.staticSetting.getValue()) {
         mc.options.fovEffectScale().set(0.0);
         mc.options.bobView().set(false);
      }

      this.perspective = mc.options.getCameraType();
      this.speedValue = this.speedSetting.getValue();
      this.cameraYaw = player.getYRot();
      this.cameraPitch = player.getXRot();
      Camera camera = mc.gameRenderer.mainCamera();
      this.cameraPosition = this.previousCameraPosition = camera.position();
      if (this.perspective == CameraType.THIRD_PERSON_FRONT) {
         this.cameraYaw += 180.0F;
         this.cameraPitch *= -1.0F;
      }

      this.previousCameraYaw = this.cameraYaw;
      this.previousCameraPitch = this.cameraPitch;
      this.isSneaking = mc.options.keyShift.isDown();
      this.enterFirstPerson(mc);
      new KeyboardInputTickEvent(player.input).reset();
      this.updatePressedKeys(mc);
      if (this.reloadChunksSetting.getValue() && mc.level != null) {
         mc.levelExtractor.allChanged();
      }
   }

   @Override
   public void onDisable() {
      if (this.perspective == null) {
         return;
      }

      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (this.reloadChunksSetting.getValue() && mc.level != null) {
         mc.execute(() -> mc.levelExtractor.allChanged());
      }

      this.settingCameraType = true;
      try {
         mc.options.setCameraType(this.perspective);
      } finally {
         this.settingCameraType = false;
      }

      if (this.staticSetting.getValue()) {
         mc.options.fovEffectScale().set(this.savedFovEffectScale);
         mc.options.bobView().set(this.savedBobView);
      }

      this.isSneaking = false;
      this.perspective = null;
   }

   public boolean shouldHideHands() {
      return this.isToggled() && !this.showHandsSetting.getValue();
   }

   public boolean isSettingCameraType() {
      return this.settingCameraType;
   }

   public void rotateCamera(double yawDelta, double pitchDelta) {
      this.previousCameraYaw = this.cameraYaw;
      this.previousCameraPitch = this.cameraPitch;
      this.cameraYaw = (float)(this.cameraYaw + yawDelta);
      this.cameraPitch = Mth.clamp((float)(this.cameraPitch + pitchDelta), -90.0F, 90.0F);
   }

   public double getInterpolatedX(float partialTick) {
      return Mth.lerp(partialTick, this.previousCameraPosition.x, this.cameraPosition.x);
   }

   public double getInterpolatedY(float partialTick) {
      return Mth.lerp(partialTick, this.previousCameraPosition.y, this.cameraPosition.y);
   }

   public double getInterpolatedZ(float partialTick) {
      return Mth.lerp(partialTick, this.previousCameraPosition.z, this.cameraPosition.z);
   }

   public double getInterpolatedYaw(float partialTick) {
      return Mth.lerp(partialTick, this.previousCameraYaw, this.cameraYaw);
   }

   public double getInterpolatedPitch(float partialTick) {
      return Mth.lerp(partialTick, this.previousCameraPitch, this.cameraPitch);
   }

   @EventHandler
   private void onKeyboardInputPre(KeyboardInputTickEvent.Pre event) {
      event.cancel();
      if (this.shouldStaySneaking()) {
         InputUtils.setSneak(event.getInput(), true);
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      Entity cameraEntity = mc.getCameraEntity();
      if (cameraEntity != null && cameraEntity.isInWall()) {
         cameraEntity.noPhysics = true;
      }

      this.previousCameraPosition = this.cameraPosition;
      this.updatePressedKeys(mc);
      Vec3 forwardVector = Vec3.directionFromRotation(0.0F, this.cameraYaw);
      Vec3 rightVector = Vec3.directionFromRotation(0.0F, this.cameraYaw + 90.0F);
      double deltaX = 0.0;
      double deltaY = 0.0;
      double deltaZ = 0.0;
      boolean movingForward = false;
      boolean movingSideways = false;
      if (this.forwardPressed) {
         deltaX += forwardVector.x;
         deltaZ += forwardVector.z;
         movingForward = true;
      }

      if (this.backwardPressed) {
         deltaX -= forwardVector.x;
         deltaZ -= forwardVector.z;
         movingForward = true;
      }

      if (this.rightPressed) {
         deltaX += rightVector.x;
         deltaZ += rightVector.z;
         movingSideways = true;
      }

      if (this.leftPressed) {
         deltaX -= rightVector.x;
         deltaZ -= rightVector.z;
         movingSideways = true;
      }

      if (movingForward && movingSideways) {
         deltaX *= DIAGONAL_FACTOR;
         deltaZ *= DIAGONAL_FACTOR;
      }

      double speed = mc.options.keySprint.isDown() ? 1.0 : 0.5;
      deltaX *= speed * this.speedValue;
      deltaZ *= speed * this.speedValue;
      if (this.upPressed) {
         deltaY += speed * this.speedValue;
      }

      if (this.downPressed) {
         deltaY -= speed * this.speedValue;
      }

      this.cameraPosition = this.cameraPosition.add(deltaX, deltaY, deltaZ);
   }

   @EventHandler
   private void onCameraSetupEvent(CameraSetupEvent event) {
      float partialTick = event.getPartialTick();
      event.setPosition(
         new Vec3(this.getInterpolatedX(partialTick), this.getInterpolatedY(partialTick), this.getInterpolatedZ(partialTick))
      );
      event.cancel();
   }

   @EventHandler
   private void onMouseHandlerTurnPlayerEvent(MouseHandlerTurnPlayerEvent event) {
      this.rotateCamera(event.getYawDelta() * 0.15F, event.getPitchDelta() * 0.15F);
      event.cancel();
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      double sensitivity = this.scrollSensitivitySetting.getValue();
      if (sensitivity <= 0.0 || ClientAccess.argusclientMinecraft.gui.screen() != null) {
         return;
      }

      double speedChange = event.getVerticalOffset() * 0.25 * (sensitivity * this.speedValue);
      this.speedValue = Math.max(0.1, this.speedValue + speedChange);
      event.cancel();
   }

   @EventHandler
   private void onInputPressEvent(InputPressEvent event) {
      if (!this.clickToPathSetting.getValue()
         || !event.isMouse()
         || event.getCode() != 0) {
         return;
      }

      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (mc.gui.screen() != null || mc.player == null || mc.level == null) {
         return;
      }

      long now = System.currentTimeMillis();
      long previousClick = this.clickTs;
      this.clickTs = now;
      if (this.doubleClickSetting.getValue() && now - previousClick > 500L) {
         return;
      }

      BaritoneBridge bridge = ClientContext.baritoneBridge;
      if (bridge == null || !bridge.isAvailable()) {
         return;
      }

      BlockPos target = this.findPathTarget(mc);
      if (target != null) {
         bridge.pathTo(target);
      }
   }

   @EventHandler
   private void onScreenChangeEvent(ScreenChangeEvent.Before event) {
      if (event.getNewScreen() instanceof LevelLoadingScreen) {
         this.disable();
         return;
      }

      this.previousCameraPosition = this.cameraPosition;
      this.previousCameraYaw = this.cameraYaw;
      this.previousCameraPitch = this.cameraPitch;
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      if (this.toggleOnLogSetting.getValue()) {
         this.disable();
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) {
         return;
      }

      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket death) {
         if (death.playerId() == player.getId() && this.toggleOnDeathSetting.getValue()) {
            this.disable();
         }
      } else if (event.getPacket() instanceof ClientboundSetHealthPacket health) {
         if (player.getHealth() - health.getHealth() > 0.0F && this.toggleOnDamageSetting.getValue()) {
            this.disable();
         }
      } else if (event.getPacket() instanceof ClientboundRespawnPacket && this.isToggled()) {
         this.disable();
      }
   }

   @EventHandler(priority = 1000)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Post || !this.rotateSetting.getValue()) {
         return;
      }

      HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
      if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
         return;
      }

      Vec3 target = hitResult.getLocation();
      if (hitResult instanceof EntityHitResult entityHitResult) {
         target = Vec3.atCenterOf(entityHitResult.getEntity().blockPosition());
      }

      event.setRotation(RotationManager.getRotationsToPosition(target));
   }

   private void updatePressedKeys(Minecraft mc) {
      this.forwardPressed = this.isMovementKeyDown(mc.options.keyUp);
      this.backwardPressed = this.isMovementKeyDown(mc.options.keyDown);
      this.leftPressed = this.isMovementKeyDown(mc.options.keyLeft);
      this.rightPressed = this.isMovementKeyDown(mc.options.keyRight);
      this.upPressed = this.isMovementKeyDown(mc.options.keyJump);
      this.downPressed = this.isMovementKeyDown(mc.options.keyShift);
   }

   private boolean isMovementKeyDown(KeyMapping mapping) {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (mc.gui.screen() == null) {
         return mapping.isDown();
      }

      NoSlowModule noSlow = ClientContext.getModule(NoSlowModule.class);
      return noSlow != null
         && noSlow.isToggled()
         && noSlow.isGuiMoveAllowed()
         && noSlow.guiMoveSetting.getValue()
         && KeyUtils.isPhysicallyDown(mapping);
   }

   private boolean shouldStaySneaking() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return this.staySneakingSetting.getValue()
         && this.isSneaking
         && player != null
         && !player.getAbilities().flying;
   }

   private void enterFirstPerson(Minecraft mc) {
      if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
         return;
      }

      this.settingCameraType = true;
      try {
         mc.options.setCameraType(CameraType.FIRST_PERSON);
      } finally {
         this.settingCameraType = false;
      }
   }

   private BlockPos findPathTarget(Minecraft mc) {
      Camera camera = mc.gameRenderer.mainCamera();
      Vec3 start = camera.position();
      Vec3 end = start.add(Vec3.directionFromRotation(camera.xRot(), camera.yRot()).scale(256.0));
      AABB searchBounds = new AABB(start, end).inflate(1.0);
      EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
         mc.player, start, end, searchBounds, entity -> true, 256.0
      );
      if (entityHitResult != null) {
         return BlockPos.containing(entityHitResult.getLocation());
      }

      BlockHitResult blockHitResult = mc.level.clip(
         new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.SOURCE_ONLY, CollisionContext.empty())
      );
      return blockHitResult.getType() == HitResult.Type.MISS
         ? null
         : blockHitResult.getBlockPos().offset(blockHitResult.getDirection().getUnitVec3i());
   }
}
