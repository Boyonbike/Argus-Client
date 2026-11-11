package dev.argus.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.argus.client.settings.NumberSetting;
import org.junit.jupiter.api.Test;

class SettingsListSliderTest {
    private static NumberSetting<Float> extraReach() {
        return new NumberSetting<>("ExtraReach", 0.0f, 0.0f, Float.MAX_VALUE).withSliderMaximum(1.0);
    }

    @Test
    void sliderFractionUsesTheSliderCeiling() {
        NumberSetting<Float> setting = extraReach();
        setting.setValue(1.0f);

        assertEquals(1.0, SettingsList.fraction(setting), 1e-9);
    }

    @Test
    void draggingMapsToTheSliderCeiling() {
        NumberSetting<Float> setting = extraReach();

        assertEquals(0.5f, (Float) SettingsList.slideValue(setting, 0.5));
        assertEquals(1.0f, (Float) SettingsList.slideValue(setting, 1.0));
    }

    @Test
    void draggingNeverProducesValuesOutsideTheSliderCeiling() {
        NumberSetting<Float> setting = extraReach();

        assertEquals(1.0f, (Float) SettingsList.slideValue(setting, 50.0));
        assertEquals(0.0f, (Float) SettingsList.slideValue(setting, -1.0));
    }

    @Test
    void typedValuesAboveTheSliderCeilingAreStillKept() {
        NumberSetting<Float> setting = extraReach();
        setting.setValue(3.0f);

        assertEquals(3.0f, setting.getValue());
        assertEquals(1.0, SettingsList.fraction(setting), 1e-9);
    }
}
