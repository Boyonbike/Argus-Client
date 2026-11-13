package dev.argus.client.render.loading;

import static dev.argus.client.render.loading.LoadingAtlas.clamp01;

import dev.argus.client.render.loading.LoadingAtlas.Region;
import org.joml.Matrix3x2fStack;

/**
 * The animated Argus loading screen: the eye wakes, looks around (and at the cursor), blinks,
 * while HUD rings spin, light rays turn and particles drift. One instance lives per
 * loading overlay; everything is drawn from a single atlas texture using a normal and an
 * additive pipeline, so a frame is a few hundred quads in a handful of batches.
 */
public final class LoadingAnimation {
   private static final boolean NORMAL = false;
   private static final boolean ADD = true;

   public static final int BACKGROUND = 0xFF050B0E;
   private static final int CYAN = 0x4FD8EA;
   private static final int TEAL = 0x1A93A6;
   private static final int ICE = 0xC9F7FF;
   private static final int DEEP = 0x0D4652;
   private static final int TEXT = 0xE4FAFF;

   private static final int PARTICLES = 96;
   private static final float WAKE_TIME = 0.6F;
   private static final String WORDMARK = "ARGUS";
   private static final String SCRAMBLE = "ABCDEFGHJKMNPQRSTVWXYZ0123456789#/<>+";

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
   private final float[] particles = new float[PARTICLES * 6];

   private LoadingCanvas graphics;
   private float alpha;

   public LoadingAnimation() {
      for (int i = 0; i < PARTICLES; i++) {
         int o = i * 6;
         this.particles[o] = hash(i * 7 + 1);
         this.particles[o + 1] = hash(i * 7 + 2);
         float depth = 0.25F + 0.75F * hash(i * 7 + 3);
         this.particles[o + 2] = depth;
         this.particles[o + 3] = hash(i * 7 + 4) * 6.2831855F;
         this.particles[o + 4] = 0.6F + 2.4F * hash(i * 7 + 5);
         // ~12% become large out-of-focus bokeh
         this.particles[o + 5] = hash(i * 7 + 6) < 0.12F ? 1.0F : 0.0F;
      }
   }

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

      float intro = easeOutCubic(clamp01(t / 0.9F));
      this.drawBackground(t, w, h, cx, cy, unit, logo, intro, outro);
      this.drawRings(t, cx, cy, logo, outro);
      this.drawLogo(atlas, t, dt, cx, cy, logo, out, outro);
      this.drawShockwaves(t, cx, cy, logo, out);
      this.drawWordmark(atlas, t, cx, cy + logo * 1.0F, logo * 0.088F, out);
      this.drawProgress(atlas, t, cx, cy + logo * 1.2F, logo, unit, fadeOut >= 0.0F);
   }

   // ------------------------------------------------------------------ background

   private void drawBackground(float t, float w, float h, float cx, float cy, float unit, float logo,
         float intro, float outro) {
      float breathe = 0.5F + 0.5F * (float) Math.sin(t * 0.9F);
      // Ambient pool of light behind the emblem.
      this.sprite(ADD, LoadingAtlas.DOT, cx, cy, unit * 2.6F, unit * 2.0F, 0.0F, DEEP, (0.55F + 0.15F * breathe) * intro);
      this.sprite(ADD, LoadingAtlas.DOT, cx, cy, logo * 2.4F, logo * 2.4F, 0.0F, TEAL, (0.22F + 0.08F * breathe) * intro * (1.0F + outro));

      // Counter-rotating god rays.
      float rays = intro * (1.0F + 1.5F * outro);
      this.sprite(ADD, LoadingAtlas.RAYS, cx, cy, logo * 3.1F, logo * 3.1F, t * 0.045F, CYAN, 0.075F * rays);
      this.sprite(ADD, LoadingAtlas.RAYS, cx, cy, logo * 3.6F, logo * 3.6F, -t * 0.03F + 1.3F, TEAL, 0.06F * rays);

      // Drifting motes with parallax; they streak outward when loading finishes.
      float warp = outro * outro * 2.2F;
      for (int i = 0; i < PARTICLES; i++) {
         int o = i * 6;
         float depth = this.particles[o + 2];
         float phase = this.particles[o + 3];
         boolean bokeh = this.particles[o + 5] > 0.5F;
         float rise = (this.particles[o + 1] + t * 0.018F * (0.4F + depth)) % 1.0F;
         float px = this.particles[o] * w + (float) Math.sin(t * 0.35F * this.particles[o + 4] + phase) * unit * 0.02F * depth;
         float py = (1.08F - rise * 1.16F) * h;
         if (warp > 0.0F) {
            px += (px - cx) * warp * depth;
            py += (py - cy) * warp * depth;
         }
         float edge = smooth(clamp01(rise / 0.15F)) * smooth(clamp01((1.0F - rise) / 0.2F));
         float twinkle = 0.55F + 0.45F * (float) Math.sin(t * this.particles[o + 4] * 1.7F + phase);
         float size;
         float a;
         if (bokeh) {
            size = unit * (0.035F + 0.05F * depth);
            a = 0.05F + 0.04F * twinkle;
         } else {
            size = unit * (0.006F + 0.014F * depth);
            a = (0.25F + 0.55F * depth) * twinkle;
         }
         a *= edge * clamp01(t * 1.5F - hash(i * 3 + 11) * 0.6F) * (1.0F - outro * 0.6F);
         if (a > 0.004F) {
            this.sprite(ADD, LoadingAtlas.DOT, px, py, size, size, 0.0F, bokeh ? TEAL : CYAN, a);
         }
      }

      this.sprite(NORMAL, LoadingAtlas.VIGNETTE, w * 0.5F, h * 0.5F, w, h, 0.0F, 0x000000, 1.0F);
   }

   // ------------------------------------------------------------------ rings

   private void drawRings(float t, float cx, float cy, float logo, float outro) {
      float expand = 1.0F + outro * 0.55F;
      float fade = 1.0F - outro;
      if (fade <= 0.0F) {
         return;
      }
      float radarIn = appear(t, 1.1F, 1.2F);
      this.sprite(ADD, LoadingAtlas.RADAR, cx, cy, logo * 1.62F * expand, logo * 1.62F * expand, t * 1.25F, CYAN, 0.16F * radarIn * fade);

      float c = appear(t, 0.55F, 1.1F);
      float a = appear(t, 0.42F, 1.0F);
      float b = appear(t, 0.3F, 0.9F);
      float pulse = 0.85F + 0.15F * (float) Math.sin(t * 2.3F);

      this.ring(LoadingAtlas.RING_C, cx, cy, logo * 1.79F * expand, t * 0.04F, c, CYAN, 0.32F * fade);
      this.ring(LoadingAtlas.RING_A, cx, cy, logo * 1.73F * expand, -t * 0.11F, a, CYAN, 0.5F * fade);
      float rotB = t * 0.55F;
      this.ring(LoadingAtlas.RING_B, cx, cy, logo * 1.6F * expand, rotB, b, ICE, 0.85F * pulse * fade);

      // Sparks riding the bright head of each arc.
      if (b > 0.0F) {
         float spin = rotB + (1.0F - b) * -1.6F;
         float radius = logo * 1.6F * expand * 0.5F * (204.0F / 256.0F) * (0.82F + 0.18F * b);
         for (int k = 0; k < 3; k++) {
            float angle = spin + (k + 0.62F) * 2.0943952F;
            float sx = cx + (float) Math.cos(angle) * radius;
            float sy = cy + (float) Math.sin(angle) * radius;
            float flicker = 0.8F + 0.2F * (float) Math.sin(t * 13.0F + k * 2.1F);
            this.sprite(ADD, LoadingAtlas.DOT, sx, sy, logo * 0.12F, logo * 0.12F, 0.0F, CYAN, 0.5F * b * fade);
            this.sprite(ADD, LoadingAtlas.SPARK, sx, sy, logo * 0.13F, logo * 0.13F, t * 0.8F + k, ICE, 0.95F * flicker * b * fade);
         }
      }
   }

   private void ring(Region region, float cx, float cy, float size, float rotation, float appear, int rgb, float a) {
      if (appear <= 0.0F) {
         return;
      }
      float scale = 0.78F + 0.22F * appear;
      float spinIn = (1.0F - appear) * 1.4F;
      this.sprite(ADD, region, cx, cy, size * scale, size * scale, rotation - spinIn, rgb, a * appear);
   }

   // ------------------------------------------------------------------ logo & eye

   private void drawLogo(LoadingAtlas atlas, float t, float dt, float cx, float cy, float logo, float out, float outro) {
      float pop = clamp01((t - 0.08F) / 0.75F);
      if (pop <= 0.0F) {
         return;
      }
      float scale = (0.84F + 0.16F * easeOutBack(pop)) * (1.0F + 0.08F * outro);
      float logoAlpha = smooth(clamp01((t - 0.08F) / 0.35F));
      float size = logo * scale;
      float bob = (float) Math.sin(t * 1.1F) * logo * 0.008F;
      float x = cx - size * 0.5F;
      float y = cy - size * 0.5F + bob;
      float wake = t - WAKE_TIME;
      float wakeFlash = wake > 0.0F ? (float) Math.exp(-wake * 3.5F) : 0.0F;
      float sweep = this.sweepPhase(t);
      float sweepGlow = sweep >= 0.0F ? (float) Math.sin(sweep * Math.PI) : 0.0F;

      // Halo
      float halo = 0.34F + 0.1F * (float) Math.sin(t * 1.7F) + 0.5F * wakeFlash + 0.12F * sweepGlow + 0.4F * outro;
      float glowSize = size / LoadingAtlas.GLOW_SCALE;
      this.sprite(ADD, LoadingAtlas.GLOW, cx, cy + bob, glowSize, glowSize, 0.0F, CYAN, halo * logoAlpha);
      this.sprite(ADD, LoadingAtlas.GLOW, cx, cy + bob, glowSize * 1.25F, glowSize * 1.25F, 0.0F, TEAL, halo * 0.5F * logoAlpha);

      Matrix3x2fStack pose = this.graphics.pose();
      pose.pushMatrix();
      pose.translate(x, y);
      pose.scale(size / LoadingAtlas.LOGO);
      float a = logoAlpha;

      if (!atlas.eyeValid) {
         this.layer(LoadingAtlas.FRAME, LoadingAtlas.EYE_SCALE, a);
         pose.popMatrix();
         return;
      }

      this.updateGaze(t, dt, cx, cy + bob + (LoadingAtlas.IRIS_CY - 128.0F) * size / 256.0F, logo, out);

      this.layer(LoadingAtlas.SOCKET, LoadingAtlas.EYE_SCALE, a);

      float lids = this.blink(t);
      // Iris: follows the gaze and dilates slightly; contracts sharply when the eye wakes.
      // Skipped while the eye is fully shut so it never shows through translucent lids.
      if (lids < 0.995F) {
         float dilate = 1.0F + 0.025F * (float) Math.sin(t * 0.9F) - 0.07F * wakeFlash + 0.05F * outro;
         pose.pushMatrix();
         pose.translate(LoadingAtlas.IRIS_CX + this.lookX, LoadingAtlas.IRIS_CY + this.lookY);
         pose.scale(dilate);
         pose.translate(-LoadingAtlas.IRIS_CX, -LoadingAtlas.IRIS_CY);
         pose.pushMatrix();
         pose.translate(LoadingAtlas.IRIS_OX, LoadingAtlas.IRIS_OY);
         this.layer(LoadingAtlas.IRIS, LoadingAtlas.IRIS_SCALE, a);
         pose.popMatrix();
         // Living glint on the pupil highlight + inner glow.
         float glint = 0.45F + 0.25F * (float) Math.sin(t * 2.6F) + 0.6F * wakeFlash;
         this.sprite(ADD, LoadingAtlas.DOT, LoadingAtlas.IRIS_CX, LoadingAtlas.IRIS_CY, 44.0F, 44.0F, 0.0F, TEAL, (0.22F + 0.5F * outro) * a);
         this.sprite(ADD, LoadingAtlas.SPARK, 119.0F, 124.0F, 22.0F, 22.0F, t * 0.6F, ICE, glint * a);
         pose.popMatrix();
      }

      this.drawEyelids(atlas, lids, a);

      this.layer(LoadingAtlas.FRAME, LoadingAtlas.EYE_SCALE, a);

      if (sweep >= 0.0F) {
         this.drawSweep(sweep, a);
      }
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
      // Glowing lash lines on the closing edges.
      float glow = a * (0.55F + 0.45F * amount);
      this.lash(atlas, topEdge, glow);
      this.lash(atlas, bottomEdge, glow);
   }

   private void lash(LoadingAtlas atlas, float edge, float a) {
      int row = Math.max(0, Math.min(LoadingAtlas.LOGO - 1, Math.round(edge)));
      int min = atlas.rowMinX[row];
      int max = atlas.rowMaxX[row];
      if (min < 0 || max <= min) {
         return;
      }
      this.rect(ADD, min, edge - 0.9F, max + 1 - min, 1.8F, CYAN, a);
      this.sprite(ADD, LoadingAtlas.DOT, (min + max + 1) * 0.5F, edge, (max - min) * 1.1F, 10.0F, 0.0F, TEAL, a * 0.6F);
   }

   /** Phase 0..1 of the current light sweep, or -1 between sweeps. */
   private float sweepPhase(float t) {
      float start = 1.7F;
      if (t < start) {
         return -1.0F;
      }
      float local = (t - start) % 4.8F;
      return local < 1.15F ? local / 1.15F : -1.0F;
   }

   private void drawSweep(float phase, float a) {
      float center = -70.0F + 400.0F * easeInOut(phase);
      float slope = 0.5F;
      float half = 30.0F;
      int seg = 6;
      Region sil = LoadingAtlas.SILHOUETTE;
      for (int y = 12; y < 244; y += 4) {
         float c = center - (y + 2 - 128) * slope;
         int from = Math.max(0, (int) Math.floor((c - half) / seg) * seg);
         int to = Math.min(256, (int) Math.ceil((c + half) / seg) * seg);
         for (int x = from; x < to; x += seg) {
            float d = (x + seg * 0.5F - c) / (half * 0.55F);
            float v = (float) Math.exp(-d * d) * 0.7F;
            if (v > 0.02F) {
               this.blit(ADD, sil, x, y, x, y, seg, 4, ICE, v * a);
            }
         }
      }
   }

   private void drawShockwaves(float t, float cx, float cy, float logo, float out) {
      float wake = (t - WAKE_TIME) / 1.2F;
      if (wake > 0.0F && wake < 1.0F) {
         float e = easeOutCubic(wake);
         float size = logo * (0.9F + 2.8F * e);
         this.sprite(ADD, LoadingAtlas.SHOCK, cx, cy, size, size, 0.0F, CYAN, (float) Math.pow(1.0F - wake, 1.6F) * 0.7F);
      }
      if (out > 0.0F && out < 1.4F) {
         float p = out / 1.4F;
         float e = easeOutCubic(p);
         float size = logo * (1.2F + 4.0F * e);
         this.sprite(ADD, LoadingAtlas.SHOCK, cx, cy, size, size, 0.0F, CYAN, (float) Math.pow(1.0F - p, 1.4F) * 0.85F);
         this.sprite(ADD, LoadingAtlas.DOT, cx, cy, logo * 3.0F, logo * 3.0F, 0.0F, CYAN, (float) Math.exp(-out * 4.0F) * 0.45F);
      }
   }

   // ------------------------------------------------------------------ text & progress

   private void drawWordmark(LoadingAtlas atlas, float t, float cx, float baseline, float cap, float out) {
      if (!atlas.glyphsValid) {
         return;
      }
      float tracking = cap * 0.62F;
      float width = this.textWidth(atlas, WORDMARK, cap, tracking, false);
      float pen = cx - width * 0.5F;
      float scale = cap / atlas.glyphCapHeight;
      for (int i = 0; i < WORDMARK.length(); i++) {
         char real = WORDMARK.charAt(i);
         float reveal = 1.05F + i * 0.1F;
         float since = t - reveal;
         int index = LoadingAtlas.glyphIndex(real);
         if (since < -0.4F) {
            pen += this.advance(atlas, index, scale, tracking);
            continue;
         }
         int shown = index;
         int rgb;
         float a;
         if (since < 0.0F) {
            shown = LoadingAtlas.glyphIndex(SCRAMBLE.charAt((int) (hash(i * 97 + (int) (t * 24.0F)) * SCRAMBLE.length())));
            rgb = CYAN;
            a = 0.55F;
         } else {
            float flash = (float) Math.exp(-since * 5.0F);
            rgb = lerpRgb(TEXT, 0xFFFFFF, flash);
            a = 1.0F;
            if (flash > 0.02F) {
               float gx = pen + (atlas.glyphInkRight[index] - atlas.glyphInkLeft[index]) * scale * 0.5F;
               this.sprite(ADD, LoadingAtlas.DOT, gx, baseline - cap * 0.5F, cap * 3.0F, cap * 3.0F, 0.0F, CYAN, flash * 0.5F);
            }
         }
         // Center scrambled glyphs on the real letter's slot so the word doesn't jitter.
         float slot = (atlas.glyphInkRight[index] - atlas.glyphInkLeft[index]) * scale;
         float shownWidth = (atlas.glyphInkRight[shown] - atlas.glyphInkLeft[shown]) * scale;
         this.glyph(atlas, shown, pen + (slot - shownWidth) * 0.5F, baseline, scale, rgb, a * (1.0F - clamp01(out - 1.0F)));
         pen += this.advance(atlas, index, scale, tracking);
      }
      // Soft underglow
      float revealAll = appear(t, 1.1F, 0.8F);
      this.sprite(ADD, LoadingAtlas.DOT, cx, baseline - cap * 0.4F, width * 1.6F, cap * 3.2F, 0.0F, TEAL, 0.22F * revealAll);
   }

   private void drawProgress(LoadingAtlas atlas, float t, float cx, float y, float logo, float unit, boolean done) {
      float appear = appear(t, 1.3F, 0.7F);
      if (appear <= 0.0F) {
         return;
      }
      float fullWidth = logo * 1.05F;
      float width = fullWidth * appear;
      float x0 = cx - width * 0.5F;
      float thick = Math.max(1.0F, unit * 0.0045F);
      float p = this.shownProgress;
      float fill = width * p;

      // Track with quarter ticks
      this.rect(NORMAL, x0, y - thick * 0.5F, width, thick, 0xFFFFFF, 0.09F * appear);
      for (int i = 1; i < 4; i++) {
         float tx = x0 + width * i / 4.0F;
         this.rect(NORMAL, tx - thick * 0.5F, y - thick * 2.0F, thick, thick * 4.0F, 0xFFFFFF, 0.08F * appear);
      }

      if (fill > 0.5F) {
         this.sprite(ADD, LoadingAtlas.DOT, x0 + fill * 0.5F, y, fill * 1.3F, thick * 14.0F, 0.0F, TEAL, 0.25F * appear);
         Region grad = LoadingAtlas.GRADIENT;
         this.stretch(ADD, grad, x0, y - thick * 0.5F, fill, thick, CYAN, 1.0F * appear);
         this.stretch(ADD, grad, x0, y - thick * 0.5F, fill, thick, 0xFFFFFF, 0.35F * appear);
         // Shimmer travelling along the filled part.
         float shimmer = (t * 0.55F) % 1.0F;
         this.sprite(ADD, LoadingAtlas.DOT, x0 + fill * shimmer, y, fullWidth * 0.2F, thick * 4.0F, 0.0F, ICE,
               0.55F * appear * (float) Math.sin(shimmer * Math.PI));
         float head = x0 + fill;
         float pulse = 0.8F + 0.2F * (float) Math.sin(t * 9.0F);
         this.sprite(ADD, LoadingAtlas.DOT, head, y, thick * 18.0F, thick * 18.0F, 0.0F, CYAN, 0.55F * appear * pulse);
         this.sprite(ADD, LoadingAtlas.SPARK, head, y, thick * 26.0F, thick * 26.0F, t * 1.5F, ICE, 0.9F * appear * pulse);
      }

      if (atlas.glyphsValid) {
         float cap = Math.max(3.0F, logo * 0.034F);
         float scale = cap / atlas.glyphCapHeight;
         float tracking = cap * 0.55F;
         float textY = y + thick * 0.5F + cap * 2.3F;
         String label = done ? "READY" : "LOADING";
         float dots = done ? 0.0F : 1.0F;
         this.text(atlas, label, x0, textY, scale, tracking, TEXT, 0.5F * appear, false);
         if (dots > 0.0F) {
            float lx = x0 + this.textWidth(atlas, label, cap, tracking, false) + tracking;
            for (int i = 0; i < 3; i++) {
               float on = 0.25F + 0.75F * clamp01((float) Math.sin(t * 5.0F - i * 0.7F));
               this.rect(NORMAL, lx + i * cap * 0.75F, textY - cap * 0.28F, cap * 0.28F, cap * 0.28F, CYAN, on * 0.7F * appear);
            }
         }
         String pct = Math.round(p * 100.0F) + "%";
         float pw = this.textWidth(atlas, pct, cap, tracking, true);
         this.text(atlas, pct, x0 + width - pw, textY, scale, tracking, done ? ICE : CYAN, 0.9F * appear, true);
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
