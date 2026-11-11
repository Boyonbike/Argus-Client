package dev.argus.client.modules.misc;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.ClientLevelRemoveEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.entity.PlayerStateUtils;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class VisualRangeModule extends Module {
   private static final int enderPearlMessageId;
   private final Setting<Boolean> joinSetting = this.add(new BooleanSetting("Join", true));
   private final Setting<Boolean> leaveSetting = this.add(new BooleanSetting("Leave", true));
   private final Setting<Boolean> targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
   private final Setting<Boolean> playersSetting = this.add(new BooleanSetting("Players", true).dependsOn(this.targetsSetting));
   private final Setting<Boolean> friendsSetting = this.add(new BooleanSetting("Friends", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> enemiesSetting = this.add(new BooleanSetting("Enemies", true).dependsOn(this.targetsSetting));
   private final Setting<Boolean> nakedsSetting = this.add(new BooleanSetting("Nakeds", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> signsSetting = this.add(new BooleanSetting("Signs", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> enderPearlsSetting = this.add(new BooleanSetting("EnderPearls", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> playSoundSetting = this.add(new BooleanSetting("PlaySound", false).markVisibilityDependency());
   private final Setting<Boolean> onlyEnemiesSetting = this.add(new BooleanSetting("OnlyEnemies", false).dependsOn(this.playSoundSetting));
   private final Setting<NamedIdentifier> soundSetting = this.add(
      new IdentifierSetting("Sound", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.playSoundSetting)
   );
   private final Setting<Float> volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.playSoundSetting));

   private void playAlertSound() {
      Setting<Boolean> playSoundOption = this.playSoundSetting;
      if (playSoundOption.getValue()) {
         SoundManager soundManagerSnapshot = ClientContext.soundManager;
         Setting<NamedIdentifier> soundOption = this.soundSetting;
         NamedIdentifier soundId = soundOption.getValue();
         SoundClip soundSnapshot = SoundManager.getSound(soundManagerSnapshot, soundId);
         Setting<Float> volumeOption = this.volumeSetting;
         float soundVolume = volumeOption.getValue();
         soundSnapshot.play(soundVolume);
      }
   }

   public VisualRangeModule() {
      super("VisualRange", "Informs you of players who enter/leave your visual range.", ModuleCategory.MISC);
      this.setDrawn(false);
   }

   @EventHandler
   private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable joinNotificationTask = () -> {
            Player targetPlayer = player;
            if (this.shouldNotifyPlayer(targetPlayer)) {
               Setting<Boolean> joinOption = this.joinSetting;
               if (joinOption.getValue()) {
                  GameProfile profile = player.getGameProfile();
                  String playerName = profile.name();
                  Color defaultColor = Color.WHITE;
                  SocialRegistry colorManager = ClientContext.socialRegistry;
                  Color playerColor = colorManager.getColor(playerName, defaultColor);
                  Object[] formatArguments = new Object[2];
                  AliasRegistry nameFormatterSnapshot = ClientContext.aliasRegistry;
                  formatArguments[0] = nameFormatterSnapshot.getPlayerAlias(playerName);
                  formatArguments[1] = ChatFormatting.WHITE;
                  String message = "%s%s entered visual range.".formatted(formatArguments);
                  MutableComponent notification = Component.literal(message);
                  UnaryOperator<Style> colorStyle = style -> {
                     int colorHash = playerColor.hashCode();
                     return style.withColor(colorHash);
                  };
                  notification = notification.withStyle(colorStyle);
                  Vec3 position = player.position();
                  this.addPositionInteractions(notification, position);
                  int nameHash = playerName.hashCode();
                  int messageId = Math.abs(nameHash) * -1;
                  messageId /= 2;
                  MessageSignature signature = ChatUtils.createSignature(messageId);
                  ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.MID;
                  ChatUtils.printChat(notification, signature, priority);
                  SocialRegistry enemyManagerSnapshot = ClientContext.socialRegistry;
                  if (!enemyManagerSnapshot.isEnemyName(playerName)) {
                     Setting<Boolean> onlyEnemiesOption = this.onlyEnemiesSetting;
                     if (onlyEnemiesOption.getValue()) {
                        return;
                     }
                  }

                  this.playAlertSound();
               }
            }
         };
         scheduler.scheduleRotationTask(joinNotificationTask, 1);
      }
   }

   private void addPositionInteractions(MutableComponent message, Vec3 position) {
      Object[] formatArguments = new Object[3];
      double x = position.x();
      formatArguments[0] = x;
      double y = position.y();
      formatArguments[1] = y;
      double z = position.z();
      formatArguments[2] = z;
      String coordinates = "%.0f, %.0f, %.0f".formatted(formatArguments);
      String hoverMessage = "at " + coordinates;
      MutableComponent hoverText = Component.literal(hoverMessage);
      UnaryOperator<Style> hoverStyle = style -> {
         HoverEvent hoverEvent = new ShowText(hoverText);
         return style.withHoverEvent(hoverEvent);
      };
      message.withStyle(hoverStyle);
      UnaryOperator<Style> clickStyle = style -> {
         String highlightCoordinates = coordinates.replace(",", "");
         String highlightCommand = "highlight " + highlightCoordinates;
         ClickEvent clickEvent = ChatUtils.suggestCommand(highlightCommand);
         return style.withClickEvent(clickEvent);
      };
      message.withStyle(clickStyle);
   }

   @EventHandler
   private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
      int entityId = event.getId();
      if (levelSnapshot.getEntity(entityId) instanceof Player player) {
         if (event.isEntityPresent() && this.shouldNotifyPlayer(player)) {
            Setting<Boolean> leaveOption = this.leaveSetting;
            if (leaveOption.getValue()) {
               GameProfile profile = player.getGameProfile();
               String playerName = profile.name();
               Color defaultColor = Color.WHITE;
               SocialRegistry colorManager = ClientContext.socialRegistry;
               Color playerColor = colorManager.getColor(playerName, defaultColor);
               Object[] formatArguments = new Object[2];
               AliasRegistry nameFormatterSnapshot = ClientContext.aliasRegistry;
               formatArguments[0] = nameFormatterSnapshot.getPlayerAlias(playerName);
               formatArguments[1] = ChatFormatting.WHITE;
               String message = "%s%s left visual range.".formatted(formatArguments);
               MutableComponent notification = Component.literal(message);
               UnaryOperator<Style> colorStyle = style -> {
                  int colorHash = playerColor.hashCode();
                  return style.withColor(colorHash);
               };
               notification = notification.withStyle(colorStyle);
               Vec3 position = player.position();
               this.addPositionInteractions(notification, position);
               int nameHash = playerName.hashCode();
               int messageId = Math.abs(nameHash) * -1;
               messageId /= 2;
               MessageSignature signature = ChatUtils.createSignature(messageId);
               ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.LOW;
               ChatUtils.printChat(notification, signature, priority);
            }
         }
      }
   }

   @EventHandler
   private void onClientChunkCacheReplaceWithPacketDataEvent(ClientChunkCacheReplaceWithPacketDataEvent event) {
      Setting<Boolean> signsOption = this.signsSetting;
      if (signsOption.getValue()) {
         AtomicBoolean foundSigns = new AtomicBoolean(false);
         LevelChunk chunk = event.getChunk();
         Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
         BiConsumer<BlockPos, BlockEntity> notifySign = (blockPosition, blockEntity) -> {
            if (blockEntity instanceof SignBlockEntity sign) {
               int positionHash = blockPosition.hashCode();
               int signMessageId = -Math.abs(positionHash);
               MutableComponent notification = Component.literal("Found a sign: ");
               boolean empty = true;
               SignText frontTextSnapshot = sign.getFrontText();
               boolean filtered = false;

               for (Component line : frontTextSnapshot.getMessages(filtered)) {
                  String lineText = line.getString();
                  String strippedLine = ChatFormatting.stripFormatting(lineText);
                  if (!strippedLine.isBlank()) {
                     notification.append(line);
                     notification.append(" ");
                     empty = false;
                  }
               }

               if (empty) {
                  notification.append("<empty>");
               }

               UnaryOperator<Style> colorStyle = style -> {
                  ChatFormatting textColor = ChatFormatting.WHITE;
                  return style.applyFormat(textColor);
               };
               notification.withStyle(colorStyle);
               BlockPos signPosition = sign.getBlockPos();
               Vec3 position = Vec3.atCenterOf(signPosition);
               this.addPositionInteractions(notification, position);
               MessageSignature signature = ChatUtils.createSignature(signMessageId);
               ChatUtils.printChat(notification, signature);
               foundSigns.set(true);
            }
         };
         blockEntities.forEach(notifySign);
         if (foundSigns.get()) {
            this.playAlertSound();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundAddEntityPacket packet && packet.getType() == EntityTypes.ENDER_PEARL) {
         Setting<Boolean> enderPearlsOption = this.enderPearlsSetting;
         if (enderPearlsOption.getValue()) {
            MutableComponent notification = Component.literal("Ender Pearl entered visual range.");
            Vec3 position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            this.addPositionInteractions(notification, position);
            MessageSignature signature = ChatUtils.createSignature(enderPearlMessageId);
            ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.MID;
            ChatUtils.printChat(notification, signature, priority);
            this.playAlertSound();
         }
      }
   }

   private boolean shouldNotifyPlayer(Player player) {
      if (ClientAccess.argusclientMinecraft.player == player) {
         return false;
      } else {
         GameProfile profile = player.getGameProfile();
         String playerName = profile.name();
         LocalPlayer localPlayer = ClientAccess.argusclientMinecraft.player;
         GameProfile localProfile = localPlayer.getGameProfile();
         if (playerName.equals(localProfile.name())) {
            return false;
         } else {
            SocialRegistry friendManager = ClientContext.socialRegistry;
            if (friendManager.isFriendPlayer(player)) {
               return this.friendsSetting.getValue();
            } else {
               SocialRegistry enemyManager = ClientContext.socialRegistry;
               if (enemyManager.isEnemyPlayer(player)) {
                  return this.enemiesSetting.getValue();
               } else {
                  return !PlayerStateUtils.hasArmor(player) ? this.nakedsSetting.getValue() : this.playersSetting.getValue();
               }
            }
         }
      }
   }

   static {
      int enderPearlHash = "EnderPearl".hashCode();
      enderPearlMessageId = -Math.abs(enderPearlHash);
   }
}
