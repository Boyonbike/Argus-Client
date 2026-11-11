package dev.argus.client.utils.inventory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public abstract class ItemRegistryJsonAdapter implements JsonDeserializer<Object>, JsonSerializer<Object> {
   protected Object resolveItem(String serializedIdentifier) {
      DefaultedRegistry<Item> items = BuiltInRegistries.ITEM;
      Identifier identifier = Identifier.parse(serializedIdentifier);
      return items.getValue(identifier);
   }

   public Object deserialize(JsonElement json, Type targetType, JsonDeserializationContext context) {
      String serializedIdentifier = json.getAsString();
      return this.resolveItem(serializedIdentifier);
   }

   public JsonElement serialize(Object value, Type sourceType, JsonSerializationContext context) {
      Optional<String> identifier = this.getItemIdentifier(value);
      Function<String, JsonElement> toPrimitive = JsonPrimitive::new;
      Optional<JsonElement> element = identifier.map(toPrimitive);
      JsonNull nullValue = JsonNull.INSTANCE;
      return element.orElse(nullValue);
   }

   protected Optional<String> getItemIdentifier(Object value) {
      Registry<Item> items = BuiltInRegistries.ITEM;
      Item item = (Item)value;
      Optional<ResourceKey<Item>> resourceKey = items.getResourceKey(item);
      Function<ResourceKey<Item>, String> toIdentifier = key -> {
         Identifier identifier = key.identifier();
         return identifier.toString();
      };
      return resourceKey.map(toIdentifier);
   }

   public static final class BlockItemAdapter extends ItemRegistryJsonAdapter {
   }

   public static final class ItemAdapter extends ItemRegistryJsonAdapter {
   }
}
