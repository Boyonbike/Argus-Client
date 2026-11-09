package dev.argus.client.access;

import dev.argus.client.utils.ClampedInterpolatedFloat;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;

public interface ChatLineAccess {
   void setContent(FormattedCharSequence var1);

   void setSignature(MessageSignature var1);

   void argusclient$setAddTime(long var1);

   long argusclient$getAddTime();

   ClampedInterpolatedFloat getProgress();

   MessageSignature getSignature();

   FormattedCharSequence getContent();
}
