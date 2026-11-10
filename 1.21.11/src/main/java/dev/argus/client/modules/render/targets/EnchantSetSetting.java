package dev.argus.client.modules.render.targets;

import com.google.gson.JsonElement;
import dev.argus.client.ClientAccess;
import dev.argus.client.settings.SetSetting;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;

/**
 * A list of enchantment ids (without namespace, for example {@code mending}). Enchantments are data-driven, so the
 * choices come from the server's registry when connected; anything can still be typed in.
 */
public final class EnchantSetSetting extends SetSetting<String> {
   public EnchantSetSetting(String name, Predicate<? super Set<String>> visibility, String... defaultEntries) {
      super(name, visibility, defaultEntries);
   }

   @Override
   public String parseEntry(String text) {
      String id = ItemRules.normalize(text);
      return id.isEmpty() ? null : id;
   }

   @Override
   public Collection<String> getAvailableEntries() {
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.getConnection();
      if (connection == null) {
         return List.of();
      }

      try {
         return connection.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .listElementIds()
            .map(key -> key.identifier().getPath())
            .sorted()
            .toList();
      } catch (RuntimeException ignored) {
         return List.of();
      }
   }

   @Override
   public String formatEntry(String entry) {
      return entry;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         Set<String> values = this.getValue();
         values.clear();
         for (JsonElement entry : json.getAsJsonArray()) {
            String id = this.parseEntry(entry.getAsString());
            if (id != null) {
               values.add(id);
            }
         }
      }
   }
}
