package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.PlayerAttackEvent;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ClientLightningState;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.awt.Color;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class KillEffectsModule extends Module {
   final Setting<Boolean> killSoundSetting;
   private final Setting<NamedIdentifier> soundSetting;
   private final Setting<NamedIdentifier> selfSoundSetting;
   private final Setting<Float> killSoundVolumeSetting;
   final Setting<Boolean> killStreakSetting;
   private final Setting<Float> killStreakVolumeSetting;
   protected final Setting<Boolean> killSaySetting;
   protected final Setting<Float> delaySetting;
   protected final Setting<String> pathSetting;
   protected final Setting<Boolean> onlyEnemiesSetting;
   protected final Setting<Boolean> randomSetting;
   protected final Setting<Boolean> refreshSetting;
   public final Setting<Boolean> thunderSetting;
   private final Setting<Integer> lightningsSetting;
   public final Setting<Color> colorSetting;
   private final Setting<KillEffectsParticlesChoice> particlesSetting;
   protected final Setting<Set<ParticleType<?>>> particleTypesSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> selfSetting;
   private final KillSayMessages killSayMessages;
   private final ConcurrentHashMap<UUID, Integer> trackedTargets;
   private final ObjectList<UUID> expiredTargets;
   private int killStreak;

   private void spawnKillParticles(Vec3 position) {
      Setting<KillEffectsParticlesChoice> particleOption = this.particlesSetting;
      KillEffectsParticlesChoice particleChoiceSnapshot = particleOption.getValue();
      ParticleOptions particle = particleChoiceSnapshot.chooseParticle(this);
      if (particle != null) {
         Setting<KillEffectsParticlesChoice> emissionOption = this.particlesSetting;
         KillEffectsParticlesChoice emissionChoice = emissionOption.getValue();
         emissionChoice.emitParticles(particle, position);
      }
   }

   @Override
   public void onEnable() {
      this.killStreak = 0;
      this.killSayMessages.reloadMessages(true);
   }

   private void spawnKillLightning(Vec3 position) {
      Setting<Boolean> thunderOption = this.thunderSetting;
      Boolean spawnThunder = thunderOption.getValue();
      if (spawnThunder) {
         int index = 0;

         while (true) {
            Setting<Integer> lightningsOption = this.lightningsSetting;
            Integer lightningCount = lightningsOption.getValue();
            if (index >= lightningCount) {
               return;
            }

            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, ClientAccess.argusclientMinecraft.level);
            lightning.setPos(position);
            lightning.snapTo(position);
            ((ClientLightningState)lightning).argusclient$setClient(true);
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            level.addEntity(lightning);
            index++;
         }
      }
   }

   @EventHandler
   private void onPlayerAttackEvent(PlayerAttackEvent event) {
      if (event.getStage() != EventStage.Post) {
         if (!this.isWorldUnavailable()) {
            if (event.getTarget() instanceof Player player) {
               if (ClientAccess.argusclientMinecraft.player != player) {
                  SocialRegistry friendManager = ClientContext.socialRegistry;
                  if (!friendManager.isFriendPlayer(player)) {
                     if (!PlayerStateUtils.hasArmor(player)) {
                        Setting<Boolean> ignoreNakedsOption = this.ignoreNakedsSetting;
                        Boolean ignoreNakeds = ignoreNakedsOption.getValue();
                        if (ignoreNakeds) {
                           return;
                        }
                     }

                     if (EntityUtils.getHealthWithAbsorption(player) > 0.0F) {
                        try {
                           ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
                           UUID uuidSnapshot = player.getUUID();
                           Integer trackedTick = ClientAccess.argusclientMinecraft.player.tickCount;
                           targetsSnapshot.put(uuidSnapshot, trackedTick);
                        } catch (Exception var7) {
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.killStreak == -1) {
         this.killStreak = 0;
         Setting<Boolean> selfOption = this.selfSetting;
         Boolean selfEnabled = selfOption.getValue();
         if (!selfEnabled) {
            return;
         }

         Setting<Boolean> selfKillSoundOption = this.killSoundSetting;
         Boolean selfKillSoundEnabled = selfKillSoundOption.getValue();
         if (selfKillSoundEnabled) {
            Setting<Float> selfVolumeCheckOption = this.killSoundVolumeSetting;
            Float selfVolumeCheck = selfVolumeCheckOption.getValue();
            if (selfVolumeCheck > 0.0F) {
               SoundManager selfSoundManagerSnapshot = ClientContext.soundManager;
               Setting<NamedIdentifier> selfSoundOption = this.selfSoundSetting;
               NamedIdentifier selfSoundId = selfSoundOption.getValue();
               SoundClip selfSound = selfSoundManagerSnapshot.getSound(selfSoundId);
               if (selfSound != null) {
                  Setting<Float> selfVolumeReadOption = this.killSoundVolumeSetting;
                  float selfPlaybackVolume = selfVolumeReadOption.getValue();
                  selfSound.play(selfPlaybackVolume);
               }
            }
         }

         LocalPlayer lightningLocalPlayer = ClientAccess.argusclientMinecraft.player;
         Vec3 selfPositionSnapshot = lightningLocalPlayer.position();
         this.spawnKillLightning(selfPositionSnapshot);
         LocalPlayer particlesLocalPlayer = ClientAccess.argusclientMinecraft.player;
         selfPositionSnapshot = particlesLocalPlayer.position();
         this.spawnKillParticles(selfPositionSnapshot);
      }

      this.expiredTargets.clear();
      synchronized (this.trackedTargets) {
         for (UUID uuid : this.trackedTargets.keySet()) {
            try {
               ClientLevel targetLevel = ClientAccess.argusclientMinecraft.level;
               Player player = targetLevel.getPlayerByUUID(uuid);
               if (player == null) {
                  this.expiredTargets.add(uuid);
               } else {
                  int trackedAtTick = ClientAccess.argusclientMinecraft.player.tickCount;
                  Integer trackedTick = this.trackedTargets.get(uuid);
                  int ageTicks = trackedAtTick - trackedTick;
                  if (ageTicks > 200) {
                     this.expiredTargets.add(uuid);
                  } else if (player.getHealth() <= 0.0F) {
                     this.expiredTargets.add(uuid);
                     this.killStreak++;
                     KillSayMessages messagesSnapshot = this.killSayMessages;
                     String playerName = player.getName().getString();
                     messagesSnapshot.sendKillMessage(player, playerName);
                     Vec3 lightningPositionSnapshot = player.position();
                     this.spawnKillLightning(lightningPositionSnapshot);
                     Vec3 particlesPositionSnapshot = player.position();
                     this.spawnKillParticles(particlesPositionSnapshot);
                     Setting<Boolean> killSoundOption = this.killSoundSetting;
                     Boolean killSoundEnabled = killSoundOption.getValue();
                     if (killSoundEnabled) {
                        Setting<Float> killVolumeCheckOption = this.killSoundVolumeSetting;
                        Float killVolumeCheck = killVolumeCheckOption.getValue();
                        if (killVolumeCheck > 0.0F) {
                           SoundManager killSoundManagerSnapshot = ClientContext.soundManager;
                           Setting<NamedIdentifier> killSoundIdOption = this.soundSetting;
                           NamedIdentifier killSoundId = killSoundIdOption.getValue();
                           SoundClip killSound = killSoundManagerSnapshot.getSound(killSoundId);
                           if (killSound != null) {
                              Setting<Float> killVolumeReadOption = this.killSoundVolumeSetting;
                              float killPlaybackVolume = killVolumeReadOption.getValue();
                              killSound.play(killPlaybackVolume);
                           }
                        }
                     }

                     Setting<Boolean> streakOption = this.killStreakSetting;
                     Boolean streakEnabled = streakOption.getValue();
                     if (streakEnabled) {
                        int currentStreak = this.killStreak;
                        if (currentStreak >= 2) {
                           Setting<Float> streakVolumeCheckOption = this.killStreakVolumeSetting;
                           Float streakVolumeCheck = streakVolumeCheckOption.getValue();
                           if (streakVolumeCheck > 0.0F) {
                              SoundManager streakSoundManagerSnapshot = ClientContext.soundManager;
                              int streakCount = this.killStreak;
                              SoundClip streakSound = streakSoundManagerSnapshot.getKillstreakSound(streakCount);
                              if (streakSound != null) {
                                 Setting<Float> streakVolumeReadOption = this.killStreakVolumeSetting;
                                 float streakPlaybackVolume = streakVolumeReadOption.getValue();
                                 streakSound.play(streakPlaybackVolume);
                              }
                           }
                        }
                     }
                  }
               }
            } catch (Exception var29) {
            }
         }
      }

      synchronized (this.expiredTargets) {
         try {
            ObjectListIterator<UUID> expiredIterator = this.expiredTargets.iterator();

            while (expiredIterator.hasNext()) {
               UUID expiredUuid = (UUID)expiredIterator.next();
               this.trackedTargets.remove(expiredUuid);
            }
         } catch (Exception var30) {
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.killStreak = 0;
   }

   public void trackTarget(Player player) {
      if (player != ClientAccess.argusclientMinecraft.player) {
         SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
         String nameSnapshot = player.getName().getString();
         if (!friendManagerSnapshot.isFriendName(nameSnapshot)) {
            try {
               ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
               UUID uuidSnapshot = player.getUUID();
               Integer trackedTick = ClientAccess.argusclientMinecraft.player.tickCount;
               targetsSnapshot.put(uuidSnapshot, trackedTick);
            } catch (Exception var7) {
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
         this.killStreak = -1;
      }
   }

   public KillEffectsModule() {
      super("KillEffects", "Does various things when someone dies.", ModuleCategory.MISC);
      Boolean killSoundDefault = false;
      BooleanSetting killSoundDefinition = new BooleanSetting("KillSound", killSoundDefault);
      killSoundDefinition.markVisibilityDependency();
      this.killSoundSetting = this.add(killSoundDefinition);
      Predicate<Object> soundPredicate = ignored -> this.killSoundSetting.isExpanded();
      BuiltinSound cssSound = BuiltinSound.CSS;
      NamedIdentifier defaultSound = cssSound.getIdentifier();
      this.soundSetting = this.add(new IdentifierSetting("Sound", defaultSound, soundPredicate));
      Predicate<Object> selfSoundPredicate = ignored -> this.killSoundSetting.isExpanded();
      BuiltinSound bodySplatSound = BuiltinSound.BODYSPLAT;
      NamedIdentifier defaultSelfSound = bodySplatSound.getIdentifier();
      this.selfSoundSetting = this.add(new IdentifierSetting("SelfSound", defaultSelfSound, selfSoundPredicate));
      Predicate<Object> killVolumePredicate = ignored -> this.killSoundSetting.isExpanded();
      this.killSoundVolumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F, killVolumePredicate));
      Boolean killStreakDefault = false;
      BooleanSetting killStreakDefinition = new BooleanSetting("KillStreak", killStreakDefault);
      killStreakDefinition.markVisibilityDependency();
      this.killStreakSetting = this.add(killStreakDefinition);
      Predicate<Object> streakVolumePredicate = ignored -> this.killStreakSetting.isExpanded();
      this.killStreakVolumeSetting = this.add(new NumberSetting<>("Volume", 0.4F, 0.0F, 1.0F, streakVolumePredicate));
      Boolean killSayDefault = false;
      BooleanSetting killSayDefinition = new BooleanSetting("KillSay", killSayDefault);
      killSayDefinition.markVisibilityDependency();
      Setting<Boolean> registeredKillSay = this.add(killSayDefinition);
      this.killSaySetting = registeredKillSay;
      NumberSetting<Float> delayDefinition = new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F);
      delayDefinition.withUnit("s");
      this.delaySetting = this.add(delayDefinition.dependsOn(registeredKillSay));
      StringSetting pathDefinition = new StringSetting("Path", "killsay.txt");
      this.pathSetting = this.add(pathDefinition.dependsOn(registeredKillSay));
      Boolean onlyEnemiesDefault = false;
      BooleanSetting onlyEnemiesDefinition = new BooleanSetting("OnlyEnemies", onlyEnemiesDefault);
      this.onlyEnemiesSetting = this.add(onlyEnemiesDefinition.dependsOn(registeredKillSay));
      Boolean randomDefault = true;
      BooleanSetting randomDefinition = new BooleanSetting("Random", randomDefault);
      this.randomSetting = this.add(randomDefinition.dependsOn(registeredKillSay));
      Boolean refreshDefault = false;
      BooleanSetting refreshDefinition = new BooleanSetting("Refresh", refreshDefault);
      this.refreshSetting = this.add(refreshDefinition.dependsOn(registeredKillSay));
      Boolean thunderDefault = true;
      BooleanSetting thunderDefinition = new BooleanSetting("Thunder", thunderDefault);
      thunderDefinition.markVisibilityDependency();
      Setting<Boolean> registeredThunder = this.add(thunderDefinition);
      this.thunderSetting = registeredThunder;
      NumberSetting<Integer> lightningsDefinition = new NumberSetting<>("Lightnings", 1, 1, 4);
      this.lightningsSetting = this.add(lightningsDefinition.dependsOn(registeredThunder));
      Color defaultColor = new Color(-8355712, true);
      ColorSetting colorDefinition = new ColorSetting("Color", defaultColor);
      this.colorSetting = this.add(colorDefinition.dependsOn(registeredThunder));
      KillEffectsParticlesChoice defaultParticles = KillEffectsParticlesChoice.OFF;
      this.particlesSetting = this.add(new EnumSetting<>("Particles", defaultParticles));
      Registry particleRegistry = BuiltInRegistries.PARTICLE_TYPE;
      ParticleType[] defaultParticleTypes = new ParticleType[0];
      this.particleTypesSetting = this.add(new RegistrySetSetting<>("ParticleTypes", particleRegistry, defaultParticleTypes));
      Boolean targetsDefault = false;
      BooleanSetting targetsDefinition = new BooleanSetting("Targets", targetsDefault);
      targetsDefinition.markAsGroup();
      targetsDefinition.markVisibilityDependency();
      Setting<Boolean> registeredTargets = this.add(targetsDefinition);
      Boolean ignoreNakedsDefault = true;
      BooleanSetting ignoreNakedsDefinition = new BooleanSetting("IgnoreNakeds", ignoreNakedsDefault);
      this.ignoreNakedsSetting = this.add(ignoreNakedsDefinition.dependsOn(registeredTargets));
      Boolean selfDefault = false;
      BooleanSetting selfDefinition = new BooleanSetting("Self", selfDefault);
      this.selfSetting = this.add(selfDefinition.dependsOn(registeredTargets));
      this.killSayMessages = new KillSayMessages(this);
      this.trackedTargets = new ConcurrentHashMap<>();
      this.expiredTargets = ObjectLists.synchronize(new ObjectArrayList());
      this.killStreak = 0;
      Setting<?> callbackOption = this.killStreakVolumeSetting;
      callbackOption.setConfigName("KillStreakVolume");
      this.killSaySetting.setChangeCallback(() -> {
         KillSayMessages messagesSnapshot = this.killSayMessages;
         boolean force = false;
         messagesSnapshot.reloadMessages(force);
      });
      this.pathSetting.setChangeCallback(() -> {
         KillSayMessages messagesSnapshot = this.killSayMessages;
         boolean force = true;
         messagesSnapshot.reloadMessages(force);
      });
      this.refreshSetting.setChangeCallback(() -> {
         Setting<Boolean> refreshOption = this.refreshSetting;
         Boolean refresh = refreshOption.getValue();
         if (refresh) {
            KillSayMessages messagesSnapshot = this.killSayMessages;
            boolean force = true;
            messagesSnapshot.reloadMessages(force);
            this.refreshSetting.setValue(false);
         }
      });
      this.randomSetting.setChangeCallback(this.killSayMessages::resetSequence);
      callbackOption = this.particleTypesSetting;
      Predicate customParticlesPredicate = ignoredParticleOption -> this.particlesSetting.getValue() == KillEffectsParticlesChoice.CUSTOM;
      callbackOption.addVisibilityPredicate(customParticlesPredicate);
      this.setDrawn(false);
   }
}
