package dev.argus.mixin;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.combat.RemotePlayerDamageAccess;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RemotePlayer.class)
public abstract class RemotePlayerMixin extends Player implements ClientAccess, RemotePlayerDamageAccess {
   public RemotePlayerMixin(Level level, GameProfile profile) {
      super(level, profile);
   }

   @Override
   public boolean damageSuper(DamageSource source, float amount) {
      return super.hurtServer((ServerLevel)this.level(), source, amount);
   }
}
