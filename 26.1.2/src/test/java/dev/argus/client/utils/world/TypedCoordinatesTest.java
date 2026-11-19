package dev.argus.client.utils.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TypedCoordinatesTest {
   @Test
   void acceptsAnySignedNumber() {
      assertEquals(120.0, TypedCoordinates.parse("120"));
      assertEquals(120.0, TypedCoordinates.parse("+120"));
      assertEquals(-4500.0, TypedCoordinates.parse("-4500"));
      assertEquals(-0.5, TypedCoordinates.parse("-.5"));
      assertEquals(64.25, TypedCoordinates.parse(" 64.25 "));
      assertEquals(-1_000_000.0, TypedCoordinates.parse("-1,000,000"));
      assertEquals(29_999_984.0, TypedCoordinates.parse("29999984"));
      assertEquals(-30_000_000.0, TypedCoordinates.parse("-30000000"));
   }

   @Test
   void rejectsWhatIsNotACoordinate() {
      assertNull(TypedCoordinates.parse(""));
      assertNull(TypedCoordinates.parse("-"));
      assertNull(TypedCoordinates.parse("+"));
      assertNull(TypedCoordinates.parse("1e5"));
      assertNull(TypedCoordinates.parse("NaN"));
      assertNull(TypedCoordinates.parse("--5"));
      assertNull(TypedCoordinates.parse("30000001"));
   }

   @Test
   void lettersAreKeptOutWhileTyping() {
      assertTrue(TypedCoordinates.typing(""));
      assertTrue(TypedCoordinates.typing("-"));
      assertTrue(TypedCoordinates.typing("+12."));
      assertTrue(TypedCoordinates.typing("-12.5"));
      assertFalse(TypedCoordinates.typing("12a"));
      assertFalse(TypedCoordinates.typing("1-2"));
      assertFalse(TypedCoordinates.typing("~5"));
   }
}
