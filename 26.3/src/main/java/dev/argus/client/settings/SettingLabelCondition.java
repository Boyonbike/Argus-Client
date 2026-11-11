package dev.argus.client.settings;

public enum SettingLabelCondition {
   MIN {
      @Override
      public boolean matches(Setting<?> setting) {
         Object value = setting.getValue();
         return value.equals(setting.getMinimumValue());
      }
   },
   MAX {
      @Override
      public boolean matches(Setting<?> setting) {
         Object value = setting.getValue();
         return value.equals(setting.getMaximumValue());
      }
   };

   public boolean matches(Setting<?> setting) {
      return false;
   }
}
