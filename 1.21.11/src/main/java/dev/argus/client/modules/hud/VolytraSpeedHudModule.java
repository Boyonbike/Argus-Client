package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Optional Volytra speed readout. Each component can be shown independently.
 */
public class VolytraSpeedHudModule extends HudWidgetModule {
   private final Setting<Boolean> showHorizontal;
   private final Setting<Boolean> showVertical;
   private final Setting<Boolean> show3d;
   private final List<HudTextRow> rows;

   public VolytraSpeedHudModule() {
      super("Volytra Speed", new String[]{"volytra-speed"});

      Setting<Boolean> tweaks = this.add(new BooleanSetting("Tweaks", false).markAsGroup());
      tweaks.expanded = true;
      this.showHorizontal = this.add(new BooleanSetting("H Speed", true));
      this.showVertical = this.add(new BooleanSetting("V Speed", true));
      this.show3d = this.add(new BooleanSetting("3D Speed", true));
      this.rows = new ArrayList<>();
      this.rows.add(new HudTextRow(() -> Component.literal(format("H Speed", horizontalSpeed())), this.showHorizontal::getValue));
      this.rows.add(new HudTextRow(() -> Component.literal(format("V Speed", verticalSpeed())), this.showVertical::getValue));
      this.rows.add(new HudTextRow(() -> Component.literal(format("3D Speed", speed3d())), this.show3d::getValue));

      this.setRenderer(new HudTextListRenderer(this, this.rows));
      this.getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
   }

   private static String format(String label, double speed) {
      return String.format(Locale.ROOT, "%s %.1f b/s", label, speed);
   }

   private static double horizontalSpeed() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) return 0.0;
      Vec3 velocity = player.getDeltaMovement();
      return Math.hypot(velocity.x, velocity.z) * 20.0;
   }

   private static double verticalSpeed() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) return 0.0;
      return Math.abs(player.getDeltaMovement().y) * 20.0;
   }

   private static double speed3d() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) return 0.0;
      Vec3 velocity = player.getDeltaMovement();
      return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20.0;
   }
}
