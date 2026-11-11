package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Your X, Y and Z in the dimension you are in. Vertical puts each on a line of its own, which can be moved on its own
 * in the HUD editor; Horizontal puts all three on one line.
 */
public class CoordsModule extends HudWidgetModule {
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private final Setting<Layout> layout = this.add(new EnumSetting<>("Layout", Layout.VERTICAL));
   private final Setting<Boolean> labels = this.add(new BooleanSetting("Labels", true));
   private final Setting<Integer> decimals = this.add(new NumberSetting<>("Decimals", 0, 0, 2));

   public CoordsModule() {
      super("Coords", "coordinates", "xyz", "position");
      List<HudTextRow> rows = new ArrayList<>();
      rows.add(new HudTextRow(this::horizontalLine, () -> this.inWorld() && this.horizontal()).withKey("line"));
      rows.add(new HudTextRow(() -> this.axis("X", Vec3::x), this::verticalInWorld).withKey("x"));
      rows.add(new HudTextRow(() -> this.axis("Y", Vec3::y), this::verticalInWorld).withKey("y"));
      rows.add(new HudTextRow(() -> this.axis("Z", Vec3::z), this::verticalInWorld).withKey("z"));
      this.setRenderer(new HudTextListRenderer(this, rows).splittable());
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_LEFT);
   }

   private boolean horizontal() {
      return this.layout.getValue() == Layout.HORIZONTAL;
   }

   private boolean verticalInWorld() {
      return this.inWorld() && !this.horizontal();
   }

   private boolean inWorld() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      return mc.player != null && mc.level != null;
   }

   /** Where the player is, or the camera while Freecam is on. */
   private static Vec3 position() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (freecam != null && freecam.isToggled()) return mc.gameRenderer.mainCamera().position().subtract(0.0, mc.player.getBbHeight(), 0.0);
      return mc.player.position();
   }

   private String number(double value) {
      int places = this.decimals.getValue();
      return places == 0 ? Long.toString((long) Math.floor(value)) : String.format(Locale.ROOT, "%." + places + "f", value);
   }

   private Component axis(String label, ToDoubleFunction<Vec3> pick) {
      if (!this.inWorld()) return Component.empty();
      String value = this.number(pick.applyAsDouble(position()));
      return Component.literal(this.labels.getValue() ? label + " " + ChatFormatting.WHITE + value : value);
   }

   /** All three on one line: {@code X 120  Y 64  Z -300}, or {@code 120, 64, -300} without labels. */
   private Component horizontalLine() {
      if (!this.inWorld()) return Component.empty();
      Vec3 at = position();
      String x = this.number(at.x), y = this.number(at.y), z = this.number(at.z);
      if (!this.labels.getValue()) return Component.literal(ChatFormatting.WHITE + x + ", " + y + ", " + z);
      return Component.literal("X " + ChatFormatting.WHITE + x + ChatFormatting.RESET
         + "  Y " + ChatFormatting.WHITE + y + ChatFormatting.RESET
         + "  Z " + ChatFormatting.WHITE + z);
   }

   public enum Layout implements NamedEntry {
      VERTICAL("Vertical"),
      HORIZONTAL("Horizontal");

      private final String displayName;

      Layout(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
