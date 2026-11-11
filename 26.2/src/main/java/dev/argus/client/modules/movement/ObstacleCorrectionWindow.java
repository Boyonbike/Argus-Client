package dev.argus.client.modules.movement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ObstacleCorrectionWindow {
   private final List<Long> corrections = Collections.synchronizedList(new ArrayList<>());
   private final int windowMillis;
   private final int maximumCorrections;

   public void clear() {
      this.corrections.clear();
   }

   public void pruneExpired() {
      this.corrections.removeIf(timestamp -> System.currentTimeMillis() > timestamp + this.windowMillis);
   }

   public ObstacleCorrectionWindow(int maximumCorrections) {
      this(1000, maximumCorrections);
   }

   public ObstacleCorrectionWindow(int windowMillis, int maximumCorrections) {
      this.windowMillis = windowMillis;
      this.maximumCorrections = maximumCorrections;
   }

   public void recordCorrection() {
      this.corrections.add(System.currentTimeMillis());
   }

   public boolean exceedsLimit() {
      return this.corrections.size() > this.maximumCorrections;
   }
}
