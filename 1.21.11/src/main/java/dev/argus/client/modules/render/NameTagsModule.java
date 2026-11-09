package dev.argus.client.modules.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.NameTagRenderEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RenderTargetRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.NameTagItemRenderer;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.combat.TotemPopTracker;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerState;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.EquipmentUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.math.Vec3Utils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class NameTagsModule extends Module {
   public static final Supplier NAME_TAG_RENDER_TARGET = RenderTargetRegistry.createLazyRenderTarget("nametags_temp");
   private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath("argusclient", "textures/argusclient.png");
   private static final StringBuilder textScratch = new StringBuilder();
   private static AnimationsModule animationsModule = ClientContext.getModule(AnimationsModule.class);
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private static FontsModule fontsModule = ClientContext.getModule(FontsModule.class);
   private static ShaderModule shader = ClientContext.getModule(ShaderModule.class);
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   private final Setting<Integer> rangeSetting = this.add(new NumberSetting<>("Range", 300, 1, 300).withUnit("m"));
   private final Setting<Float> scaleSetting = this.add(new NumberSetting<>("Scale", 1.2F, 0.1F, 3.0F));
   final Setting<Boolean> infoSetting = this.add(new BooleanSetting("Info", true).markAsGroup());
   private final Setting<Boolean> nameSetting = this.add(new BooleanSetting("Name", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }));
   private final Setting<Boolean> pingSetting = this.add(new BooleanSetting("Ping", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }).markVisibilityDependency());
   private final Setting<Boolean> colorSetting = this.add(new BooleanSetting("Color", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }).dependsOn(this.pingSetting));
   final Setting<Boolean> healthSetting = this.add(new BooleanSetting("Health", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }).markVisibilityDependency());
   private final Setting<Boolean> decimalsSetting = this.add(new BooleanSetting("Decimals", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded() && this.healthSetting.isExpanded();
   }));
   private final Setting<Boolean> gamemodeSetting = this.add(new BooleanSetting("Gamemode", true, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }));
   private final Setting<Boolean> totemPopsSetting = this.add(new BooleanSetting("TotemPops", false, ignored -> {
      Setting<Boolean> option = this.infoSetting;
      return option.isExpanded();
   }));
   private final Setting<Boolean> itemSetting = this.add(new BooleanSetting("Item", false).markAsGroup());
   private final Setting<Boolean> armorSetting = this.add(new BooleanSetting("Armor", true).markVisibilityDependency().dependsOn(this.itemSetting));
   private final Setting<NameTagsModule.DurabilityChoice> durabilitySetting = this.add(
      new EnumSetting<>("Durability", NameTagsModule.DurabilityChoice.SHOW).dependsOn(this.itemSetting, this.armorSetting)
   );
   private final Setting<Boolean> itemsSetting = this.add(new BooleanSetting("Items", true).dependsOn(this.itemSetting));
   private final Setting<Boolean> showEatingSetting = this.add(new BooleanSetting("ShowEating", false).dependsOn(this.itemSetting, this.itemsSetting));
   private final Setting<Boolean> enchantsSetting = this.add(new BooleanSetting("Enchants", true).dependsOn(this.itemSetting));
   private final Setting<Boolean> hideMaxSetting = this.add(new BooleanSetting("HideMax", false).dependsOn(this.itemSetting, this.enchantsSetting));
   private final Setting<Boolean> itemNameSetting = this.add(new BooleanSetting("Name", true).dependsOn(this.itemSetting));
   private final Setting<Boolean> amountSetting = this.add(new BooleanSetting("Amount", true).dependsOn(this.itemSetting));
   private final Setting<Boolean> targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup());
   private final Setting<Boolean> playersSetting = this.add(new BooleanSetting("Players", true).markVisibilityDependency().dependsOn(this.targetsSetting));
   private final Setting<Boolean> deadSetting = this.add(new BooleanSetting("Dead", true).dependsOn(this.playersSetting, this.targetsSetting));
   private final Setting<Boolean> selfSetting = this.add(new BooleanSetting("Self", false).dependsOn(this.playersSetting, this.targetsSetting));
   private final Setting<Boolean> botsSetting = this.add(new BooleanSetting("Bots", true).dependsOn(this.playersSetting, this.targetsSetting));
   private final Setting<Boolean> animalsSetting = this.add(new BooleanSetting("Animals", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> hostilesSetting = this.add(new BooleanSetting("Hostiles", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
   final Setting<Boolean> colorsSetting = this.add(new BooleanSetting("Colors", true).markAsGroup());
   private final Setting<Boolean> friendSetting = this.add(new BooleanSetting("Friend", true).dependsOn(this.colorsSetting));
   private final Setting<Boolean> enemySetting = this.add(new BooleanSetting("Enemy", true).dependsOn(this.colorsSetting));
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Color> textSetting;
   private final Setting<Color> invisiblesSetting;
   private final Setting<Color> sneakSetting;
   private final Setting<Color> eatingSetting;
   private final MeshBuffer overlayMesh;
   private final Color itemOverlayColor;
   private final List<Entity> visibleEntities;
   private final List<ItemStack> equipmentItems;
   private final ElapsedTimer entityRefreshTimer;
   private boolean hasEquipmentItem;
   private boolean hasDurabilityText;

   private void collectEquipment(Player player) {
      this.equipmentItems.clear();
      Setting<Boolean> itemsOption = this.itemsSetting;
      if (itemsOption.getValue()) {
         ItemStack offhandCheck = player.getOffhandItem();
         if (!offhandCheck.isEmpty()) {
            this.equipmentItems.add(player.getOffhandItem());
         }
      }

      for (ItemStack armorStack : EquipmentUtils.getArmorStacks(player)) {
         if (!armorStack.isEmpty()) {
            Setting<Boolean> armorOption = this.armorSetting;
            if (armorOption.getValue()) {
               this.equipmentItems.add(armorStack);
            }
         }
      }

      Setting<Boolean> items2Option = this.itemsSetting;
      if (items2Option.getValue()) {
         ItemStack mainHandCheck = player.getMainHandItem();
         if (!mainHandCheck.isEmpty()) {
            this.equipmentItems.add(player.getMainHandItem());
         }
      }
   }

   private boolean shouldRenderSelf() {
      FreecamModule freecam = freecamModule;
      if (freecam.isToggled()) {
         return true;
      }

      Setting<Boolean> selfOption = this.selfSetting;
      if (selfOption.getValue()) {
         Options options = ClientAccess.argusclientMinecraft.options;
         if (options.getCameraType() != CameraType.FIRST_PERSON) {
            return true;
         }
      }

      return false;
   }

   private float getBackgroundPadding() {
      Color configuredOutline = this.outlineSetting.getValue();
      if (configuredOutline.getAlpha() == 0) {
         Color configuredFill = this.fillSetting.getValue();
         return configuredFill.getAlpha() == 0 ? 0.54F : 0.58F;
      } else {
         return 0.6F;
      }
   }

   private Component buildTagText(LivingEntity entity) {
      MutableComponent label = Component.empty();
      String entityName = entity.getName().getString();
      if (entity instanceof Player namedPlayer) {
         GameProfile profile = namedPlayer.getGameProfile();
         entityName = profile.name();
      }

      Setting<Boolean> nameOption = this.nameSetting;
      if (nameOption.getValue()) {
         AliasRegistry aliasesSnapshot = ClientContext.aliasRegistry;
         String alias = aliasesSnapshot.getPlayerAlias(entityName);
         label.append(alias);
      }

      if (entity instanceof Player infoPlayer) {
         Setting<Boolean> gamemodeOption = this.gamemodeSetting;
         if (gamemodeOption.getValue()) {
            label.append(" [");
            ClientPacketListener gameModeConnectionSnapshot = ClientAccess.argusclientMinecraft.player.connection;
            GameProfile gameModeProfile = infoPlayer.getGameProfile();
            UUID gameModeUuid = gameModeProfile.id();
            PlayerInfo gameModePlayerInfo = gameModeConnectionSnapshot.getPlayerInfo(gameModeUuid);
            String gameModeLabel;
            if (gameModePlayerInfo == null) {
               gameModeLabel = "S";
            } else {
               GameType playerGameMode = gameModePlayerInfo.getGameMode();
               gameModeLabel = this.getGameModeLabel(playerGameMode);
            }

            label.append(gameModeLabel);
            label.append("]");
         }

         Setting<Boolean> pingOption = this.pingSetting;
         if (pingOption.getValue()) {
            int ping = ClientContext.serverStateManager.getPlayerLatencyMillis(infoPlayer);
            if (ping > 0) {
               label.append(" ");
               int latencyArgb = ColorUtils.getLatencyColor(ping).hashCode();
               Setting<Boolean> colorOption = this.colorSetting;
               if (colorOption.getValue()) {
                  String coloredPingText = "%dms".formatted(ping);
                  MutableComponent coloredPingComponent = Component.literal(coloredPingText);
                  label.append(coloredPingComponent.withColor(latencyArgb));
               } else {
                  String plainPingText = "%dms".formatted(ping);
                  label.append(Component.nullToEmpty(plainPingText));
               }
            }
         }
      }

      Setting<Boolean> healthOption = this.healthSetting;
      if (healthOption.getValue()) {
         float health = EntityUtils.getHealthWithAbsorption(entity);
         double healthRatioSnapshot = EntityUtils.getHealthFraction(entity);
         int healthColor = ColorUtils.getHealthColor(healthRatioSnapshot);
         ColorsSchemeChoice colorScheme = colorsModule.schemeSetting.getValue();
         healthColor = colorScheme.transform(healthColor);
         boolean configuredDecimals = this.decimalsSetting.getValue();
         String healthFormat;
         if (configuredDecimals) {
            healthFormat = "%.1f";
         } else {
            healthFormat = "%.0f";
         }

         String healthText = String.format(healthFormat, health);
         MutableComponent healthLabelSnapshot = Component.literal(healthText);
         MutableComponent healthComponent = healthLabelSnapshot.withColor(healthColor);
         label.append(" ");
         label.append(healthComponent);
      }

      Setting<Boolean> totemPopsOption = this.totemPopsSetting;
      if (totemPopsOption.getValue() && entity instanceof Player popsPlayer) {
         TotemPopTracker popTrackerSnapshot = ClientContext.totemPopTracker;
         int pops = popTrackerSnapshot.getPopCount(popsPlayer);
         if (pops > 0) {
            int popColor = getTotemPopColor(pops);
            String popText = " -" + pops;
            MutableComponent popComponentSnapshot = Component.literal(popText);
            label.append(popComponentSnapshot.withColor(popColor));
         }
      }

      return label;
   }

   private void renderEquipmentItem(Player player, PoseStack poses, ItemStack stack, float x, float y, float scale) {
      int durabilityPercent = DamageUtils.getDurabilityPercent(stack);
      Setting<Boolean> itemsOption = this.itemsSetting;
      if (itemsOption.getValue()) {
         Setting<Boolean> showEatingOption = this.showEatingSetting;
         if (showEatingOption.getValue() && player.isUsingItem()) {
            InteractionHand usedHand = player.getUsedItemHand();
            if (player.getItemInHand(usedHand) == stack && stack.has(DataComponents.FOOD)) {
               float eatingBottomBaseY = y * scale;
               float eatingBottom = eatingBottomBaseY + 7.55F;
               float eatingTopBaseY = y * scale;
               float eatingTop = eatingTopBaseY - 7.55F;
               long elapsedUseMillis = System.currentTimeMillis() - ((PlayerState)player).argusclient$getLastEatingTime();
               float elapsedUseMillisAsFloat = (float)elapsedUseMillis;
               float useDurationTicks = stack.getUseDuration(player);
               float scaledUseDuration = useDurationTicks * 50.0F;
               float useProgress = elapsedUseMillisAsFloat / (scaledUseDuration + 50.0F);
               float eatingProgressWidth = 0.9F * Mth.clamp(useProgress, 0.0F, 1.0F);
               float eatingLeft = (x - 0.45F) * scale;
               float eatingRight = (x - 0.45F + eatingProgressWidth) * scale;
               Color configuredEating = this.eatingSetting.getValue();
               GuiDrawUtils.fillRect(poses, eatingLeft, eatingTop, eatingRight, eatingBottom, ColorUtils.toArgb(configuredEating, 0.2F));
            }
         }
      }

      boolean showItemDetails = this.durabilitySetting.getValue() != NameTagsModule.DurabilityChoice.ONLY
         || !stack.has(DataComponents.EQUIPPABLE) && !stack.is(Items.ELYTRA);
      if (showItemDetails) {
         boolean blockItem = stack.getItem() instanceof BlockItem;
         float itemPoseScaleZ;
         if (blockItem) {
            itemPoseScaleZ = 0.1F;
         } else {
            itemPoseScaleZ = 0.5F;
         }

         float itemPoseScaleX = blockItem ? -scale : scale;
         ItemDisplayContext itemDisplayContext = blockItem ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI;
         this.hasEquipmentItem = true;
         poses.pushPose();
         float itemPoseX = x * scale;
         float itemPoseY = y * scale;
         poses.translate(itemPoseX, itemPoseY, 0.0F);
         float itemPoseScaleY = -scale;
         poses.scale(itemPoseScaleX, itemPoseScaleY, itemPoseScaleZ);
         ItemStackRenderState itemRenderState = new ItemStackRenderState();
         SubmitNodeStorage itemBuffers = NameTagItemRenderer.getSubmitNodes();
         ClientAccess.argusclientMinecraft
            .getItemModelResolver()
            .updateForTopItem(itemRenderState, stack, itemDisplayContext, ClientAccess.argusclientMinecraft.level, null, 0);
         itemRenderState.submit(poses, itemBuffers, 15728880, OverlayTexture.NO_OVERLAY, 0);
         poses.popPose();
      }

      Setting<Boolean> amountOption = this.amountSetting;
      if (amountOption.getValue() && stack.getCount() > 1) {
         int itemCount = stack.getCount();
         String countText = String.valueOf(itemCount);
         float countX = (x + 0.5F) * scale;
         float countBaseY = y * scale;
         float countY = countBaseY + 1.5F;
         double countScaleAsDouble = scale;
         double countTextScale = countScaleAsDouble * 2.25;
         NameTagsModule.TextAlignment countAlignment = NameTagsModule.TextAlignment.RIGHT;
         Color countColor = Color.white;
         this.drawText(poses, countText, countX, countY, countTextScale, countColor, false, countAlignment);
      }

      if (stack.isBarVisible() && showItemDetails) {
         float barBaseY = y * scale;
         float barY = barBaseY + 5.5F;
         float barBackgroundLeft = (x - 0.45F) * scale;
         float barBackgroundTop = barY - 0.05F;
         float barBackgroundRight = (x + 0.45F) * scale;
         float barBackgroundBottom = barY + 2.0F;
         GuiDrawUtils.fillRect(poses, barBackgroundLeft, barBackgroundTop, barBackgroundRight, barBackgroundBottom, -16777216);
         float barFillLeft = (x - 0.45F) * scale;
         float barFillTop = barY - 0.05F;
         float vanillaBarWidth = stack.getBarWidth();
         float barProgress = vanillaBarWidth / 13.0F;
         float barFillRight = (x + Mth.lerp(barProgress, -0.45F, 0.45F)) * scale;
         float barFillBottom = barY + 1.05F;
         GuiDrawUtils.fillRect(poses, barFillLeft, barFillTop, barFillRight, barFillBottom, 0xFF000000 | stack.getBarColor());
      }

      ItemEnchantments enchantmentComponents = stack.getEnchantments();
      Set<Entry<Holder<Enchantment>>> enchantments = enchantmentComponents.entrySet();
      float enchantRowOffset = -1.2F;
      Item enchantedGoldenApple = Items.ENCHANTED_GOLDEN_APPLE;
      if (stack.is(enchantedGoldenApple) && showItemDetails) {
         float goldenAppleLabelX = x * scale;
         float goldenAppleLabelY = y * scale;
         goldenAppleLabelY = --goldenAppleLabelY + FontRenderer.INSTANCE.getHeight() * 0.6F * enchantRowOffset;
         double goldenAppleTextScale = scale * 1.3F;
         NameTagsModule.TextAlignment goldenAppleAlignment = NameTagsModule.TextAlignment.RIGHT;
         Color goldenAppleColor = this.itemOverlayColor;
         this.drawText(poses, "God", goldenAppleLabelX, goldenAppleLabelY, goldenAppleTextScale, goldenAppleColor, false, goldenAppleAlignment);
      } else {
         if (showItemDetails) {
            Setting<Boolean> enchantsOption = this.enchantsSetting;
            if (enchantsOption.getValue()) {
               boolean hideMaxEnchantments = this.hideMaxSetting.getValue() && EquipmentUtils.hasRequiredEnchantments(stack);
               if (hideMaxEnchantments) {
                  float maxLabelX = (x - 0.5F) * scale;
                  float maxLabelBaseY = y * scale;
                  float maxLabelPaddedY = maxLabelBaseY - 2.0F;
                  float maxLabelLineHeight = FontRenderer.INSTANCE.getHeight() * 0.6F;
                  float maxLabelY = maxLabelPaddedY + maxLabelLineHeight / 1.2F * enchantRowOffset;
                  double maxLabelTextScale = scale * 1.3F;
                  NameTagsModule.TextAlignment maxLabelAlignment = NameTagsModule.TextAlignment.LEFT;
                  Color maxLabelColor = Color.RED;
                  this.drawText(poses, "Max", maxLabelX, maxLabelY, maxLabelTextScale, maxLabelColor, false, maxLabelAlignment);
               }

               for (Entry enchantmentEntry : enchantments) {
                  if (hideMaxEnchantments) {
                     break;
                  }

                  Enchantment enchantment = (Enchantment)((Holder)enchantmentEntry.getKey()).value();
                  boolean singleLevelEnchantment = enchantment.getMaxLevel() == 1;
                  String enchantmentName = Enchantment.getFullname((Holder<Enchantment>)enchantmentEntry.getKey(), enchantmentEntry.getIntValue()).getString();
                  int enchantmentPrefixLimit;
                  if (singleLevelEnchantment) {
                     enchantmentPrefixLimit = 3;
                  } else {
                     enchantmentPrefixLimit = 2;
                  }

                  int enchantmentNameLength = enchantmentName.length();
                  int enchantmentPrefixLength = Math.min(enchantmentPrefixLimit, enchantmentNameLength);
                  enchantmentName = enchantmentName.substring(0, enchantmentPrefixLength);
                  if (!enchantmentName.equalsIgnoreCase("Cu")) {
                     if (!singleLevelEnchantment) {
                        String enchantmentLevel = String.valueOf(enchantmentEntry.getIntValue());
                        enchantmentName = "\u0001\u0001"
                           .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(enchantmentName)))
                           .replaceFirst("\u0001", Matcher.quoteReplacement(enchantmentLevel));
                     }

                     float enchantmentX = (x - 0.5F) * scale;
                     float enchantmentBaseY = y * scale;
                     float enchantmentPaddedY = enchantmentBaseY - 2.0F;
                     float enchantmentLineHeight = FontRenderer.INSTANCE.getHeight() * 0.6F;
                     float enchantmentY = enchantmentPaddedY + enchantmentLineHeight / 1.2F * enchantRowOffset;
                     double enchantmentTextScale = scale * 1.3F;
                     NameTagsModule.TextAlignment enchantmentAlignment = NameTagsModule.TextAlignment.LEFT;
                     Color enchantmentColor = Color.WHITE;
                     this.drawText(poses, enchantmentName, enchantmentX, enchantmentY, enchantmentTextScale, enchantmentColor, false, enchantmentAlignment);
                     enchantRowOffset++;
                  }
               }
            }
         }

         if (stack.isDamageableItem()) {
            Setting<NameTagsModule.DurabilityChoice> durability2Option = this.durabilitySetting;
            if (durability2Option.getValue() != NameTagsModule.DurabilityChoice.HIDE) {
               float durabilityBaseY = y * scale;
               float durabilityPaddedY = durabilityBaseY - 4.0F;
               float durabilityY = durabilityPaddedY - FontRenderer.INSTANCE.getHeight();
               if (!showItemDetails) {
                  durabilityY = y - FontRenderer.INSTANCE.getHeight();
               }

               byte emptyTextLength = 0;
               StringBuilder clearTextBuilder = textScratch;
               clearTextBuilder.setLength(emptyTextLength);
               StringBuilder percentValueBuilder = textScratch;
               percentValueBuilder.append(durabilityPercent);
               StringBuilder percentSuffixBuilder = textScratch;
               percentSuffixBuilder.append("%");
               ColorsSchemeChoice durabilityColorScheme = colorsModule.schemeSetting.getValue();
               Color vanillaDurabilityColor = new Color(stack.getBarColor(), false);
               Color durabilityColor = durabilityColorScheme.transform(vanillaDurabilityColor);
               this.hasDurabilityText = true;
               StringBuilder durabilityTextBuilder = textScratch;
               String durabilityText = durabilityTextBuilder.toString();
               float durabilityBaseX = (float)((x - 0.5) * scale);
               float durabilityX = durabilityBaseX + 1.0F;
               double durabilityTextScale = scale * 1.5F;
               NameTagsModule.TextAlignment durabilityAlignment = NameTagsModule.TextAlignment.LEFT;
               this.drawText(poses, durabilityText, durabilityX, durabilityY, durabilityTextScale, durabilityColor, false, durabilityAlignment);
               return;
            }
         }
      }
   }

   private void renderEquipment(PoseStack poses, Player player) {
      int maximumEnchantRows = -3;

      for (ItemStack stack : this.equipmentItems) {
         int enchantRows = EnchantmentUtils.getLevels(stack).size();
         Setting<Boolean> hideMaxOption = this.hideMaxSetting;
         if (hideMaxOption.getValue() && EquipmentUtils.hasRequiredEnchantments(stack)) {
            enchantRows = 0;
         }

         if (enchantRows > maximumEnchantRows) {
            maximumEnchantRows = enchantRows;
         }
      }

      int rowsBeforeClamp = maximumEnchantRows;
      maximumEnchantRows = Math.max(rowsBeforeClamp, 0);
      Setting<NameTagsModule.DurabilityChoice> durabilityOption = this.durabilitySetting;
      if (durabilityOption.getValue() == NameTagsModule.DurabilityChoice.ONLY) {
         maximumEnchantRows = 0;
      } else {
         Setting<Boolean> enchantsOption = this.enchantsSetting;
         if (!enchantsOption.getValue()) {
            maximumEnchantRows = 0;
         }
      }

      float enchantRowCount = maximumEnchantRows;
      float enchantFontHeight = enchantRowCount * FontRenderer.INSTANCE.getHeight();
      float scaledEnchantHeight = enchantFontHeight * 0.5F;
      float dividedEnchantHeight = scaledEnchantHeight / 1.2F;
      float enchantBlockHeight = dividedEnchantHeight / 17.25F;
      float backgroundPadding = this.getBackgroundPadding();
      float equipmentY = -Math.max(enchantBlockHeight, backgroundPadding);

      for (int itemIndex = 0; itemIndex < this.equipmentItems.size(); itemIndex++) {
         ItemStack equipmentStack = this.equipmentItems.get(this.equipmentItems.size() - 1 - itemIndex);
         float equipmentX = itemIndex;
         equipmentX += 0.5F;
         float itemCountForCentering = this.equipmentItems.size();
         equipmentX -= itemCountForCentering * 0.5F;
         this.renderEquipmentItem(player, poses, equipmentStack, equipmentX, equipmentY, 17.25F);
      }

      ItemStack mainHandForCheck = player.getMainHandItem();
      if (!mainHandForCheck.isEmpty()) {
         Setting<Boolean> itemNameOption = this.itemNameSetting;
         if (itemNameOption.getValue()) {
            float scaledItemNameY = equipmentY * 17.25F;
            float paddedItemNameY = scaledItemNameY - 4.0F;
            float itemNameY = paddedItemNameY - FontRenderer.INSTANCE.getHeight();
            Setting<Boolean> armorOption = this.armorSetting;
            boolean hasItemRow;
            if (armorOption.getValue()) {
               hasItemRow = this.hasEquipmentItem && this.durabilitySetting.getValue() != NameTagsModule.DurabilityChoice.ONLY;
            } else {
               hasItemRow = this.hasEquipmentItem;
            }

            if (!hasItemRow) {
               itemNameY = -FontRenderer.INSTANCE.getHeight();
            }

            if (this.hasDurabilityText) {
               float durabilityFontHeight = FontRenderer.INSTANCE.getHeight();
               itemNameY -= durabilityFontHeight * 0.5F;
               if (!hasItemRow) {
                  itemNameY--;
               }
            }

            ItemStack namedItem = player.getMainHandItem();
            String formattedItemName = namedItem.getHoverName().getString();
            String plainItemName = ChatFormatting.stripFormatting(formattedItemName);
            Rarity itemRarity = namedItem.getRarity();
            ChatFormatting rarityFormatting = itemRarity.color();
            Integer nullableRarityRgb = rarityFormatting.getColor();
            int rarityRgb;
            if (nullableRarityRgb == null) {
               rarityRgb = -1;
            } else {
               rarityRgb = nullableRarityRgb;
            }

            Color itemNameColor = new Color(rarityRgb);
            NameTagsModule.TextAlignment itemNameAlignment = NameTagsModule.TextAlignment.CENTER;
            this.drawText(poses, plainItemName, 0.0F, itemNameY, 22.424999177455902, itemNameColor, false, itemNameAlignment);
            return;
         }
      }
   }

   private String getGameModeLabel(GameType gameMode) {
      return switch (gameMode) {
         case SURVIVAL -> "S";
         case CREATIVE -> "C";
         case ADVENTURE -> "A";
         case SPECTATOR -> "SP";
      };
   }

   @EventHandler(priority = -1100)
   private void onWorldRender(WorldRenderEvent.Overlay event) {
      RenderTarget renderTarget = (RenderTarget)NAME_TAG_RENDER_TARGET.get();
      RenderUtils.getEntityBuffers().endBatch();
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();

      for (Entity entity : this.visibleEntities) {
         float partialTick = RenderUtils.getPartialTick();
         double interpolationPartialTick = partialTick;
         double previousXSnapshot = entity.xo;
         double currentX = entity.getX();
         double relativeX = Mth.lerp(interpolationPartialTick, previousXSnapshot, currentX) - cameraPosition.x;
         interpolationPartialTick = partialTick;
         double previousYSnapshot = entity.yo;
         double currentY = entity.getY();
         double relativeY = Mth.lerp(interpolationPartialTick, previousYSnapshot, currentY) - cameraPosition.y;
         interpolationPartialTick = partialTick;
         double previousZSnapshot = entity.zo;
         double currentZ = entity.getZ();
         double relativeZ = Mth.lerp(interpolationPartialTick, previousZSnapshot, currentZ) - cameraPosition.z;
         EntityRenderDispatcher entityDispatcherSnapshot = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher();
         EntityRenderer entityRenderer = entityDispatcherSnapshot.getRenderer(entity);
         EntityRenderState renderState = entityRenderer.createRenderState(entity, partialTick);
         Vec3 renderOffset = entityRenderer.getRenderOffset(renderState);
         event.getPoseStack().pushPose();
         PoseStack translatePosesSnapshot = event.getPoseStack();
         double translatedXSnapshot = relativeX + renderOffset.x();
         double translatedYSnapshot = relativeY + renderOffset.y();
         double translatedZ = relativeZ + renderOffset.z();
         translatePosesSnapshot.translate(translatedXSnapshot, translatedYSnapshot, translatedZ);
         PoseStack tagPosesSnapshot = event.getPoseStack();
         LivingEntity tagEntity = (LivingEntity)entity;
         this.renderTag(tagPosesSnapshot, tagEntity);
         event.getPoseStack().popPose();
      }

      OptionInstance glintOptionSnapshot = ClientAccess.argusclientMinecraft.options.glintStrength();
      double originalGlint = (Double)glintOptionSnapshot.get();
      double reducedGlint = originalGlint * 0.5;
      glintOptionSnapshot.set(reducedGlint);
      if (this.overlayMesh.isBuilding()) {
         RenderType overlayRenderTypeSnapshot = ClientRenderTypes.image(ICON_TEXTURE);
         MeshData overlayMeshData = this.overlayMesh.build();
         overlayRenderTypeSnapshot.draw(overlayMeshData);
      }

      Lighting lightingSnapshot = ClientAccess.argusclientMinecraft.gameRenderer.getLighting();
      com.mojang.blaze3d.platform.Lighting.Entry flatLightingEntry = com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_FLAT;
      lightingSnapshot.setupFor(flatLightingEntry);
      ShaderModule shaderSnapshot = shader;
      if (shaderSnapshot.includesNameTags()) {
         RenderTargetUtils.overrideRenderTargets(renderTarget);
         RenderUtils.clearColor(renderTarget);
         RenderUtils.clearDepth(renderTarget);
         NameTagItemRenderer.flush();
         RenderTargetUtils.restoreRenderTargets();
      } else {
         NameTagItemRenderer.flush();
      }

      lightingSnapshot = ClientAccess.argusclientMinecraft.gameRenderer.getLighting();
      com.mojang.blaze3d.platform.Lighting.Entry levelLightingEntry = com.mojang.blaze3d.platform.Lighting.Entry.LEVEL;
      lightingSnapshot.setupFor(levelLightingEntry);
      originalGlint = (Double)glintOptionSnapshot.get();
      double restoredGlint = originalGlint * 2.0;
      glintOptionSnapshot.set(restoredGlint);
      GuiDrawUtils.flush(true);
   }

   @EventHandler
   private void refreshVisibleEntities(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         ElapsedTimer refreshTimerSnapshot = this.entityRefreshTimer;
         if (isEntityRefreshDue(refreshTimerSnapshot)) {
            this.visibleEntities.clear();
            ClientLevel level = ClientAccess.argusclientMinecraft.level;

            for (Entity entity : level.entitiesForRendering()) {
               if (this.shouldRenderEntity(entity)) {
                  GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
                  Vec3 cameraPositionSnapshot = gameRenderer.getMainCamera().position();
                  Vec3 entityPositionSnapshot = entity.position();
                  float distance = Vec3Utils.horizontalDistance(cameraPositionSnapshot, entityPositionSnapshot);
                  Integer maximumRange = this.rangeSetting.getValue();
                  if (!(distance > maximumRange.intValue())) {
                     AABB boundsSnapshot = entity.getBoundingBox();
                     if (RotationManager.isVisible(boundsSnapshot)) {
                        this.visibleEntities.add(entity);
                     }
                  }
               }
            }

            this.visibleEntities.sort(Comparator.comparingDouble(sortedEntity -> {
               Vec3 sortCameraPositionSnapshot = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
               Vec3 sortEntityPosition = sortedEntity.position();
               return -sortCameraPositionSnapshot.distanceToSqr(sortEntityPosition);
            }));
         }
      }
   }

   private static boolean isEntityRefreshDue(ElapsedTimer timer) {
      return timer.hasElapsedAndReset(50L);
   }

   @EventHandler
   private void onNameTagCheck(NameTagRenderEvent event) {
      Entity entitySnapshot = event.getEntity();
      if (this.shouldRenderEntity(entitySnapshot)) {
         event.cancel();
      }
   }

   private void drawText(PoseStack poses, String text, float x, float y, double scale, Color color, boolean vanillaFont, NameTagsModule.TextAlignment alignment) {
      poses.pushPose();
      poses.translate(x, y, 0.0F);
      float textScaleX = 0.025F * (float)scale;
      float textScaleY = 0.025F * (float)scale;
      poses.scale(textScaleX, textScaleY, 1.0F);
      float alignmentOffset = alignment.getOffset(text, vanillaFont);
      if (vanillaFont) {
         int vanillaOffset = (int)alignmentOffset;
         GuiDrawUtils.drawText(poses, text, vanillaOffset, 0, color.hashCode(), true);
      } else {
         FontRenderer.INSTANCE.drawWithShadow(poses, text, alignmentOffset, 0.0F, color, DisplayMode.SEE_THROUGH);
      }

      poses.popPose();
   }

   private void renderTag(PoseStack poses, LivingEntity entity) {
      this.hasDurabilityText = this.hasEquipmentItem = false;
      AnimationsModule animationsSnapshot = animationsModule;
      boolean animated = animationsSnapshot.isPlayerScalingEnabled() && entity != ClientAccess.argusclientMinecraft.player;
      float entityHeight = entity.getBbHeight();
      float modelHeightScale;
      if (animated) {
         modelHeightScale = animationsModule.playerScaleSetting.getValue();
      } else {
         modelHeightScale = 1.0F;
      }

      float scaledEntityHeight = entityHeight * modelHeightScale;
      float anchorHeight = scaledEntityHeight + 0.5F;
      float configuredTagScale = this.scaleSetting.getValue();
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      float renderPartialTick = RenderUtils.getPartialTick();
      Vec3 interpolatedPosition = entity.getPosition(renderPartialTick);
      double scaleAsDouble = configuredTagScale;
      float tagScale = (float)MathUtils.getDistanceScale(cameraPosition, interpolatedPosition, scaleAsDouble);
      poses.pushPose();
      float scaleAboveOrigin = tagScale - 1.0F;
      float scaleHeightOffset = scaleAboveOrigin * 0.25F;
      float tagAnchorY = anchorHeight + scaleHeightOffset;
      poses.translate(0.0F, tagAnchorY, 0.0F);
      Camera dispatcherCamera = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera;
      poses.mulPose(dispatcherCamera.rotation());
      float poseScaleX = tagScale * 0.025F;
      float negativeScaleY = -tagScale;
      float poseScaleY = negativeScaleY * 0.025F;
      float negativeScaleZ = -tagScale;
      float poseScaleZ = negativeScaleZ * 0.025F;
      poses.scale(poseScaleX, poseScaleY, poseScaleZ);
      Component label = this.buildTagText(entity);
      String plainLabel = label.getString();
      float textX = -FontRenderer.INSTANCE.getWidth(plainLabel);
      textX *= 0.5F;
      Color outline = this.outlineSetting.getValue();
      float fillLeft = textX - 1.0F;
      float textFillWidth = textX * -1.0F;
      float fillWidth = textFillWidth + 1.0F;
      float fillFontHeight = FontRenderer.INSTANCE.getHeight();
      float fillHeight = fillFontHeight + 1.0F;
      Setting<Color> fillOption = this.fillSetting;
      GuiDrawUtils.fillRect(poses, fillLeft, -1.0F, fillWidth, fillHeight, fillOption.getValue());
      float outlineLeft = textX - 2.5F + 1.5F;
      float textOutlineWidth = textX * -1.0F;
      float outlineWidth = textOutlineWidth + 1.0F;
      float outlineFontHeight = FontRenderer.INSTANCE.getHeight();
      GuiDrawUtils.drawLineRect(poses, outlineLeft, -1.0F, outlineWidth, outlineFontHeight + 1.0F, outline);
      Color textColor = this.textSetting.getValue();
      if (entity.isShiftKeyDown()) {
         textColor = this.sneakSetting.getValue();
      }

      if (entity.isInvisible()) {
         textColor = this.invisiblesSetting.getValue();
      }

      SocialRegistry friendCheckManager = ClientContext.socialRegistry;
      if (friendCheckManager.isFriendEntity(entity)) {
         Setting<Boolean> friendOption = this.friendSetting;
         if (friendOption.getValue()) {
            SocialRegistry friendColorManager = ClientContext.socialRegistry;
            textColor = friendColorManager.getFriendColor();
         }
      }

      SocialRegistry enemyCheckManager = ClientContext.socialRegistry;
      if (enemyCheckManager.isEnemyEntity(entity)) {
         Setting<Boolean> enemyOption = this.enemySetting;
         if (enemyOption.getValue()) {
            SocialRegistry enemyColorManager = ClientContext.socialRegistry;
            textColor = enemyColorManager.getEnemyColor();
         }
      }

      FontsModule queriedFontsModule = fontsModule;
      queriedFontsModule.isToggled();
      FontRenderer.INSTANCE.drawWithShadow(poses, label, textX, 1.0F, textColor, DisplayMode.SEE_THROUGH);
      if (entity instanceof Player equipmentPlayer) {
         this.collectEquipment(equipmentPlayer);
         this.renderEquipment(poses, equipmentPlayer);
      }

      poses.popPose();
   }

   public static int getTotemPopColor(int pops) {
      float fraction = Mth.clamp(pops / 10.0F, 0.0F, 1.0F);
      return ColorUtils.blend(Color.yellow, Color.red, fraction).getRGB();
   }

   private boolean isBot(Player player) {
      if (player instanceof FakeplayerCommand.SimulatedPlayer) {
         return false;
      }

      ClientPacketListener connectionSnapshot = ClientAccess.argusclientMinecraft.player.connection;
      GameProfile profile = player.getGameProfile();
      UUID uuid = profile.id();
      PlayerInfo playerInfo = connectionSnapshot.getPlayerInfo(uuid);
      ClientPacketListener onlineConnection = ClientAccess.argusclientMinecraft.player.connection;
      return onlineConnection.getOnlinePlayers().contains(playerInfo) && playerInfo != null ? playerInfo.getProfile() == null && player.hasCustomName() : true;
   }

   private boolean shouldRenderEntity(Entity entity) {
      if (!(entity instanceof LivingEntity)) {
         return false;
      }

      if (entity instanceof Player player) {
         if (player == ClientAccess.argusclientMinecraft.player && !this.shouldRenderSelf()) {
            return false;
         }

         if (player.isDeadOrDying()) {
            Setting<Boolean> deadOption = this.deadSetting;
            if (!deadOption.getValue()) {
               return false;
            }
         }

         Setting<Boolean> botsOption = this.botsSetting;
         return !botsOption.getValue() && this.isBot(player) ? false : this.playersSetting.getValue();
      } else {
         if (entity instanceof AgeableMob || entity instanceof AbstractFish || entity instanceof Squid) {
            Setting<Boolean> animalsOption = this.animalsSetting;
            if (animalsOption.getValue()) {
               return true;
            }
         }

         if (entity instanceof Enemy) {
            Setting<Boolean> hostilesOption = this.hostilesSetting;
            if (hostilesOption.getValue()) {
               return true;
            }
         }

         if (entity instanceof NeutralMob) {
            Setting<Boolean> neutralsOption = this.neutralsSetting;
            if (neutralsOption.getValue()) {
               return true;
            }
         }

         return false;
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      animationsModule = (AnimationsModule)modules.getModule(AnimationsModule.class);
      freecamModule = (FreecamModule)modules.getModule(FreecamModule.class);
      fontsModule = (FontsModule)modules.getModule(FontsModule.class);
      shader = (ShaderModule)modules.getModule(ShaderModule.class);
      colorsModule = (ColorsModule)modules.getModule(ColorsModule.class);
   }

   public NameTagsModule() {
      super("NameTags", "Draws advanced nametags for living entities.", ModuleCategory.RENDER);
      Predicate<Object> fillVisibility = ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      };
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(657930, true), fillVisibility));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(12425727, true), ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      }));
      this.textSetting = this.add(new ColorSetting("Text", new Color(-1, true), ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      }));
      this.invisiblesSetting = this.add(new ColorSetting("Invisibles", new Color(-65536, true), ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      }));
      this.sneakSetting = this.add(new ColorSetting("Sneak", new Color(-28416, true), ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      }));
      this.eatingSetting = this.add(new ColorSetting("Eating", new Color(-16711936, true), ignored -> {
         Setting<Boolean> option = this.colorsSetting;
         return option.isExpanded();
      }));
      this.overlayMesh = MeshBuffer.create().setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      this.itemOverlayColor = new Color(153, 69, 58);
      this.visibleEntities = new ObjectArrayList();
      this.equipmentItems = new ObjectArrayList(6);
      this.entityRefreshTimer = new ElapsedTimer();
      Setting<Boolean> identitySetting = this.itemNameSetting;
      identitySetting.setConfigName("ItemName");
      identitySetting = this.amountSetting;
      identitySetting.setConfigName("ItemAmount");
      identitySetting = this.colorSetting;
      identitySetting.setConfigName("PingColor");
   }

   public enum DurabilityChoice implements NamedEntry {
      SHOW("Show"),
      ONLY("Only"),
      HIDE("Hide");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      DurabilityChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   enum TextAlignment {
      CENTER,
      LEFT,
      RIGHT;

      public float getOffset(String text, boolean vanillaFont) {
         float width;
         if (vanillaFont) {
            Font font = ClientAccess.argusclientMinecraft.font;
            width = font.width(text);
         } else {
            width = FontRenderer.INSTANCE.getWidth(text);
         }
         return switch (this) {
            case CENTER -> -width * 0.5F;
            case LEFT -> 0.0F;
            case RIGHT -> -width;
         };
      }
   }
}
