package dev.argus.client.modules.client;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PermanentModule;
import dev.argus.client.modules.render.SearchModule;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import java.awt.Color;

public class ColorsModule extends PermanentModule {
   private static SearchModule searchModule = ClientContext.getModule(SearchModule.class);
   private final Setting<Float> rainbowSpeedSetting = this.add(new NumberSetting<>("RainbowSpeed", 1.0F, 0.1F, 10.0F));
   public final Setting<Color> themeColorSetting = this.add(new ColorSetting("ThemeColor", new Color(-3697420, true)));
   public final Setting<Color> friendColorSetting = this.add(new ColorSetting("FriendColor", new Color(-16711681, true)));
   public final Setting<Color> enemyColorSetting = this.add(new ColorSetting("EnemyColor", new Color(-4980736, true)));
   public final Setting<ColorsSchemeChoice> schemeSetting = this.add(new EnumSetting<>("Scheme", ColorsSchemeChoice.NORMAL));

   public float getRainbowPeriod() {
      return 1.0F / this.rainbowSpeedSetting.getValue();
   }

   public static void bindSearchModule(ModuleRegistry registry) {
      searchModule = (SearchModule)registry.getModule(SearchModule.class);
   }

   public ColorsModule() {
      super("Colors", "Manages the client's color system.", ModuleCategory.CLIENT);
      this.schemeSetting.setChangeCallback(() -> {
         if (searchModule.isToggled()) {
            searchModule.restartSearch();
         }
      });
   }
}
