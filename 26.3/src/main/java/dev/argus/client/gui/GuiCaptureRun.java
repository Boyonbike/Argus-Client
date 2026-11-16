package dev.argus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

/**
 * Developer tool: with {@code -Dargus.capture=true} the client opens each reworked GUI screen in dark and light mode,
 * saves a screenshot of each to {@code screenshots/rework/}, restores the preferences it changed and quits.
 * Without the property it does nothing. Used to review the GUI rework without driving the game by hand.
 */
public final class GuiCaptureRun {
    private GuiCaptureRun() {}

    /** Ticks to let a screen's open animation settle before capturing it. */
    private static final int SETTLE = 40;

    private record Step(String name, Runnable setup) {}

    public static void installIfRequested() {
        if (!Boolean.getBoolean("argus.capture")) return;
        ClientTickEvents.END_CLIENT_TICK.register(new Runner()::tick);
    }

    private static final class Runner {
        private final List<Step> steps = new ArrayList<>();
        private boolean done;
        private int overlayTicks;
        private int index = -1, wait = 200;  // let the title screen finish loading first
        private Screen title;
        private GuiPreferences.Mode savedMode;
        private boolean savedGrain, savedBevels, savedSmooth;
        private int savedCorner;

        void tick(Minecraft mc) {
            if (done) return;
            // The startup loading screen: captured once it has been up for a second (reloads fade in, startup doesn't).
            if (index == -1 && mc.gui.overlay() != null) {
                if (++overlayTicks == 20) capture(mc, "loader");
                return;
            }
            if (index == -1 && mc.gui.screen() == null) return;
            if (--wait > 0) return;
            if (index >= steps.size()) { done = true; finish(mc); return; }
            if (index == -1) begin(mc);
            else capture(mc, steps.get(index).name());
            // After the last capture, wait a moment so its file is written before the game quits.
            if (++index >= steps.size()) { wait = 20; return; }
            steps.get(index).setup().run();
            wait = SETTLE;
        }

        private void begin(Minecraft mc) {
            title = mc.gui.screen();
            var p = GuiState.preferences();
            savedMode = p.mode; savedGrain = p.grain; savedBevels = p.bevels; savedCorner = p.corner; savedSmooth = p.smoothCorners;
            for (var mode : GuiPreferences.Mode.values()) {
                String m = mode.name().toLowerCase(java.util.Locale.ROOT);
                Runnable theme = () -> { plain(); GuiState.preferences().mode = mode; };
                steps.add(new Step(m + "-modules", () -> { theme.run(); mc.gui.setScreen(new ModulesScreen(title)); }));
                steps.add(new Step(m + "-settings-appearance", () -> { theme.run(); mc.gui.setScreen(new SettingsScreen(title, SettingsScreen.APPEARANCE, null)); }));
                steps.add(new Step(m + "-settings-presets", () -> { theme.run(); mc.gui.setScreen(new SettingsScreen(title, SettingsScreen.PRESETS, null)); }));
                var module = firstConfigurableModule();
                if (module != null)
                    steps.add(new Step(m + "-module-page", () -> { theme.run(); mc.gui.setScreen(new ModuleSettingsScreen(title, module, null)); }));
                steps.add(new Step(m + "-hud-editor", () -> { theme.run(); mc.gui.setScreen(new ArgusHudEditorScreen()); }));
            }
            steps.add(new Step("dark-surface-on-appearance", () -> {
                var p2 = GuiState.preferences();
                p2.mode = GuiPreferences.Mode.DARK; p2.grain = true; p2.bevels = true; p2.corner = GuiPreferences.MAX_CORNER;
                mc.gui.setScreen(new SettingsScreen(title, SettingsScreen.APPEARANCE, null));
            }));
            for (var mode : GuiPreferences.Mode.values()) {
                String m = mode.name().toLowerCase(java.util.Locale.ROOT);
                Runnable smooth = () -> { plain(); GuiState.preferences().mode = mode; GuiState.preferences().smoothCorners = true; };
                steps.add(new Step(m + "-smooth-modules", () -> { smooth.run(); mc.gui.setScreen(new ModulesScreen(title)); }));
                steps.add(new Step(m + "-smooth-appearance", () -> { smooth.run(); mc.gui.setScreen(new SettingsScreen(title, SettingsScreen.APPEARANCE, null)); }));
            }
        }

        /** The default look, so each screenshot shows only what its step changes. */
        private void plain() {
            var p = GuiState.preferences();
            p.grain = false; p.bevels = false; p.corner = 2; p.smoothCorners = false;
        }

        private static dev.argus.client.modules.Module firstConfigurableModule() {
            for (String name : List.of("AutoCrystal", "KillAura", "ESP", "Nametags")) {
                var module = GuiState.module(name);
                if (module != null) return module;
            }
            return null;
        }

        private void capture(Minecraft mc, String name) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots/rework/capture-" + name + ".png");
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try (NativeImage owned = image) {
                    Files.createDirectories(out.getParent());
                    owned.writeToFile(out);
                } catch (Exception failure) {
                    dev.argus.client.ArgusClient.LOGGER.error("GUI capture failed for {}", name, failure);
                }
            });
            dev.argus.client.ArgusClient.LOGGER.info("GUI capture: {}", out);
        }

        private void finish(Minecraft mc) {
            var p = GuiState.preferences();
            p.mode = savedMode; p.grain = savedGrain; p.bevels = savedBevels; p.corner = savedCorner; p.smoothCorners = savedSmooth;
            GuiState.save();
            dev.argus.client.ArgusClient.LOGGER.info("GUI capture finished");
            mc.stop();
        }
    }
}
