package dev.argus.client.modules.player.rekit;

import dev.argus.client.modules.player.AutoRekitModule;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RekitItemMatchingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // The core enum codec reads launch flags; unit tests have no running Fabric launcher.
        net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE.setGameProvider(new net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider() {
            @Override public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }
        });
    }
    @Test void renamedItemsMatchThePresetButKeepDistinctStackability() {
        ItemStack a = new ItemStack(Items.STONE, 12), b = new ItemStack(Items.STONE, 64);
        a.set(DataComponents.CUSTOM_NAME, Component.literal("First"));
        b.set(DataComponents.CUSTOM_NAME, Component.literal("Second"));
        var saved = AutoRekitModule.snapshot(a);
        var source = AutoRekitModule.snapshot(b);
        assertEquals(saved.matchKey(), source.matchKey());
        assertNotEquals(saved.mergeKey(), source.mergeKey());
        assertEquals(new RekitPlanner.Move(0, 64, true), RekitPlanner.choose(saved, saved, List.of(source), true, true, false));
    }
    @Test void damageIsIgnoredForIdentityWhileOtherComponentsStayExact() {
        ItemStack a = new ItemStack(Items.DIAMOND_PICKAXE), b = a.copy();
        a.setDamageValue(100); b.setDamageValue(10);
        assertEquals(AutoRekitModule.snapshot(a).matchKey(), AutoRekitModule.snapshot(b).matchKey());
        b.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        assertNotEquals(AutoRekitModule.snapshot(a).matchKey(), AutoRekitModule.snapshot(b).matchKey());
    }
    @Test void moduleConfigurationPreservesPresetsAndActiveSelection() {
        var module = new AutoRekitModule();
        var data = com.google.gson.JsonParser.parseString("{\"rekitPresets\":{\"Building\":{\"0\":{\"id\":\"minecraft:stone\",\"count\":20}}}}");
        module.fromJson(data);
        module.activePreset.setValue("Building");
        var loaded = new AutoRekitModule();
        loaded.fromJson(module.toJson());
        assertEquals(List.of("Building"), loaded.presetNames());
        assertEquals("Building", loaded.activePreset.getValue());
        loaded.fromJson(new com.google.gson.JsonObject());
        assertEquals(List.of("Building"), loaded.presetNames());
    }
    @Test void capturesAndReloadsAllFourWornArmourSlotsAndOffhand() {
        var stacks = new java.util.HashMap<Integer, ItemStack>();
        stacks.put(0, new ItemStack(Items.STONE, 20));
        stacks.put(36, new ItemStack(Items.DIAMOND_BOOTS));
        stacks.put(37, new ItemStack(Items.DIAMOND_LEGGINGS));
        stacks.put(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
        stacks.put(39, new ItemStack(Items.DIAMOND_HELMET));
        stacks.put(40, new ItemStack(Items.TOTEM_OF_UNDYING));
        var ops = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)
                .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var captured = RekitInventorySnapshot.capture(i -> stacks.getOrDefault(i, ItemStack.EMPTY), ops);
        var decoded = RekitInventorySnapshot.decode(captured, ops);
        assertEquals(java.util.Set.of(0, 36, 37, 38, 39, 40), decoded.keySet());
        assertEquals(Items.DIAMOND_HELMET, decoded.get(39).getItem());
        assertEquals(Items.TOTEM_OF_UNDYING, decoded.get(40).getItem());
        assertEquals(20, decoded.get(0).getCount());
    }
    @Test void equipmentIndicesMatchMinecraftsInventoryMapping() {
        for (int index = 36; index < 40; index++)
            assertEquals(net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.get(index), RekitInventorySnapshot.armourSlot(index));
        assertEquals(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.get(40));
    }
    @Test void oldPresetsWithoutEquipmentStillDecodeWithoutInventingSlots() {
        var ops = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)
                .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var slots = com.google.gson.JsonParser.parseString("{\"0\":{\"id\":\"minecraft:stone\",\"count\":20}}").getAsJsonObject();
        assertEquals(java.util.Set.of(0), RekitInventorySnapshot.decode(slots, ops).keySet());
    }
    @Test void ordinaryCopiesHaveEqualStackabilityKeys() {
        ItemStack a = new ItemStack(Items.STONE, 12);
        assertEquals(AutoRekitModule.snapshot(a).mergeKey(), AutoRekitModule.snapshot(a.copy()).mergeKey());
    }
}
