package net.bdew.generators.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncChunksTest {
    private static int roundTrip(int value) {
        return SyncChunks.join(SyncChunks.piece(value, 0), SyncChunks.piece(value, 1), SyncChunks.piece(value, 2));
    }

    @Test void smallValuesSurvive() {
        assertEquals(0, roundTrip(0));
        assertEquals(1000, roundTrip(1000));
        assertEquals(32767, roundTrip(32767));
    }

    /** These were cut down to their low 15 bits on the client: 235600 (2,356 RPM) showed as 102 RPM. */
    @Test void valuesAbove15BitsSurvive() {
        assertEquals(32768, roundTrip(32768));
        assertEquals(235600, roundTrip(235600));
        assertEquals(1_100_000, roundTrip(1_100_000));
        assertEquals(Integer.MAX_VALUE, roundTrip(Integer.MAX_VALUE));
    }

    /** Resource ids set bit 30 to say "fluid", which lives in the third piece. */
    @Test void theFluidFlagInTheThirdPieceSurvives() {
        int fluid = (1 << 30) | 1234;
        assertEquals(fluid, roundTrip(fluid));
        assertEquals(1, SyncChunks.piece(fluid, 2));
    }

    @Test void negativeValuesSurvive() {
        assertEquals(-5, roundTrip(-5));
        assertEquals(Integer.MIN_VALUE, roundTrip(Integer.MIN_VALUE));
    }
}
