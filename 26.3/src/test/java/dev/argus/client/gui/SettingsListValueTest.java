package dev.argus.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.SettingLabelCondition;
import org.junit.jupiter.api.Test;

class SettingsListValueTest {
   @Test
   void sliderEndWithABoundaryLabelShowsTheLabel() {
      NumberSetting<Double> boost = new NumberSetting<>("Boost H Speed", 301.0, 0.0, 301.0);
      boost.setBoundaryLabel("MAX", SettingLabelCondition.MAX);
      assertEquals("MAX", SettingsList.value(boost));
      boost.setValue(300.0);
      assertEquals("300", SettingsList.value(boost));
   }
}
