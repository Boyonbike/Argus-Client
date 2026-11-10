package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Your coordinates with X, Y and Z on lines of their own, plus the matching Nether or Overworld spot. Each line can be
 * moved on its own in the HUD editor, or the whole block moved together.
 */
public class CoordsModule extends HudWidgetModule {
   private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private final Setting<Boolean> labels = this.add(new BooleanSetting("Labels", true));
   private final Setting<Integer> decimals = this.add(new NumberSetting<>("Decimals", 0, 0, 2));
   private final Setting<Boolean> otherDimension = this.add(new BooleanSetting("Other dimension", true));
   private final Setting<Boolean> dimensionName = this.add(new BooleanSetting("Dimension", false));

   public CoordsModule() {
      super("Coords", "coordinates", "xyz", "position");
      List<HudTextRow> rows = new ArrayList<>();
      rows.add(new HudTextRow(() -> axis("X", Vec3::x), this::inWorld).withKey("x"));
      rows.add(new HudTextRow(() -> axis("Y", Vec3::y), this::inWorld).withKey("y"));
      rows.add(new HudTextRow(() -> axis("Z", Vec3::z), this::inWorld).withKey("z"));
      rows.add(new HudTextRow(this::otherLine, () -> this.inWorld() && this.otherDimension.getValue()
         && WorldUtils.getDimension() != DimensionType.THE_END).withKey("other"));
      rows.add(new HudTextRow(this::dimensionLine, () -> this.inWorld() && this.dimensionName.getValue()).withKey("dimension"));
      this.setRenderer(new HudTextListRenderer(this, rows).splittable());
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_LEFT);
   }

   private boolean inWorld() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      return mc.player != null && mc.level != null;
   }

   /** Where the player is, or the camera while Freecam is on. */
   private static Vec3 position() {
      Minecraft mc = ClientAccess.argusclientMinecraft;
      if (freecam != null && freecam.isToggled()) return mc.gameRenderer.getMainCamera().position().subtract(0.0, mc.player.getBbHeight(), 0.0);
      return mc.player.position();
   }

   private String number(double value) {
      int places = this.decimals.getValue();
      return places == 0 ? Long.toString((long) Math.floor(value)) : String.format(Locale.ROOT, "%." + places + "f", value);
   }

   private Component line(String label, String value) {
      return Component.literal(this.labels.getValue() ? label + " " + ChatFormatting.WHITE + value : value);
   }

   private Component axis(String label, java.util.function.ToDoubleFunction<Vec3> pick) {
      if (!this.inWorld()) return Component.empty();
      return this.line(label, this.number(pick.applyAsDouble(position())));
   }

   private Component otherLine() {
      if (!this.inWorld()) return Component.empty();
      boolean nether = WorldUtils.getDimension() == DimensionType.THE_NETHER;
      Vec3 at = position();
      double factor = nether ? 8.0 : 0.125;
      return this.line(nether ? "Overworld" : "Nether", this.number(at.x * factor) + ", " + this.number(at.z * factor));
   }

   private Component dimensionLine() {
      if (!this.inWorld()) return Component.empty();
      Supplier<String> name = () -> switch (WorldUtils.getDimension()) {
         case THE_NETHER -> "Nether";
         case THE_END -> "End";
         default -> "Overworld";
      };
      return Component.literal(this.labels.getValue() ? "Dimension " + ChatFormatting.WHITE + name.get() : name.get());
   }
}
