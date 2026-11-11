package dev.argus.client.gui.components;

import java.awt.Color;

public enum TetrisShape {
   NoShape(new Color(0, 0, 0)),
   ZShape(new Color(204, 102, 102)),
   SShape(new Color(102, 204, 102)),
   LineShape(new Color(102, 102, 204)),
   TShape(new Color(204, 204, 102)),
   SquareShape(new Color(204, 102, 204)),
   LShape(new Color(102, 204, 204)),
   MirroredLShape(new Color(218, 170, 0));

   private final Color color;

   TetrisShape(Color shapeColor) {
      this.color = shapeColor;
   }

   public Color getColor() {
      return this.color;
   }
}
