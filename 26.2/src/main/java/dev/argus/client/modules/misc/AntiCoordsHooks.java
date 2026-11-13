package dev.argus.client.modules.misc;

/**
 * Tells the AntiCoords mixins whether the module exists yet. Block seeds and offsets are read during bootstrap, before
 * Minecraft is constructed; calling into {@link AntiCoordsModule} then would initialise ClientAccess (through Module)
 * and freeze its Minecraft field as null. This class deliberately touches nothing else.
 */
public final class AntiCoordsHooks {
   public static volatile boolean ready;

   private AntiCoordsHooks() {}
}
