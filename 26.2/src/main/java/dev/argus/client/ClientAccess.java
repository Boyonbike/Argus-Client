package dev.argus.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.utils.inventory.ItemRegistryJsonAdapter;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ClientAccess {
   Logger logger = LoggerFactory.getLogger("Argus Client");
   Minecraft argusclientMinecraft = Minecraft.getInstance();
   Gson gson = new GsonBuilder()
      .registerTypeAdapter(Item.class, new ItemRegistryJsonAdapter.ItemAdapter())
      .registerTypeAdapter(BlockItem.class, new ItemRegistryJsonAdapter.BlockItemAdapter())
      .setPrettyPrinting()
      .create();
   EventBus eventBus = new EventBus();
   /**
    * Shared background pool. Bounded to the machine's core count (it used to be unbounded) with daemon,
    * slightly lower-priority threads so background work never starves the render thread or blocks exit.
    */
   ExecutorService executorService = createSharedExecutor();

   private static ExecutorService createSharedExecutor() {
      int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
      java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
      java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
         threads, threads, 30L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "Argus worker " + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
         }
      );
      pool.allowCoreThreadTimeOut(true);
      return pool;
   }

   default boolean isWorldUnavailable() {
      return argusclientMinecraft.player == null || argusclientMinecraft.level == null;
   }

   static String getVersion() {
      return ArgusClient.VERSION;
   }

   static boolean isLocalPlayer(Object candidate) {
      return argusclientMinecraft.player == candidate;
   }
}
