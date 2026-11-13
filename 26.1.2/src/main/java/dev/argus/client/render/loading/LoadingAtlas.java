package dev.argus.client.render.loading;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Builds the single texture atlas used by the animated loading screen.
 *
 * <p>Every sprite is generated procedurally from the Argus logo at startup so the whole
 * screen renders from one texture with two blend modes, which keeps it to a handful of
 * draw calls. This class is plain Java (no Minecraft types) so it can be exercised offline.
 */
public final class LoadingAtlas {
   public static final int WIDTH = 4096;
   public static final int HEIGHT = 2048;
   public static final int LOGO = 256;
   /** Texels per logo unit of the FRAME, SOCKET and SILHOUETTE layers (the logo, upscaled edge-aware). */
   public static final int LOGO_SCALE = 4;
   /** Texels per logo unit of the procedurally drawn IRIS. */
   public static final int IRIS_SCALE = 4;

   public record Region(int x, int y, int w, int h) {
   }

   // Logo layers share the logo's own 256x256 coordinate space; FRAME/SOCKET/SILHOUETTE/IRIS are supersampled.
   public static final Region GLOW = new Region(512, 0, 256, 256);
   public static final Region EYELID = new Region(0, 256, 256, 256);
   public static final Region DOT = new Region(336, 256, 64, 64);
   public static final Region SPARK = new Region(400, 256, 64, 64);
   public static final Region WHITE = new Region(432, 340, 16, 16);
   public static final Region GRADIENT = new Region(464, 340, 256, 16);
   public static final Region RADAR = new Region(256, 352, 160, 160);
   public static final Region VIGNETTE = new Region(772, 260, 248, 248);
   public static final Region RING_A = new Region(0, 512, 512, 512);
   public static final Region RAYS = new Region(512, 512, 512, 512);
   public static final Region RING_B = new Region(1024, 0, 512, 512);
   public static final Region SHOCK = new Region(1536, 0, 512, 512);
   public static final Region RING_C = new Region(1024, 512, 512, 512);
   public static final Region GLYPHS = new Region(1536, 512, 512, 512);
   public static final Region FRAME = new Region(2048, 0, LOGO * LOGO_SCALE, LOGO * LOGO_SCALE);
   public static final Region SILHOUETTE = new Region(3072, 0, LOGO * LOGO_SCALE, LOGO * LOGO_SCALE);
   /** The socket layer only covers the eye: this logo-space rectangle. */
   public static final int SOCKET_OX = 56;
   public static final int SOCKET_OY = 88;
   public static final int SOCKET_W = 144;
   public static final int SOCKET_H = 104;
   public static final Region SOCKET = new Region(0, 1024, SOCKET_W * LOGO_SCALE, SOCKET_H * LOGO_SCALE);
   public static final Region IRIS = new Region(1024, 1024, 80 * IRIS_SCALE, 80 * IRIS_SCALE);

   /** Size of the soft glow silhouette relative to its cell; drawn at logoSize / GLOW_SCALE. */
   public static final float GLOW_SCALE = 0.62F;

   // Eye geometry measured from argusclient.png (logo texel space, pixel edges at integers).
   public static final float IRIS_CX = 128.0F;
   public static final float IRIS_CY = 134.5F;
   private static final float IRIS_RIM = 33.2F;
   /** The cyan iris ring and the round highlight that bites into the pupil, as drawn in the art. */
   private static final float RING_INNER = 19.3F;
   private static final float RING_OUTER = 31.6F;
   private static final float HIGHLIGHT_X = 117.0F;
   private static final float HIGHLIGHT_Y = 124.5F;
   private static final float HIGHLIGHT_R = 6.6F;
   /** Width of the anti-aliased transition on drawn edges, in logo units. */
   private static final float EDGE_AA = 0.45F;
   /** Logo-space origin of the IRIS sprite (80x80 logo units). */
   public static final int IRIS_OX = 88;
   public static final int IRIS_OY = 94;
   private static final int SOCKET_SEED_X = 128;
   private static final int SOCKET_SEED_Y = 108;
   /** The eye's outline runs on past its corners into the shield, so the fill is bounded here. */
   private static final int EYE_MIN_X = 62;
   private static final int EYE_MAX_X = 194;
   private static final int EYE_MIN_Y = 96;
   private static final int EYE_MAX_Y = 182;
   /** How far inside the eyelids the iris may travel before it tucks under them. */
   private static final float LID_TUCK = 6.5F;
   private static final int SOCKET_DARK = 0xFF252D32;

   public static final String GLYPH_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789%#/<>+-:.";
   public static final int GLYPH_CELL_W = 48;
   public static final int GLYPH_CELL_H = 64;
   private static final int GLYPH_COLUMNS = GLYPHS.w / GLYPH_CELL_W;
   private static final int GLYPH_BASELINE = 50;

   public final int[] pixels = new int[WIDTH * HEIGHT];
   private boolean abgr;

   /** Eye socket rows (logo space) and the eyelid closing line. */
   public int eyeTop;
   public int eyeBottom;
   public float eyeMid;
   /** Inclusive horizontal span of the socket per logo row, or -1 when the row is empty. */
   public final int[] rowMinX = new int[LOGO];
   public final int[] rowMaxX = new int[LOGO];
   public boolean eyeValid;

   /** Ink bounds of each glyph inside its cell, in cell pixels (right is exclusive). */
   public final int[] glyphInkLeft = new int[GLYPH_CHARS.length()];
   public final int[] glyphInkRight = new int[GLYPH_CHARS.length()];
   public float glyphCapHeight;
   public boolean glyphsValid;

   private LoadingAtlas() {
   }

   /**
    * @param logo 256x256 ARGB pixels of the Argus logo
    * @param glyphSheet optional 512x512 ARGB glyph sheet baked by tools/BakeLoadingGlyphs; may be null
    */
   public static LoadingAtlas build(int[] logo, int[] glyphSheet) {
      if (logo.length != LOGO * LOGO) {
         throw new IllegalArgumentException("Logo must be 256x256");
      }
      LoadingAtlas atlas = new LoadingAtlas();
      atlas.buildLogoLayers(logo);
      atlas.buildGlow(logo);
      atlas.buildSprites();
      atlas.buildRings();
      atlas.buildRays();
      if (glyphSheet != null && glyphSheet.length == GLYPHS.w * GLYPHS.h) {
         atlas.buildGlyphs(glyphSheet);
      }
      return atlas;
   }

   /**
    * The pixels in NativeImage's in-memory order (ABGR), for a straight memory copy on upload. Converts
    * {@link #pixels} in place on first call so the atlas is not held twice.
    */
   public synchronized int[] pixelsAbgr() {
      if (!this.abgr) {
         int[] p = this.pixels;
         IntStream.range(0, HEIGHT).parallel().forEach(y -> {
            for (int i = y * WIDTH, end = i + WIDTH; i < end; i++) {
               int v = p[i];
               p[i] = v & 0xFF00FF00 | (v & 0xFF) << 16 | v >> 16 & 0xFF;
            }
         });
         this.abgr = true;
      }
      return this.pixels;
   }

   // ---------------------------------------------------------------- logo

   private void buildLogoLayers(int[] logo) {
      boolean[] socket = close(floodSocket(logo), 3);
      int socketCount = 0;
      for (boolean b : socket) {
         if (b) {
            socketCount++;
         }
      }
      // A leak into the shield would mean the measured geometry no longer matches the art.
      this.eyeValid = socketCount > 1500 && socketCount < 22000;
      if (!this.eyeValid) {
         Arrays.fill(socket, false);
      }

      this.eyeTop = LOGO;
      this.eyeBottom = -1;
      Arrays.fill(this.rowMinX, -1);
      Arrays.fill(this.rowMaxX, -1);

      for (int y = 0; y < LOGO; y++) {
         for (int x = 0; x < LOGO; x++) {
            int i = y * LOGO + x;
            if (socket[i]) {
               this.eyeTop = Math.min(this.eyeTop, y);
               this.eyeBottom = Math.max(this.eyeBottom, y);
               if (this.rowMinX[y] < 0) {
                  this.rowMinX[y] = x;
               }
               this.rowMaxX[y] = x;
            }
         }
      }
      this.buildEyeLayers(logo, socket);
      this.eyeMid = IRIS_CY + 1.5F;
      if (this.eyeBottom < this.eyeTop) {
         this.eyeTop = this.eyeBottom = (int) this.eyeMid;
      }

      // Eyelid skin: a teal lid that darkens towards the crease.
      float half = Math.max(1.0F, Math.max(this.eyeMid - this.eyeTop, this.eyeBottom - this.eyeMid));
      for (int y = 0; y < LOGO; y++) {
         float crease = Math.abs(y + 0.5F - this.eyeMid) / half;
         float shade = 0.72F + 0.28F * smooth(crease);
         int lid = rgb(0x1D * shade, 0x5C * shade, 0x68 * shade);
         for (int x = 0; x < LOGO; x++) {
            put(EYELID, x, y, socket[y * LOGO + x] ? 0xFF000000 | lid : lid);
         }
      }

      this.buildIris(logo);
   }

   /**
    * FRAME (the logo with the inner socket punched out), SOCKET (what shows through the hole) and the
    * SILHOUETTE used by the light sweep, all at {@link #LOGO_SCALE}x from an edge-aware upscale of the art
    * so the whole emblem is as crisp as the procedurally drawn iris. The punch-out is a level set of a
    * smoothed distance-to-lid field (the lid art is stair-stepped pixel art) and the socket's dark iris bed
    * is drawn analytically, so neither edge steps along the logo's texel grid when magnified.
    */
   private void buildEyeLayers(int[] logo, boolean[] socket) {
      int size = LOGO * LOGO_SCALE;
      int[] hd = new LogoUpscaler(logo).render(LOGO_SCALE, EDGE_AA);
      float[] lidDistance = lidDistance(logo, socket);
      for (int pass = 0; pass < 3; pass++) {
         boxBlur(lidDistance, LOGO, 3);
      }
      int sx0 = SOCKET_OX * LOGO_SCALE;
      int sy0 = SOCKET_OY * LOGO_SCALE;
      IntStream.range(0, size).parallel().forEach(y -> {
         for (int x = 0; x < size; x++) {
            float lx = (x + 0.5F) / LOGO_SCALE;
            float ly = (y + 0.5F) / LOGO_SCALE;
            int p = hd[y * size + x];
            int rgb = p & 0x00FFFFFF;
            float alpha = (p >>> 24) / 255.0F;
            boolean inSocket = socket[(int) ly * LOGO + (int) lx];
            put(SILHOUETTE, x, y, silhouette(p));

            float hole = inSocket ? coverage(sampleField(lidDistance, lx, ly) - LID_TUCK) : 0.0F;
            // RGB is kept under the hole so filtering at its edge never pulls in black.
            put(FRAME, x, y, rgb | Math.round(alpha * (1.0F - hole) * 255.0F) << 24);

            int bx = x - sx0;
            int by = y - sy0;
            if (bx >= 0 && by >= 0 && bx < SOCKET.w && by < SOCKET.h) {
               if (inSocket) {
                  float bed = coverage(IRIS_RIM + 0.75F - dist(lx, ly, IRIS_CX, IRIS_CY));
                  put(SOCKET, bx, by, 0xFF000000 | lerpRgb(rgb, SOCKET_DARK, bed));
               } else {
                  put(SOCKET, bx, by, SOCKET_DARK & 0x00FFFFFF);
               }
            }
         }
      });
   }

   /** Bilinear sample of a LOGO x LOGO scalar field at logo-space (x, y). */
   private static float sampleField(float[] field, float x, float y) {
      float fx = x - 0.5F;
      float fy = y - 0.5F;
      int x0 = (int) Math.floor(fx);
      int y0 = (int) Math.floor(fy);
      float tx = fx - x0;
      float ty = fy - y0;
      float sum = 0.0F;
      for (int k = 0; k < 4; k++) {
         int sx = clamp(x0 + (k & 1), 0, LOGO - 1);
         int sy = clamp(y0 + (k >> 1), 0, LOGO - 1);
         sum += field[sy * LOGO + sx] * ((k & 1) == 1 ? tx : 1.0F - tx) * ((k >> 1) == 1 ? ty : 1.0F - ty);
      }
      return sum;
   }

   private boolean[] floodSocket(int[] logo) {
      boolean[] seen = new boolean[LOGO * LOGO];
      if (isLidWall(logo[SOCKET_SEED_Y * LOGO + SOCKET_SEED_X])) {
         return seen;
      }
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      int seed = SOCKET_SEED_Y * LOGO + SOCKET_SEED_X;
      seen[seed] = true;
      queue.add(seed);
      while (!queue.isEmpty()) {
         int i = queue.poll();
         int x = i % LOGO;
         int y = i / LOGO;
         if (y <= EYE_MIN_Y || y >= EYE_MAX_Y) {
            // Escaped the eye vertically: treat the art as unrecognised.
            Arrays.fill(seen, false);
            return seen;
         }
         int[] next = {i - 1, i + 1, i - LOGO, i + LOGO};
         for (int n : next) {
            int nx = n % LOGO;
            if (nx < EYE_MIN_X || nx > EYE_MAX_X) {
               continue;
            }
            if (!seen[n] && !isLidWall(logo[n])) {
               seen[n] = true;
               queue.add(n);
            }
         }
      }
      // The iris ring is cyan like the lids, so it blocks the fill; add the whole iris disc.
      for (int y = 0; y < LOGO; y++) {
         for (int x = 0; x < LOGO; x++) {
            if (dist(x + 0.5F, y + 0.5F, IRIS_CX, IRIS_CY) <= IRIS_RIM + 0.75F) {
               seen[y * LOGO + x] = true;
            }
         }
      }
      return seen;
   }

   /** Morphological closing: absorbs thin outline slivers the flood fill could not enter. */
   private static boolean[] close(boolean[] mask, int radius) {
      return morph(morph(mask, radius, true), radius, false);
   }

   private static boolean[] morph(boolean[] mask, int radius, boolean dilate) {
      boolean[] out = new boolean[mask.length];
      for (int y = 0; y < LOGO; y++) {
         for (int x = 0; x < LOGO; x++) {
            boolean result = !dilate;
            search:
            for (int dy = -radius; dy <= radius; dy++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  if (dx * dx + dy * dy > radius * radius) {
                     continue;
                  }
                  int sx = x + dx;
                  int sy = y + dy;
                  boolean v = sx >= 0 && sy >= 0 && sx < LOGO && sy < LOGO && mask[sy * LOGO + sx];
                  if (dilate && v) {
                     result = true;
                     break search;
                  }
                  if (!dilate && !v) {
                     result = false;
                     break search;
                  }
               }
            }
            out[y * LOGO + x] = result;
         }
      }
      return out;
   }

   private static boolean isLidWall(int p) {
      int a = p >>> 24;
      int r = p >> 16 & 255;
      int g = p >> 8 & 255;
      return a < 128 || (g > 140 && r < 110);
   }

   /** Euclidean distance from each socket pixel to the nearest eyelid (cyan wall outside the iris). */
   private static float[] lidDistance(int[] logo, boolean[] socket) {
      float[] out = new float[LOGO * LOGO];
      int[] lids = new int[LOGO * LOGO];
      int count = 0;
      for (int y = 1; y < LOGO - 1; y++) {
         for (int x = 1; x < LOGO - 1; x++) {
            int i = y * LOGO + x;
            if (socket[i] || !isLidWall(logo[i])) {
               continue;
            }
            if (socket[i - 1] || socket[i + 1] || socket[i - LOGO] || socket[i + LOGO]) {
               lids[count++] = i;
            }
         }
      }
      for (int i = 0; i < out.length; i++) {
         if (!socket[i]) {
            continue;
         }
         float px = i % LOGO;
         float py = i / LOGO;
         float best = Float.MAX_VALUE;
         for (int k = 0; k < count; k++) {
            float dx = lids[k] % LOGO - px;
            float dy = lids[k] / LOGO - py;
            float d = dx * dx + dy * dy;
            if (d < best) {
               best = d;
            }
         }
         out[i] = (float) Math.sqrt(best);
      }
      return out;
   }

   /**
    * The iris, redrawn analytically at {@link #IRIS_SCALE}x from the art's measured geometry: a dark bed,
    * the cyan ring and its round highlight. The art hides the top of the iris under the lid, so copying
    * its texels left a seam there and stepped edges once magnified; colours are still taken from the art.
    */
   private void buildIris(int[] logo) {
      int ring = logo[Math.round(IRIS_CY + (RING_INNER + RING_OUTER) * 0.5F) * LOGO + (int) IRIS_CX] & 0x00FFFFFF;
      int pupil = logo[Math.round(IRIS_CY + RING_INNER * 0.5F) * LOGO + (int) IRIS_CX] & 0x00FFFFFF;
      for (int y = 0; y < IRIS.h; y++) {
         for (int x = 0; x < IRIS.w; x++) {
            float lx = IRIS_OX + (x + 0.5F) / IRIS_SCALE;
            float ly = IRIS_OY + (y + 0.5F) / IRIS_SCALE;
            float d = dist(lx, ly, IRIS_CX, IRIS_CY);
            float band = Math.min(coverage(RING_OUTER - d), coverage(d - RING_INNER));
            float highlight = coverage(HIGHLIGHT_R - dist(lx, ly, HIGHLIGHT_X, HIGHLIGHT_Y));
            float cyan = Math.max(band, highlight);
            float alpha = clamp01(IRIS_RIM + 0.5F - d);
            put(IRIS, x, y, lerpRgb(pupil, ring, cyan) | Math.round(alpha * 255.0F) << 24);
         }
      }
   }

   private static int silhouette(int p) {
      int a = p >>> 24;
      float luma = ((p >> 16 & 255) * 0.3F + (p >> 8 & 255) * 0.59F + (p & 255) * 0.11F) / 255.0F;
      float v = a / 255.0F * (0.2F + 0.8F * luma * luma);
      return 0x00FFFFFF | Math.round(v * 255.0F) << 24;
   }

   private void buildGlow(int[] logo) {
      int n = GLOW.w;
      float[] alpha = new float[n * n];
      float offset = n * (1.0F - GLOW_SCALE) * 0.5F;
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            float lx = (x + 0.5F - offset) / GLOW_SCALE;
            float ly = (y + 0.5F - offset) / GLOW_SCALE;
            alpha[y * n + x] = sampleAlpha(logo, lx, ly);
         }
      }
      for (int pass = 0; pass < 3; pass++) {
         boxBlur(alpha, n, 7);
      }
      float max = 0.0F;
      for (float v : alpha) {
         max = Math.max(max, v);
      }
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            float v = max > 0.0F ? alpha[y * n + x] / max : 0.0F;
            float edge = edgeFade(x, y, n, n);
            put(GLOW, x, y, 0x00FFFFFF | Math.round(clamp01(v * edge) * 255.0F) << 24);
         }
      }
   }

   private static float sampleAlpha(int[] logo, float x, float y) {
      int ix = (int) Math.floor(x);
      int iy = (int) Math.floor(y);
      if (ix < 0 || iy < 0 || ix >= LOGO || iy >= LOGO) {
         return 0.0F;
      }
      return (logo[iy * LOGO + ix] >>> 24) / 255.0F;
   }

   private static void boxBlur(float[] data, int n, int radius) {
      float[] tmp = new float[data.length];
      float norm = 1.0F / (radius * 2 + 1);
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            float sum = 0.0F;
            for (int k = -radius; k <= radius; k++) {
               int sx = x + k;
               if (sx >= 0 && sx < n) {
                  sum += data[y * n + sx];
               }
            }
            tmp[y * n + x] = sum * norm;
         }
      }
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            float sum = 0.0F;
            for (int k = -radius; k <= radius; k++) {
               int sy = y + k;
               if (sy >= 0 && sy < n) {
                  sum += tmp[sy * n + x];
               }
            }
            data[y * n + x] = sum * norm;
         }
      }
   }

   // ---------------------------------------------------------------- small sprites

   private void buildSprites() {
      for (int y = 0; y < DOT.h; y++) {
         for (int x = 0; x < DOT.w; x++) {
            float r = dist(x + 0.5F, y + 0.5F, DOT.w * 0.5F, DOT.h * 0.5F) / (DOT.w * 0.5F);
            float a = r >= 1.0F ? 0.0F : (float) Math.exp(-r * r * 4.0F) * sq(1.0F - r * r);
            putWhite(DOT, x, y, a);
         }
      }
      for (int y = 0; y < SPARK.h; y++) {
         for (int x = 0; x < SPARK.w; x++) {
            float dx = (x + 0.5F - SPARK.w * 0.5F) / (SPARK.w * 0.5F);
            float dy = (y + 0.5F - SPARK.h * 0.5F) / (SPARK.h * 0.5F);
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            float falloff = r >= 1.0F ? 0.0F : sq(1.0F - r);
            float streak = (float) (Math.exp(-Math.abs(dx) * 3.0 - dy * dy * 900.0) + Math.exp(-Math.abs(dy) * 3.0 - dx * dx * 900.0));
            float core = (float) Math.exp(-r * r * 60.0);
            float halo = (float) Math.exp(-r * r * 9.0) * 0.35F;
            putWhite(SPARK, x, y, clamp01((streak * 0.9F + core + halo) * falloff));
         }
      }
      for (int y = 0; y < WHITE.h; y++) {
         for (int x = 0; x < WHITE.w; x++) {
            putWhite(WHITE, x, y, 1.0F);
         }
      }
      for (int y = 0; y < GRADIENT.h; y++) {
         for (int x = 0; x < GRADIENT.w; x++) {
            float t = (x + 0.5F) / GRADIENT.w;
            putWhite(GRADIENT, x, y, 0.18F + 0.82F * (float) Math.pow(t, 1.8));
         }
      }
      for (int y = 0; y < VIGNETTE.h; y++) {
         for (int x = 0; x < VIGNETTE.w; x++) {
            float dx = (x + 0.5F) / VIGNETTE.w * 2.0F - 1.0F;
            float dy = (y + 0.5F) / VIGNETTE.h * 2.0F - 1.0F;
            float r = (float) Math.sqrt(dx * dx + dy * dy) / 1.4142F;
            float a = smoothstep(0.28F, 1.0F, r);
            put(VIGNETTE, x, y, Math.round(a * 0.92F * 255.0F) << 24);
         }
      }
      for (int y = 0; y < RADAR.h; y++) {
         for (int x = 0; x < RADAR.w; x++) {
            float dx = x + 0.5F - RADAR.w * 0.5F;
            float dy = y + 0.5F - RADAR.h * 0.5F;
            float r = (float) Math.sqrt(dx * dx + dy * dy) / (RADAR.w * 0.5F - 1.0F);
            float angle = (float) ((Math.atan2(dy, dx) / (Math.PI * 2.0) + 1.0) % 1.0);
            float sweep = (float) Math.pow(angle, 4.0);
            float radial = smoothstep(0.08F, 0.35F, r) * (1.0F - smoothstep(0.82F, 1.0F, r));
            putWhite(RADAR, x, y, sweep * radial);
         }
      }
   }

   // ---------------------------------------------------------------- rings

   private void buildRings() {
      fillRadial(RING_A, (r, a) -> {
         float v = band(r, 236.0F, 0.9F) * 0.55F;
         // 24 long dashes
         v = Math.max(v, band(r, 225.5F, 3.2F) * dashes(a, r, 24, 0.62) * 0.95F);
         // 120 ticks, every 10th one long
         int tickIndex = (int) (a / (Math.PI * 2.0) * 120.0);
         float tickLen = tickIndex % 10 == 0 ? range(r, 240.0F, 252.0F) : range(r, 241.0F, 246.5F) * 0.55F;
         v = Math.max(v, ticks(a, r, 120, 0.8F) * tickLen);
         return v;
      });
      fillRadial(RING_B, (r, a) -> {
         // Three bright arcs that brighten towards their leading edge.
         double phase = a / (Math.PI * 2.0) * 3.0 % 1.0;
         float head = (float) Math.min(1.0, phase / 0.62);
         float v = band(r, 204.0F, 2.4F) * dashes(a, r, 3, 0.62) * (0.3F + 0.7F * head * head);
         // A thin inner track broken into quarters.
         v = Math.max(v, band(r, 192.0F, 0.75F) * dashes(a + 0.045, r, 4, 0.95) * 0.4F);
         return v;
      });
      fillRadial(RING_C, (r, a) -> {
         // 180 tiny dots on the outer track plus a faint hairline.
         double cell = a / (Math.PI * 2.0) * 180.0 % 1.0;
         float along = (float) ((cell - 0.5) * Math.PI * 2.0 * 246.0 / 180.0);
         float dot = (float) Math.exp(-(along * along + (r - 246.0F) * (r - 246.0F)) / 1.4);
         float hair = band(r, 232.0F, 0.6F) * 0.28F;
         return Math.max(dot * 0.85F, hair);
      });
      fillRadial(SHOCK, (r, a) -> {
         float ring = (float) Math.exp(-sq((r - 236.0F) / 5.0F));
         float inner = r < 236.0F ? (float) Math.pow(r / 236.0F, 6.0) * 0.18F : 0.0F;
         return clamp01(ring + inner) * (1.0F - smoothstep(246.0F, 254.0F, r));
      });
   }

   /** Anti-aliased coverage of {@code count} evenly spaced dashes, each {@code duty} of its period. */
   private static float dashes(double angle, float r, int count, double duty) {
      double period = Math.PI * 2.0 / count;
      double t = ((angle % period) + period) % period;
      float s = (float) (t * r);
      float len = (float) (duty * period * r);
      float total = (float) (period * r);
      float signed = s < len ? Math.min(s, len - s) : -Math.min(s - len, total - s);
      return clamp01(signed + 0.5F);
   }

   /** Anti-aliased coverage of {@code count} thin radial ticks centred in each period. */
   private static float ticks(double angle, float r, int count, float halfWidth) {
      double period = Math.PI * 2.0 / count;
      double t = ((angle % period) + period) % period;
      float s = (float) ((t - period * 0.5) * r);
      return clamp01(halfWidth + 0.5F - Math.abs(s));
   }

   private interface RadialFn {
      float apply(float r, double angle);
   }

   private void fillRadial(Region region, RadialFn fn) {
      float cx = region.w * 0.5F;
      float cy = region.h * 0.5F;
      IntStream.range(0, region.h).parallel().forEach(y -> {
         for (int x = 0; x < region.w; x++) {
            float dx = x + 0.5F - cx;
            float dy = y + 0.5F - cy;
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r > region.w * 0.5F - 1.5F) {
               putWhite(region, x, y, 0.0F);
               continue;
            }
            double angle = Math.atan2(dy, dx);
            if (angle < 0.0) {
               angle += Math.PI * 2.0;
            }
            putWhite(region, x, y, clamp01(fn.apply(r, angle)));
         }
      });
   }

   private void buildRays() {
      fillRadial(RAYS, (r, a) -> {
         float n = (float) (0.5 + 0.22 * Math.cos(a * 7.0 + 0.4) + 0.16 * Math.cos(a * 13.0 + 1.9)
               + 0.12 * Math.cos(a * 23.0 + 4.2) + 0.08 * Math.cos(a * 41.0 + 2.6) + 0.06 * Math.cos(a * 67.0 + 0.7));
         float rays = (float) Math.pow(clamp01(n), 3.2);
         float rn = r / 254.0F;
         float radial = smoothstep(0.0F, 0.18F, rn) * sq(1.0F - rn);
         return rays * radial * 1.6F;
      });
   }

   // ---------------------------------------------------------------- glyphs

   private void buildGlyphs(int[] sheet) {
      for (int y = 0; y < GLYPHS.h; y++) {
         for (int x = 0; x < GLYPHS.w; x++) {
            putWhite(GLYPHS, x, y, (sheet[y * GLYPHS.w + x] >>> 24) / 255.0F);
         }
      }
      int capTop = GLYPH_CELL_H;
      int capBottom = 0;
      int h = GLYPH_CHARS.indexOf('H');
      for (int i = 0; i < GLYPH_CHARS.length(); i++) {
         int cellX = i % GLYPH_COLUMNS * GLYPH_CELL_W;
         int cellY = i / GLYPH_COLUMNS * GLYPH_CELL_H;
         int left = GLYPH_CELL_W;
         int right = 0;
         for (int y = 0; y < GLYPH_CELL_H; y++) {
            for (int x = 0; x < GLYPH_CELL_W; x++) {
               if ((sheet[(cellY + y) * GLYPHS.w + cellX + x] >>> 24) > 40) {
                  left = Math.min(left, x);
                  right = Math.max(right, x + 1);
                  if (i == h) {
                     capTop = Math.min(capTop, y);
                     capBottom = Math.max(capBottom, y + 1);
                  }
               }
            }
         }
         this.glyphInkLeft[i] = right > left ? left : 0;
         this.glyphInkRight[i] = right > left ? right : 0;
      }
      this.glyphCapHeight = Math.max(0, capBottom - capTop);
      this.glyphsValid = this.glyphCapHeight > 4.0F;
   }

   public static int glyphIndex(char c) {
      return GLYPH_CHARS.indexOf(Character.toUpperCase(c));
   }

   public static int glyphU(int index) {
      return GLYPHS.x + index % GLYPH_COLUMNS * GLYPH_CELL_W;
   }

   public static int glyphV(int index) {
      return GLYPHS.y + index / GLYPH_COLUMNS * GLYPH_CELL_H;
   }

   public static float glyphBaseline() {
      return GLYPH_BASELINE;
   }

   // ---------------------------------------------------------------- helpers

   private void put(Region region, int x, int y, int argb) {
      this.pixels[(region.y + y) * WIDTH + region.x + x] = argb;
   }

   private void putWhite(Region region, int x, int y, float alpha) {
      put(region, x, y, 0x00FFFFFF | Math.round(clamp01(alpha) * 255.0F) << 24);
   }

   private static float band(float r, float center, float halfWidth) {
      return clamp01(halfWidth + 0.5F - Math.abs(r - center));
   }

   private static float range(float r, float from, float to) {
      return clamp01(Math.min(r - from + 0.5F, to - r + 0.5F));
   }

   private static float edgeFade(int x, int y, int w, int h) {
      int d = Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y));
      return clamp01(d / 6.0F);
   }

   /** Anti-aliased coverage of a shape whose signed distance (positive inside, logo units) is {@code d}. */
   private static float coverage(float d) {
      return clamp01(d / EDGE_AA + 0.5F);
   }

   private static int lerpRgb(int a, int b, float t) {
      return rgb((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t,
            (a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t,
            (a & 255) + ((b & 255) - (a & 255)) * t);
   }

   private static float dist(float x, float y, float cx, float cy) {
      float dx = x - cx;
      float dy = y - cy;
      return (float) Math.sqrt(dx * dx + dy * dy);
   }

   private static int rgb(float r, float g, float b) {
      return clamp(Math.round(r), 0, 255) << 16 | clamp(Math.round(g), 0, 255) << 8 | clamp(Math.round(b), 0, 255);
   }

   static float smoothstep(float a, float b, float x) {
      float t = clamp01((x - a) / (b - a));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float smooth(float x) {
      return smoothstep(0.0F, 1.0F, x);
   }

   private static float sq(float v) {
      return v * v;
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : Math.min(v, hi);
   }
}
