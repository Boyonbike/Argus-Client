package dev.argus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.NamedFeature;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.AbstractRegistry;
import dev.argus.client.settings.ValueProvider;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class FeatureBoxRenderer extends AbstractRegistry<FeatureBoxRenderer.RenderConfiguration> implements ClientAccess {
   private final Map<FeatureBoxRenderer.RenderConfiguration, Set<FadingBoxRenderState>> boxesByConfiguration = new HashMap<>();

   public FeatureBoxRenderer() {
      super(new ArrayList<>());
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public boolean register(Object entry) {
      FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)entry;
      return this.register(configuration);
   }

   @Override
   public boolean unregister(Object entry) {
      FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)entry;
      return this.unregister(configuration);
   }

   public void addBox(NamedFeature feature, BlockPos position) {
      if (position != null) {
         AABB bounds = new AABB(position);
         this.addBox(feature, bounds);
      }
   }

   public boolean register(FeatureBoxRenderer.RenderConfiguration configuration) {
      this.boxesByConfiguration.put(configuration, new ObjectLinkedOpenHashSet());
      return this.getRegistry().add(configuration);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      synchronized (this.boxesByConfiguration) {
         for (Entry<FeatureBoxRenderer.RenderConfiguration, Set<FadingBoxRenderState>> entry : this.boxesByConfiguration.entrySet()) {
            Set<FadingBoxRenderState> boxes = entry.getValue();
            Predicate<FadingBoxRenderState> expiredBox = box -> {
               FeatureBoxRenderer.RenderConfiguration configuration = entry.getKey();
               float fadeDuration = configuration.getFadeDuration();
               return box.getFadeFactor(fadeDuration) == 0.0F;
            };
            boxes.removeIf(expiredBox);
         }
      }
   }

   public void addBox(NamedFeature feature, AABB bounds) {
      if (bounds != null) {
         Predicate<FeatureBoxRenderer.RenderConfiguration> matchesFeature = candidate -> candidate.feature().equals(feature);
         FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)this.findFirstOrNull(matchesFeature);
         if (configuration == null) {
            throw new NoSuchElementException();
         }

         synchronized (this.boxesByConfiguration) {
            Set<FadingBoxRenderState> boxes = this.boxesByConfiguration.get(configuration);
            if (configuration.shouldRetainPreviousBoxes()) {
               for (FadingBoxRenderState box : boxes) {
                  if (bounds.equals(box.getBounds())) {
                     box.refreshFade();
                     return;
                  }
               }
            } else {
               boxes.clear();
            }

            FadingBoxRenderState newBox = new FadingBoxRenderState();
            newBox.setBounds(bounds);
            newBox.refreshFade();
            boxes.add(newBox);
         }
      }
   }

   public boolean unregister(FeatureBoxRenderer.RenderConfiguration configuration) {
      throw new UnsupportedOperationException();
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      synchronized (this.boxesByConfiguration) {
         for (Entry<FeatureBoxRenderer.RenderConfiguration, Set<FadingBoxRenderState>> entry : this.boxesByConfiguration.entrySet()) {
            FeatureBoxRenderer.RenderConfiguration configuration = entry.getKey();

            for (FadingBoxRenderState box : entry.getValue()) {
               float lineWidth = configuration.getLineWidth();
               box.setLineWidth(lineWidth);
               PoseStack poseStack = event.getPoseStack();
               Color fillColor = configuration.getFillColor();
               Color outlineColor = configuration.getOutlineColor();
               float fadeDuration = configuration.getFadeDuration();
               boolean fadeEnabled = configuration.isFadeEnabled();
               box.render(poseStack, fillColor, outlineColor, fadeDuration, fadeEnabled);
            }
         }
      }
   }

   public static class Builder {
      private final NamedFeature feature;
      private final ValueProvider<Color> fillColorSupplier;
      private final ValueProvider<Color> outlineColorSupplier;
      private ValueProvider<Float> lineWidthSupplier = () -> 1.0F;
      private ValueProvider<Float> fadeDurationSupplier = () -> 1.0F;
      private ValueProvider<Boolean> retainPreviousBoxesSupplier = () -> true;
      private ValueProvider<Boolean> fadeEnabledSupplier = () -> true;
      private int fadeTimeScale = 1000;

      public Builder(NamedFeature feature, ValueProvider<Color> fillColorSupplier, ValueProvider<Color> outlineColorSupplier) {
         this.feature = feature;
         this.fillColorSupplier = fillColorSupplier;
         this.outlineColorSupplier = outlineColorSupplier;
      }

      public FeatureBoxRenderer.Builder withFadeEnabled(ValueProvider<Boolean> supplier) {
         this.fadeEnabledSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.RenderConfiguration build() {
         return new FeatureBoxRenderer.RenderConfiguration(
            this.feature,
            this.fillColorSupplier,
            this.outlineColorSupplier,
            this.lineWidthSupplier,
            this.fadeDurationSupplier,
            this.retainPreviousBoxesSupplier,
            this.fadeEnabledSupplier,
            this.fadeTimeScale
         );
      }

      public FeatureBoxRenderer.Builder withLineWidth(ValueProvider<Float> supplier) {
         this.lineWidthSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withFadeDuration(ValueProvider<Float> supplier) {
         this.fadeDurationSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withRetainPreviousBoxes(ValueProvider<Boolean> supplier) {
         this.retainPreviousBoxesSupplier = supplier;
         return this;
      }

      public FeatureBoxRenderer.Builder withFadeTimeScale(int fadeTimeScale) {
         this.fadeTimeScale = fadeTimeScale;
         return this;
      }
   }

   public record RenderConfiguration(
      NamedFeature feature,
      ValueProvider<Color> fillColorSupplier,
      ValueProvider<Color> outlineColorSupplier,
      ValueProvider<Float> lineWidthSupplier,
      ValueProvider<Float> fadeDurationSupplier,
      ValueProvider<Boolean> retainPreviousBoxesSupplier,
      ValueProvider<Boolean> fadeEnabledSupplier,
      int fadeTimeScale
   ) {
      public float getLineWidth() {
         return this.lineWidthSupplier.getValue();
      }

      public boolean isFadeEnabled() {
         return this.fadeEnabledSupplier.getValue();
      }

      public Color getOutlineColor() {
         return this.outlineColorSupplier.getValue();
      }

      public float getFadeDuration() {
         return this.fadeEnabledSupplier.getValue() ? this.fadeDurationSupplier.getValue() * this.fadeTimeScale : 0.0F;
      }

      public Color getFillColor() {
         return this.fillColorSupplier.getValue();
      }

      public boolean shouldRetainPreviousBoxes() {
         return this.retainPreviousBoxesSupplier.getValue();
      }

      @Override
      public boolean equals(Object other) {
         if (this == other) {
            return true;
         } else if (other != null && this.getClass() == other.getClass()) {
            FeatureBoxRenderer.RenderConfiguration configuration = (FeatureBoxRenderer.RenderConfiguration)other;
            return Objects.equals(this.feature, configuration.feature);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.feature);
      }
   }
}
