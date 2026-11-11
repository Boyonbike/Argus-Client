package dev.argus.client.utils.inventory;

import java.util.Collection;

public interface RegistryContainer {
   boolean register(Object var1);

   boolean unregister(Object var1);

   Collection<?> getRegistry();
}
