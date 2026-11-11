package dev.argus.client.modules.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.ClientLevelAccessor;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;

public class SkyboxModule extends Module {
   public final Setting<SkyboxModule.SkyType> typeSetting = this.add(new EnumSetting<>("Type", SkyboxModule.SkyType.NONE));
   public final Setting<Boolean> splitFogSetting = this.add(new BooleanSetting("SplitFog", false));
   public final Setting<Boolean> fancyStarsSetting = this.add(new BooleanSetting("FancyStars", false).markVisibilityDependency());
   public final Setting<Color> starsColorSetting = this.add(new ColorSetting("Color", new Color(-1, true)).dependsOn(this.fancyStarsSetting));
   public final Setting<Boolean> shaderSetting = this.add(new BooleanSetting("Shader", false).markVisibilityDependency());
   public final Setting<Float> parallaxSetting = this.add(new NumberSetting<>("Parallax", 1.0F, 0.0F, 1.5F).dependsOn(this.shaderSetting));
   public final Setting<Color> colorSetting = this.add(new ColorSetting("Color", new Color(-2900225, true)).dependsOn(this.shaderSetting));
   public final Setting<Boolean> cloudsSetting = this.add(new BooleanSetting("Clouds", false).markVisibilityDependency());
   public final Setting<Float> heightSetting = this.add(new NumberSetting<>("Height", 192.0F, 0.0F, 256.0F).dependsOn(this.cloudsSetting));
   public final Setting<Color> cloudsColorSetting = this.add(new ColorSetting("CloudsColor", new Color(-1, true)).dependsOn(this.cloudsSetting));
   public final Setting<Boolean> skyColorSetting = this.add(new BooleanSetting("SkyColor", false).markAsGroup().markVisibilityDependency());
   public final Setting<Boolean> denseSetting = this.add(new BooleanSetting("Dense", false).dependsOn(this.skyColorSetting));
   public final Setting<Color> fogSetting = this.add(new ColorSetting("Fog", new Color(-4351489, true)).dependsOn(this.skyColorSetting));
   public final Setting<Color> skySetting = this.add(new ColorSetting("Sky", new Color(-4351489, true)).dependsOn(this.skyColorSetting));
   public final Setting<Color> backgroundSetting = this.add(new ColorSetting("Background", new Color(-2900225, true)).dependsOn(this.skyColorSetting));
   private final Setting<Boolean> dimensionsSetting = this.add(new BooleanSetting("Dimensions", true).markAsGroup().markVisibilityDependency());
   private final Setting<Boolean> overworldSetting = this.add(new BooleanSetting("Overworld", true).dependsOn(this.dimensionsSetting));
   private final Setting<Boolean> netherSetting = this.add(new BooleanSetting("Nether", true).dependsOn(this.dimensionsSetting));
   private final Setting<Boolean> endSetting = this.add(new BooleanSetting("End", true).dependsOn(this.dimensionsSetting));
   private MappableRingBuffer skyFogBuffer;
   private volatile boolean environmentRefreshPending;

   @Override
   public void onToggle() {
      this.requestEnvironmentRefresh();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundRespawnPacket || event.getPacket() instanceof ClientboundLoginPacket) {
         this.requestEnvironmentRefresh();
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.environmentRefreshPending) {
         ClientLevelAccessor level = (ClientLevelAccessor)ClientAccess.argusclientMinecraft.level;
         level.argusclient$setEnvironmentAttributes(
            level.argusclient$addEnvironmentAttributeLayers(EnvironmentAttributeSystem.builder().addDefaultLayers(ClientAccess.argusclientMinecraft.level))
               .build()
         );
         this.environmentRefreshPending = false;
      }
   }

   private void requestEnvironmentRefresh() {
      this.environmentRefreshPending = true;
   }

   public Float getCloudHeight(Float originalHeight) {
      if (this.isToggled()) {
         Boolean cloudsEnabled = this.cloudsSetting.getValue();
         if (cloudsEnabled) {
            return this.heightSetting.getValue();
         }
      }

      return originalHeight;
   }

   public void drawShaderQuad() {
      Tesselator tesselatorCapture = Tesselator.getInstance();
      VertexFormat positionFormat = DefaultVertexFormat.POSITION;
      Mode quadMode = Mode.QUADS;
      BufferBuilder buffer = tesselatorCapture.begin(quadMode, positionFormat);
      buffer.addVertex(-1.0F, -1.0F, 0.0F);
      buffer.addVertex(-1.0F, 1.0F, 0.0F);
      buffer.addVertex(1.0F, 1.0F, 0.0F);
      buffer.addVertex(1.0F, -1.0F, 0.0F);
      RenderType renderTypeCapture = ClientRenderTypes.entityShader();
      MeshData mesh = buffer.buildOrThrow();
      renderTypeCapture.draw(mesh);
   }

   public boolean isCurrentDimensionEnabled() {
      return switch (WorldUtils.getDimension()) {
         case OVERWORLD -> this.overworldSetting.getValue();
         case THE_NETHER -> this.netherSetting.getValue();
         default -> this.endSetting.getValue();
      };
   }

   public float getParallax() {
      return this.parallaxSetting.getValue();
   }

   public MappableRingBuffer getSkyFogBuffer() {
      if (this.skyFogBuffer == null) {
         this.skyFogBuffer = new MappableRingBuffer(() -> "Sky Fog UBO", skyFogBufferUsage(), FogRenderer.FOG_UBO_SIZE);
      }

      return this.skyFogBuffer;
   }

   private static int skyFogBufferUsage() {
      return 130;
   }

   public Color getShaderColor() {
      return this.colorSetting.getValue();
   }

   public GpuBufferSlice getSkySlice() {
      MappableRingBuffer fogBuffer = this.getSkyFogBuffer();
      GpuBuffer bufferCapture = fogBuffer.currentBuffer();
      long sliceLength = FogRenderer.FOG_UBO_SIZE;
      return bufferCapture.slice(0L, sliceLength);
   }

   public SkyboxModule() {
      super("Skybox", "Changes vanilla sky rendering.", ModuleCategory.RENDER);
      this.setDrawn(false);
      this.typeSetting.setChangeCallback(this::requestEnvironmentRefresh);
      Setting<?> visibilitySetting = this.starsColorSetting;
      visibilitySetting.setConfigName("StarsColor");
      visibilitySetting = this.skySetting;
      Predicate<Object> visibilityPredicate = ignoredValue -> {
         Setting<SkyboxModule.SkyType> typeSettingSnapshot = this.typeSetting;
         return typeSettingSnapshot.getValue() != SkyboxModule.SkyType.END;
      };
      visibilitySetting.addVisibilityPredicate(visibilityPredicate);
      visibilitySetting = this.denseSetting;
      visibilityPredicate = ignoredValue -> {
         Setting<SkyboxModule.SkyType> typeSettingSnapshot = this.typeSetting;
         if (typeSettingSnapshot.getValue() != SkyboxModule.SkyType.FLAT) {
            Boolean netherEnabled = this.netherSetting.getValue();
            if (!netherEnabled) {
               Boolean endEnabled = this.endSetting.getValue();
               if (!endEnabled) {
                  return false;
               }
            }

            return this.typeSetting.getValue() == SkyboxModule.SkyType.NONE;
         } else {
            return true;
         }
      };
      visibilitySetting.addVisibilityPredicate(visibilityPredicate);
   }

   public record FogDistances(
      float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudsEnd
   ) {
   }

   public enum SkyType implements NamedEntry {
      NONE("Vanilla"),
      OVERWORLD("Overworld"),
      FLAT("Flat"),
      END("End");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SkyType(String displayName) {
         this.displayName = displayName;
      }
   }
}
