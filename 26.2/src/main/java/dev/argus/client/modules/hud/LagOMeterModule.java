package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import java.awt.Color;
import net.minecraft.client.Minecraft;

public class LagOMeterModule extends HudWidgetModule {
   private final Setting<Boolean> foliaSetting;
   private final Setting<Color> colorSetting;
   private final ClampedInterpolatedFloat visibilityAnimation;

   private WelcomerModule getWelcomerModule() {
      Class<WelcomerModule> welcomerType = WelcomerModule.class;
      ModuleRegistry registry = ClientContext.moduleRegistry;
      return (WelcomerModule)registry.getModule(welcomerType);
   }

   private String formatUnresponsiveDuration() {
      Boolean useEntityPackets = this.foliaSetting.getValue();
      ServerStateManager.PacketActivity packetKind = !useEntityPackets && !RotationManager.is2b2tMode()
         ? ServerStateManager.PacketActivity.ANY
         : ServerStateManager.PacketActivity.ENTITY;
      long nowMillis = System.currentTimeMillis();
      ServerStateManager packetTracker = ClientContext.serverStateManager;
      float elapsedMillis = (float)(nowMillis - packetTracker.getLastPacketTime(packetKind));
      float elapsedSeconds = elapsedMillis / 1000.0F;
      return elapsedSeconds >= 3600.0F ? "more than an hour." : "%.1fs".formatted(elapsedSeconds);
   }

   @Override
   public float[] calculateDimensions() {
      String warningText = this.getWarningText();
      if (warningText != null) {
         WelcomerModule welcomer = this.getWelcomerModule();
         if (!welcomer.shouldHideWidget(this)) {
            return new float[]{FontRenderer.INSTANCE.getWidth(warningText), FontRenderer.INSTANCE.getHeight() + 1};
         }
      }

      return new float[]{0.0F, 0.0F};
   }

   @Override
   public void renderContent(PoseStack poses) {
      String warningText = this.getWarningText();
      ClampedInterpolatedFloat animationReceiver = this.visibilityAnimation;
      boolean visible = warningText != null;
      animationReceiver.setTarget(visible);
      if (warningText != null) {
         WelcomerModule welcomer = this.getWelcomerModule();
         if (!welcomer.shouldHideWidget(this)) {
            float visibility = this.visibilityAnimation.updateAndGet();
            FontRenderer font = FontRenderer.INSTANCE;
            float slideOffsetY = (1.0F - visibility) * -FontRenderer.INSTANCE.getHeight();
            Setting<Color> colorOption = this.colorSetting;
            font.drawWithShadow(poses, warningText, 0.0F, slideOffsetY, colorOption.getValue());
            return;
         }
      }
   }

   private String getWarningText() {
      Boolean useEntityPackets = this.foliaSetting.getValue();
      ServerStateManager.PacketActivity packetKind = !useEntityPackets && !RotationManager.is2b2tMode()
         ? ServerStateManager.PacketActivity.ANY
         : ServerStateManager.PacketActivity.ENTITY;
      long nowMillis = System.currentTimeMillis();
      ServerStateManager packetTracker = ClientContext.serverStateManager;
      long elapsedMillis = nowMillis - packetTracker.getLastPacketTime(packetKind);
      long clampedAgeMillis = Math.max(elapsedMillis, 0L);
      boolean remoteServerUnresponsive = false;
      if (clampedAgeMillis > 1000L) {
         Minecraft client = ClientAccess.argusclientMinecraft;
         remoteServerUnresponsive = !client.isLocalServer();
      }

      if (!remoteServerUnresponsive && !this.isInHudEditor()) {
         return null;
      }

      String durationText = this.formatUnresponsiveDuration();
      return "Server is not responding for " + durationText;
   }

   private static String[] createAliases() {
      return new String[]{"lag", "lagometer"};
   }

   public LagOMeterModule() {
      super("Lag'O'Meter", createAliases());
      boolean defaultFolia = false;
      this.foliaSetting = this.add(new BooleanSetting("Folia", defaultFolia));
      this.colorSetting = this.add(new ColorSetting("Color", Color.gray));
      this.visibilityAnimation = new ClampedInterpolatedFloat(1.0F);
      ResizableHudWidgetRenderer renderer = new ResizableHudWidgetRenderer(this);
      renderer.enableHudScale();
      this.setRenderer(renderer);
   }
}
