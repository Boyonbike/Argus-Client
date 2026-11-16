package dev.argus.client.render.loading;

import static dev.argus.client.render.loading.LoadingAtlas.clamp01;

import dev.argus.client.render.loading.LoadingAtlas.Region;
import org.joml.Matrix3x2fStack;

/**
 * The Argus loading screen: the emblem drawn flat on the GUI background, the wordmark, and a thin progress bar
 * with its percentage. The eye still wakes, looks around (and at the cursor) and blinks, since that is the emblem
 * itself; there are no glows, rings, rays, particles or shimmer. Colours match the GUI's dark theme.
 */
public final class LoadingAnimation {
   private static final boolean NORMAL = false;
   private static final boolean ADD = true;

   /** The GUI's dark background and roles (see UiTheme): bg, text, textDim, line and the teal accent. */
   public static final int BACKGROUND = 0xFF1B1C1E;
   private static final int TEXT = 0xE6E4DF;
   private static final int DIM = 0xA19E97;
   private static final int LINE = 0x3A3D42;
   private static final int ACCENT = 0x6DB8AB;

   private static final float WAKE_TIME = 0.6F;
   private static final String WORDMARK = "ARGUS";

   private static volatile LoadingAtlas atlas;

   private long startNanos = Long.MIN_VALUE;
   private long lastNanos;
   private float now;
   private float lookX;
   private float lookY;
   private int mouseX = Integer.MIN_VALUE;
   private int mouseY = Integer.MIN_VALUE;
   private float mouseMovedAt = -100.0F;
   private float shownProgress;

   private LoadingCanvas graphics;
   private float alpha;

   public static void setAtlas(LoadingAtlas value) {
      atlas = value;
   }

   public void mouse(int x, int y) {
      if (this.mouseX != Integer.MIN_VALUE && (x != this.mouseX || y != this.mouseY)) {
         this.mouseMovedAt = this.now;
      }
      this.mouseX = x;
      this.mouseY = y;
   }

   /**
    * @param overlayAlpha vanilla's logo alpha (fades in on reloads, out at the end)
    * @param progress     vanilla's smoothed reload progress
    * @param fadeOut      seconds since the reload finished, or a negative value while loading
    * @param nanos        monotonic clock in nanoseconds
    */
   public void render(LoadingCanvas graphics, float overlayAlpha, float progress, float fadeOut, long nanos) {
      LoadingAtlas atlas = LoadingAnimation.atlas;
      if (this.startNanos == Long.MIN_VALUE) {
         this.startNanos = nanos;
         this.lastNanos = nanos;
      }
      float dt = Math.min(0.1F, (nanos - this.lastNanos) / 1.0E9F);
      this.lastNanos = nanos;
      float t = (nanos - this.startNanos) / 1.0E9F;
      this.now = t;
      this.graphics = graphics;
      this.alpha = clamp01(overlayAlpha);
      if (this.alpha <= 0.002F) {
         return;
      }

      float w = graphics.width();
      float h = graphics.height();
      float unit = Math.min(h, w * 0.62F);
      float logo = unit * 0.38F;
      float cx = w * 0.5F;
      float cy = h * 0.41F;
      float out = fadeOut < 0.0F ? 0.0F : fadeOut;
      float outro = easeInOut(clamp01(out / 1.1F));

      this.shownProgress = Math.max(this.shownProgress, clamp01(progress));
      if (fadeOut >= 0.0F) {
         this.shownProgress += (1.0F - this.shownProgress) * (1.0F - (float) Math.exp(-dt * 10.0F));
      }

      if (atlas == null) {
         this.renderFallback(cx, cy, logo, w);
         return;
      }

      this.drawLogo(atlas, t, dt, cx, cy, logo, out, outro);
      this.drawWordmark(atlas, t, cx, cy + logo * 1.0F, logo * 0.088F, out);
      this.drawProgress(atlas, t, cx, cy + logo * 1.2F, logo, unit, fadeOut >= 0.0F);
   }

   // ------------------------------------------------------------------ logo & eye

   private void drawLogo(LoadingAtlas atlas, float t, float dt, float cx, float cy, float logo, float out, float outro) {
      float pop = clamp01((t - 0.08F) / 0.75F);
      if (pop <= 0.0F) {
         return;
      }
      // Fades in where it will stay: no scale pop, bob or halo.
      float logoAlpha = smooth(clamp01((t - 0.08F) / 0.35F));
      float size = logo;
      float x = cx - size * 0.5F;
      float y = cy - size * 0.5F;

      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(x, y);
      pose.scale(size / LoadingAtlas.LOGO);
      float a = logoAlpha;

      if (!atlas.eyeValid) {
         this.layer(LoadingAtlas.FRAME, LoadingAtlas.LOGO_SCALE, a);
         pose.popMatrix();
         return;
      }

      this.updateGaze(t, dt, cx, cy + (LoadingAtlas.IRIS_CY - 128.0F) * size / 256.0F, logo, out);

      pose.pushMatrix();
      pose.translate(LoadingAtlas.SOCKET_OX, LoadingAtlas.SOCKET_OY);
      this.layer(LoadingAtlas.SOCKET, LoadingAtlas.LOGO_SCALE, a);
      pose.popMatrix();

      float lids = this.blink(t);
      // Iris: follows the gaze and dilates slightly; contracts sharply when the eye wakes.
      // Skipped while the eye is fully shut so it never shows through translucent lids.
      if (lids < 0.995F) {
         pose.pushMatrix();
         pose.translate(LoadingAtlas.IRIS_CX + this.lookX, LoadingAtlas.IRIS_CY + this.lookY);
         pose.translate(-LoadingAtlas.IRIS_CX, -LoadingAtlas.IRIS_CY);
         pose.pushMatrix();
         pose.translate(LoadingAtlas.IRIS_OX, LoadingAtlas.IRIS_OY);
         this.layer(LoadingAtlas.IRIS, LoadingAtlas.IRIS_SCALE, a);
         pose.popMatrix();
         pose.popMatrix();
      }

      this.drawEyelids(atlas, lids, a);

      this.layer(LoadingAtlas.FRAME, LoadingAtlas.LOGO_SCALE, a);
      pose.popMatrix();
   }

   private void updateGaze(float t, float dt, float eyeX, float eyeY, float logo, float out) {
      float tx;
      float ty;
      boolean mouseActive = this.mouseX != Integer.MIN_VALUE && t - this.mouseMovedAt < 2.5F && out <= 0.0F;
      if (mouseActive) {
         float dx = this.mouseX - eyeX;
         float dy = this.mouseY - eyeY;
         float len = (float) Math.sqrt(dx * dx + dy * dy);
         float reach = clamp01(len / (logo * 0.9F));
         tx = len > 0.0F ? dx / len * reach : 0.0F;
         ty = len > 0.0F ? dy / len * reach : 0.0F;
      } else if (out > 0.0F || t < WAKE_TIME + 0.5F) {
         tx = 0.0F;
         ty = 0.0F;
      } else {
         // Saccades: hold a point, then snap to the next one.
         int k = (int) Math.floor((t - WAKE_TIME) / 1.35F);
         if (hash(k * 13 + 5) < 0.3F) {
            tx = 0.0F;
            ty = 0.0F;
         } else {
            float angle = hash(k * 13 + 1) * 6.2831855F;
            float r = 0.45F + 0.55F * hash(k * 13 + 2);
            tx = (float) Math.cos(angle) * r;
            ty = (float) Math.sin(angle) * r;
         }
      }
      float targetX = tx * 5.5F;
      float targetY = ty * 3.2F;
      float k = 1.0F - (float) Math.exp(-dt * (mouseActive ? 14.0F : 20.0F));
      this.lookX += (targetX - this.lookX) * k;
      this.lookY += (targetY - this.lookY) * k;
   }

   /** 0 = open, 1 = shut. The eye starts closed and wakes, then blinks on an irregular schedule. */
   private float blink(float t) {
      if (t < WAKE_TIME) {
         return 1.0F;
      }
      float wake = t - WAKE_TIME;
      if (wake < 0.4F) {
         return 1.0F - easeOutCubic(wake / 0.4F);
      }
      float amount = 0.0F;
      int k = (int) Math.floor((t - 2.4F) / 4.3F);
      for (int i = k - 1; i <= k; i++) {
         if (i < 0) {
            continue;
         }
         float start = 2.4F + i * 4.3F + hash(i * 31 + 7) * 2.0F;
         amount = Math.max(amount, blinkShape(t - start));
         if (hash(i * 31 + 9) < 0.28F) {
            amount = Math.max(amount, blinkShape(t - start - 0.3F));
         }
      }
      return amount;
   }

   private static float blinkShape(float s) {
      if (s < 0.0F || s > 0.24F) {
         return 0.0F;
      }
      if (s < 0.075F) {
         return easeInQuad(s / 0.075F);
      }
      if (s < 0.105F) {
         return 1.0F;
      }
      return 1.0F - easeOutCubic((s - 0.105F) / 0.135F);
   }

   private void drawEyelids(LoadingAtlas atlas, float amount, float a) {
      if (amount <= 0.002F) {
         return;
      }
      float mid = atlas.eyeMid;
      float topEdge = atlas.eyeTop + amount * (mid - atlas.eyeTop);
      float bottomEdge = atlas.eyeBottom + 1 - amount * (atlas.eyeBottom + 1 - mid);
      // Lids are stretched to their exact (fractional) edge so they glide instead of stepping a texel at a time.
      Region lid = LoadingAtlas.EYELID;
      int top = atlas.eyeTop;
      int topRows = (int) Math.ceil(topEdge) - top;
      if (topRows > 0) {
         this.stretchRegion(NORMAL, lid.x(), lid.y() + top, lid.w(), topRows, 0.0F, top, lid.w(), topEdge - top, 0xFFFFFF, a);
      }
      int bottomEnd = atlas.eyeBottom + 1;
      int bottomStart = (int) Math.floor(bottomEdge);
      if (bottomEnd > bottomStart) {
         this.stretchRegion(NORMAL, lid.x(), lid.y() + bottomStart, lid.w(), bottomEnd - bottomStart,
               0.0F, bottomEdge, lid.w(), bottomEnd - bottomEdge, 0xFFFFFF, a);
      }
   }

   // ------------------------------------------------------------------ text & progress

   private void drawWordmark(LoadingAtlas atlas, float t, float cx, float baseline, float cap, float out) {
      if (!atlas.glyphsValid) {
         return;
      }
      // Plain wordmark at normal tracking, fading in once with the emblem.
      float tracking = cap * 0.18F;
      float width = this.textWidth(atlas, WORDMARK, cap, tracking, false);
      float scale = cap / atlas.glyphCapHeight;
      float a = appear(t, 0.5F, 0.4F);
      if (a > 0.0F) {
         this.text(atlas, WORDMARK, cx - width * 0.5F, baseline, scale, tracking, TEXT, a, false);
      }
   }

   private void drawProgress(LoadingAtlas atlas, float t, float cx, float y, float logo, float unit, boolean done) {
      float appear = appear(t, 0.6F, 0.4F);
      if (appear <= 0.0F) {
         return;
      }
      float width = logo * 1.05F;
      float x0 = cx - width * 0.5F;
      // Two GUI pixels tall, like the GUI's slider track; the accent fill is the only colour on screen.
      float thick = Math.max(1.0F, Math.round(unit * 0.006F));
      float p = this.shownProgress;
      this.rect(NORMAL, x0, y - thick * 0.5F, width, thick, LINE, appear);
      if (width * p > 0.0F) {
         this.rect(NORMAL, x0, y - thick * 0.5F, width * p, thick, ACCENT, appear);
      }

      if (atlas.glyphsValid) {
         float cap = Math.max(3.0F, logo * 0.034F);
         float scale = cap / atlas.glyphCapHeight;
         float tracking = cap * 0.18F;
         float textY = y + thick * 0.5F + cap * 2.3F;
         this.text(atlas, done ? "READY" : "LOADING", x0, textY, scale, tracking, DIM, appear, false);
         String pct = Math.round(p * 100.0F) + "%";
         float pw = this.textWidth(atlas, pct, cap, tracking, true);
         this.text(atlas, pct, x0 + width - pw, textY, scale, tracking, TEXT, appear, true);
      }
   }

   private float textWidth(LoadingAtlas atlas, String s, float cap, float tracking, boolean monoDigits) {
      float scale = cap / atlas.glyphCapHeight;
      float width = 0.0F;
      for (int i = 0; i < s.length(); i++) {
         int index = LoadingAtlas.glyphIndex(s.charAt(i));
         width += monoDigits && Character.isDigit(s.charAt(i)) ? this.digitWidth(atlas) * scale + tracking : this.advance(atlas, index, scale, tracking);
      }
      return Math.max(0.0F, width - tracking);
   }

   private void text(LoadingAtlas atlas, String s, float x, float baseline, float scale, float tracking, int rgb, float a, boolean monoDigits) {
      float pen = x;
      float digit = this.digitWidth(atlas) * scale;
      for (int i = 0; i < s.length(); i++) {
         int index = LoadingAtlas.glyphIndex(s.charAt(i));
         if (index < 0) {
            continue;
         }
         float ink = (atlas.glyphInkRight[index] - atlas.glyphInkLeft[index]) * scale;
         if (monoDigits && Character.isDigit(s.charAt(i))) {
            this.glyph(atlas, index, pen + (digit - ink) * 0.5F, baseline, scale, rgb, a);
            pen += digit + tracking;
         } else {
            this.glyph(atlas, index, pen, baseline, scale, rgb, a);
            pen += ink + tracking;
         }
      }
   }

   private float digitWidth(LoadingAtlas atlas) {
      int max = 0;
      for (char c = '0'; c <= '9'; c++) {
         int i = LoadingAtlas.glyphIndex(c);
         max = Math.max(max, atlas.glyphInkRight[i] - atlas.glyphInkLeft[i]);
      }
      return max;
   }

   private float advance(LoadingAtlas atlas, int index, float scale, float tracking) {
      if (index < 0) {
         return tracking;
      }
      return (atlas.glyphInkRight[index] - atlas.glyphInkLeft[index]) * scale + tracking;
   }

   /** Draws a glyph with its ink starting at {@code x} and sitting on {@code baseline}. */
   private void glyph(LoadingAtlas atlas, int index, float x, float baseline, float scale, int rgb, float a) {
      if (index < 0) {
         return;
      }
      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(x - atlas.glyphInkLeft[index] * scale, baseline - LoadingAtlas.glyphBaseline() * scale);
      pose.scale(scale);
      this.graphics.blit(NORMAL, 0, 0, LoadingAtlas.glyphU(index), LoadingAtlas.glyphV(index),
            LoadingAtlas.GLYPH_CELL_W, LoadingAtlas.GLYPH_CELL_H, argb(rgb, a * this.alpha));
      pose.popMatrix();
   }

   // ------------------------------------------------------------------ fallback

   /** Used only if the atlas failed to build: the static logo and a plain bar. */
   private void renderFallback(float cx, float cy, float logo, float w) {
      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(cx - logo * 0.5F, cy - logo * 0.5F);
      pose.scale(logo / LoadingAtlas.LOGO);
      this.graphics.blit(NORMAL, 0, 0, 0, 0, 256, 256, argb(0xFFFFFF, this.alpha));
      pose.popMatrix();
   }

   // ------------------------------------------------------------------ drawing primitives

   /** Draws a whole atlas region centred on (cx, cy) with float precision and optional rotation. */
   private void sprite(boolean pipeline, Region r, float cx, float cy, float w, float h, float rotation, int rgb, float a) {
      float total = a * this.alpha;
      if (total <= 0.003F || w <= 0.0F || h <= 0.0F) {
         return;
      }
      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(cx, cy);
      if (rotation != 0.0F) {
         pose.rotate(rotation);
      }
      pose.scale(w / r.w(), h / r.h());
      this.graphics.blit(pipeline, -r.w() / 2, -r.h() / 2, r.x(), r.y(), r.w(), r.h(), argb(rgb, total));
      pose.popMatrix();
   }

   /** Draws a whole logo layer stored at {@code scale} texels per logo unit in the current (logo-space) transform. */
   private void layer(Region r, int scale, float a) {
      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.scale(1.0F / scale);
      this.blit(NORMAL, r, 0, 0, 0, 0, r.w(), r.h(), 0xFFFFFF, a);
      pose.popMatrix();
   }

   /** Blits a texel-aligned sub-rectangle of a region in the current (logo-space) transform. */
   private void blit(boolean pipeline, Region r, int x, int y, int u, int v, int w, int h, int rgb, float a) {
      float total = a * this.alpha;
      if (total <= 0.003F || w <= 0 || h <= 0) {
         return;
      }
      this.graphics.blit(pipeline, x, y, r.x() + u, r.y() + v, w, h, argb(rgb, total));
   }

   /** A solid rectangle with float bounds, sampled from the atlas' white block. */
   private void rect(boolean pipeline, float x, float y, float w, float h, int rgb, float a) {
      Region white = LoadingAtlas.WHITE;
      this.stretchRegion(pipeline, white.x() + 6, white.y() + 6, 4, 4, x, y, w, h, rgb, a);
   }

   /** Stretches a horizontal strip of a region across a float rectangle. */
   private void stretch(boolean pipeline, Region r, float x, float y, float w, float h, int rgb, float a) {
      this.stretchRegion(pipeline, r.x(), r.y() + r.h() / 2 - 2, r.w(), 4, x, y, w, h, rgb, a);
   }

   private void stretchRegion(boolean pipeline, int u, int v, int rw, int rh, float x, float y, float w, float h, int rgb, float a) {
      float total = a * this.alpha;
      if (total <= 0.003F || w <= 0.0F || h <= 0.0F) {
         return;
      }
      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(x, y);
      pose.scale(w / rw, h / rh);
      this.graphics.blit(pipeline, 0, 0, u, v, rw, rh, argb(rgb, total));
      pose.popMatrix();
   }

   // ------------------------------------------------------------------ math

   private static int argb(int rgb, float a) {
      return Math.round(clamp01(a) * 255.0F) << 24 | rgb & 0xFFFFFF;
   }

   private static int lerpRgb(int a, int b, float t) {
      t = clamp01(t);
      int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
      int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
      int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
      return r << 16 | g << 8 | bl;
   }

   private static float appear(float t, float start, float duration) {
      return easeOutCubic(clamp01((t - start) / duration));
   }

   private static float hash(int n) {
      int x = n * 0x27D4EB2D;
      x ^= x >>> 15;
      x *= 0x85EBCA6B;
      x ^= x >>> 13;
      x *= 0xC2B2AE35;
      x ^= x >>> 16;
      return (x >>> 8) / 16777216.0F;
   }

   private static float smooth(float x) {
      return x * x * (3.0F - 2.0F * x);
   }

   private static float easeOutCubic(float x) {
      float i = 1.0F - clamp01(x);
      return 1.0F - i * i * i;
   }

   private static float easeInQuad(float x) {
      return x * x;
   }

   private static float easeInOut(float x) {
      return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0) / 2.0F;
   }

   private static float easeOutBack(float x) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float i = x - 1.0F;
      return 1.0F + c3 * i * i * i + c1 * i * i;
   }
}
