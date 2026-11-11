package dev.argus.client.gui;

import dev.argus.client.gui.mixin.EditBoxAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Text field drawn and hit-tested with the menu font. The widget's box is the inner text area;
 * the visible field extends {@link #PAD} units to each side.
 */
final class UiInput extends EditBox {
    static final int PAD = 7;
    private String placeholder;
    private String icon;
    private boolean secret;
    UiInput secret(boolean value) { secret = value; return this; }
    /** Changes the hint shown while the field is empty. */
    UiInput placeholder(String hint) { placeholder = hint; return this; }
    private String displayValue() { return secret ? "•".repeat(getValue().length()) : getValue(); }
    private int displayStart;
    private float focus, caret, placeholderShown = 1;
    private long lastEdit;

    UiInput(int x, int y, int width, int height, String hint) {
        super(Minecraft.getInstance().font, x + PAD, y, width - PAD * 2, height, Component.literal(hint));
        placeholder = hint;
        setBordered(false); setMaxLength(256);
        setResponder(value -> {});
    }

    UiInput withIcon(String icon) {
        this.icon = icon;
        setX(getX() + 10); setWidth(getWidth() - 10);
        return this;
    }

    /** Places the visible field (including padding and icon) at the given box. */
    void place(int x, int y, int width) {
        int inset = PAD + (icon == null ? 0 : 10);
        setX(x + inset); setY(y); setWidth(Math.max(10, width - inset - PAD));
    }

    @Override public void setResponder(java.util.function.Consumer<String> responder) {
        super.setResponder(value -> { lastEdit = System.nanoTime(); responder.accept(value); });
    }

    private float w(String text) { return UiDraw.width(text); }

    private void alignCursor() {
        String text = displayValue(); int cursor = getCursorPosition();
        displayStart = Math.clamp(displayStart, 0, cursor);
        if (w(text.substring(displayStart, cursor)) <= getWidth() - 2) return;
        int low = displayStart, high = cursor;
        while (low < high) { int middle = (low + high) / 2; if (w(text.substring(middle, cursor)) > getWidth() - 2) low = middle + 1; else high = middle; }
        displayStart = low;
    }

    private int displayEnd() {
        String text = displayValue(); int low = displayStart, high = text.length();
        while (low < high) { int middle = (low + high + 1) / 2; if (w(text.substring(displayStart, middle)) <= getWidth() - 2) low = middle; else high = middle - 1; }
        return low;
    }

    private float left() { return getX() - PAD - (icon == null ? 0 : 10); }

    @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        alignCursor();
        String text = displayValue(); int end = displayEnd();
        focus = UiAnim.approach(focus, isFocused() ? 1 : 0, 16);
        boolean hover = isMouseOver(mx, my);
        float x = left(), y = getY() - 4, fw = getWidth() + (getX() - x) + PAD, fh = getHeight() + 8, r = Math.min(7, fh / 2);
        UiDraw.rounded(g, x, y, fw, fh, r, UiDraw.mix(t.crust(), t.mantle(), hover && !isFocused() ? .6f : 0));
        UiDraw.outline(g, x, y, fw, fh, r, .7f, UiDraw.mix(UiDraw.alpha(t.overlay(), .7f), t.accent(), focus));
        if (focus > 0) UiDraw.outline(g, x - 1.5f, y - 1.5f, fw + 3, fh + 3, r + 1.5f, 1.2f, UiDraw.alpha(t.accent(), focus * .18f));
        if (icon != null) UiDraw.icon(g, icon, x + PAD - 1, getY() + (getHeight() - 9) / 2f, 9, UiDraw.mix(t.muted(), t.accent(), focus));
        float cy = getY() + getHeight() / 2f;
        g.enableScissor(getX() - 1, getY() - 2, getX() + getWidth() + 2, getY() + getHeight() + 2);
        // The placeholder fades and drifts aside as typing starts, and back when the box is cleared.
        placeholderShown = UiAnim.approach(placeholderShown, text.isEmpty() ? 1 : 0, text.isEmpty() ? 14 : 24);
        if (placeholderShown > .01f) UiDraw.text(g, UiDraw.fit(placeholder, getWidth()), getX() + (1 - placeholderShown) * 6, cy, UiDraw.alpha(t.muted(), .85f * placeholderShown));
        if (!text.isEmpty()) {
            int highlight = ((EditBoxAccessor) (Object) this).argusclient$getHighlightPosition();
            int first = Math.max(displayStart, Math.min(getCursorPosition(), highlight)), last = Math.min(end, Math.max(getCursorPosition(), highlight));
            if (isFocused() && first < last) {
                float sx = getX() + w(text.substring(displayStart, first)), sw = w(text.substring(first, last));
                UiDraw.rounded(g, sx - .5f, cy - 5.5f, sw + 1, 11, 2, UiDraw.alpha(t.accent(), .32f));
            }
            UiDraw.text(g, text.substring(displayStart, end), getX(), cy, active ? t.text() : t.muted());
        }
        if (isFocused()) {
            // Solid while typing, then a soft pulse instead of a hard blink.
            float since = (System.nanoTime() - lastEdit) / 1e9f;
            float pulse = since < .5f ? 1 : .55f + .45f * (float) Math.cos((since - .5f) * Math.PI * 1.6);
            int cursor = Math.clamp(getCursorPosition(), displayStart, end);
            float target = w(text.substring(displayStart, cursor));
            caret = Math.abs(caret - target) > 40 ? target : UiAnim.approach(caret, target, 30);
            UiDraw.rounded(g, getX() + caret - .3f, cy - 5, 1, 10, .5f, UiDraw.alpha(t.accent(), pulse * focus));
        }
        g.disableScissor();
    }

    @Override public boolean isMouseOver(double mx, double my) { return visible && UiDraw.over(mx, my, left(), getY() - 4, getWidth() + (getX() - left()) + PAD, getHeight() + 8); }

    private int cursorAt(double mx) {
        alignCursor();
        String text = displayValue(); int end = displayEnd(); double point = mx - getX();
        if (point < 0) return Math.max(0, displayStart - 1);
        if (point >= getWidth()) return Math.min(text.length(), end + 1);
        float previous = 0;
        for (int i = displayStart; i < end; i++) {
            float next = w(text.substring(displayStart, i + 1));
            if (point < (previous + next) / 2.0) return i;
            previous = next;
        }
        return end;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!visible || !active || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        setFocused(true); lastEdit = System.nanoTime();
        moveCursorTo(cursorAt(event.x()), (event.modifiers() & 1) != 0);
        if (doubled) { int first = getWordPosition(-1), last = getWordPosition(1); moveCursorTo(first, false); moveCursorTo(last, true); }
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!isFocused() || event.button() != 0) return false;
        moveCursorTo(cursorAt(event.x()), true); return true;
    }
}
