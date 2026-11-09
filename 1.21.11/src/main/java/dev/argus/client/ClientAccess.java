package dev.argus.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.utils.inventory.ItemRegistryJsonAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
   ExecutorService executorService = Executors.newCachedThreadPool();

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
