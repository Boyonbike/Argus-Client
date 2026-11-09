package dev.argus.client.utils.world;

import com.google.gson.annotations.SerializedName;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.math.MathUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.world.phys.Vec3;

public class Waypoint implements NamedEntry {
   @SerializedName("x")
   private final double x;
   @SerializedName("y")
   private final double y;
   @SerializedName("z")
   private final double z;
   @SerializedName("name")
   private final String name;
   @SerializedName("dimension")
   private final String dimension;
   @SerializedName("server")
   private final String server;
   @SerializedName("toggled")
   private boolean toggled;
   private transient Vec3 cachedPosition;

   public Vec3 getPosition() {
      if (this.cachedPosition == null) {
         this.cachedPosition = new Vec3(this.x, this.y, this.z);
      }

      return this.cachedPosition;
   }

   public double distanceTo(Vec3 position) {
      Vec3 waypointPosition = this.getPosition();
      return position.distanceTo(waypointPosition);
   }

   public String getDimension() {
      return this.dimension;
   }

   private int getDisplayColor() {
      int color;
      if (this.isToggled()) {
         color = -1;
      } else {
         Integer disabledColor = ChatFormatting.GRAY.getColor();
         color = disabledColor;
      }

      return color;
   }

   public double roundCoordinate(double coordinate) {
      byte decimalPlaces = 1;
      return MathUtils.floorToDecimals(coordinate, decimalPlaces);
   }

   public String getServer() {
      return this.server;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Waypoint(String name, double x, double y, double z, String dimension, String server) {
      this.x = this.roundCoordinate(x);
      this.y = this.roundCoordinate(y);
      this.z = this.roundCoordinate(z);
      this.dimension = dimension;
      this.server = server;
      this.name = name;
      this.cachedPosition = new Vec3(x, y, z);
      this.toggled = true;
   }

   public Waypoint(String name, Vec3 position, String dimension, String server) {
      this(name, position.x, position.y, position.z, dimension, server);
   }

   public boolean isToggled() {
      return this.toggled;
   }

   public double getZ() {
      return this.z;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public void setToggled(boolean toggled) {
      this.toggled = toggled;
   }

   @Override
   public Component getDisplayComponent() {
      Object[] infoArguments = new Object[7];
      infoArguments[0] = this.name;
      double infoX = this.x;
      infoArguments[1] = infoX;
      double infoY = this.y;
      infoArguments[2] = infoY;
      double infoZ = this.z;
      infoArguments[3] = infoZ;
      infoArguments[4] = this.dimension;
      infoArguments[5] = this.server;
      boolean infoToggled = this.toggled;
      infoArguments[6] = infoToggled;
      Object[] infoFormatArguments = infoArguments;
      String infoFormat = "%s's info:\nx: %.1f\ny: %.1f\nz: %.1f\ndimension: %s\nserver: %s\ntoggled: %b";
      String infoText = String.format(infoFormat, infoFormatArguments);
      Object[] commandArguments = new Object[]{CommandRegistry.getPrefix(), this.getName()};
      Object[] commandFormatArguments = commandArguments;
      String commandFormat = "%swaypoints toggle %s";
      String toggleCommand = commandFormat.formatted(commandFormatArguments);
      ShowText hoverEvent = new ShowText(Component.literal(infoText).withStyle(hoverStyle -> {
         ChatFormatting hoverFormatting = ChatFormatting.GRAY;
         return hoverStyle.applyFormat(hoverFormatting);
      }));
      RunCommand clickEvent = new RunCommand(toggleCommand);
      String displayName = this.name;
      return Component.literal(displayName).withStyle(nameStyle -> {
         Style hoveredStyle = nameStyle.withHoverEvent(hoverEvent);
         Style clickedStyle = hoveredStyle.withClickEvent(clickEvent);
         return ChatUtils.withDynamicColor(clickedStyle, this::getDisplayColor);
      });
   }

   public boolean matches(String requestedName, String requestedServer) {
      if (this.getName().equals(requestedName)) {
         String waypointServer = this.getServer();
         if (waypointServer.equalsIgnoreCase(requestedServer)) {
            return true;
         }
      }

      return false;
   }
}
