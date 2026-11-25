package dev.argus.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers a server's /sethome override dialog from its show_dialog packet without opening the screen. Shared by
 * AutoSetHome and ElytraTrip; holds no state.
 */
public final class HomeOverrideDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeOverrideDialog.class);

    private HomeOverrideDialog() {
    }

    /** The button whose label matches {@code buttonPhrase}, if the dialog's title, body or buttons match {@code dialogPhrase}. */
    public static ActionButton overrideButton(Dialog dialog, String dialogPhrase, String buttonPhrase) {
        if (!(dialog instanceof SimpleDialog simple)) return null;
        if (dialogPhrase.isBlank() || buttonPhrase.isBlank()) return null;

        List<String> texts = new ArrayList<>();
        for (DialogBody body : dialog.common().body()) {
            if (body instanceof PlainMessage message) texts.add(message.contents().getString());
        }
        for (ActionButton button : simple.mainActions()) texts.add(button.button().label().getString());
        if (!HomeOverride.isHomeDialog(dialog.common().title().getString(), texts, dialogPhrase)) return null;

        for (ActionButton button : simple.mainActions()) {
            if (HomeOverride.isOverrideButton(button.button().label().getString(), buttonPhrase)) return button;
        }
        return null;
    }

    /** Sends what pressing the button would send, without a screen. Mirrors DialogScreen.handleDialogClickEvent. */
    public static boolean press(ClientPacketListener connection, ActionButton button) {
        Optional<ClickEvent> click = button.action().flatMap(action -> action.createAction(Map.of()));
        if (click.isEmpty()) return false;
        switch (click.get()) {
            case ClickEvent.Custom custom -> connection.send(new ServerboundCustomClickActionPacket(custom.id(), custom.payload()));
            case ClickEvent.RunCommand run -> connection.sendCommand(Commands.trimOptionalPrefix(run.command()));
            default -> {
                LOGGER.warn("Home override button uses unsupported action {}", click.get().action());
                return false;
            }
        }
        return true;
    }
}
