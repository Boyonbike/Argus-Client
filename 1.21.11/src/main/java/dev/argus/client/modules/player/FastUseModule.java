package dev.argus.client.modules.player;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class FastUseModule extends Module {
   public enum Mode implements NamedEntry {
      ALL("All"),
      SOME("Some");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   public final Setting<Mode> modeSetting = this.add(new EnumSetting<>("Mode", Mode.ALL));
   public final Setting<Set<Item>> itemsSetting = this.add(new RegistrySetSetting<Item>("Items", BuiltInRegistries.ITEM));
   public final Setting<Boolean> blocksSetting = this.add(new BooleanSetting("Blocks", false));
   public final Setting<Integer> cooldownSetting = this.add(new NumberSetting<>("Cooldown", 0, 0, 4));
   public final Setting<Boolean> preventContainerOpenSetting = this.add(new BooleanSetting("PreventContainerOpen", true));

   public FastUseModule() {
      super("FastUse", "Uses items at high speed without opening containers you just placed.", ModuleCategory.PLAYER);
      this.itemsSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SOME);
      this.blocksSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SOME);
   }

   public boolean appliesTo(ItemStack stack) {
      return this.modeSetting.getValue() == Mode.ALL
         || this.itemsSetting.getValue().contains(stack.getItem())
         || (this.blocksSetting.getValue() && stack.getItem() instanceof BlockItem);
   }

   public int cooldownFor(ItemStack stack) {
      return this.appliesTo(stack) ? this.cooldownSetting.getValue() : 4;
   }
}
