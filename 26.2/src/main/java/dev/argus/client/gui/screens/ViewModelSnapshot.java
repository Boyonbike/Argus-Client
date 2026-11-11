package dev.argus.client.gui.screens;

import dev.argus.client.modules.render.ViewModelModule;

public record ViewModelSnapshot(
   float mainX,
   float mainY,
   float mainZ,
   float offX,
   float offY,
   float offZ,
   float mainScaleX,
   float mainScaleY,
   float mainScaleZ,
   float offScaleX,
   float offScaleY,
   float offScaleZ,
   float mainRotateX,
   float mainRotateY,
   float mainRotateZ,
   float offRotateX,
   float offRotateY,
   float offRotateZ
) {
   static ViewModelSnapshot capture(ViewModelModule module) {
      return new ViewModelSnapshot(
         module.mainXSetting.getValue(),
         module.mainYSetting.getValue(),
         module.mainZSetting.getValue(),
         module.offXSetting.getValue(),
         module.offYSetting.getValue(),
         module.offZSetting.getValue(),
         module.mainScaleXSetting.getValue(),
         module.mainScaleYSetting.getValue(),
         module.mainScaleZSetting.getValue(),
         module.offScaleXSetting.getValue(),
         module.offScaleYSetting.getValue(),
         module.offScaleZSetting.getValue(),
         module.mainRotateXSetting.getValue(),
         module.mainRotateYSetting.getValue(),
         module.mainRotateZSetting.getValue(),
         module.offRotateXSetting.getValue(),
         module.offRotateYSetting.getValue(),
         module.offRotateZSetting.getValue()
      );
   }

   static ViewModelSnapshot createDefaults() {
      return new ViewModelSnapshot(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public void apply(ViewModelModule module) {
      module.mainXSetting.setValue(this.mainX);
      module.mainYSetting.setValue(this.mainY);
      module.mainZSetting.setValue(this.mainZ);
      module.offXSetting.setValue(this.offX);
      module.offYSetting.setValue(this.offY);
      module.offZSetting.setValue(this.offZ);
      module.mainScaleXSetting.setValue(this.mainScaleX);
      module.mainScaleYSetting.setValue(this.mainScaleY);
      module.mainScaleZSetting.setValue(this.mainScaleZ);
      module.offScaleXSetting.setValue(this.offScaleX);
      module.offScaleYSetting.setValue(this.offScaleY);
      module.offScaleZSetting.setValue(this.offScaleZ);
      module.mainRotateXSetting.setValue(this.mainRotateX);
      module.mainRotateYSetting.setValue(this.mainRotateY);
      module.mainRotateZSetting.setValue(this.mainRotateZ);
      module.offRotateXSetting.setValue(this.offRotateX);
      module.offRotateYSetting.setValue(this.offRotateY);
      module.offRotateZSetting.setValue(this.offRotateZ);
   }
}
