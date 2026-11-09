package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.misc.NoPacketKickModule;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IdDispatchCodec.class)
public class IdDispatchCodecMixin {
   private static final NoPacketKickModule noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);

   @WrapOperation(
      method = "decode(Lio/netty/buffer/ByteBuf;)Ljava/lang/Object;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
   )
   private Object decodeWithPacketKickHandling(StreamCodec codec, Object input, Operation original) {
      if (!noPacketKickModule.isToggled()) {
         return original.call(new Object[]{codec, input});
      }

      try {
         return original.call(new Object[]{codec, input});
      } catch (DecoderException decoderFailure) {
         noPacketKickModule.logInterceptedException(decoderFailure);
         if (input instanceof ByteBuf buffer) {
            buffer.skipBytes(buffer.readableBytes());
         }

         throw decoderFailure;
      }
   }
}
