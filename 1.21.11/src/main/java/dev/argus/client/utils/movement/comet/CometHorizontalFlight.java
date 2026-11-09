/*
 * Ported from Comet (Boyonabike's Meteor addon): the horizontal half of AdaptiveFlightEngine.step and
 * AdaptiveFlightStep.move, with Comet's terrain envelope/corridor, close-object control, maneuver mode,
 * server glide guard and vertical blend deliberately left out.
 */

package dev.argus.client.utils.movement.comet;

import dev.argus.client.utils.movement.comet.AdaptiveFlightController.Motion;
import java.util.Objects;

/**
 * Comet's horizontal flight: cruise, unlimited Sprint boost and the transition between them.
 *
 * <ul>
 *   <li>Cruise: momentum approaches the keys' heading at the cruise speed by {@code acceleration} a tick and
 *       slows by the braking policy when the request is slower or reversed.</li>
 *   <li>Boost: VolytraFly's boost curve ({@link BoostRamp}) engages from the current speed (never below
 *       {@value #BOOST_START} blocks/tick), climbs towards the boost top and stops at the boost cap. While
 *       boosting and not braking, speed is the ramp speed on the assisted heading.</li>
 *   <li>Boost to cruise: the ramp releases and momentum brakes down to cruise with Comet's speed-scaled braking
 *       (gentle near cruise, much firmer at boost speed) rather than snapping.</li>
 *   <li>Setbacks rewind the ramp {@value #BOOST_REWIND_TICKS} ticks and hold it {@value #BOOST_SETBACK_HOLD}
 *       tick; a teleport stops controlled movement.</li>
 * </ul>
 */
public final class CometHorizontalFlight {
   /** VolytraFly's 60 b/s boost start and 300 b/s curve top, in blocks per tick (Comet's BOOST_START/BOOST_TOP). */
   public static final double BOOST_START = 2.999;
   public static final double BOOST_TOP = 14.999;
   /** Comet's boost-rewind-ticks and boost-setback-hold defaults. */
   public static final int BOOST_REWIND_TICKS = 15;
   public static final int BOOST_SETBACK_HOLD = 1;
   /**
    * Comet's default lookahead (64, within FlightCorridor.MAX_DISTANCE) less its 0.35 terrain margin. Comet plans
    * its braking and scales turn assistance against this horizon; only those two uses are kept here.
    */
   public static final double HORIZON = 64.0 - 0.35;

   /**
    * Speeds are blocks per tick. {@code boostEnabled} is Comet's sprint-boost with unlimited-boost on;
    * {@code boostCap} is its boost-max-bps / 20. {@code boostTop} is the fastest the boost curve goes:
    * Volytra Fly's Horizontal Speed, {@link #BOOST_TOP} by default.
    */
   public record Settings(double acceleration, double braking, double turnRate, double cruise,
                          boolean boostEnabled, double boostCap, double boostRampStep, double boostTop) {
      /** VolytraFly's default top speed ({@link #BOOST_TOP}) as the boost curve's top. */
      public Settings(double acceleration, double braking, double turnRate, double cruise,
                      boolean boostEnabled, double boostCap, double boostRampStep) {
         this(acceleration, braking, turnRate, cruise, boostEnabled, boostCap, boostRampStep, BOOST_TOP);
      }
   }

   private final AdaptiveFlightController controller =
      new AdaptiveFlightController(new AdaptiveFlightController.Settings(0.15, 0.3, 15.0));
   private final BoostRamp ramp = new BoostRamp(new BoostRamp.Settings(0, 0, 1, 0, 0));
   private boolean boosting;

   /**
    * One tick of horizontal flight. {@code forward}/{@code right} are -1, 0 or 1 from the movement keys and
    * {@code boostKey} is the Sprint key. Returns the horizontal motion (y is always zero).
    */
   public Motion step(Settings s, int forward, int right, float yawDegrees, boolean boostKey) {
      Objects.requireNonNull(s, "settings");
      // Comet's controllerSettings: with unlimited boost, braking is planned from the ramp's top speed.
      controller.configure(new AdaptiveFlightController.Settings(s.acceleration(), s.braking(), s.turnRate(),
         HORIZON, s.boostEnabled() ? s.boostTop() : 0));
      controller.tick();
      // Comet's configureRamps: the boost cap stops the ramp without reshaping its curve.
      ramp.configure(new BoostRamp.Settings(BOOST_START, s.boostTop(), s.boostRampStep(), BOOST_REWIND_TICKS,
         BOOST_SETBACK_HOLD, Finite.clamp(s.boostCap(), 0, Double.MAX_VALUE, 0)));

      Motion direction = requested(forward, right, yawDegrees, 1.0);
      boolean moving = direction.horizontalLength() > 0;
      Motion previous = controller.output();

      // AdaptiveFlightEngine.updateBoost (horizontal ramp).
      if (s.boostEnabled() && boostKey && moving) {
         // Comet: boost engages from the current speed (never below BOOST_START) and climbs VolytraFly's
         // curve. Jumping straight to the cap gives the server one huge move and rubber-bands.
         if (ramp.engaged()) ramp.advance();
         else ramp.engage(previous.horizontalLength());
      } else {
         ramp.disengage();
      }
      boosting = moving && ramp.speed() > 0;

      // AdaptiveFlightStep.move, horizontal axis.
      double maximum = boosting ? ramp.speed() : Math.max(0.0, s.cruise());
      Motion target = moving ? direction.scaled(maximum) : Motion.ZERO;
      double stepping = Math.max(maximum, previous.horizontalLength());
      Motion output = controller.step(target, stepping, boostKey && s.boostEnabled(),
         Braking.safeSpeed(s.braking(), HORIZON), boosting);
      // Boost's hard ceiling is the ramp itself, so a rewound ramp lowers speed at once.
      double horizontal = output.horizontalLength();
      if (boosting && horizontal > ramp.speed()) output = output.scaled(ramp.speed() / horizontal);
      controller.accept(output);
      return output;
   }

   /**
    * The server rejects total displacement, so while boosting the whole 3D move is held to
    * {@link ServerMoveLimit#SINGLE}; otherwise unlimited.
    */
   public double totalCeiling() {
      return boosting ? ServerMoveLimit.SINGLE : Double.POSITIVE_INFINITY;
   }

   /** The horizontal motion actually sent this tick, after later limits (chunks, crash guard, caps). */
   public void sent(double x, double z) {
      controller.accept(new Motion(x, 0.0, z));
   }

   /** A wall let only {@code (dx, dz)} through: momentum and the boost ramp keep only that. */
   public void collided(double dx, double dz) {
      double moved = Math.hypot(dx, dz);
      if (moved >= controller.output().horizontalLength() - 1.0E-3) return;
      controller.accept(new Motion(dx, 0.0, dz));
      ramp.limitTo(moved);
   }

   /** A server position correction: a setback rewinds and holds the boost ramp; a teleport stops flight. */
   public void setback(boolean teleport) {
      if (teleport) {
         controller.reset();
         ramp.disengage();
         boosting = false;
         return;
      }
      ramp.corrected();
   }

   public void reset() {
      controller.reset();
      ramp.reset();
      boosting = false;
   }

   public Motion output() { return controller.output(); }
   public boolean boosting() { return boosting; }
   public double boostSpeed() { return ramp.speed(); }

   /** Comet's key-to-velocity mapping: the requested horizontal velocity at {@code speed} blocks per tick. */
   public static Motion requested(int forward, int right, float yawDegrees, double speed) {
      double length = Math.hypot(forward, right);
      if (length == 0.0 || !(speed > 0.0)) return Motion.ZERO;
      double yaw = Math.toRadians(yawDegrees);
      double x = (-Math.sin(yaw) * forward - Math.cos(yaw) * right) * speed / length;
      double z = (Math.cos(yaw) * forward - Math.sin(yaw) * right) * speed / length;
      return new Motion(x, 0.0, z);
   }
}
