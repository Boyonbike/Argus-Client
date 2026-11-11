package dev.argus.mixin;

import dev.argus.client.access.ChatLineAccess;
import dev.argus.client.modules.misc.BetterChatModule;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Line.class)
public class GuiMessageLineMixin implements ChatLineAccess {
   @Unique
   private ClampedInterpolatedFloat progress = null;
   @Unique
   private MessageSignature signature;
   @Mutable
   @Final
   @Shadow
   private FormattedCharSequence content;
   @Unique
   private long addTimeMillis;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void initializeChatLineAnimation(int addedAtTick, FormattedCharSequence text, GuiMessageTag tag, boolean endOfEntry, CallbackInfo callback) {
      this.getProgress().setValue(false);
      this.addTimeMillis = System.currentTimeMillis();
   }

   @Override
   public MessageSignature getSignature() {
      return this.signature;
   }

   @Override
   public void setSignature(MessageSignature newSignature) {
      this.signature = newSignature;
   }

   @Override
   public FormattedCharSequence getContent() {
      return this.content;
   }

   @Override
   public void setContent(FormattedCharSequence newContent) {
      this.content = newContent;
   }

   @Override
   public ClampedInterpolatedFloat getProgress() {
      if (this.progress == null) {
         this.progress = new ClampedInterpolatedFloat(() -> BetterChatModule.getInstance().speedSetting.getValue() * 2.0F, true);
      }

      return this.progress;
   }

   @Override
   public long argusclient$getAddTime() {
      return this.addTimeMillis;
   }

   @Override
   public void argusclient$setAddTime(long addedAtMillis) {
      this.addTimeMillis = addedAtMillis;
   }
}
