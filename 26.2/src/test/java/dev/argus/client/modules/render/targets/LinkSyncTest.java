package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinkSyncTest {
   /** A field that counts writes, to catch ping-pong. */
   private static final class Box implements LinkSync.Field {
      String value;
      int writes;

      Box(String value) {
         this.value = value;
      }

      @Override
      public String read() {
         return this.value;
      }

      @Override
      public void write(String value) {
         this.value = value;
         this.writes++;
      }
   }

   @Test
   void copyIsOneShot() {
      Box esp = new Box("red");
      Box tracers = new Box("blue");
      LinkSync.copy(List.of(new LinkSync.Pair(esp, tracers)), true);
      assertEquals("red", tracers.value);
      esp.value = "green";
      assertEquals("red", tracers.value);
   }

   @Test
   void linkTakesTheChosenSide() {
      Box esp = new Box("red");
      Box tracers = new Box("blue");
      new LinkSync(List.of(new LinkSync.Pair(esp, tracers))).link(false);
      assertEquals("blue", esp.value);
   }

   @Test
   void changesFlowBothWaysWithoutPingPong() {
      Box esp = new Box("a");
      Box tracers = new Box("b");
      LinkSync sync = new LinkSync(List.of(new LinkSync.Pair(esp, tracers)));
      sync.link(true);
      tracers.writes = 0;

      esp.value = "esp-change";
      assertEquals(1, sync.sync());
      assertEquals("esp-change", tracers.value);
      assertEquals(0, sync.sync());

      tracers.value = "tracers-change";
      assertEquals(1, sync.sync());
      assertEquals("tracers-change", esp.value);
      assertEquals(0, sync.sync());
      assertEquals(0, sync.sync());
      assertEquals(1, esp.writes);
      assertEquals(1, tracers.writes);
   }

   @Test
   void leftWinsWhenBothChangeInOneSync() {
      Box esp = new Box("a");
      Box tracers = new Box("a");
      LinkSync sync = new LinkSync(List.of(new LinkSync.Pair(esp, tracers)));
      sync.link(true);
      esp.value = "esp";
      tracers.value = "tracers";
      sync.sync();
      assertEquals("esp", esp.value);
      assertEquals("esp", tracers.value);
   }

   @Test
   void manyPairsSyncIndependently() {
      Box a1 = new Box("1"), a2 = new Box("1");
      Box b1 = new Box("x"), b2 = new Box("x");
      LinkSync sync = new LinkSync(List.of(new LinkSync.Pair(a1, a2), new LinkSync.Pair(b1, b2)));
      sync.link(true);
      a1.value = "2";
      b2.value = "y";
      assertEquals(2, sync.sync());
      assertEquals("2", a2.value);
      assertEquals("y", b1.value);
   }
}
