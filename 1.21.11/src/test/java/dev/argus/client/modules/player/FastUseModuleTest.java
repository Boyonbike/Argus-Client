package dev.argus.client.modules.player;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FastUseModuleTest {
    private FastUseModule module;

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void setUp() {
        module = new FastUseModule();
    }

    @Test void defaultsMatchMeteorSpeedWithContainerSafetyOn() {
        assertEquals(FastUseModule.Mode.ALL, module.modeSetting.getValue());
        assertEquals(0, module.cooldownSetting.getValue());
        assertFalse(module.blocksSetting.getValue());
        assertTrue(module.preventContainerOpenSetting.getValue());
        assertTrue(module.itemsSetting.getValue().isEmpty());
    }

    @Test void allModeAppliesTheConfiguredCooldownToEveryItem() {
        module.cooldownSetting.setValue(1);
        assertEquals(1, module.cooldownFor(new ItemStack(Items.STONE)));
        assertEquals(1, module.cooldownFor(new ItemStack(Items.APPLE)));
        assertTrue(module.appliesTo(new ItemStack(Items.STONE)));
        assertTrue(module.appliesTo(new ItemStack(Items.APPLE)));
    }

    @Test void someModeOnlyAppliesToListedItems() {
        module.modeSetting.setValue(FastUseModule.Mode.SOME);
        module.itemsSetting.getValue().add(Items.APPLE);
        assertTrue(module.appliesTo(new ItemStack(Items.APPLE)));
        assertEquals(0, module.cooldownFor(new ItemStack(Items.APPLE)));
        assertFalse(module.appliesTo(new ItemStack(Items.STONE)));
        assertEquals(4, module.cooldownFor(new ItemStack(Items.STONE)));
    }

    @Test void someModeBlocksToggleIncludesBlockItems() {
        module.modeSetting.setValue(FastUseModule.Mode.SOME);
        module.blocksSetting.setValue(true);
        assertTrue(module.appliesTo(new ItemStack(Items.STONE)));
        assertEquals(0, module.cooldownFor(new ItemStack(Items.STONE)));
        assertFalse(module.appliesTo(new ItemStack(Items.APPLE)));
        assertEquals(4, module.cooldownFor(new ItemStack(Items.APPLE)));
    }

    @Test void emptySelectionLeavesVanillaCooldown() {
        module.modeSetting.setValue(FastUseModule.Mode.SOME);
        assertFalse(module.appliesTo(new ItemStack(Items.STONE)));
        assertFalse(module.appliesTo(new ItemStack(Items.APPLE)));
        assertEquals(4, module.cooldownFor(new ItemStack(Items.STONE)));
        assertEquals(4, module.cooldownFor(new ItemStack(Items.APPLE)));
    }

    @Test void chestLikeBlocksAreContainersWithoutAMenuProvider() {
        assertTrue(FastUseModule.isContainer(Blocks.CHEST.defaultBlockState(), false));
        assertTrue(FastUseModule.isContainer(Blocks.ENDER_CHEST.defaultBlockState(), false));
    }

    @Test void menuProviderBlocksAreContainers() {
        assertTrue(FastUseModule.isContainer(Blocks.SHULKER_BOX.defaultBlockState(), true));
        assertFalse(FastUseModule.isContainer(Blocks.STONE.defaultBlockState(), false));
    }
}
