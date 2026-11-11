package dev.argus.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NavbarAnimationTest {
    @Test void reopeningStartsCollapsedEvenAfterALongFrame() {
        var animation = new NavbarAnimation();
        animation.restore(true);
        animation.advance(.1f);
        assertEquals(0, animation.progress(), .0001f, "The first visible frame must show the collapsed face");
        animation.advance(.1f);
        assertEquals(0, animation.progress(), .0001f, "Let the menu fade in before expanding");
        animation.advance(.12f);
        assertTrue(animation.progress() > 0 && animation.progress() < .3f);
        animation.advance(.5f);
        assertEquals(1, animation.progress(), .0001f);
    }

    @Test void closedPreferenceStaysCollapsed() {
        var animation = new NavbarAnimation();
        animation.restore(false);
        for (int i = 0; i < 20; i++) animation.advance(.1f);
        assertFalse(animation.open());
        assertEquals(0, animation.progress());
    }

    @Test void eachFreshOpenReplaysButPageChangesKeepProgress() {
        var animation = new NavbarAnimation();
        animation.restore(true);
        for (int i = 0; i < 20; i++) animation.advance(.1f);
        assertEquals(1, animation.progress(), .0001f);
        animation.advance(.016f);
        assertEquals(1, animation.progress(), .0001f);
        animation.restore(true);
        animation.advance(.1f);
        assertEquals(0, animation.progress());
    }

    @Test void clickingTheFaceAnimatesAndCanReverseDirection() {
        var animation = new NavbarAnimation();
        animation.setOpen(true);
        animation.advance(.1f);
        assertTrue(animation.progress() > 0 && animation.progress() < 1);
        animation.setOpen(false);
        animation.advance(.3f);
        assertEquals(0, animation.progress(), .0001f);
    }
}
