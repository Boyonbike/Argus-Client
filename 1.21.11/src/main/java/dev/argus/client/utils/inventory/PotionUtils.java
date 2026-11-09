package dev.argus.client.utils.inventory;

import dev.argus.mixin.LivingEntityAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

public final class PotionUtils {
   private static final Map EFFECTS_BY_COLOR = new HashMap();
   public static final Potion MISSING_POTION = new Potion("empty");

   private PotionUtils() {
   }

   public static Potion getPotion(ItemStack stack) {
      DataComponentType<PotionContents> potionComponent = DataComponents.POTION_CONTENTS;
      PotionContents contents = stack.get(potionComponent);
      if (contents == null) {
         return MISSING_POTION;
      }

      Holder potionHolder = contents.potion().orElse(null);
      return potionHolder == null ? MISSING_POTION : (Potion)potionHolder.value();
   }

   public static Set getEffectsByParticleColor(LivingEntity entity) {
      EntityDataAccessor swirlAccessor = LivingEntityAccessor.argusclient$potion_swirls();
      HashSet effects = new HashSet();

      for (ParticleOptions alpha : (List<ParticleOptions>)entity.getEntityData().get(swirlAccessor)) {
         if (alpha instanceof ColorParticleOption colorParticle) {
            float alphax = 1.0F;
            float red = colorParticle.getRed();
            float green = colorParticle.getGreen();
            float blue = colorParticle.getBlue();
            int color = ARGB.colorFromFloat(alphax, red, green, blue);
            Holder effect = (Holder)EFFECTS_BY_COLOR.get(color);
            if (effect != null) {
               effects.add(effect);
            }
         }
      }

      return effects;
   }

   private static void registerEffectColor(Reference<MobEffect> reference) {
      Map colorLookup = EFFECTS_BY_COLOR;
      int effectColor = reference.value().getColor();
      int color = ARGB.color(255, effectColor);
      colorLookup.put(color, reference);
   }

   static {
      BuiltInRegistries.MOB_EFFECT.listElements().forEach(PotionUtils::registerEffectColor);
   }
}
