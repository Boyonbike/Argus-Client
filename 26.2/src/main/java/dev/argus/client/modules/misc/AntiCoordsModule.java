/*
 * Texture rotations and model offsets adapted from NoTextureRotations by rfresh2
 * (https://github.com/rfresh2/NoTextureRotations), via Comet. Modified for Argus by Boyonabike.
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.KeyedHash;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Makes it as hard as possible to work out your coordinates from what you show: block texture rotations and model
 * offsets no longer follow the block's position, and screenshots contain only the world.
 */
public final class AntiCoordsModule extends Module {
   public enum Mode implements NamedEntry {
      FIXED("Fixed"),
      KEYED("Keyed");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   private static final long FIXED_SEED = 42L;
   // The block hooks run for every block meshed, on worker threads, so they read this instead of looking the module up.
   private static AntiCoordsModule instance;
   // Only meaningful on the client thread; meshing threads must never take real offsets from it.
   private static boolean entityTicking;
   private static volatile LevelKey levelKey;

   private record LevelKey(WeakReference<ClientLevel> level, KeyedHash key) {
   }

   private final Setting<Mode> modeSetting;
   private final Setting<Boolean> hideRotationsSetting;
   private final Setting<Boolean> hideOffsetsSetting;
   private final Setting<Boolean> cleanScreenshotsSetting;

   private Consumer<Component> pendingScreenshot;
   private boolean capturing;
   private boolean hudWasHidden;

   public AntiCoordsModule() {
      super("AntiCoords", "Makes it as hard as possible to leak your coordinates through block textures and screenshots.", ModuleCategory.MISC, "NoTextureRotations");
      this.modeSetting = this.add(new EnumSetting<>("Mode", Mode.FIXED));
      this.hideRotationsSetting = this.add(new BooleanSetting("HideRotations", true));
      this.hideOffsetsSetting = this.add(new BooleanSetting("HideOffsets", true));
      this.cleanScreenshotsSetting = this.add(new BooleanSetting("CleanScreenshots", true));
      this.modeSetting.setChangeCallback(this::onTextureSettingChanged);
      this.hideRotationsSetting.setChangeCallback(this::onTextureSettingChanged);
      this.hideOffsetsSetting.setChangeCallback(this::onTextureSettingChanged);
      instance = this;
   }

   @Override
   public String getInfo() {
      return this.modeSetting.getValue().getName();
   }

   @Override
   public void onToggle() {
      rebuildChunks();
   }

   @Override
   public void onDisable() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (this.capturing && mc != null && mc.gui.hud.isHidden() != this.hudWasHidden) {
         mc.gui.hud.toggle();
      }

      this.pendingScreenshot = null;
      this.capturing = false;
   }

   private void onTextureSettingChanged() {
      if (this.isToggled()) {
         rebuildChunks();
      }
   }

   private static void rebuildChunks() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (mc != null && mc.level != null) {
         mc.levelExtractor.allChanged();
      }
   }

   private static Mode seedMode() {
      AntiCoordsModule module = instance;
      return module != null && module.isToggled() && module.hideRotationsSetting.getValue() ? module.modeSetting.getValue() : null;
   }

   /** Whether texture seeds are being replaced, so Argus's per-session seed salt should step aside. */
   public static boolean replacesSeeds() {
      return seedMode() != null;
   }

   /** In Keyed mode, texture seeds come from the block's place within a 256-block square, so even a stolen key reveals no more. */
   public static BlockPos seedPos(BlockPos pos) {
      return seedMode() == Mode.KEYED ? new BlockPos(pos.getX() & 255, pos.getY(), pos.getZ() & 255) : pos;
   }

   public static long seed(long seed) {
      Mode mode = seedMode();
      if (mode == null) {
         return seed;
      }

      return mode == Mode.FIXED ? FIXED_SEED : levelKey().hash(seed);
   }

   /** Null keeps the real offset: the client's entity tick and the integrated server collide with it, as the server does. */
   public static Mode offsetMode() {
      AntiCoordsModule module = instance;
      if (module == null || !module.isToggled() || !module.hideOffsetsSetting.getValue()) {
         return null;
      }

      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (entityTicking && mc.isSameThread()) {
         return null;
      }

      MinecraftServer server = mc.getSingleplayerServer();
      if (server != null && server.isSameThread()) {
         return null;
      }

      return module.modeSetting.getValue();
   }

   /** Vanilla offsets ignore height, so both halves of a tall plant still line up. */
   public static BlockPos offsetPos(BlockPos pos) {
      return BlockPos.of(levelKey().hash(BlockPos.asLong(pos.getX() & 255, 0, pos.getZ() & 255)));
   }

   public static void entityTick(boolean ticking) {
      entityTicking = ticking;
   }

   private static KeyedHash levelKey() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      LevelKey cached = levelKey;
      if (cached != null && cached.level().get() == level) {
         return cached.key();
      }

      KeyedHash key = level == null ? MasterKey.KEY : MasterKey.KEY.derive(context(mc, level));
      levelKey = new LevelKey(new WeakReference<>(level), key);
      return key;
   }

   private static String context(Minecraft mc, ClientLevel level) {
      String dimension = level.dimension().identifier().toString();
      MinecraftServer server = mc.getSingleplayerServer();
      if (server != null) {
         return "sp\0" + server.getWorldData().getLevelName() + "\0" + dimension;
      }

      ServerData data = mc.getCurrentServer();
      return "mp\0" + (data == null ? "" : data.ip.trim().toLowerCase(Locale.ROOT)) + "\0" + dimension;
   }

   // Loaded by whichever thread first meshes a block in Keyed mode.
   private static final class MasterKey {
      static final KeyedHash KEY = KeyedHash.loadOrCreate(ConfigurationManager.ROOT_DIRECTORY.resolve("anti-coords.key"));
   }

   /**
    * The screenshot key's frame already has the HUD in it, so the capture waits for the next one.
    * Returns false to let vanilla take the screenshot as usual.
    */
   public static boolean deferScreenshot(Consumer<Component> callback) {
      AntiCoordsModule module = instance;
      if (module == null || !module.isToggled() || !module.cleanScreenshotsSetting.getValue() || ClientAccess.argusclientMinecraft.level == null) {
         return false;
      }

      module.pendingScreenshot = callback;
      return true;
   }

   /** Before the frame is extracted: hides the hand, block outline and name tags, as F1 does. */
   public static void beforeFrame() {
      AntiCoordsModule module = instance;
      if (module == null || module.pendingScreenshot == null || module.capturing) {
         return;
      }

      Minecraft mc = ClientAccess.argusclientMinecraft;
      module.capturing = true;
      module.hudWasHidden = mc.gui.hud.isHidden();
      if (!module.hudWasHidden) {
         mc.gui.hud.toggle();
      }
   }

   /** Once the world is drawn and before every 2D layer, so no HUD, overlay or menu from any mod can be in the capture. */
   public static void captureWorld() {
      AntiCoordsModule module = instance;
      if (module == null || !module.capturing) {
         return;
      }

      Consumer<Component> callback = module.pendingScreenshot;
      module.pendingScreenshot = null;
      module.capturing = false;
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (mc.gui.hud.isHidden() != module.hudWasHidden) {
         mc.gui.hud.toggle();
      }

      Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), callback);
   }
}
