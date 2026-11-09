package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;

public abstract class ChestStealerController implements ClientAccess {
   protected final ChestStealerModule module;

   public ChestStealerController(ChestStealerModule module) {
      this.module = module;
   }
}
