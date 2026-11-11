package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

public class EffectHudRow extends HudTextRow implements ClientAccess {
   private final Holder<MobEffect> effectHolder;

   public EffectHudRow(Holder<MobEffect> holder, Supplier<?> visibilitySupplier) {
      super(() -> {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         MobEffectInstance effectInstance = player.getActiveEffectsMap().get(holder);
         if (effectInstance == null) {
            return Component.empty();
         }

         float durationScale = 1.0F;
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         TickRateManager tickRateManager = level.tickRateManager();
         float tickRate = tickRateManager.tickrate();
         Component durationComponent = MobEffectUtil.formatDuration(effectInstance, durationScale, tickRate);
         String durationText = durationComponent.getString();
         if (durationText.startsWith("0")) {
            durationText = durationText.substring(1);
         }

         String amplifierTextResult;
         if (effectInstance.getAmplifier() == 0) {
            amplifierTextResult = "";
         } else {
            int displayedLevel = effectInstance.getAmplifier() + 1;
            amplifierTextResult = displayedLevel + " ";
         }

         Object[] formatArguments = new Object[4];
         MobEffect effect = holder.value();
         Component effectDisplayName = effect.getDisplayName();
         formatArguments[0] = effectDisplayName.getString();
         formatArguments[1] = amplifierTextResult;
         formatArguments[2] = ChatFormatting.WHITE;
         formatArguments[3] = durationText;
         String formattedText = "%s %s%s%s".formatted(formatArguments);
         return Component.literal(formattedText);
      }, visibilitySupplier);
      this.effectHolder = holder;
   }

   public Holder<MobEffect> getEffectHolder() {
      return this.effectHolder;
   }
}
