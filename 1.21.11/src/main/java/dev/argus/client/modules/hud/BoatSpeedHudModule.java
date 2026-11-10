package dev.argus.client.modules.hud;

import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.movement.BoatPhaseModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Boat Phase's speedometer as a HUD element: speed sent to the server, the server's echo, the travel mode and the ride
 * status. Lines show only while you drive a boat with Boat Phase on, and each can be moved on its own in the HUD editor.
 */
public class BoatSpeedHudModule extends HudWidgetModule {
    private final Setting<Boolean> showSent;
    private final Setting<Boolean> showEcho;
    private final Setting<Boolean> showMode;
    private final Setting<Boolean> showStatus;

    public BoatSpeedHudModule() {
        super("Boat Speed", new String[] {"boat-speed", "boat-phase", "speedometer", "ferryman"});
        Setting<Boolean> tweaks = add(new BooleanSetting("Tweaks", false).markAsGroup());
        tweaks.expanded = true;
        showSent = add(new BooleanSetting("Sent", true));
        showEcho = add(new BooleanSetting("Echo", true));
        showMode = add(new BooleanSetting("Mode", true));
        showStatus = add(new BooleanSetting("Status", true));

        List<HudTextRow> rows = new ArrayList<>();
        rows.add(line("Sent", showSent, BoatPhaseModule::sentReadout).withKey("sent"));
        rows.add(line("Echo", showEcho, BoatPhaseModule::echoReadout).withKey("echo"));
        rows.add(line("Mode", showMode, BoatPhaseModule::modeReadout).withKey("mode"));
        rows.add(line("Status", showStatus, BoatPhaseModule::statusReadout).withKey("status"));
        setRenderer(new HudTextListRenderer(this, rows).splittable());
        getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
    }

    /** A label in the HUD colour followed by a white value; it keeps its last text while fading out. */
    private static HudTextRow line(String label, Setting<Boolean> shown, Function<BoatPhaseModule, String> value) {
        String[] last = {""};
        return new HudTextRow(() -> {
            BoatPhaseModule boat = BoatPhaseModule.active();
            if (boat != null && boat.hasReadout()) last[0] = value.apply(boat);
            return Component.literal(label + " " + ChatFormatting.WHITE + last[0]);
        }, () -> {
            BoatPhaseModule boat = BoatPhaseModule.active();
            return shown.getValue() && boat != null && boat.hasReadout();
        });
    }
}
