package dev.argus.client.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.config.EnumJsonConverter;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.HudColorMode;
import dev.argus.client.render.HudAnchorGroup;
import dev.argus.client.render.HudHorizontalAlignment;
import dev.argus.client.render.HudVerticalAlignment;
import dev.argus.client.render.HudWidgetRenderer;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import java.awt.Color;

public abstract class HudWidgetModule extends Module {
   private static final EnumJsonConverter<HudAnchor> anchorCodec = new EnumJsonConverter<>(HudAnchor.class);
   protected static HUDModule hud = ClientContext.getModule(HUDModule.class);
   protected HudWidgetRenderer renderer;

   public HudWidgetModule(String name, String... aliases) {
      super(name, ModuleCategory.HUD, aliases);
   }

   @Override
   public boolean isDrawn() {
      return false;
   }

   public int getAnchorIndex() {
      int index = 0;
      HudAnchorGroup anchorGroup = ClientContext.getHudEditor().getAnchorGroup(this.renderer.getAnchor());

      for (HudWidgetRenderer candidate : anchorGroup.getWidgets()) {
         if (candidate.getWidgetModule().equals(this)) {
            return index;
         }

         index++;
      }

      return -1;
   }

   @Override
   public void fromJson(JsonElement json) {
      super.fromJson(json);
      JsonObject config = json.getAsJsonObject();
      if (config.has("hud")) {
         config = config.getAsJsonObject("hud");
         EnumJsonConverter<HudAnchor> codec = anchorCodec;
         this.setAnchor(codec.fromJson(config.get("anchor")));
         if (this.renderer.getAnchor() == HudAnchor.NONE) {
            int horizontalOrdinal = config.has("h") ? config.get("h").getAsInt() : 0;
            int verticalOrdinal = config.has("v") ? config.get("v").getAsInt() : 0;
            this.renderer.setHorizontalAlignment(HudHorizontalAlignment.values()[horizontalOrdinal]);
            this.renderer.setVerticalAlignment(HudVerticalAlignment.values()[verticalOrdinal]);
            this.renderer.setX(config.get("x").getAsFloat(), false);
            this.renderer.setY(config.get("y").getAsFloat(), false);
         }

         ClientContext.getHudEditor().requestDirectionRefresh();
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("enabled", this.isToggled());
      JsonObject settingsJson = new JsonObject();

      for (Setting<?> setting : this.getSettings()) {
         if (!setting.isGroupOnly()) {
            String configName = setting.getConfigName();
            JsonElement settingJson = setting.toJson();
            settingsJson.add(configName, settingJson);
         }
      }

      root.add("settings", settingsJson);
      JsonObject hudJson = new JsonObject();
      EnumJsonConverter<HudAnchor> codec = anchorCodec;
      HudAnchor anchor = this.renderer.getAnchor();
      hudJson.add("anchor", codec.toJson(anchor));
      hudJson.addProperty("index", this.getAnchorIndex());
      if (this.renderer.getAnchor() == HudAnchor.NONE) {
         hudJson.addProperty("h", this.renderer.getHorizontalAlignment().ordinal());
         hudJson.addProperty("v", this.renderer.getVerticalAlignment().ordinal());
         hudJson.addProperty("x", this.renderer.getRelativeX());
         hudJson.addProperty("y", this.renderer.getRelativeY());
      }

      root.add("hud", hudJson);
      return root;
   }

   public float[] calculateDimensions() {
      throw new RuntimeException();
   }

   public void renderContent(PoseStack poses) {
   }

   public void setAnchor(HudAnchor anchor) {
      this.renderer.setAnchor(anchor);
      HudEditorScreen editorForRemoval = ClientContext.getHudEditor();
      HudWidgetRenderer rendererForRemoval = this.renderer;
      editorForRemoval.removeWidget(rendererForRemoval);
      ClientContext.getHudEditor().addWidget(anchor, this.renderer);
   }

   protected void setRenderer(HudWidgetRenderer renderer) {
      this.renderer = renderer;
   }

   public Color getHudColor(float position) {
      Setting<HudColorMode> fadeSetting = hud.fadeSetting;
      HudColorMode fadeMode = fadeSetting.getValue();
      HUDModule capturedHud = hud;
      return fadeMode.getColor(capturedHud, position);
   }

   public HudWidgetRenderer getRenderer() {
      return this.renderer;
   }

   public HudAnchor getAnchor() {
      return this.renderer.getAnchor();
   }

   protected boolean isInHudEditor() {
      return ClientAccess.argusclientMinecraft.screen instanceof HudEditorScreen;
   }
}
