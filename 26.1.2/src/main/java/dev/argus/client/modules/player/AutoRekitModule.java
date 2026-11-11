package dev.argus.client.modules.player;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.argus.client.ClientContext;
import dev.argus.mixin.AbstractContainerScreenAccessor;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.modules.player.rekit.*;
import dev.argus.client.settings.*;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.*;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Standalone storage rekit; uses the same module/settings/click APIs as Chest Stealer. */
public final class AutoRekitModule extends Module {
    public enum Mode implements NamedEntry {
        AUTO("Auto"), MANUAL("Manual");
        private final String name;
        Mode(String name) { this.name = name; }
        public String getName() { return name; }
    }
    public enum Counts implements NamedEntry {
        SAVED("Saved counts"), FULL("Full stacks");
        private final String name;
        Counts(String name) { this.name = name; }
        public String getName() { return name; }
    }
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.AUTO));
    public final EnumSetting<Counts> counts = add(new EnumSetting<>("Stack size", Counts.SAVED));
    public final BooleanSetting durability = add(new BooleanSetting("Replace damaged gear", false));
    public final BooleanSetting larger = add(new BooleanSetting("Replace with larger stacks", false));
    public final NumberSetting<Integer> delay = add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
    public final StringSetting activePreset = add(new StringSetting("Active preset", ""));
    private final RekitPresets presets = new RekitPresets();
    private final Map<Integer, ItemStack> decoded = new TreeMap<>();
    private String decodedName;
    private Object decodedRegistries;
    private AbstractContainerMenu currentMenu;
    private boolean manualRequested;
    private long nextTransfer;
    private String status = "";

    public AutoRekitModule() {
        super("AutoRekit", "Refills saved inventory slots from storage using an active rekit preset.", ModuleCategory.PLAYER,
                "rekit", "restock", "inventory refill");
        activePreset.setChangeCallback(this::invalidate);
    }
    public static AutoRekitModule get() {
        return ClientContext.moduleRegistry == null ? null : ClientContext.getModule(AutoRekitModule.class);
    }
    public List<String> presetNames() { return presets.names(); }
    public String status() { return status; }
    public void selectPreset(String name) {
        if (!presets.names().contains(name)) throw new IllegalArgumentException("That preset no longer exists.");
        activePreset.setValue(name); invalidate(); saveConfig();
    }
    public void deletePreset(String name) {
        presets.delete(name);
        if (activePreset.getValue().equals(name)) activePreset.setValue("");
        invalidate(); saveConfig();
    }
    public void capturePreset(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) throw new IllegalStateException("Join a world to save your inventory.");
        var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject slots = RekitInventorySnapshot.capture(mc.player.getInventory()::getItem, ops);
        if (slots.isEmpty()) throw new IllegalArgumentException("Your inventory and equipment are empty.");
        presets.put(name, slots); activePreset.setValue(name); invalidate(); saveConfig();
    }
    private static void saveConfig() {
        if (ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
    }
    private void invalidate() { decodedName = null; manualRequested = false; }
    @Override public void onDisable() { manualRequested = false; currentMenu = null; }
    @Override public JsonElement toJson() {
        JsonObject json = super.toJson().getAsJsonObject();
        json.add("rekitPresets", presets.toJson());
        return json;
    }
    @Override public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject object = json.getAsJsonObject();
        // Partial module presets without this field must not erase saved inventory presets.
        if (object.has("rekitPresets")) presets.load(object.get("rekitPresets"));
        super.fromJson(json); invalidate();
    }
    private void decode(Minecraft mc) {
        Object registries = mc.level.registryAccess();
        String name = activePreset.getValue();
        if (name.equals(decodedName) && decodedRegistries == registries) return;
        decoded.clear(); decodedName = name; decodedRegistries = registries;
        JsonObject slots = presets.get(name);
        if (slots == null) return;
        var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        decoded.putAll(RekitInventorySnapshot.decode(slots, ops));
    }
    public static boolean isStorage(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu
                || menu instanceof DispenserMenu;
    }
    public boolean ownsStorage() {
        Minecraft mc = Minecraft.getInstance();
        return isToggled() && mc.player != null && mc.screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() == mc.player.containerMenu && isStorage(screen.getMenu())
                && presets.names().contains(activePreset.getValue());
    }
    public void requestRekit() {
        if (!isToggled() || mode.getValue() != Mode.MANUAL) return;
        currentMenu = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.containerMenu;
        manualRequested = true; nextTransfer = 0; status = "Rekitting…";
    }
    public static void addContainerButton(Minecraft mc, Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !isStorage(container.getMenu())) return;
        var bounds = (AbstractContainerScreenAccessor) container;
        var position = RekitButtonPlacement.place(width, bounds.getX(), bounds.getY(), bounds.getBgWidth());
        Button button = Button.builder(Component.literal("Rekit"), ignored -> {
            AutoRekitModule module = get(); if (module != null) module.requestRekit();
        }).bounds(position.x(), position.y(), 58, 20).build();
        Screens.getButtons(screen).add(button);
        ScreenEvents.beforeRender(screen).register((s, graphics, mx, my, delta) -> {
            AutoRekitModule module = get();
            button.visible = module != null && module.isToggled() && module.mode.getValue() == Mode.MANUAL;
            button.active = button.visible && module.presets.names().contains(module.activePreset.getValue()) && !module.manualRequested;
            button.setMessage(Component.literal(module != null && module.manualRequested ? "Rekitting…" : "Rekit"));
        });
    }
    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu() != mc.player.containerMenu || !isStorage(screen.getMenu())) {
            currentMenu = null; manualRequested = false; return;
        }
        if (currentMenu != screen.getMenu()) {
            currentMenu = screen.getMenu(); manualRequested = false; nextTransfer = 0;
        }
        if (!isToggled() || mode.getValue() == Mode.MANUAL && !manualRequested) return;
        if (mode.getValue() == Mode.AUTO) manualRequested = false;
        if (!currentMenu.getCarried().isEmpty() || mc.player.isUsingItem() || System.currentTimeMillis() < nextTransfer) return;
        decode(mc);
        if (decoded.isEmpty()) { status = "Select a saved rekit preset."; manualRequested = false; return; }
        var inventory = mc.player.getInventory();
        List<Slot> storage = currentMenu.slots.stream().filter(slot -> slot.container != inventory).toList();
        // One complete transfer per cycle, matching the base client's bounded inventory work.
        for (var entry : decoded.entrySet()) {
            EquipmentSlot armourSlot = RekitInventorySnapshot.armourSlot(entry.getKey());
            if (armourSlot != null) {
                if (transferArmour(mc, storage, armourSlot, entry.getValue())) return;
                continue;
            }
            boolean offhand = entry.getKey() == 40;
            int targetInventorySlot = offhand ? inventory.getSelectedSlot() : entry.getKey();
            Slot destination = currentMenu.slots.stream().filter(slot -> slot.container == inventory
                    && slot.getContainerSlot() == targetInventorySlot).findFirst().orElse(null);
            if (destination == null || !destination.mayPickup(mc.player)) continue;
            ItemStack current = offhand ? inventory.getItem(40) : destination.getItem();
            List<RekitPlanner.Stack> sources = storage.stream().map(slot -> {
                ItemStack stack = slot.getItem();
                if (!slot.mayPickup(mc.player) || !destination.mayPlace(stack)) return RekitPlanner.Stack.EMPTY;
                // Whole swaps must have somewhere to return the displaced item.
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)
                        && (!slot.mayPlace(current) || slot.getMaxStackSize(current) < current.getCount())) return RekitPlanner.Stack.EMPTY;
                return snapshot(stack);
            }).toList();
            // A partial replacement creates a third stack (source remainder + new stack + displaced item).
            int spare = storage.stream().filter(slot -> !slot.hasItem() && slot.mayPlace(current)
                    && slot.getMaxStackSize(current) >= current.getCount()).mapToInt(slot -> slot.index).findFirst().orElse(-1);
            RekitPlanner.Stack saved = snapshot(entry.getValue());
            // Respect destination slot capacity as well as the saved item's own maximum.
            saved = new RekitPlanner.Stack(saved.matchKey(), saved.mergeKey(), saved.count(),
                    Math.min(saved.maximum(), destination.getMaxStackSize(entry.getValue())), saved.durability());
            RekitPlanner.Move move = RekitPlanner.choose(saved, snapshot(current), sources,
                    counts.getValue() == Counts.FULL, larger.getValue(), durability.getValue(), spare >= 0);
            if (move == null) continue;
            Slot source = storage.get(move.source());
            List<RekitTransfer.Click> clicks = offhand
                    ? RekitTransfer.offhandPlan(source.index, destination.index, source.getItem().getCount(), move.amount(), move.replacing(), spare)
                    : RekitTransfer.plan(source.index, destination.index, source.getItem().getCount(), move.amount(), move.replacing(), spare);
            if (clicks.isEmpty()) continue;
            if (ClientContext.inventoryStateManager == null
                    || InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, clicks.size())) return;
            ItemStack originalHand = destination.getItem().copy();
            for (int i = 0; i < clicks.size(); i++) {
                var click = clicks.get(i);
                InventoryUtils.clickSlot(click.slot(), click.button(), click.swap() ? ContainerInput.SWAP : ContainerInput.PICKUP);
                if (offhand && i == 0 && (!sameStack(destination.getItem(), current)
                        || !sameStack(inventory.getItem(40), originalHand))) {
                    nextTransfer = System.currentTimeMillis() + 1000;
                    status = "Offhand staging was blocked; waiting to retry.";
                    return;
                }
            }
            nextTransfer = System.currentTimeMillis() + delay.getValue();
            status = "Rekitting…";
            return;
        }
        manualRequested = false; status = "Finished: filled every slot possible from this container.";
    }
    private boolean transferArmour(Minecraft mc, List<Slot> storage, EquipmentSlot equipmentSlot, ItemStack saved) {
        if (mc.gameMode == null) return false;
        ItemStack worn = mc.player.getItemBySlot(equipmentSlot);
        if (!mc.player.isCreative() && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
        int originalSelected = mc.player.getInventory().getSelectedSlot();
        List<RekitPlanner.Stack> sources = storage.stream().map(slot -> {
            ItemStack candidate = slot.getItem();
            var equippable = candidate.get(DataComponents.EQUIPPABLE);
            // Single pieces avoid vanilla's stacked-equipment fallback, which can drop displaced gear.
            if (candidate.getCount() != 1 || equippable == null || equippable.slot() != equipmentSlot
                    || !equippable.swappable() || !equippable.canBeEquippedBy(mc.player.getType())
                    || candidate.has(DataComponents.CONSUMABLE) || mc.player.getCooldowns().isOnCooldown(candidate)
                    || !slot.mayPickup(mc.player) || !slot.mayPlace(worn) || armourStagingSlot(mc, slot) < 0) return RekitPlanner.Stack.EMPTY;
            return snapshot(candidate);
        }).toList();
        RekitPlanner.Move move = RekitPlanner.choose(snapshot(saved), snapshot(worn), sources, false, false, durability.getValue());
        if (move == null || ClientContext.inventoryStateManager == null
                || InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2)) return false;
        Slot source = storage.get(move.source());
        ItemStack candidate = source.getItem().copy();
        int selected = armourStagingSlot(mc, source);
        if (selected < 0) return false;
        ItemStack originalHand = mc.player.getInventory().getItem(selected).copy();
        boolean equipped = RekitEquipmentTransfer.execute(new RekitEquipmentTransfer.Access() {
            public boolean stage() {
                InventoryUtils.clickSlot(source.index, selected, ContainerInput.SWAP);
                ItemStack staged = mc.player.getInventory().getItem(selected);
                return sameStack(staged, candidate) && sameStack(source.getItem(), originalHand);
            }
            public boolean equip() {
                InventoryUtils.selectHotbarSlot(selected);
                return mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction();
            }
            public void restore() {
                try { InventoryUtils.clickSlot(source.index, selected, ContainerInput.SWAP); }
                finally { InventoryUtils.selectHotbarSlot(originalSelected); }
            }
        });
        nextTransfer = System.currentTimeMillis() + (equipped ? delay.getValue() : Math.max(1000, delay.getValue()));
        status = equipped ? "Rekitting equipment…" : "Equipment action blocked; waiting to retry.";
        return true;
    }
    private static int armourStagingSlot(Minecraft mc, Slot source) {
        var inventory = mc.player.getInventory();
        NoSlowModule noSlow = ClientContext.getModule(NoSlowModule.class);
        boolean selectionCanChange = noSlow == null || !noSlow.isToggled() || !noSlow.isGrimStrictInventoryEnabled();
        return RekitEquipmentTransfer.stagingSlot(inventory.getSelectedSlot(), selectionCanChange, slot -> {
            ItemStack held = inventory.getItem(slot);
            return source.mayPlace(held) && source.getMaxStackSize(held) >= held.getCount();
        });
    }
    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && (a.isEmpty() && b.isEmpty() || ItemStack.isSameItemSameComponents(a, b));
    }
    private record Key(Item item, DataComponentMap components) {}
    public static RekitPlanner.Stack snapshot(ItemStack stack) {
        if (stack.isEmpty()) return RekitPlanner.Stack.EMPTY;
        DataComponentMap components = stack.getComponents();
        var match = DataComponentMap.builder().addAll(components.filter(type -> type != DataComponents.CUSTOM_NAME && type != DataComponents.ITEM_NAME
                && type != DataComponents.DAMAGE)).build();
        return new RekitPlanner.Stack(new Key(stack.getItem(), match), new Key(stack.getItem(), DataComponentMap.builder().addAll(components).build()),
                stack.getCount(), stack.getMaxStackSize(), stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : -1);
    }
}
