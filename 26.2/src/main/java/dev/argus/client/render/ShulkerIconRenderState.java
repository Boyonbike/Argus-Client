package dev.argus.client.render;

/** Style travels with each submitted GUI item until Minecraft composites its texture. */
public interface ShulkerIconRenderState {
   Integer argus$iconColour();

   void argus$iconColour(Integer colour);
}
