package dev.argus.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributeSystem.Builder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
   @Accessor("environmentAttributes")
   @Mutable
   void argusclient$setEnvironmentAttributes(EnvironmentAttributeSystem var1);

   @Invoker("addEnvironmentAttributeLayers")
   Builder argusclient$addEnvironmentAttributeLayers(Builder var1);

   @Accessor("blockStatePredictionHandler")
   BlockStatePredictionHandler getPendingUpdateManager();
}
