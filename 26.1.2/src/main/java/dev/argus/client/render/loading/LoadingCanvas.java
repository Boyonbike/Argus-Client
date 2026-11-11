package dev.argus.client.render.loading;

import org.joml.Matrix3x2fStack;

/** The minimal drawing surface the loading animation needs; backed by GuiGraphics in game. */
public interface LoadingCanvas {
   float width();

   float height();

   Matrix3x2fStack pose();

   /**
    * Draws atlas texels (u, v, w, h) as a w x h quad at (x, y) in the current pose.
    *
    * @param additive SRC_ALPHA, ONE blending instead of normal alpha blending
    */
   void blit(boolean additive, int x, int y, int u, int v, int w, int h, int argb);
}
