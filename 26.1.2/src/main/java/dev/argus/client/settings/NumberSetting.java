package dev.argus.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Predicate;

public final class NumberSetting<N extends Number> extends Setting<N> implements CyclingTextSetting {
   private String unit = "";
   private double sliderMaximum = Double.NaN;

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

   public NumberSetting<N> withSliderMaximum(double sliderMaximum) {
      this.sliderMaximum = sliderMaximum;
      return this;
   }

   public double getSliderMaximumValue() {
      double minimum = this.getMinimumValue().doubleValue();
      double maximum = this.getMaximumValue().doubleValue();
      double configured = Double.isNaN(this.sliderMaximum) ? maximum : this.sliderMaximum;
      return Math.max(minimum, Math.min(maximum, configured));
   }

   public double sliderFraction() {
      double minimum = this.getMinimumValue().doubleValue();
      double maximum = this.getSliderMaximumValue();
      if (maximum <= minimum) {
         return 0.0;
      }

      double fraction = (this.getValue().doubleValue() - minimum) / (maximum - minimum);
      return Math.max(0.0, Math.min(1.0, fraction));
   }

   public double valueAtSliderFraction(double fraction) {
      double minimum = this.getMinimumValue().doubleValue();
      double maximum = this.getSliderMaximumValue();
      if (maximum <= minimum) {
         return minimum;
      }

      double clampedFraction = Math.max(0.0, Math.min(1.0, fraction));
      return minimum + clampedFraction * (maximum - minimum);
   }
}
