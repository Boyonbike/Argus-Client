package dev.argus.client.modules.hud;

import java.awt.Color;

public class HudNotification {
   protected String text;
   protected final Color color;
   protected long startTimeMillis;
   protected long endTimeMillis;

   public long getStartTimeMillis() {
      return this.startTimeMillis;
   }

   public String getText() {
      return this.text;
   }

   public long getEndTimeMillis() {
      return this.endTimeMillis;
   }

   public HudNotification(String text, long durationMillis, Color color) {
      this.text = text;
      this.color = color;
      this.startTimeMillis = System.currentTimeMillis();
      this.endTimeMillis = this.startTimeMillis + durationMillis;
   }

   public Color getColor() {
      return this.color;
   }

   public void update() {
   }
}
