package dev.argus.client.access;

public interface LocalPlayerTickAccess {
   void sendMovementPacketsWrapper();

   void superTick();

   void resetEvent();

   void argusclient$updateServer(float var1, float var2);

   void resetRotations();
}
