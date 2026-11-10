package dev.argus.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumberSettingSliderTest {
    @Test
    void sliderMaximumDefaultsToTheHardMaximum() {
        NumberSetting<Integer> setting = new NumberSetting<>("Number", 5, 0, 10);

        assertEquals(10.0, setting.getSliderMaximumValue());
    }

    @Test
    void sliderMaximumCanBeLowerThanTheHardMaximum() {
        NumberSetting<Float> setting = new NumberSetting<>("Extra", 0.0f, 0.0f, 4.0f).withSliderMaximum(1.0);

        assertEquals(1.0, setting.getSliderMaximumValue());
        setting.setValue(3.0f);
        assertEquals(3.0f, setting.getValue());
    }

    @Test
    void sliderMaximumStaysInsideTheValueRange() {
        NumberSetting<Float> setBelowMinimum = new NumberSetting<>("Number", 2.0f, 0.0f, 4.0f).withSliderMaximum(-1.0);
        NumberSetting<Float> setAboveMaximum = new NumberSetting<>("Number", 2.0f, 0.0f, 4.0f).withSliderMaximum(9.0);

        assertEquals(0.0, setBelowMinimum.getSliderMaximumValue());
        assertEquals(4.0, setAboveMaximum.getSliderMaximumValue());
    }

    @Test
    void sliderFractionClampsToTheSliderRange() {
        NumberSetting<Float> setting = new NumberSetting<>("Extra", 0.0f, 0.0f, 4.0f).withSliderMaximum(1.0);

        setting.setValue(0.5f);
        assertEquals(0.5, setting.sliderFraction(), 1e-9);

        setting.setValue(3.0f);
        assertEquals(1.0, setting.sliderFraction(), 1e-9);
    }

    @Test
    void valueAtSliderFractionMapsAndClamps() {
        NumberSetting<Float> setting = new NumberSetting<>("Extra", 0.0f, 0.0f, 4.0f).withSliderMaximum(1.0);

        assertEquals(0.0, setting.valueAtSliderFraction(-1.0), 1e-9);
        assertEquals(0.5, setting.valueAtSliderFraction(0.5), 1e-9);
        assertEquals(1.0, setting.valueAtSliderFraction(2.0), 1e-9);
    }

    @Test
    void hardMaximumStillClampsTypedValues() {
        NumberSetting<Float> setting = new NumberSetting<>("Extra", 0.0f, 0.0f, 4.0f).withSliderMaximum(1.0);

        setting.setValue(9.0f);
        assertEquals(4.0f, setting.getValue());
    }
}
