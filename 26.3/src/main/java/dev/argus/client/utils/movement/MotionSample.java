package dev.argus.client.utils.movement;

public record MotionSample(
   double motionX, double motionY, double motionZ, double accelerationX, double accelerationZ, boolean collisionBelow, boolean sneaking, boolean fallFlying
) {
}
