package dev.argus.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.argus.client.config.EnumJsonConverter;
import java.util.function.Predicate;

public final class EnumSetting<E extends Enum<E> & NamedEntry> extends Setting<E> implements CyclingTextSetting {
   private final EnumJsonConverter<E> converter;

   public EnumSetting(String name, E defaultValue, Predicate<? super E> visibility) {
      super(name, defaultValue, visibility);
      this.converter = new EnumJsonConverter<>(defaultValue.getDeclaringClass());
      if (!(defaultValue instanceof NamedEntry)) {
         throw new IllegalArgumentException();
      }
   }

   public EnumSetting(String name, E defaultValue) {
      super(name, defaultValue);
      this.converter = new EnumJsonConverter<>(defaultValue.getDeclaringClass());
      if (!(defaultValue instanceof NamedEntry)) {
         throw new IllegalArgumentException();
      }
   }

   @Override
   public boolean matchesText(String text) {
      return this.toJson().getAsString().equalsIgnoreCase(text);
   }

   @Override
   public void fromJson(JsonElement json) {
      EnumJsonConverter<E> converter = this.converter;
      this.setValue(converter.fromJson(json));
   }

   @Override
   public JsonElement toJson() {
      EnumJsonConverter<E> converter = this.converter;
      E value = this.getValue();
      return converter.toJson(value);
   }

   @Override
   public void setValueFromText(String text) {
      this.fromJson(new JsonPrimitive(text));
   }

   @Override
   public void parseValue(String text) {
      CyclingTextSetting.super.parseValue(text);
   }
}
