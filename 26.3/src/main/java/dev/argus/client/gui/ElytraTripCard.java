package dev.argus.client.gui;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.movement.elytratrip.ElytraTripModule;
import dev.argus.client.modules.movement.elytratrip.ElytraTripModule.Destination;
import dev.argus.client.utils.InputKeys;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;

/**
 * ElytraTrip's trip card: where to go, Start, Pause and Stop, how the trip is going, and saved destinations.
 *
 * <pre>
 *  [ Name           ] [ X      ] [ Z      ]  [Here] [Save]
 *  ● Flying, 18,400 blocks left, 3 spare elytras   [Start] [Pause] [Stop]
 *  Turned off for this trip: AutoEat, Offhand
 *  ─────────────────────────────────────────────────────────
 *  Spawn base      100, 200 (1.2k blocks away)        [Go] [Delete]
 * </pre>
 */
final class ElytraTripCard extends InlineCard {
    private static final float INPUTS = 30, CONTROLS = 30, NOTE = 16, LIST_HEAD = 16, ROW = 26;

    private final ElytraTripModule trip;
    private final UiInput name, x, z;
    private String armed = "";
    private long armedAt;

    ElytraTripCard(UiScreen host, ElytraTripModule trip) {
        super(host);
        this.trip = trip;
        name = new UiInput(0, 0, 100, 16, "Name (optional)");
        name.setMaxLength(32);
        x = new UiInput(0, 0, 60, 16, "X");
        z = new UiInput(0, 0, 60, 16, "Z");
        for (UiInput field : List.of(x, z)) field.setMaxLength(9);
        int[] typed = trip.typedDestination();
        if (typed != null) {
            x.setValue(Integer.toString(typed[0]));
            z.setValue(Integer.toString(typed[1]));
        }
        x.setResponder(value -> trip.typeDestination(x.getValue(), z.getValue()));
        z.setResponder(value -> trip.typeDestination(x.getValue(), z.getValue()));
    }

    @Override String title() { return "Trip"; }
    @Override String icon() { return "compass"; }
    @Override String subtitle() { return trip.statusLine(); }

    private boolean noteShown() { return !trip.turnedOff().isEmpty(); }

    @Override float bodyHeight() {
        return INPUTS + CONTROLS + (noteShown() ? NOTE : 0) + LIST_HEAD + Math.max(1, trip.savedDestinations().size()) * ROW + 4;
    }

    private List<UiInput> fields() { return List.of(name, x, z); }

    // ---- fields ------------------------------------------------------------------------------

    private static Integer parse(String text) {
        try { return Integer.parseInt(text.trim()); } catch (NumberFormatException failure) { return null; }
    }

    /** The typed X and Z; throws with a reason the page shows when either isn't a whole number. */
    private int[] typed() {
        Integer typedX = parse(x.getValue()), typedZ = parse(z.getValue());
        if (typedX == null || typedZ == null) throw new IllegalArgumentException("Type whole numbers for X and Z.");
        if (Math.abs(typedX) > 30_000_000 || Math.abs(typedZ) > 30_000_000) throw new IllegalArgumentException("That's outside the world border.");
        return new int[]{typedX, typedZ};
    }

    private void fill(Destination destination) {
        name.setValue(destination.name());
        x.setValue(Integer.toString(destination.x()));
        z.setValue(Integer.toString(destination.z()));
    }

    private void useMyPosition() {
        LocalPlayer player = ClientAccess.argusclientMinecraft.player;
        if (player == null) throw new IllegalStateException("Join a world first.");
        x.setValue(Integer.toString(player.getBlockX()));
        z.setValue(Integer.toString(player.getBlockZ()));
    }

    private void save() {
        int[] at = typed();
        trip.saveDestination(name.getValue(), at[0], at[1]);
        name.setValue("");
    }

    private void start(int[] at) {
        String problem = trip.startProblem();
        if (problem != null) throw new IllegalStateException(problem);
        trip.startTrip(at[0], at[1]);
    }

    @Override void reattach() { for (UiInput field : fields()) host.hostAdd(field); }
    @Override void stopEditing() { for (UiInput field : fields()) field.setFocused(false); }
    @Override void hide() { for (UiInput field : fields()) { field.visible = false; field.setFocused(false); } }

    @Override boolean widgetClicked(MouseButtonEvent event, boolean doubled) {
        for (UiInput field : fields()) {
            if (field.visible && field.isMouseOver(event.x(), event.y())) {
                for (UiInput other : fields()) if (other != field) other.setFocused(false);
                host.hostFocus(field);
                return field.mouseClicked(event, doubled);
            }
        }
        stopEditing();
        return false;
    }

    @Override boolean keyPressed(KeyEvent event) {
        List<UiInput> fields = fields();
        int focused = -1;
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused() && fields.get(i).visible) focused = i;
        if (focused < 0) return false;
        int key = InputKeys.glfw(event);
        if (key == 257 || key == 335) { attempt(this::save, "Saved the destination."); return true; }
        if (key == 258) {
            fields.get(focused).setFocused(false);
            UiInput next = fields.get((focused + 1) % fields.size());
            host.hostFocus(next);
            next.setFocused(true);
            return true;
        }
        return false;
    }

    // ---- drawing -----------------------------------------------------------------------------

    private boolean isArmed(String key) { return key.equals(armed) && System.currentTimeMillis() - armedAt < 4000; }

    private static String distance(Destination destination) {
        LocalPlayer player = ClientAccess.argusclientMinecraft.player;
        if (player == null) return "";
        double blocks = Math.hypot(destination.x() + .5 - player.getX(), destination.z() + .5 - player.getZ());
        return " (" + (blocks >= 10_000 ? String.format("%.1fk", blocks / 1000) : Long.toString(Math.round(blocks))) + " blocks away)";
    }

    private void placeField(GuiGraphicsExtractor g, int mx, int my, UiTheme t, UiInput field, String label, float fx, float cy, float fw) {
        if (label != null) {
            UiDraw.text(g, label, fx, cy, UiDraw.mix(t.textDim(), t.on(), field.isFocused() ? 1 : 0));
            fx += 10; fw -= 10;
        }
        field.place(Math.round(fx), Math.round(cy - 8), Math.round(fw));
        field.visible = inView(cy - 8, 16);
        if (field.visible) field.extractRenderState(g, mx, my, 0); else field.setFocused(false);
    }

    @Override void draw(GuiGraphicsExtractor g, int mx, int my, UiTheme t, float left, float y, float w) {
        // Destination: name, X, Z, then fill from where you are and save to the list.
        float cy = y + INPUTS / 2 + 2, right = left + w;
        float saveW = buttonWidth("Save") + 4, hereW = buttonWidth("Here") + 4;
        button(g, mx, my, t, "save", right - saveW, cy - 9, saveW, 18, "Save", false, "Save these coordinates to the list below (Enter)",
                () -> attempt(this::save, "Saved the destination."));
        right -= saveW + 4;
        button(g, mx, my, t, "here", right - hereW, cy - 9, hereW, 18, "Here", false, "Fill X and Z with where you are now",
                () -> attempt(this::useMyPosition, ""));
        right -= hereW + 6;
        float space = right - left, coordW = Math.max(56, Math.min(96, space * .24f));
        float nameW = Math.max(40, space - coordW * 2 - 12);
        placeField(g, mx, my, t, name, null, left, cy, nameW);
        placeField(g, mx, my, t, x, "X", left + nameW + 6, cy, coordW);
        placeField(g, mx, my, t, z, "Z", left + nameW + coordW + 12, cy, coordW);

        // Trip controls: Start (or Resume), Pause and Stop, with how the trip is going.
        cy = y + INPUTS + CONTROLS / 2;
        right = left + w;
        boolean running = trip.running(), held = trip.paused(), flying = running && !held;
        float stopW = buttonWidth("Stop") + 4, pauseW = buttonWidth("Pause") + 4, startW = buttonWidth("Resume") + 4;
        button(g, mx, my, t, "stop", right - stopW, cy - 9, stopW, 18, "Stop", false,
                running ? "End the trip; the modules it turned off come back on" : "No trip to stop",
                () -> attempt(trip::stop, running ? "Trip stopped." : "No trip is running."));
        right -= stopW + 4;
        button(g, mx, my, t, "pause", right - pauseW, cy - 9, pauseW, 18, "Pause", false,
                flying ? "Hand control back to you; Resume carries on" : "No trip to pause",
                () -> attempt(trip::pause, flying ? "Trip paused." : "No trip to pause."));
        right -= pauseW + 4;
        button(g, mx, my, t, "start", right - startW, cy - 9, startW, 18, held ? "Resume" : "Start", !running || held,
                held ? "Carry on with the trip" : running ? "A trip is running" : "Fly to the X and Z above",
                () -> {
                    if (held) attempt(trip::resume, "Trip resumed.");
                    else if (!running) attempt(() -> start(typed()), "Trip started.");
                    else fail("A trip is already running.");
                });
        right -= startW + 8;
        int dot = flying ? t.on() : held ? UiDraw.mix(t.textDim(), t.on(), .5f) : UiDraw.alpha(t.textDim(), .6f);
        UiDraw.circle(g, left + 6, cy, 3, dot);
        UiDraw.text(g, UiDraw.fit(trip.statusLine(), right - left - 16, UiDraw.SMALL), left + 14, cy, UiDraw.SMALL, flying ? t.text() : t.textDim());

        float listTop = y + INPUTS + CONTROLS;
        if (noteShown()) {
            UiDraw.text(g, UiDraw.fit("Turned off for this trip: " + String.join(", ", trip.turnedOff()), w - 8, UiDraw.SMALL),
                    left + 4, listTop + NOTE / 2, UiDraw.SMALL, t.textDim());
            listTop += NOTE;
        }

        // Saved destinations.
        UiDraw.rect(g, left + 4, listTop - .3f, w - 8, .6f, UiDraw.alpha(t.line(), .4f));
        List<Destination> saved = trip.savedDestinations();
        UiDraw.text(g, "Saved destinations" + (saved.isEmpty() ? "" : " (" + saved.size() + ")"), left + 4, listTop + LIST_HEAD / 2 + 1, UiDraw.SMALL, t.textDim());
        float rowsTop = listTop + LIST_HEAD;
        if (saved.isEmpty()) {
            UiDraw.text(g, UiDraw.fit("None yet: type a name and coordinates above, then Save", w - 8, UiDraw.SMALL), left + 4, rowsTop + ROW / 2, UiDraw.SMALL, UiDraw.alpha(t.textDim(), .8f));
            return;
        }
        int[] typedNow = trip.typedDestination();
        for (int i = 0; i < saved.size(); i++) {
            Destination destination = saved.get(i);
            int index = i;
            String rowKey = "ic.trip.row." + destination.name();
            float ry = rowsTop + UiMotion.rowY(rowKey, "", i * ROW), rcy = ry + ROW / 2;
            float before = UiRender.opacity;
            UiRender.opacity = before * UiMotion.rowAppear(rowKey, "");
            float rowRight = left + w - 2;
            String deleteKey = "del." + destination.name();
            boolean waiting = isArmed(deleteKey);
            float delW = buttonWidth("Confirm") + 4, goW = buttonWidth("Go") + 8, deleteX = rowRight - delW;
            button(g, mx, my, t, deleteKey, deleteX, rcy - 8, delW, 16, waiting ? "Confirm" : "Delete", waiting, "Remove from the list", () -> {
                if (!isArmed(deleteKey)) { armed = deleteKey; armedAt = System.currentTimeMillis(); say("Click Confirm to delete " + destination.name() + "."); return; }
                armed = "";
                attempt(() -> trip.deleteDestination(index), "Deleted " + destination.name() + ".");
            });
            rowRight -= delW + 4;
            button(g, mx, my, t, "go." + destination.name(), rowRight - goW, rcy - 8, goW, 16, "Go", true, "Start a trip here now",
                    () -> attempt(() -> { fill(destination); start(new int[]{destination.x(), destination.z()}); }, "Flying to " + destination.name() + "."));
            rowRight -= goW + 4;
            float rowW = rowRight - left;
            boolean current = typedNow != null && destination.x() == typedNow[0] && destination.z() == typedNow[1];
            boolean hover = hovering(mx, my, left, ry, rowW, ROW - 2);
            float hv = UiAnim.smooth(rowKey + ".h", hover ? 1 : 0, 18), act = UiAnim.smooth(rowKey + ".act", current ? 1 : 0, 16);
            int idle = hv > 0 ? UiDraw.alpha(t.fill(), hv * .8f) : i % 2 == 0 ? UiDraw.alpha(t.fill(), .3f) : UiDraw.alpha(t.fill(), 0f);
            UiDraw.rounded(g, left, ry + 1, rowW, ROW - 2, 6, UiDraw.mix(idle, UiDraw.alpha(t.on(), .14f + hv * .06f), act));
            UiDraw.text(g, UiDraw.fit(destination.name(), rowW - 16), left + 10, rcy - 5, UiDraw.mix(t.text(), t.on(), act));
            UiDraw.text(g, UiDraw.fit(destination.x() + ", " + destination.z() + distance(destination), rowW - 16, UiDraw.SMALL), left + 10, rcy + 6, UiDraw.SMALL, t.textDim());
            UiRender.opacity = before;
            hit(left, ry, rowW, ROW - 2, () -> fill(destination));
            if (waiting) UiMotion.drain(g, deleteX + 6, rcy + 6, delW - 12, System.nanoTime() - (System.currentTimeMillis() - armedAt) * 1_000_000L, 4_000_000_000L, UiDraw.alpha(t.on(), .8f));
        }
    }
}
