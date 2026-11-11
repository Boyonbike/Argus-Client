package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.events.SelectedSlotChangeEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.combat.AnchorAuraModule;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.modules.combat.AutoExpModule;
import dev.argus.client.modules.combat.AutoMineMiningTracker;
import dev.argus.client.modules.combat.AutoMineModule;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.modules.render.LogoutSpotsModule;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.settings.options.SpeedMineAutoSwapChoice;
import dev.argus.client.settings.options.SpeedMineColorModeChoice;
import dev.argus.client.settings.options.SpeedMineConfirmChoice;
import dev.argus.client.settings.options.SpeedMineRebreakChoice;
import dev.argus.client.settings.options.SpeedMineRenderModeChoice;
import dev.argus.client.settings.options.SpeedMineValue121Choice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.combat.ExtraBreakTarget;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.ExtraBreakTracker;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockBreakingState;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpeedMineModule extends Module {
   private static AutoExpModule autoExpModule = ClientContext.getModule(AutoExpModule.class);
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static AutoMineModule autoMineModule = ClientContext.getModule(AutoMineModule.class);
   private static NoGlitchBlocksModule noGlitchBlocksModule = ClientContext.getModule(NoGlitchBlocksModule.class);
   private static ItemSaverModule itemSaverModule = ClientContext.getModule(ItemSaverModule.class);
   private static LogoutSpotsModule logoutSpotsModule = ClientContext.getModule(LogoutSpotsModule.class);
   private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
   private static AnchorAuraModule anchorAuraModule = ClientContext.getModule(AnchorAuraModule.class);
   public static boolean automatedMiningStart;
   /** Above zero while Minecraft handles the attack key ({@code startAttack}/{@code continueAttack}); set by MinecraftMixin. */
   private static int playerAttackDepth;
   /** True while the queue itself starts its next block. */
   private static boolean queueDrainStart;

   public static void beginPlayerAttack() {
      playerAttackDepth++;
   }

   public static void endPlayerAttack() {
      playerAttackDepth = Math.max(0, playerAttackDepth - 1);
   }
   protected final Setting<Set<Block>> blocksSetting = this.add(
      new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
   );
   public final Setting<ListFilterMode> modeSetting = this.add(new EnumSetting<>("Mode", ListFilterMode.ANY));
   public final Setting<Float> damageSetting = this.add(new NumberSetting<>("Damage", 1.0F, 0.6F, 1.0F));
   public final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 5.2F, 1.0F, 6.0F).withUnit("m"));
   public final Setting<Boolean> grimSetting = this.add(new BooleanSetting("Grim", false));
   private final Setting<Boolean> tpsSyncSetting = this.add(new BooleanSetting("TPSSync", true));
   public final Setting<Boolean> noBreakDelaySetting = this.add(new BooleanSetting("NoBreakDelay", true));
   public final Setting<Boolean> queueSetting = this.add(new BooleanSetting("Queue", true));
   public final Setting<Boolean> extraBreakSetting = this.add(new BooleanSetting("ExtraBreak", false).markVisibilityDependency());
   public final Setting<Boolean> sneakExtendSetting = this.add(new BooleanSetting("SneakExtend", false).dependsOn(this.extraBreakSetting));
   public final Setting<Boolean> rebreakEnabledSetting = this.add(new BooleanSetting("Rebreak", true).markAsGroup().markVisibilityDependency());
   public final Setting<SpeedMineRebreakChoice> rebreakSetting = this.add(
      new EnumSetting<>("Rebreak", SpeedMineRebreakChoice.NONE, this::isRebreakPageExpanded)
   );
   public final Setting<Boolean> asyncSetting = this.add(new BooleanSetting("Async", false, this::isInstantRebreak).dependsOn(this.rebreakEnabledSetting));
   protected final Setting<Float> instantDelaySetting = this.add(
      new NumberSetting<>("InstantDelay", 0.0F, 0.0F, 2.0F, this::isInstantRebreakPageExpanded).withUnit("s")
   );
   public final Setting<SpeedMineConfirmChoice> confirmSetting = this.add(
      new EnumSetting<>("Confirm", SpeedMineConfirmChoice.BYPASS, this::isInstantRebreakPageExpanded)
   );
   private final Setting<Boolean> holdingBestSetting = this.add(new BooleanSetting("HoldingBest", false, this::isInstantRebreakPageExpanded));
   public final Setting<Boolean> autoSwapSetting = this.add(new BooleanSetting("AutoSwap", true).markAsGroup().markVisibilityDependency());
   final Setting<SpeedMineAutoSwapChoice> autoSwapModeSetting = this.add(
      new EnumSetting<>("AutoSwap", SpeedMineAutoSwapChoice.NONE, this::isAutoSwapPageExpanded)
   );
   private final Setting<Boolean> swapBackSetting = this.add(new BooleanSetting("SwapBack", false, this::isNormalSwap).dependsOn(this.autoSwapSetting));
   private final Setting<SpeedMineValue121Choice> value121Setting = this.add(
      new EnumSetting<>("1.21", SpeedMineValue121Choice.OFF, this::isSilentSwapPageExpanded)
   );
   public final Setting<Boolean> alternativeSetting = this.add(new BooleanSetting("Alternative", false, this::isSilentSwapPageExpanded));
   public final Setting<Boolean> swapResetSetting = this.add(new BooleanSetting("SwapReset", true, this::isSwapResetVisible));
   final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", false).markVisibilityDependency());
   private final Setting<Float> limitSetting = this.add(
      new NumberSetting<>("Limit", 0.9F, 0.0F, 1.0F, this::isRotatePageExpanded).setBoundaryLabel("Off", SettingLabelCondition.MAX)
   );
   final Setting<Boolean> renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
   public final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.0F, 5.0F, this::isRenderPageExpanded));
   public final Setting<SpeedMineRenderModeChoice> renderModeSetting = this.add(
      new EnumSetting<>("RenderMode", SpeedMineRenderModeChoice.OUT, this::isRenderPageExpanded)
   );
   public final Setting<Boolean> airSetting = this.add(new BooleanSetting("Air", false, this::isAirVisible));
   public final Setting<Boolean> fadeSetting = this.add(new BooleanSetting("Fade", false, this::isRenderPageExpanded));
   public final Setting<SpeedMineColorModeChoice> colorModeSetting = this.add(
      new EnumSetting<>("ColorMode", SpeedMineColorModeChoice.PROGRESS, this::isRenderPageExpanded)
   );
   public final Setting<Color> fillSetting = this.add(new ColorSetting("Fill", new Color(280861183, true), this::isCustomColorVisible));
   public final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(-1044538881, true), this::isCustomColorVisible));
   private final Setting<Boolean> abortSetting = this.add(new BooleanSetting("Abort", false, ignored -> false));
   private final SpeedMineRebreakTracker rebreakTracker = new SpeedMineRebreakTracker(this);
   private final SpeedMineConfirmation confirmation = new SpeedMineConfirmation();
   private final SpeedMineQueue mineQueue = new SpeedMineQueue();
   private final SpeedMineRenderQueue renderQueue = new SpeedMineRenderQueue();
   private final ExtraBreakTracker extraBreakTracker = new ExtraBreakTracker();
   private final ArrayDeque<Boolean> pendingBreakPositions = new ArrayDeque<>();
   private final ElapsedTimer startDestroyRetryTimer = new ElapsedTimer();
   private final ElapsedTimer retargetTimer = new ElapsedTimer();
   private final ElapsedTimer breakDelayTimer = new ElapsedTimer();
   private final ElapsedTimer itemSpawnTimer = new ElapsedTimer();
   private final ElapsedTimer crystalDelayTimer = new ElapsedTimer();
   private final ElapsedTimer extraBreakTimer = new ElapsedTimer();
   private final ElapsedTimer stalledBreakTimer = new ElapsedTimer();
   private static final AtomicBoolean swapPending = new AtomicBoolean();
   private static final AtomicBoolean restartPending = new AtomicBoolean();
   protected float miningProgress;
   private boolean waitingForRebreak;
   /** Block starts earned at the server's tick rate; see {@link SpeedMineTiming#replenishStartCredit}. */
   private double startCredit = 1.0;
   private long lastStartCreditMillis = System.currentTimeMillis();
   private float silentSwapProgress;
   private int bestToolSlot = -1;
   private int previousSelectedSlot;
   private ExtraBreakTarget extraBreakTarget = null;
   protected BlockPos miningPos;
   protected BlockPos lastMiningPos;
   public volatile boolean crystalAttackPending = false;

   public boolean isInMiningRange(BlockPos blockPos) {
      return SpeedMineRange.isInRange(blockPos, this.rangeSetting.getValue());
   }

   private void rotateToBlock(BlockPos blockPos) {
      Boolean rotationEnabled = this.rotateSetting.getValue();
      if (rotationEnabled) {
         if (!RotationManager.isSilentRotationMode()) {
            if (WorldUtils.canMineBlock(blockPos)) {
               BlockSampleMode sampleMode = BlockSampleMode.X8;
               Vec3 targetPoint = WorldUtils.findVisiblePoint(blockPos, sampleMode);
               if (targetPoint == null) {
                  targetPoint = Vec3.atCenterOf(blockPos);
               }

               float[] rotations = RotationManager.getRotationsToPosition(targetPoint);
               RotationManager rotationManager = ClientContext.rotationManager;
               rotationManager.requestRotation(rotations, 150, true);
            }
         }
      }
   }

   public int getMiningToolSlot() {
      return this.bestToolSlot < 0 ? ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot() : this.bestToolSlot;
   }

   public static void clearRebreakAndReset(SpeedMineModule owner) {
      owner.lastMiningPos = null;
      reset(owner);
   }

   private boolean canBreakNearCrystal() {
      BlockPos miningPosition = this.miningPos;
      if (WorldUtils.getBlock(miningPosition) != Blocks.OBSIDIAN) {
         BlockPos currentPosition = this.miningPos;
         if (!WorldUtils.isAir(currentPosition)) {
            return true;
         }
      }

      AABB miningBounds = new AABB(this.getMiningPos());

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof EndCrystal) {
            AABB crystalBounds = entity.getBoundingBox();
            double verticalInset = 0.25;
            if (crystalBounds.inflate(0.0, verticalInset, 0.0).intersects(miningBounds)) {
               return true;
            }
         }
      }

      BlockPos positionAboveCheck = this.getMiningPos();
      if (this.isPlayerAboveBlock(positionAboveCheck)) {
         ElapsedTimer crystalDelay = this.crystalDelayTimer;
         float baseDelayMillis = 1000.0F;
         ServerStateManager serverState = ClientContext.serverStateManager;
         long scaledDelayMillis = (long)(baseDelayMillis / serverState.getTickRateScale());
         if (!crystalDelay.hasElapsed(scaledDelayMillis)) {
            return false;
         }
      }

      return true;
   }

   public boolean isBlockAllowed(BlockPos blockPos) {
      if (WorldUtils.canMineBlock(blockPos)) {
         ListFilterMode filterMode = this.modeSetting.getValue();
         Block block = ClientAccess.argusclientMinecraft.level.getBlockState(blockPos).getBlock();
         Setting<Set<Block>> selectedBlocks = this.blocksSetting;
         if (ListFilterMode.matches(filterMode, block, selectedBlocks)) {
            return true;
         }
      }

      return false;
   }

   public ExtraBreakTracker getExtraBreakTracker() {
      return this.extraBreakTracker;
   }

   private boolean isPlayerAboveBlock(BlockPos blockPos) {
      if (blockPos == null) {
         return true;
      }

      AABB blockBounds = new AABB(blockPos);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      ArrayList<LivingEntity> players = new ArrayList<>(level.players());
      if (logoutSpotsModule.isToggled()) {
         Collection logoutPlayers = logoutSpotsModule.getSpotPlayers();
         players.addAll(logoutPlayers);
      }

      for (LivingEntity player : players) {
         if (player != ClientAccess.argusclientMinecraft.player) {
            AABB playerBounds = player.getBoundingBox();
            playerBounds = playerBounds.expandTowards(0.0, 1.0, 0.0);
            if (playerBounds.inflate(1.0, 0.0, 1.0).intersects(blockBounds) && this.getMiningPos().getY() - player.getBlockY() > 0) {
               return true;
            }
         }
      }

      return false;
   }

   public BlockPos getMiningPos() {
      return this.miningPos;
   }

   public BlockPos getMiningOrNearlyBrokenPos() {
      if (this.isToggled()) {
         return this.miningPos;
      } else {
         return ClientAccess.argusclientMinecraft.gameMode.isDestroying()
               && ((BlockBreakingState)ClientAccess.argusclientMinecraft.gameMode).getBreakingProgress() >= 0.9
            ? ((BlockBreakingState)ClientAccess.argusclientMinecraft.gameMode).getCurrentBreakingBlock()
            : null;
      }
   }

   public static boolean isSwapPending() {
      return swapPending.get();
   }

   public float getMiningProgress() {
      return Mth.clamp(this.miningProgress, 0.0F, 1.0F);
   }

   private void playBreakEffects(BlockPos blockPos) {
      if (ClientAccess.argusclientMinecraft.level.getFluidState(blockPos).isEmpty()) {
         BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(blockPos);
         if (!state.isAir()) {
            SoundType soundType = state.getSoundType();
            SoundEvent sound = soundType.getBreakSound();
            float soundValue = soundType.getVolume();
            soundValue++;
            float volume = soundValue / 2.0F;
            soundValue = soundType.getPitch();
            float pitch = soundValue * 0.8F;
            SoundSource soundSource = SoundSource.BLOCKS;
            ClientAccess.argusclientMinecraft.level.playLocalSound(blockPos, sound, soundSource, volume, pitch, false);
         }

         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockState destroyedState = ClientAccess.argusclientMinecraft.level.getBlockState(blockPos);
         level.addDestroyBlockEffect(blockPos, destroyedState);
      }
   }

   public float getLineWidth() {
      return this.lineWidthSetting.getValue();
   }

   public BlockPos getRebreakPos() {
      SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
      switch (rebreakMode) {
         case NONE:
            return null;
         case FAST:
            return this.lastMiningPos;
         case INSTANT:
            return this.miningPos;
         default:
            throw new MatchException(null, null);
      }
   }

   public ExtraBreakTarget getExtraBreakTarget() {
      return this.extraBreakTarget;
   }

   private boolean isMainHandMultitask() {
      InteractionHand hand = ClientAccess.argusclientMinecraft.player.getUsedItemHand();
      return multiTaskModule.shouldPauseModule(this) && hand == InteractionHand.MAIN_HAND;
   }

   public ElapsedTimer getBreakDelayTimer() {
      return this.breakDelayTimer;
   }

   public double getTickBreakProgress() {
      if (this.miningPos == null) {
         return 0.0;
      }

      BlockPos miningPosition = this.miningPos;
      BlockState miningBlockState = ClientAccess.argusclientMinecraft.level.getBlockState(miningPosition);
      float tickRateScale = ClientContext.serverStateManager.getTickRateScale();
      Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
      int toolSlot = this.getMiningToolSlot();
      ItemStack tool = inventory.getItem(toolSlot);
      boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
      return WorldUtils.getBreakProgress(tool, miningBlockState, onGround) * tickRateScale;
   }

   private boolean restartForRebreak() {
      SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
      if (!rebreakMode.isInstant()) {
         return false;
      }

      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE) {
         return false;
      }

      SpeedMineRebreakTracker tracker = this.rebreakTracker;
      tracker.validateTarget();
      tracker = this.rebreakTracker;
      boolean delayElapsed = tracker.hasRebreakDelayElapsed();
      BlockPos targetPosition = this.miningPos;
      if (delayElapsed) {
         this.renderQueue.remove(targetPosition);
         reset(this);
         restartPending.set(true);
         TaskScheduler scheduleRebreakRotationReceiver = ClientContext.taskScheduler;
         Runnable scheduleRebreakRotationTask = () -> {
            restartPending.set(false);
            Direction direction = Direction.UP;
            ClientAccess.argusclientMinecraft.gameMode.startDestroyBlock(targetPosition, direction);
         };
         scheduleRebreakRotationReceiver.scheduleRotationTask(scheduleRebreakRotationTask, 1);
      }

      return delayElapsed;
   }

   public boolean isBreakDelayActive() {
      return SpeedMineTiming.isBreakDelayActive(
         this.noBreakDelaySetting.getValue(), this.breakDelayTimer.hasElapsed(SpeedMineTiming.BREAK_DELAY_MILLIS)
      );
   }

   private boolean isAlternativeAllowed() {
      boolean is2b2tMode = RotationManager.is2b2tMode();
      return SpeedMinePolicy.isAlternativeAllowed(
         is2b2tMode,
         is2b2tMode && multiTaskModule.isItemUseRestricted(),
         is2b2tMode && multiTaskModule.isItemUseActiveOrRecent(150L),
         this.alternativeSetting.getValue()
      );
   }

   public float getRenderProgress() {
      return SpeedMineTiming.renderProgress(this.getMiningProgress(), this.damageSetting.getValue());
   }

   public static void reset(SpeedMineModule owner) {
      if (owner.miningPos != null && !owner.isWorldUnavailable()) {
         Action abortAction = Action.ABORT_DESTROY_BLOCK;
         BlockPos abortPosition = owner.miningPos;
         BlockPos facePosition = owner.miningPos;
         Direction direction = selectDestroyFace(facePosition);
         PacketSender.sendPlayerAction(abortAction, abortPosition, direction);
         SwingAction swingAction = SwingAction.MINING;
         InteractionHand hand = InteractionHand.MAIN_HAND;
         ClientContext.swingManager.swingPacket(hand, swingAction);
         ((BlockBreakingState)ClientAccess.argusclientMinecraft.gameMode).setBreakingBlock(false);
         ((BlockBreakingState)ClientAccess.argusclientMinecraft.gameMode).setBreakingProgress(0.0F);
      }

      owner.miningPos = null;
      owner.silentSwapProgress = owner.miningProgress = 0.0F;
      owner.bestToolSlot = -1;
      owner.pendingBreakPositions.clear();
      owner.retargetTimer.reset();
      owner.rebreakTracker.reset();
      owner.confirmation.clear();
   }

   /**
    * Server block updates, before they are applied to the client world (from {@code ClientLevelMixin}).
    * Air on the block being rebroken confirms the last break.
    */
   public void onServerBlockUpdate(BlockPos blockPos, BlockState blockState) {
      if (this.isToggled() && blockState.isAir()) {
         this.confirmation.onServerAir(blockPos.asLong());
      }
   }

   /**
    * Whether an Instant rebreak may be sent at {@code blockPos} now. Breaks before the server has confirmed
    * the block are retries and always allowed. Strict (and Bypass until confirmed) only rebreaks a real
    * replacement block, waits for the previous rebreak's confirmation, and waits for TPS start credit.
    */
   private boolean mayInstantRebreak(BlockPos blockPos) {
      long pos = blockPos.asLong();
      SpeedMineConfirmChoice mode = this.confirmSetting.getValue();
      if (!this.confirmation.isRebreak(pos) || this.confirmation.isStrong(mode)) {
         return true;
      }
      return !WorldUtils.isAir(blockPos) && this.confirmation.canRebreak(mode, System.currentTimeMillis()) && this.canStartNewBlock();
   }

   /** Records an Instant break sent at {@code blockPos}; a confirmation-gated rebreak also spends TPS credit. */
   private void recordInstantBreak(BlockPos blockPos) {
      long pos = blockPos.asLong();
      if (this.confirmation.isRebreak(pos) && !this.confirmation.isStrong(this.confirmSetting.getValue())) {
         this.startCredit = SpeedMineTiming.spendStartCredit(this.tpsSyncSetting.getValue(), this.startCredit);
      }
      this.confirmation.onBreakSent(pos, System.currentTimeMillis());
   }

   @Override
   public String getInfo() {
      BlockPos miningPosition = this.miningPos;
      if (!WorldUtils.canMineBlock(miningPosition)) {
         return null;
      }

      Object[] arguments = new Object[1];
      float progress = this.getRenderProgress();
      arguments[0] = progress;
      return "%.2f".formatted(arguments);
   }

   @Override
   public void onEnable() {
      reset(this);
      this.lastMiningPos = null;
      swapPending.set(false);
      this.renderQueue.clear();
      this.resetStartCredit();
      this.mineQueue.clear();
   }

   /**
    * True while the current block still needs mining: it is there and the server has not yet confirmed a break
    * we sent for it. Once confirmed, any rebreaking of it gives way to queued blocks.
    */
   private boolean isMiningUnfinished() {
      return this.miningPos != null && WorldUtils.canMineBlock(this.miningPos) && !this.confirmation.isRebreak(this.miningPos.asLong());
   }

   /** Starts the next queued block once the current one is finished, dropping queued blocks that became invalid. */
   private void drainQueue() {
      if (!this.queueSetting.getValue()) {
         this.mineQueue.clear();
         return;
      }

      this.mineQueue.prune(pos -> {
         BlockPos queued = BlockPos.of(pos);
         return !WorldUtils.canMineBlock(queued) || !this.isBlockAllowed(queued) || !this.isInMiningRange(queued);
      });
      if (this.mineQueue.isEmpty() || this.isMiningUnfinished() || !this.canStartNewBlock() || this.isBreakDelayActive()
         || ClientAccess.argusclientMinecraft.gameMode == null) {
         return;
      }

      BlockPos next = BlockPos.of(this.mineQueue.head());
      queueDrainStart = true;
      try {
         ClientAccess.argusclientMinecraft.gameMode.startDestroyBlock(next, selectDestroyFace(next));
      } finally {
         queueDrainStart = false;
      }

      if (next.equals(this.miningPos)) {
         this.mineQueue.headStarted();
      } else {
         this.mineQueue.headFailed();
      }
   }

   /** Records a break sent at {@code blockPos}: Instant goes through confirmation gating, other modes are only tracked. */
   private void recordBreakSent(BlockPos blockPos) {
      if (this.rebreakSetting.getValue().isInstant()) {
         this.recordInstantBreak(blockPos);
      } else {
         this.confirmation.onBreakSent(blockPos.asLong(), System.currentTimeMillis());
      }
   }

   private void resetStartCredit() {
      this.startCredit = 1.0;
      this.lastStartCreditMillis = System.currentTimeMillis();
   }

   /** Measured server TPS, or 20 before the first time update has been measured. */
   private float serverTps() {
      float tps = ClientContext.serverStateManager.getTickRate();
      return tps > 0.0F ? tps : 20.0F;
   }

   /** False while TPS sync is on, the server is lagging and no start has been earned yet. */
   private boolean canStartNewBlock() {
      return SpeedMineTiming.canStart(this.tpsSyncSetting.getValue(), this.serverTps(), this.startCredit);
   }

   private static Direction selectDestroyFace(BlockPos blockPos) {
      List directions = BlockInteractionUtils.getStrictDirections(blockPos);
      return !directions.isEmpty() ? (Direction)directions.get(0) : Direction.UP;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      float progressScale = SpeedMineTiming.progressScale(
         this.tpsSyncSetting.getValue(),
         ClientContext.serverStateManager.getTickRateScale(),
         ClientContext.tickRateController.getMultiplier()
      );
      long now = System.currentTimeMillis();
      this.startCredit = SpeedMineTiming.replenishStartCredit(this.startCredit, now - this.lastStartCreditMillis, this.serverTps());
      this.lastStartCreditMillis = now;
      this.getExtraBreakTracker().pruneExpired();
      this.renderQueue.snapshotProgress();
      this.updateExtraBreakProgress(progressScale);
      this.updateMiningProgress(progressScale);
      this.drainQueue();
   }

   private void updateExtraBreakProgress(float progressScale) {
      if (this.extraBreakTarget != null) {
         BlockPos extraPosition = this.extraBreakTarget.getPosition();
         BlockState extraState = ClientAccess.argusclientMinecraft.level.getBlockState(extraPosition);
         Boolean alternativeEnabled = this.alternativeSetting.getValue();
         boolean allowInventoryTools = !alternativeEnabled;
         int extraToolSlot = InventoryUtils.findBestToolSlot(extraPosition, allowInventoryTools);
         ItemStack extraTool = ClientAccess.argusclientMinecraft.player.getInventory().getItem(extraToolSlot);
         boolean extraOnGround = ClientAccess.argusclientMinecraft.player.onGround();
         double extraProgress = WorldUtils.getBreakProgress(extraTool, extraState, extraOnGround) * progressScale;
         ExtraBreakTarget extraTarget = this.extraBreakTarget;
         float extraProgressFloat = (float)extraProgress;
         extraTarget.addProgress(extraProgressFloat);
         if (!extraPosition.equals(this.miningPos)) {
            SpeedMineRenderQueue extraRenderQueue = this.renderQueue;
            float extraRenderProgress = this.extraBreakTarget.getClampedProgress();
            extraRenderQueue.setProgress(extraPosition, extraRenderProgress);
         }

         if (ClientAccess.argusclientMinecraft.level.isEmptyBlock(extraPosition)) {
            this.extraBreakTarget = null;
            this.extraBreakTimer.reset();
         }
      }
   }

   private void updateMiningProgress(float progressScale) {
      if (this.lastMiningPos != null) {
         BlockPos lastPosition = this.lastMiningPos;
         if (ClientAccess.argusclientMinecraft.level.getBlockState(lastPosition).getBlock() instanceof ShulkerBoxBlock) {
            WorldStateManager worldState = ClientContext.worldStateManager;
            BlockPos placedPosition = this.lastMiningPos;
            if (worldState.hasKnownPlacement(placedPosition)) {
               this.lastMiningPos = null;
            }
         }
      }

      AtomicBoolean pendingRestart = restartPending;
      if (!pendingRestart.get()) {
         if (this.miningPos == null) {
            if (this.lastMiningPos != null) {
               LocalPlayer player = ClientAccess.argusclientMinecraft.player;
               if (!player.isCreative()) {
                  BlockPos rebreakPosition = this.lastMiningPos;
                  if (WorldUtils.canMineBlock(rebreakPosition) && this.rebreakSetting.getValue().isFast()) {
                     this.miningPos = this.lastMiningPos;
                  }
               }
            }
         } else {
            BlockPos currentPosition = this.miningPos;
            if (!WorldUtils.canMineBlock(currentPosition)) {
               if (this.rebreakSetting.getValue().isInstant()) {
                  BlockPos airPosition = this.miningPos;
                  if (WorldUtils.isAir(airPosition) && this.airSetting.getValue()) {
                     SpeedMineRenderQueue airRenderQueue = this.renderQueue;
                     BlockPos airRenderPosition = this.miningPos;
                     float airProgress = this.miningProgress;
                     Float airDamageThreshold = this.damageSetting.getValue();
                     float airRenderProgress = airProgress / airDamageThreshold;
                     airRenderQueue.setProgress(airRenderPosition, airRenderProgress);
                  }
               }

               if (this.rebreakSetting.getValue().isFast()) {
                  ArrayDeque<Boolean> pendingPositions = this.pendingBreakPositions;
                  boolean queuedOnGround = ClientAccess.argusclientMinecraft.player.onGround();
                  Boolean queuedGroundState = queuedOnGround;
                  pendingPositions.add(queuedGroundState);
                  if (this.pendingBreakPositions.size() > 200) {
                     this.pendingBreakPositions.removeLast();
                  }
               }
            } else {
               BlockPos miningPosition = this.miningPos;
               BlockState miningState = ClientAccess.argusclientMinecraft.level.getBlockState(miningPosition);
               Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
               int toolSlot = this.getMiningToolSlot();
               ItemStack toolStack = inventory.getItem(toolSlot);
               ItemStack unenchantedStack = new ItemStack(toolStack.getItem());
               int selectedToolSlot = this.getMiningToolSlot();
               Inventory selectionInventory = ClientAccess.argusclientMinecraft.player.getInventory();
               if (selectedToolSlot != selectionInventory.getSelectedSlot() && this.value121Setting.getValue() == SpeedMineValue121Choice.SLOW) {
                  toolStack = unenchantedStack;
               }

               boolean processedQueuedProgress = false;
               if (this.rebreakSetting.getValue().isFast()) {
                  while (!this.pendingBreakPositions.isEmpty()) {
                     boolean queuedOnGround = this.pendingBreakPositions.poll();
                     this.miningProgress = this.miningProgress + (float)(WorldUtils.getBreakProgress(toolStack, miningState, queuedOnGround) * progressScale);
                     processedQueuedProgress = true;
                     SpeedMineRenderQueue queuedRenderQueue = this.renderQueue;
                     BlockPos queuedPosition = this.miningPos;
                     float queuedProgress = this.miningProgress;
                     Float queuedDamageThreshold = this.damageSetting.getValue();
                     float queuedRenderProgress = queuedProgress / queuedDamageThreshold;
                     queuedRenderQueue.setProgressTwice(queuedPosition, queuedRenderProgress);
                  }
               }

               if (processedQueuedProgress) {
                  Block obsidian = Blocks.OBSIDIAN;
                  if (miningState.is(obsidian)) {
                     float obsidianProgress = this.miningProgress;
                     float maximumObsidianProgress = 0.99F;
                     this.miningProgress = Mth.clamp(obsidianProgress, 0.0F, maximumObsidianProgress);
                     SpeedMineRenderQueue obsidianRenderQueue = this.renderQueue;
                     BlockPos obsidianPosition = this.miningPos;
                     float clampedObsidianProgress = this.miningProgress;
                     Float obsidianDamageThreshold = this.damageSetting.getValue();
                     float obsidianRenderProgress = clampedObsidianProgress / obsidianDamageThreshold;
                     obsidianRenderQueue.setProgressTwice(obsidianPosition, obsidianRenderProgress);
                     return;
                  }
               }

               float previousSilentProgress = this.silentSwapProgress;
               boolean silentOnGround = ClientAccess.argusclientMinecraft.player.onGround();
               this.silentSwapProgress = previousSilentProgress
                  + (float)(WorldUtils.getBreakProgress(unenchantedStack, miningState, silentOnGround) * progressScale);
               float previousMiningProgress = this.miningProgress;
               boolean miningOnGround = ClientAccess.argusclientMinecraft.player.onGround();
               this.miningProgress = previousMiningProgress + (float)(WorldUtils.getBreakProgress(toolStack, miningState, miningOnGround) * progressScale);
               SpeedMineRenderQueue miningRenderQueue = this.renderQueue;
               BlockPos renderPosition = this.miningPos;
               float renderProgress = this.miningProgress;
               Float renderDamageThreshold = this.damageSetting.getValue();
               float renderFraction = renderProgress / renderDamageThreshold;
               miningRenderQueue.setProgress(renderPosition, renderFraction);
            }
         }
      }
   }

   private boolean canReplaceExtraBreakTarget(ExtraBreakTarget candidate, BlockPos blockPos) {
      ElapsedTimer timer = this.extraBreakTimer;
      if (!timer.hasElapsed(200L)) {
         return false;
      }

      if (candidate == null) {
         return true;
      }

      boolean samePosition = candidate.getPosition().equals(blockPos);
      BlockPos candidatePosition = candidate.getPosition();
      boolean invalidCandidate = !this.isBlockAllowed(candidatePosition);
      if (!samePosition && !invalidCandidate) {
         AtomicBoolean pendingSwap = swapPending;
         if (!pendingSwap.get()) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      reset(this);
      this.lastMiningPos = null;
      this.extraBreakTarget = null;
      this.resetStartCredit();
      this.mineQueue.clear();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundRespawnPacket) {
         reset(this);
         this.lastMiningPos = null;
         this.extraBreakTarget = null;
         ClientAccess.argusclientMinecraft.execute(this.mineQueue::clear);
      }

      this.rebreakTracker.handleBlockUpdates(event);
   }

   public boolean hasCrystalDelayElapsed(long delayMillis) {
      return this.crystalDelayTimer.hasElapsed(delayMillis);
   }

   @EventHandler(priority = 100)
   private void onSelectedSlotChange(SelectedSlotChangeEvent event) {
      Boolean alternativeEnabled = this.alternativeSetting.getValue();
      if (alternativeEnabled) {
         if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
            Boolean resetEnabled = this.swapResetSetting.getValue();
            if (resetEnabled) {
               restartPending.set(true);
               BlockPos currentPosition = this.miningPos;
               if (WorldUtils.canMineBlock(currentPosition)) {
                  BlockPos renderPosition = this.miningPos;
                  this.renderQueue.setProgressTwice(renderPosition, 0.0F);
               }

               this.silentSwapProgress = 0.0F;
               this.miningProgress = 0.0F;
               TaskScheduler scheduleStartDestroyRotationReceiver = ClientContext.taskScheduler;
               Runnable scheduleStartDestroyRotationTask = () -> {
                  restartPending.set(false);
                  BlockPos targetPosition = this.miningPos;
                  if (targetPosition != null) {
                     SwingAction swingAction = SwingAction.MINING;
                     InteractionHand hand = InteractionHand.MAIN_HAND;
                     ClientContext.swingManager.swing(hand, swingAction);
                     if (WorldUtils.canMineBlock(targetPosition)) {
                        automatedMiningStart = true;
                        reset(this);
                        MultiPlayerGameModeStartDestroyBlockEvent startEvent = new MultiPlayerGameModeStartDestroyBlockEvent(
                           targetPosition, selectDestroyFace(targetPosition)
                        );
                        this.onMultiPlayerGameModeStartDestroyBlockEvent(startEvent);
                        automatedMiningStart = false;
                     } else if (this.rebreakSetting.getValue().isFast()) {
                        Action startAction = Action.START_DESTROY_BLOCK;
                        Direction direction = selectDestroyFace(targetPosition);
                        PacketSender.sendPlayerAction(startAction, targetPosition, direction);
                        this.pendingBreakPositions.clear();
                     }
                  }
               };
               scheduleStartDestroyRotationReceiver.scheduleRotationTask(scheduleStartDestroyRotationTask, 1);
            }
         }
      }
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      if (this.miningPos != null && this.rebreakSetting.getValue().isInstant()) {
         if (event.getEntity() instanceof ItemEntity) {
            AABB miningBounds = new AABB(this.miningPos);
            Vec3 entityPosition = event.getEntity().position();
            if (miningBounds.contains(entityPosition)) {
               if (this.asyncSetting.getValue()) {
                  this.crystalDelayTimer.reset();
               }

               if (this.alternativeSetting.getValue()) {
                  this.itemSpawnTimer.clear();
               }
            }
         }
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      if (this.renderSetting.getValue() && ClientAccess.argusclientMinecraft.player.gameMode() == GameType.SURVIVAL) {
         this.renderQueue.render(event);
         if (!this.mineQueue.isEmpty()) {
            Color queuedOutline = this.outlineSetting.getValue();
            float lineWidth = this.lineWidthSetting.getValue();
            for (long pos : this.mineQueue.snapshot()) {
               WorldBoxRenderer.outlineBox(event.getPoseStack(), BlockPos.of(pos), queuedOutline, lineWidth);
            }
         }
      }
   }

   @EventHandler(priority = -100)
   private void onMultiPlayerGameModeStartDestroyBlockEvent(MultiPlayerGameModeStartDestroyBlockEvent event) {
      BlockPos requestedPosition = event.getPos();
      if (!event.isCancelled()) {
         if (!this.isBlockAllowed(requestedPosition)) {
            this.lastMiningPos = null;
            reset(this);
            if (!WorldUtils.canMineBlock(requestedPosition)) {
               event.cancel();
            }
         } else {
            LocalPlayer spectatorCheckPlayer = ClientAccess.argusclientMinecraft.player;
            if (!spectatorCheckPlayer.isSpectator()) {
               LocalPlayer creativeCheckPlayer = ClientAccess.argusclientMinecraft.player;
               if (!creativeCheckPlayer.isCreative()) {
                  event.cancel();
                  ElapsedTimer timer = this.startDestroyRetryTimer;
                  if (!timer.hasElapsed(100L) && !automatedMiningStart && !queueDrainStart) {
                     if (requestedPosition.equals(this.miningPos)) {
                        timer = this.startDestroyRetryTimer;
                        timer.reset();
                     }

                     return;
                  }

                  if (this.extraBreakSetting.getValue() && this.sneakExtendSetting.getValue()) {
                     KeyMapping sneakKey = ClientAccess.argusclientMinecraft.options.keyShift;
                     if (KeyUtils.isPhysicallyDown(sneakKey)) {
                        ExtraBreakTarget currentExtraTarget = this.extraBreakTarget;
                        if (!this.canReplaceExtraBreakTarget(currentExtraTarget, requestedPosition)) {
                           return;
                        }

                        if (this.extraBreakTarget == null) {
                           float[] rotations = RotationManager.getRotationsToBlock(requestedPosition);
                           float yaw = rotations[0];
                           float pitch = rotations[1];
                           Vec3 blockCenter = Vec3.atCenterOf(requestedPosition);
                           Vec3 lookDirection = Vec3.directionFromRotation(pitch, yaw);
                           Position containingPosition = blockCenter.add(lookDirection);
                           BlockPos extendedPosition = BlockPos.containing(containingPosition);
                           if (this.isBlockAllowed(extendedPosition)) {
                              requestedPosition = extendedPosition;
                           }
                        }
                     }
                  }

                  if (!WorldUtils.canMineBlock(requestedPosition) && !automatedMiningStart) {
                     return;
                  }

                  if (this.extraBreakSetting.getValue() && this.extraBreakTarget != null) {
                     ExtraBreakTarget existingExtraTarget = this.extraBreakTarget;
                     if (requestedPosition.equals(existingExtraTarget.getPosition())) {
                        return;
                     }
                  }

                  if (this.isBreakDelayActive()) {
                     BlockPos currentTarget = this.miningPos;
                     if (!WorldUtils.canMineBlock(currentTarget)) {
                        reset(this);
                     }

                     return;
                  }

                  if (!this.isInMiningRange(requestedPosition)) {
                     return;
                  }

                  // Your clicks on other blocks wait their turn while the current block is unfinished or others are queued.
                  if (playerAttackDepth > 0
                     && this.queueSetting.getValue()
                     && !requestedPosition.equals(this.miningPos)
                     && (this.isMiningUnfinished() || !this.mineQueue.isEmpty())) {
                     this.mineQueue.add(requestedPosition.asLong());
                     return;
                  }

                  // A new block waits for TPS credit; vanilla re-requests the start next tick while attack is held.
                  // Restarts of the current or last block (rebreaks) are left to the rebreak modes.
                  boolean newBlock = !requestedPosition.equals(this.miningPos)
                     && !requestedPosition.equals(this.lastMiningPos)
                     && !automatedMiningStart;
                  if (newBlock && !this.canStartNewBlock()) {
                     return;
                  }

                  timer = this.startDestroyRetryTimer;
                  timer.reset();
                  boolean changedTarget = false;
                  if (this.miningPos != null && !this.miningPos.equals(requestedPosition)) {
                     SpeedMineRenderQueue oldRenderQueue = this.renderQueue;
                     BlockPos oldTarget = this.miningPos;
                     oldRenderQueue.remove(oldTarget);
                     reset(this);
                     changedTarget = true;
                  }

                  if (!changedTarget && this.miningPos != null && this.miningPos.equals(requestedPosition)) {
                     timer = this.retargetTimer;
                     if (timer.hasElapsed(100L)) {
                        SpeedMineRenderQueue retargetRenderQueue = this.renderQueue;
                        BlockPos retargetPosition = this.miningPos;
                        retargetRenderQueue.remove(retargetPosition);
                        this.lastMiningPos = null;
                        reset(this);
                     }

                     return;
                  }

                  if (this.alternativeSetting.getValue()
                     && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT
                     && this.swapResetSetting.getValue()) {
                     if (this.crystalAttackPending) {
                        this.crystalAttackPending = false;
                        reset(this);
                        return;
                     }

                     if (autoExpModule.isToggled() && autoExpModule.modeSetting.getValue() == AutoExpModule.ModeChoice.SILENT && !autoExpModule.stopped) {
                        reset(this);
                        return;
                     }
                  }

                  if (!automatedMiningStart) {
                     autoMineModule.setMiningActive(true);
                  }

                  this.miningPos = this.lastMiningPos = requestedPosition;
                  if (newBlock) {
                     this.startCredit = SpeedMineTiming.spendStartCredit(this.tpsSyncSetting.getValue(), this.startCredit);
                  }
                  BlockPos facePosition = this.miningPos;
                  Direction destroyFace = selectDestroyFace(facePosition);
                  SwingAction miningSwing = SwingAction.MINING;
                  InteractionHand miningHand = InteractionHand.MAIN_HAND;
                  ClientContext.swingManager.swing(miningHand, miningSwing);
                  BlockPos instantBreakPosition = this.miningPos;
                  SpeedMineModule.InstantBreakResult instantBreak = this.evaluateInstantBreak(instantBreakPosition);
                  int toolSlot = instantBreak.toolSlot();
                  int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
                  BlockPos hardnessPosition = this.miningPos;
                  float hardness = WorldUtils.getHardness(hardnessPosition);
                  if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL) {
                     BlockPos toolSearchPosition = this.miningPos;
                     toolSlot = InventoryUtils.findBestToolSlot(toolSearchPosition, true);
                     if (toolSlot != selectedSlot && this.swapBackSetting.getValue()) {
                        this.previousSelectedSlot = selectedSlot;
                     }
                  }

                  if (instantBreak.canInstantBreak()) {
                     if (this.alternativeSetting.getValue() && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                        InventoryUtils.swapIntoSelectedSlot(toolSlot);
                     } else if (selectedSlot != instantBreak.toolSlot()) {
                        InventoryUtils.selectHotbarSlotForPlacement(toolSlot);
                     }
                  }

                  if (this.extraBreakSetting.getValue() && !instantBreak.breaksInOneTick()) {
                     BlockPos extraStartPosition = this.miningPos;
                     Action extraStartAction = Action.START_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(extraStartAction, extraStartPosition, destroyFace);
                     BlockPos extraGrimPosition = this.miningPos;
                     this.sendGrimStartPacket(extraGrimPosition, destroyFace);
                     BlockPos extraStopPosition = this.miningPos;
                     Action extraStopAction = Action.STOP_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(extraStopAction, extraStopPosition, destroyFace);
                     timer = this.breakDelayTimer;
                     timer.reset();
                     if (this.extraBreakTarget == null) {
                        this.extraBreakTarget = new ExtraBreakTarget(this.miningPos);
                     }
                  } else {
                     BlockPos startPosition = this.miningPos;
                     Action startAction = Action.START_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(startAction, startPosition, destroyFace);
                     BlockPos grimPosition = this.miningPos;
                     this.sendGrimStartPacket(grimPosition, destroyFace);
                     if (instantBreak.canInstantBreak() && !instantBreak.breaksInOneTick()) {
                        BlockPos stopPosition = this.miningPos;
                        Action stopAction = Action.STOP_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(stopAction, stopPosition, destroyFace);
                        timer = this.breakDelayTimer;
                        timer.reset();
                     }
                  }

                  if (instantBreak.canInstantBreak() && toolSlot != -1) {
                     if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                        if (this.alternativeSetting.getValue()) {
                           InventoryUtils.swapIntoSelectedSlot(toolSlot);
                        } else if (selectedSlot != toolSlot) {
                           InventoryUtils.selectHotbarSlotForPlacement(selectedSlot);
                        }
                     }

                     WorldStateManager worldState = ClientContext.worldStateManager;
                     BlockPos predictedPosition = this.miningPos;
                     worldState.destroyBlockPredicted(predictedPosition);
                     BlockPos attemptPosition = this.miningPos;
                     this.recordBreakAttempt(attemptPosition);
                     this.recordBreakSent(attemptPosition);

                     this.startDestroyRetryTimer.clear();
                     this.extraBreakTarget = null;
                     if (this.rebreakSetting.getValue().isInstant() && hardness == 0.0F) {
                        this.waitingForRebreak = true;
                     }

                     if (this.rebreakSetting.getValue().isNone()) {
                        this.miningPos = null;
                     }
                  }

                  timer = this.retargetTimer;
                  timer.reset();
                  return;
               }
            }
         }
      }
   }

   @EventHandler(priority = 100)
   private void onMiningTick(RotationRequestEvent event) {
      if (this.waitingForRebreak) {
         BlockPos rebreakPosition = this.miningPos;
         if (WorldUtils.canMineBlock(rebreakPosition)) {
            rebreakPosition = this.miningPos;
            if (WorldUtils.getHardness(rebreakPosition) > 0.0F) {
               this.waitingForRebreak = false;
               this.miningProgress = 0.0F;
            }
         }
      }

      BlockPos currentMiningPosition = this.miningPos;
      if (WorldUtils.canMineBlock(currentMiningPosition)) {
         BlockPos toolSearchPosition = this.miningPos;
         Boolean alternativeEnabled = this.alternativeSetting.getValue();
         boolean allowInventoryTools = !alternativeEnabled;
         this.bestToolSlot = InventoryUtils.findBestToolSlot(toolSearchPosition, allowInventoryTools);
      } else if (this.rebreakSetting.getValue().isNone()) {
         reset(this);
      }

      int toolSlot = this.bestToolSlot;
      if (!swapPending.get()) {
         if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE) {
            ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
            if (!ItemSaverModule.isSafeToUse(heldStack) && itemSaverModule.isToggled()) {
               return;
            }
         }

         float health = EntityUtils.getPlayerHealthWithAbsorption();
         boolean holdingTotem = ClientAccess.argusclientMinecraft.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
         boolean canSwapTools = SpeedMinePolicy.canSwapTools(health, holdingTotem);

         if (this.extraBreakTarget != null) {
            ExtraBreakTarget extraTarget = this.extraBreakTarget;
            if (!extraTarget.getPosition().equals(this.miningPos)
               && this.extraBreakTarget.getClampedProgress() >= 1.0F
               && !this.isMainHandMultitask()
               && canSwapTools) {
               swapPending.set(true);
               boolean extraInventorySwap = false;
               if (this.alternativeSetting.getValue() && !RotationManager.is2b2tMode()) {
                  extraInventorySwap = true;
               }

               boolean useExtraInventorySwap = extraInventorySwap;
               int extraPreviousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
               currentMiningPosition = this.extraBreakTarget.getPosition();
               boolean allowExtraInventoryTools = !useExtraInventorySwap;
               int extraToolSlot = InventoryUtils.findBestToolSlot(currentMiningPosition, allowExtraInventoryTools);
               if (useExtraInventorySwap) {
                  InventoryUtils.swapIntoSelectedSlot(extraToolSlot);
               } else {
                  PacketSender.sendSelectedSlot(extraToolSlot);
               }

               SwingInteractionManager extraSwingManager = ClientContext.swingManager;
               InteractionHand extraSwingHand = InteractionHand.MAIN_HAND;
               SwingAction extraSwingAction = SwingAction.MINING;
               extraSwingManager.swing(extraSwingHand, extraSwingAction, true);
               BlockPos extraRotationPosition = this.extraBreakTarget.getPosition();
               this.rotateToBlock(extraRotationPosition);
               BlockPos extraAttemptPosition = this.extraBreakTarget.getPosition();
               this.recordBreakAttempt(extraAttemptPosition);
               BlockPos extraEffectPosition = this.extraBreakTarget.getPosition();
               this.playBreakEffects(extraEffectPosition);
               this.extraBreakTimer.reset();
               ClientContext.worldStateManager.markExtraBreak(this.extraBreakTarget.getPosition());
               AutoMineMiningTracker miningTracker = autoMineModule.miningTracker;
               BlockPos trackedExtraPosition = this.extraBreakTarget.getPosition();
               miningTracker.rememberPosition(trackedExtraPosition);
               this.extraBreakTarget = null;
               TaskScheduler extraSwapScheduler = ClientContext.taskScheduler;
               Runnable restoreExtraSlot = () -> {
                  if (useExtraInventorySwap) {
                     InventoryUtils.swapIntoSelectedSlot(extraToolSlot);
                  } else {
                     PacketSender.sendSelectedSlot(extraPreviousSlot);
                     LocalPlayer player = ClientAccess.argusclientMinecraft.player;
                     Inventory inventory = player.getInventory();
                     inventory.setSelectedSlot(extraPreviousSlot);
                  }

                  swapPending.set(false);
               };
               extraSwapScheduler.scheduleRotationTask(restoreExtraSlot, 2);
               return;
            }
         }

         boolean lastBreakWasShulker = this.extraBreakTracker.getLastBlock() instanceof ShulkerBoxBlock;
         boolean blockedByContainer = RotationManager.is2b2tMode() && ClientAccess.argusclientMinecraft.gui.screen() instanceof AbstractContainerScreen;
         currentMiningPosition = this.miningPos;
         boolean canContinueMining = WorldUtils.canMineBlock(currentMiningPosition);
         if (this.asyncSetting.getValue() && !blockedByContainer && !lastBreakWasShulker) {
            canContinueMining = true;
         }

         if (this.miningPos == null) {
            canContinueMining = false;
         }

         currentMiningPosition = this.miningPos;
         if (!WorldUtils.canMineBlock(currentMiningPosition)) {
            BlockPos aboveCheckPosition = this.getMiningPos();
            if (this.isPlayerAboveBlock(aboveCheckPosition)) {
               canContinueMining = false;
            }
         }

         if (this.previousSelectedSlot != -1
            && !canContinueMining
            && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL
            && this.swapBackSetting.getValue()) {
            int previousSlot = this.previousSelectedSlot;
            InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
            this.previousSelectedSlot = -1;
         } else {
            if (!this.restartForRebreak()) {
               if (this.miningPos != null) {
                  AnchorAuraModule anchorAura = anchorAuraModule;
                  BlockPos anchorPosition = this.miningPos;
                  if (anchorAura.isManagedAnchor(anchorPosition)) {
                     return;
                  }
               }

               LocalPlayer spectatorCheckPlayer = ClientAccess.argusclientMinecraft.player;
               if (!spectatorCheckPlayer.isSpectator()) {
                  LocalPlayer creativeCheckPlayer = ClientAccess.argusclientMinecraft.player;
                  if (!creativeCheckPlayer.isCreative() && canContinueMining) {
                     boolean rotate = SpeedMinePolicy.shouldRotate(
                        this.limitSetting.getValue(),
                        this.getMiningProgress(),
                        this.damageSetting.getValue(),
                        this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE,
                        this.holdingBestSetting.getValue(),
                        toolSlot == ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot()
                     );
                     if (rotate) {
                        BlockPos rotationTarget = this.miningPos;
                        this.rotateToBlock(rotationTarget);
                     }

                     float progress = this.getMiningProgress();
                     Float damageThreshold = this.damageSetting.getValue();
                     if (progress < damageThreshold) {
                        return;
                     }

                     boolean alternativeAllowed = this.isAlternativeAllowed();
                     float instantDelay = SpeedMineTiming.instantDelaySeconds(
                        this.instantDelaySetting.getValue(), alternativeAllowed, RotationManager.is2b2tMode()
                     );

                     if (this.rebreakSetting.getValue().isInstant()) {
                        double instantDelaySeconds = instantDelay;
                        TimeUnit instantDelayUnit = TimeUnit.SECONDS;
                        ElapsedTimer instantDelayTimer = this.breakDelayTimer;
                        if (!instantDelayTimer.hasElapsed(instantDelaySeconds, instantDelayUnit)) {
                           return;
                        }

                        if (!this.mayInstantRebreak(this.miningPos)) {
                           return;
                        }
                     }

                     if (this.isMainHandMultitask() && !alternativeAllowed) {
                        return;
                     }

                     boolean holdingTool = toolSlot == InventoryUtils.getSelectedSlot();
                     if (this.rebreakSetting.getValue().isInstant() && !holdingTool && alternativeAllowed) {
                        ElapsedTimer itemSpawnDelay = this.itemSpawnTimer;
                        if (!itemSpawnDelay.hasElapsed(150L)) {
                           return;
                        }
                     }

                     if (!this.canBreakNearCrystal()) {
                        return;
                     }

                     boolean swappedTool = false;
                     int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
                     BlockPos statePosition = this.miningPos;
                     BlockState miningState = ClientAccess.argusclientMinecraft.level.getBlockState(statePosition);
                     BlockPos facePosition = this.miningPos;
                     Direction destroyFace = selectDestroyFace(facePosition);
                     if (this.autoSwapModeSetting.getValue() != SpeedMineAutoSwapChoice.NONE
                        && toolSlot != -1
                        && ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot() != toolSlot) {
                        if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && alternativeAllowed) {
                           InventoryUtils.swapIntoSelectedSlot(toolSlot);
                        } else {
                           this.previousSelectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
                           InventoryUtils.selectHotbarSlotForPlacement(toolSlot);
                        }

                        swappedTool = true;
                     }

                     if (!swappedTool && selectedSlot != toolSlot && this.rebreakSetting.getValue().isInstant() && this.holdingBestSetting.getValue()) {
                        return;
                     }

                     this.breakDelayTimer.reset();
                     this.itemSpawnTimer.reset();
                     SwingAction finishSwingAction = SwingAction.MINING;
                     InteractionHand finishSwingHand = InteractionHand.MAIN_HAND;
                     ClientContext.swingManager.swingPacket(finishSwingHand, finishSwingAction);
                     currentMiningPosition = this.miningPos;
                     boolean recordAttempt = WorldUtils.canMineBlock(currentMiningPosition);
                     if (!recordAttempt) {
                        Boolean asyncEnabled = this.asyncSetting.getValue();
                        recordAttempt = asyncEnabled;
                     }

                     if (recordAttempt) {
                        BlockPos attemptedPosition = this.miningPos;
                        this.recordBreakAttempt(attemptedPosition);
                     }

                     currentMiningPosition = this.miningPos;
                     if (WorldUtils.canMineBlock(currentMiningPosition)
                        && this.sneakExtendSetting.getValue()
                        && ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
                        WorldStateManager predictedWorldState = ClientContext.worldStateManager;
                        facePosition = this.miningPos;
                        predictedWorldState.destroyBlockPredicted(facePosition);
                     }

                     if (!RotationManager.isMovementSyncEnabled()) {
                        SpeedMineRebreakChoice rebreakMode = this.rebreakSetting.getValue();
                        if (!rebreakMode.isInstant()) {
                           double playerX = ClientAccess.argusclientMinecraft.player.getX();
                           double playerY = ClientAccess.argusclientMinecraft.player.getY();
                           double playerZ = ClientAccess.argusclientMinecraft.player.getZ();
                           boolean packetOnGround = ClientAccess.argusclientMinecraft.player.onGround();
                           PacketSender.sendPosition(playerX, playerY, playerZ, packetOnGround);
                        }
                     }

                     statePosition = this.miningPos;
                     SpeedMineRebreakTracker tracker = this.rebreakTracker;
                     BlockPos trackedPosition = this.miningPos;
                     tracker.startTracking(trackedPosition);
                     if (this.waitingForRebreak) {
                        BlockPos restartPosition = this.miningPos;
                        Action restartAction = Action.START_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(restartAction, restartPosition, destroyFace);
                     }

                     if (!miningState.isAir() && this.stalledBreakTimer.hasElapsedAndReset(200L)) {
                        BlockPos effectPosition = this.miningPos;
                        this.playBreakEffects(effectPosition);
                     }

                     BlockPos stopPosition = this.miningPos;
                     Action stopAction = Action.STOP_DESTROY_BLOCK;
                     PacketSender.sendPlayerAction(stopAction, stopPosition, destroyFace);
                     this.recordBreakSent(stopPosition);

                     WorldStateManager unconfirmedWorldState = ClientContext.worldStateManager;
                     BlockPos unconfirmedPosition = this.miningPos;
                     unconfirmedWorldState.markUnconfirmedBreak(unconfirmedPosition);
                     if (this.rebreakSetting.getValue().isFast()) {
                        BlockPos abortPosition = this.miningPos;
                        Action abortAction = Action.ABORT_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(abortAction, abortPosition, destroyFace);
                        BlockPos rebreakStopPosition = this.miningPos;
                        Action rebreakStopAction = Action.STOP_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(rebreakStopAction, rebreakStopPosition, destroyFace);
                        BlockPos rebreakStartPosition = this.miningPos;
                        Action rebreakStartAction = Action.START_DESTROY_BLOCK;
                        PacketSender.sendPlayerAction(rebreakStartAction, rebreakStartPosition, destroyFace);
                        this.miningPos = null;
                     }

                     Setting<SpeedMineAutoSwapChoice> silentSwapSetting = this.autoSwapModeSetting;
                     if (silentSwapSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && toolSlot != -1) {
                        boolean needsExtraTick = this.silentSwapProgress < 0.425F;
                        int slotToRestore = selectedSlot;
                        Runnable restoreSlotTask = () -> {
                           if (alternativeAllowed && this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT) {
                              InventoryUtils.swapIntoSelectedSlot(toolSlot);
                           } else if (slotToRestore != toolSlot || this.value121Setting.getValue() == SpeedMineValue121Choice.TICK) {
                              InventoryUtils.selectHotbarSlotForPlacement(slotToRestore);
                           }

                           autoCrystalModule.placementGate.set(false);
                        };
                        if (needsExtraTick && this.value121Setting.getValue() == SpeedMineValue121Choice.TICK && WorldUtils.canMineBlock(statePosition)) {
                           swapPending.set(true);
                           TaskScheduler scheduler = ClientContext.taskScheduler;
                           BlockPos delayedBreakPosition = statePosition;
                           Runnable delayedBreak = () -> {
                              PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, delayedBreakPosition, destroyFace);
                              WorldStateManager worldState = ClientContext.worldStateManager;
                              worldState.destroyBlockPredicted(delayedBreakPosition);
                              swapPending.set(false);
                              restoreSlotTask.run();
                           };
                           scheduler.scheduleRotationTask(delayedBreak, 1);
                        } else {
                           restoreSlotTask.run();
                        }
                     }

                     boolean resetAfterBreak = true;
                     if (this.rebreakSetting.getValue().isInstant()) {
                        if (!(miningState.getBlock() instanceof ShulkerBoxBlock)) {
                           resetAfterBreak = false;
                        } else {
                           WorldStateManager worldState = ClientContext.worldStateManager;
                           BlockPos miningPosition = this.miningPos;
                           if (!worldState.hasKnownPlacement(miningPosition)) {
                              resetAfterBreak = false;
                           }
                        }
                     }

                     if (resetAfterBreak) {
                        reset(this);
                     }

                     if (this.rotateSetting.getValue() && RotationManager.isSilentRotationMode() && WorldUtils.canMineBlock(statePosition)) {
                        RotationManager rotationManager = ClientContext.rotationManager;
                        Vec3 blockCenter = Vec3.atCenterOf(statePosition);
                        float[] rotations = RotationManager.getRotationsToPosition(blockCenter);
                        rotationManager.requestRotation(rotations, 150);
                     }

                     return;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre && this.miningPos != null) {
         BlockPos miningPosition = this.miningPos;
         if (!this.isInMiningRange(miningPosition)) {
            clearRebreakAndReset(this);
         }
      }
   }

   private void recordBreakAttempt(BlockPos blockPos) {
      if (WorldUtils.canMineBlock(blockPos)) {
         this.crystalDelayTimer.reset();
      }

      this.extraBreakTracker.trackBreak(blockPos);
   }

   private void sendGrimStartPacket(BlockPos blockPos, Direction direction) {
      if (this.grimSetting.getValue()) {
         Action startAction = Action.START_DESTROY_BLOCK;
         BlockPos offsetPosition = blockPos.above(1420);
         PacketSender.sendPlayerAction(startAction, offsetPosition, direction);
      }
   }

   public static void bindModules(ModuleRegistry moduleRegistry) {
      autoExpModule = (AutoExpModule)moduleRegistry.getModule(AutoExpModule.class);
      autoCrystalModule = (AutoCrystalModule)moduleRegistry.getModule(AutoCrystalModule.class);
      autoMineModule = (AutoMineModule)moduleRegistry.getModule(AutoMineModule.class);
      noGlitchBlocksModule = (NoGlitchBlocksModule)moduleRegistry.getModule(NoGlitchBlocksModule.class);
      itemSaverModule = (ItemSaverModule)moduleRegistry.getModule(ItemSaverModule.class);
      logoutSpotsModule = (LogoutSpotsModule)moduleRegistry.getModule(LogoutSpotsModule.class);
      multiTaskModule = (MultiTaskModule)moduleRegistry.getModule(MultiTaskModule.class);
      anchorAuraModule = (AnchorAuraModule)moduleRegistry.getModule(AnchorAuraModule.class);
   }

   private SpeedMineModule.InstantBreakResult evaluateInstantBreak(BlockPos blockPos) {
      if (noGlitchBlocksModule.isToggled() && noGlitchBlocksModule.breakSetting.getValue()) {
         return new SpeedMineModule.InstantBreakResult(false, -1, false);
      }

      BlockPos miningPosition = this.miningPos;
      Boolean alternativeEnabled = this.alternativeSetting.getValue();
      boolean allowInventoryTools = !alternativeEnabled;
      int bestToolSlot = InventoryUtils.findBestToolSlot(miningPosition, allowInventoryTools);
      int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      ItemStack tool = ClientAccess.argusclientMinecraft.player.getInventory().getItem(bestToolSlot);
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(blockPos);
      boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
      double breakProgress = WorldUtils.getBreakProgress(tool, state, onGround);
      boolean holdingBestTool = bestToolSlot == selectedSlot;
      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NONE && !holdingBestTool) {
         return new SpeedMineModule.InstantBreakResult(false, -1, false);
      }

      if (this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT
         && this.value121Setting.getValue() != SpeedMineValue121Choice.OFF
         && !holdingBestTool) {
         return new SpeedMineModule.InstantBreakResult(false, -1, false);
      }

      Float damageThreshold = this.damageSetting.getValue();
      return breakProgress < damageThreshold.floatValue()
         ? new SpeedMineModule.InstantBreakResult(false, -1, false)
         : new SpeedMineModule.InstantBreakResult(true, bestToolSlot, breakProgress >= 1.0);
   }

   public SpeedMineModule() {
      super("SpeedMine", "Mines blocks silently.", ModuleCategory.PLAYER);
      this.blocksSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue().isFiltering());
      this.rebreakEnabledSetting.setConfigName("RebreakPage");
      this.autoSwapSetting.setConfigName("SwapPage");
      this.abortSetting.setChangeCallback(() -> {
         if (!this.isWorldUnavailable() && this.abortSetting.getValue()) {
            reset(this);
            this.abortSetting.setValue(false);
         }
      });
   }

   private boolean isRebreakPageExpanded(Object ignored) {
      return this.rebreakEnabledSetting.isExpanded();
   }

   private boolean isInstantRebreak(Object ignored) {
      return this.rebreakSetting.getValue().isInstant();
   }

   private boolean isInstantRebreakPageExpanded(Object ignored) {
      return this.rebreakSetting.getValue().isInstant() && this.rebreakEnabledSetting.isExpanded();
   }

   private boolean isAutoSwapPageExpanded(Object ignored) {
      return this.autoSwapSetting.isExpanded();
   }

   private boolean isNormalSwap(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.NORMAL;
   }

   private boolean isSilentSwapPageExpanded(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && this.autoSwapSetting.isExpanded();
   }

   private boolean isSwapResetVisible(Object ignored) {
      return this.autoSwapModeSetting.getValue() == SpeedMineAutoSwapChoice.SILENT && this.alternativeSetting.getValue() && this.autoSwapSetting.isExpanded();
   }

   private boolean isRotatePageExpanded(Object ignored) {
      return this.rotateSetting.isExpanded();
   }

   private boolean isRenderPageExpanded(Object ignored) {
      return this.renderSetting.isExpanded();
   }

   private boolean isAirVisible(Object ignored) {
      return this.renderSetting.isExpanded() && this.rebreakSetting.getValue().isInstant();
   }

   private boolean isCustomColorVisible(Object ignored) {
      return this.renderSetting.isExpanded() ? this.colorModeSetting.getValue() == SpeedMineColorModeChoice.CUSTOM : false;
   }

   record InstantBreakResult(boolean canInstantBreak, int toolSlot, boolean breaksInOneTick) {
   }
}
