package dev.argus.client.render.loading;

import java.util.stream.IntStream;

/**
 * Edge-aware upscale of the flat-shaded 256x256 Argus logo.
 *
 * <p>The art is a handful of flat colours (plus gentle gradients) with anti-aliased edges. Every pixel is
 * split into soft memberships of those colour classes (and "transparent"); the membership fields are
 * interpolated bicubically and each output texel takes the dominant class, anti-aliased against the
 * runner-up by its signed distance to where the two are equal. Edges therefore land where the source
 * anti-aliasing put them, but stay one texel sharp at any scale instead of smearing like bilinear
 * filtering. Colours are taken locally from the art, so gradients survive.
 *
 * <p>Plain Java so it can be exercised offline.
 */
final class LogoUpscaler {
   private static final int N = LoadingAtlas.LOGO;
   /** Opaque colour classes; class {@code CLASSES} is "transparent". */
   private static final int CLASSES = 4;
   private static final int CLEAR = CLASSES;
   /** Largest per-channel step to a neighbour for a pixel to count as flat colour (not an edge). */
   private static final int FLAT_STEP = 22;
   /** Squared RGB error above which an edge pixel is not a blend of the classes around it. */
   private static final float MAX_RESIDUAL = 24.0F * 24.0F;

   private final int[] palette = new int[CLASSES];
   /** Membership of each class per logo pixel, [class][pixel]. */
   private final float[][] member = new float[CLASSES + 1][N * N];
   /** Blurred, weighted colour sums per class ({r, g, b, weight} interleaved per pixel). */
   private final float[][] colour = new float[CLASSES][N * N * 4];

   LogoUpscaler(int[] logo) {
      boolean[] flat = flatPixels(logo);
      this.buildPalette(logo, flat);
      this.decompose(logo, flat);
   }

   /**
    * Renders the logo at {@code scale} texels per logo unit into a (256 * scale)^2 ARGB image (straight
    * alpha; RGB is kept under transparent texels so filtering never pulls in black).
    *
    * @param aa width of the anti-aliased edge transition, in logo units
    */
   int[] render(int scale, float aa) {
      int size = N * scale;
      // Separable Catmull-Rom: the integer scale means each output column/row has fixed taps and weights.
      int[] tap = new int[size];
      float[] weight = new float[size * 4];
      for (int o = 0; o < size; o++) {
         float f = (o + 0.5F) / scale - 0.5F;
         int base = (int) Math.floor(f);
         float t = f - base;
         tap[o] = base;
         for (int i = 0; i < 4; i++) {
            weight[o * 4 + i] = cubic(i - 1 - t);
         }
      }
      float[][] fields = new float[CLASSES + 1][];
      IntStream.rangeClosed(0, CLASSES).parallel().forEach(k -> fields[k] = upsample(this.member[k], size, tap, weight));

      int[] out = new int[size * size];
      float soft = aa * scale;
      IntStream.range(0, size).parallel().forEach(y -> {
         float[] c1 = new float[3];
         float[] c2 = new float[3];
         for (int x = 0; x < size; x++) {
            int i = y * size + x;
            int k1 = 0;
            int k2 = -1;
            for (int k = 1; k <= CLASSES; k++) {
               if (fields[k][i] > fields[k1][i]) {
                  k2 = k1;
                  k1 = k;
               } else if (k2 < 0 || fields[k][i] > fields[k2][i]) {
                  k2 = k;
               }
            }
            // Signed distance (texels) to the k1/k2 boundary from the local slope of their difference.
            float diff = fields[k1][i] - fields[k2][i];
            int xl = Math.max(x - 1, 0);
            int xr = Math.min(x + 1, size - 1);
            int yu = Math.max(y - 1, 0);
            int yd = Math.min(y + 1, size - 1);
            float gx = (fields[k1][y * size + xr] - fields[k2][y * size + xr] - fields[k1][y * size + xl] + fields[k2][y * size + xl]) / Math.max(1, xr - xl);
            float gy = (fields[k1][yd * size + x] - fields[k2][yd * size + x] - fields[k1][yu * size + x] + fields[k2][yu * size + x]) / Math.max(1, yd - yu);
            float slope = Math.max((float) Math.sqrt(gx * gx + gy * gy), 1.0E-4F);
            float cover = LoadingAtlas.clamp01(0.5F + diff / slope / soft);

            float lx = (x + 0.5F) / scale;
            float ly = (y + 0.5F) / scale;
            boolean o1 = k1 != CLEAR;
            boolean o2 = k2 != CLEAR;
            if (o1) {
               this.classColour(k1, lx, ly, c1);
            }
            if (o2) {
               this.classColour(k2, lx, ly, c2);
            }
            float alpha;
            if (o1 && o2) {
               alpha = 1.0F;
               for (int ch = 0; ch < 3; ch++) {
                  c1[ch] = c2[ch] + (c1[ch] - c2[ch]) * cover;
               }
            } else if (o1) {
               alpha = cover;
            } else if (o2) {
               alpha = 1.0F - cover;
               System.arraycopy(c2, 0, c1, 0, 3);
            } else {
               alpha = 0.0F;
               c1[0] = c1[1] = c1[2] = 0.0F;
            }
            out[i] = Math.round(alpha * 255.0F) << 24 | rgb(c1[0], c1[1], c1[2]);
         }
      });
      return out;
   }

   /** Upsamples an N x N field to size x size: rows first (N x size), then columns. */
   private static float[] upsample(float[] field, int size, int[] tap, float[] weight) {
      float[] rows = new float[N * size];
      for (int y = 0; y < N; y++) {
         int row = y * N;
         for (int x = 0; x < size; x++) {
            int base = tap[x];
            float sum = 0.0F;
            for (int i = 0; i < 4; i++) {
               sum += field[row + clamp(base - 1 + i, 0, N - 1)] * weight[x * 4 + i];
            }
            rows[y * size + x] = sum;
         }
      }
      float[] out = new float[size * size];
      for (int y = 0; y < size; y++) {
         int base = tap[y];
         int r0 = clamp(base - 1, 0, N - 1) * size;
         int r1 = clamp(base, 0, N - 1) * size;
         int r2 = clamp(base + 1, 0, N - 1) * size;
         int r3 = clamp(base + 2, 0, N - 1) * size;
         float w0 = weight[y * 4];
         float w1 = weight[y * 4 + 1];
         float w2 = weight[y * 4 + 2];
         float w3 = weight[y * 4 + 3];
         int o = y * size;
         for (int x = 0; x < size; x++) {
            out[o + x] = rows[r0 + x] * w0 + rows[r1 + x] * w1 + rows[r2 + x] * w2 + rows[r3 + x] * w3;
         }
      }
      return out;
   }

   /** The local colour of class {@code k} near logo-space (x, y). */
   private void classColour(int k, float x, float y, float[] out) {
      float[] field = this.colour[k];
      float fx = x - 0.5F;
      float fy = y - 0.5F;
      int x0 = (int) Math.floor(fx);
      int y0 = (int) Math.floor(fy);
      float tx = fx - x0;
      float ty = fy - y0;
      float r = 0.0F;
      float g = 0.0F;
      float b = 0.0F;
      float w = 0.0F;
      for (int q = 0; q < 4; q++) {
         int sx = clamp(x0 + (q & 1), 0, N - 1);
         int sy = clamp(y0 + (q >> 1), 0, N - 1);
         float f = ((q & 1) == 1 ? tx : 1.0F - tx) * ((q >> 1) == 1 ? ty : 1.0F - ty);
         int o = (sy * N + sx) * 4;
         r += field[o] * f;
         g += field[o + 1] * f;
         b += field[o + 2] * f;
         w += field[o + 3] * f;
      }
      if (w > 1.0E-3F) {
         out[0] = r / w;
         out[1] = g / w;
         out[2] = b / w;
      } else {
         int p = this.palette[k];
         out[0] = p >> 16 & 255;
         out[1] = p >> 8 & 255;
         out[2] = p & 255;
      }
   }

   // ---------------------------------------------------------------- analysis

   /** Opaque pixels whose 8 neighbours are all (nearly) the same colour. */
   private static boolean[] flatPixels(int[] logo) {
      boolean[] flat = new boolean[N * N];
      for (int y = 1; y < N - 1; y++) {
         for (int x = 1; x < N - 1; x++) {
            int p = logo[y * N + x];
            if ((p >>> 24) < 250) {
               continue;
            }
            boolean ok = true;
            for (int dy = -1; dy <= 1 && ok; dy++) {
               for (int dx = -1; dx <= 1; dx++) {
                  int q = logo[(y + dy) * N + x + dx];
                  if ((q >>> 24) < 250 || step(p, q) > FLAT_STEP) {
                     ok = false;
                     break;
                  }
               }
            }
            flat[y * N + x] = ok;
         }
      }
      return flat;
   }

   /** k-means over the flat pixels, seeded by farthest-point picking so it is deterministic. */
   private void buildPalette(int[] logo, boolean[] flat) {
      int count = 0;
      for (boolean f : flat) {
         if (f) {
            count++;
         }
      }
      float[][] samples = new float[Math.max(count, 1)][3];
      int s = 0;
      for (int i = 0; i < flat.length; i++) {
         if (flat[i]) {
            int p = logo[i];
            samples[s][0] = p >> 16 & 255;
            samples[s][1] = p >> 8 & 255;
            samples[s][2] = p & 255;
            s++;
         }
      }
      float[][] centres = new float[CLASSES][3];
      float[] mean = new float[3];
      for (float[] v : samples) {
         for (int c = 0; c < 3; c++) {
            mean[c] += v[c] / samples.length;
         }
      }
      float[] near = new float[samples.length];
      java.util.Arrays.fill(near, Float.MAX_VALUE);
      float[] from = mean;
      for (int k = 0; k < CLASSES; k++) {
         int best = 0;
         for (int i = 0; i < samples.length; i++) {
            near[i] = Math.min(near[i], dist2(samples[i], from));
            if (near[i] > near[best]) {
               best = i;
            }
         }
         centres[k] = samples[best].clone();
         from = centres[k];
         if (k == 0) {
            java.util.Arrays.fill(near, Float.MAX_VALUE);
            for (int i = 0; i < samples.length; i++) {
               near[i] = dist2(samples[i], from);
            }
         }
      }
      int[] label = new int[samples.length];
      for (int iter = 0; iter < 24; iter++) {
         float[][] sum = new float[CLASSES][4];
         for (int i = 0; i < samples.length; i++) {
            label[i] = nearest(centres, samples[i]);
            float[] t = sum[label[i]];
            t[0] += samples[i][0];
            t[1] += samples[i][1];
            t[2] += samples[i][2];
            t[3]++;
         }
         for (int k = 0; k < CLASSES; k++) {
            if (sum[k][3] > 0.0F) {
               for (int c = 0; c < 3; c++) {
                  centres[k][c] = sum[k][c] / sum[k][3];
               }
            }
         }
      }
      for (int k = 0; k < CLASSES; k++) {
         this.palette[k] = rgb(centres[k][0], centres[k][1], centres[k][2]);
      }
   }

   /**
    * Splits every pixel into class memberships: flat pixels belong to their nearest class, edge pixels
    * are read as a blend along the closest pair of the classes found flat around them (that is what
    * anti-aliasing produced; a half-way slate/cyan blend must not be mistaken for the teal class it
    * happens to resemble), and alpha is the complement of "transparent".
    */
   private void decompose(int[] logo, boolean[] flat) {
      float[][] pal = new float[CLASSES][3];
      for (int k = 0; k < CLASSES; k++) {
         pal[k][0] = this.palette[k] >> 16 & 255;
         pal[k][1] = this.palette[k] >> 8 & 255;
         pal[k][2] = this.palette[k] & 255;
      }
      float[] v = new float[3];
      int[] flatClass = new int[N * N];
      for (int i = 0; i < N * N; i++) {
         if (flat[i]) {
            int p = logo[i];
            v[0] = p >> 16 & 255;
            v[1] = p >> 8 & 255;
            v[2] = p & 255;
            flatClass[i] = nearest(pal, v);
         } else {
            flatClass[i] = -1;
         }
      }
      for (int i = 0; i < N * N; i++) {
         int p = logo[i];
         float a = (p >>> 24) / 255.0F;
         this.member[CLEAR][i] = 1.0F - a;
         if (a <= 0.0F) {
            continue;
         }
         v[0] = p >> 16 & 255;
         v[1] = p >> 8 & 255;
         v[2] = p & 255;
         int ki = nearest(pal, v);
         int kj = ki;
         float t = 0.0F;
         if (!flat[i]) {
            // Classes found flat nearby explain anti-aliased edges; thin strokes have no flat pixels of their
            // own, so fall back to every class when the nearby ones leave the colour unexplained.
            float[] fit = new float[3];
            if (blend(pal, v, nearbyClasses(flatClass, i % N, i / N), fit) > MAX_RESIDUAL) {
               blend(pal, v, (1 << CLASSES) - 1, fit);
            }
            ki = (int) fit[0];
            kj = (int) fit[1];
            t = fit[2];
         }
         this.member[ki][i] += a * (1.0F - t);
         this.member[kj][i] += a * t;

         // Colour evidence: flat pixels carry the art's real (possibly gradient) colour; edge pixels only
         // vouch weakly for the palette colour so blends never tint a class.
         if (flat[i]) {
            addColour(this.colour[ki], i, v, a);
         } else {
            addColour(this.colour[ki], i, pal[ki], 0.05F * a * (1.0F - t));
            if (kj != ki) {
               addColour(this.colour[kj], i, pal[kj], 0.05F * a * t);
            }
         }
      }
      for (float[] field : this.colour) {
         for (int pass = 0; pass < 2; pass++) {
            blur4(field, 2);
         }
      }
   }

   /**
    * Best explanation of colour {@code v} as a blend of two allowed classes (or one).
    *
    * @param out receives {first class, second class, blend amount towards the second}
    * @return squared RGB distance left unexplained
    */
   private static float blend(float[][] pal, float[] v, int allowed, float[] out) {
      int single = nearest(pal, v, allowed);
      float best = dist2(v, pal[single]);
      out[0] = single;
      out[1] = single;
      out[2] = 0.0F;
      for (int m = 0; m < CLASSES; m++) {
         for (int n = m + 1; n < CLASSES; n++) {
            if ((allowed & 1 << m) == 0 || (allowed & 1 << n) == 0) {
               continue;
            }
            float dx0 = pal[n][0] - pal[m][0];
            float dy0 = pal[n][1] - pal[m][1];
            float dz0 = pal[n][2] - pal[m][2];
            float len = dx0 * dx0 + dy0 * dy0 + dz0 * dz0;
            if (len <= 0.0F) {
               continue;
            }
            float u = LoadingAtlas.clamp01(((v[0] - pal[m][0]) * dx0 + (v[1] - pal[m][1]) * dy0 + (v[2] - pal[m][2]) * dz0) / len);
            float dx = pal[m][0] + dx0 * u - v[0];
            float dy = pal[m][1] + dy0 * u - v[1];
            float dz = pal[m][2] + dz0 * u - v[2];
            float d = dx * dx + dy * dy + dz * dz;
            if (d < best - 1.0F) {
               best = d;
               out[0] = m;
               out[1] = n;
               out[2] = u;
            }
         }
      }
      return best;
   }

   /** Bit set of the classes with flat pixels close to (x, y); every class if none are found. */
   private static int nearbyClasses(int[] flatClass, int x, int y) {
      for (int radius = 3; radius <= 6; radius += 3) {
         int set = 0;
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
               int sx = x + dx;
               int sy = y + dy;
               if (sx >= 0 && sy >= 0 && sx < N && sy < N && flatClass[sy * N + sx] >= 0) {
                  set |= 1 << flatClass[sy * N + sx];
               }
            }
         }
         if (Integer.bitCount(set) >= 2 || radius == 6 && set != 0) {
            return set;
         }
      }
      return (1 << CLASSES) - 1;
   }

   private static void addColour(float[] field, int i, float[] rgb, float w) {
      int o = i * 4;
      field[o] += rgb[0] * w;
      field[o + 1] += rgb[1] * w;
      field[o + 2] += rgb[2] * w;
      field[o + 3] += w;
   }

   /** Box blur of an interleaved 4-channel N x N field. */
   private static void blur4(float[] data, int radius) {
      float[] tmp = new float[data.length];
      for (int y = 0; y < N; y++) {
         for (int x = 0; x < N; x++) {
            for (int c = 0; c < 4; c++) {
               float sum = 0.0F;
               for (int k = -radius; k <= radius; k++) {
                  sum += data[(y * N + clamp(x + k, 0, N - 1)) * 4 + c];
               }
               tmp[(y * N + x) * 4 + c] = sum;
            }
         }
      }
      for (int y = 0; y < N; y++) {
         for (int x = 0; x < N; x++) {
            for (int c = 0; c < 4; c++) {
               float sum = 0.0F;
               for (int k = -radius; k <= radius; k++) {
                  sum += tmp[(clamp(y + k, 0, N - 1) * N + x) * 4 + c];
               }
               data[(y * N + x) * 4 + c] = sum;
            }
         }
      }
   }

   private static float cubic(float d) {
      d = Math.abs(d);
      if (d < 1.0F) {
         return 1.5F * d * d * d - 2.5F * d * d + 1.0F;
      }
      if (d < 2.0F) {
         return -0.5F * d * d * d + 2.5F * d * d - 4.0F * d + 2.0F;
      }
      return 0.0F;
   }

   private static int nearest(float[][] centres, float[] v) {
      return nearest(centres, v, (1 << centres.length) - 1);
   }

   private static int nearest(float[][] centres, float[] v, int allowed) {
      int best = -1;
      for (int k = 0; k < centres.length; k++) {
         if ((allowed & 1 << k) != 0 && (best < 0 || dist2(v, centres[k]) < dist2(v, centres[best]))) {
            best = k;
         }
      }
      return Math.max(best, 0);
   }

   private static int step(int p, int q) {
      return Math.max(Math.abs((p >> 16 & 255) - (q >> 16 & 255)),
            Math.max(Math.abs((p >> 8 & 255) - (q >> 8 & 255)), Math.abs((p & 255) - (q & 255))));
   }

   private static float dist2(float[] a, float[] b) {
      float dx = a[0] - b[0];
      float dy = a[1] - b[1];
      float dz = a[2] - b[2];
      return dx * dx + dy * dy + dz * dz;
   }

   private static int rgb(float r, float g, float b) {
      return clamp(Math.round(r), 0, 255) << 16 | clamp(Math.round(g), 0, 255) << 8 | clamp(Math.round(b), 0, 255);
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : Math.min(v, hi);
   }
}
