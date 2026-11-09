package dev.argus.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Predicate;

public final class NumberSetting<N extends Number> extends Setting<N> implements CyclingTextSetting {
   private String unit = "";

   @Override
   public boolean matchesText(String text) {
      try {
         double currentValue = this.getValue().doubleValue();
         return Double.compare(currentValue, Double.parseDouble(text)) == 0;
      } catch (NumberFormatException invalidNumber) {
         return false;
      }
   }

   public NumberSetting(String name, N defaultValue, N minimumValue, N maximumValue, Predicate<? super N> visibility) {
      super(name, defaultValue, minimumValue, maximumValue, visibility);
   }

   public NumberSetting(String name, N defaultValue, N minimumValue, N maximumValue) {
      super(name, defaultValue, minimumValue, maximumValue);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (this.getValue() instanceof Double) {
         this.setNumber(json.getAsDouble());
      } else if (this.getValue() instanceof Integer) {
         this.setNumber(json.getAsInt());
      } else if (this.getValue() instanceof Float) {
         this.setNumber(json.getAsFloat());
      }
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.getValue());
   }

   @Override
   public void setValueFromText(String text) {
      if (this.getValue() instanceof Double) {
         this.setNumber(Double.parseDouble(text));
      } else if (this.getValue() instanceof Integer) {
         this.setNumber(Integer.parseInt(text));
      } else if (this.getValue() instanceof Float) {
         this.setNumber(Float.parseFloat(text));
      }
   }

   public void setNumber(Number number) {
      super.setValue(this.convertToValueType(number));
   }

   private N convertToValueType(Number number) {
      Number converted;
      if (this.getValue() instanceof Double) {
         converted = number.doubleValue();
      } else if (this.getValue() instanceof Integer) {
         converted = number.intValue();
      } else if (this.getValue() instanceof Float) {
         converted = number.floatValue();
      } else {
         converted = number.longValue();
      }

      return (N)this.getValue().getClass().cast(converted);
   }

   public String getUnit() {
      return this.unit;
   }

   @Override
   public void parseValue(String text) {
      CyclingTextSetting.super.parseValue(text);
   }

   public void setValue(N value) {
      this.setNumber(value);
   }

   public NumberSetting<N> withUnit(String unit) {
      this.unit = unit;
      return this;
   }
}
