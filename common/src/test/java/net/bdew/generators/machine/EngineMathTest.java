package net.bdew.generators.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Expected values are worked out from the original Scala controllers with the default config. */
class EngineMathTest {
    private static final double MAX_RPM = 5000, MULT = .025, EXP = -2, BASE_DRAG = .2, SPIN_UP = 1, DENSITY = 2;

    @Test void clampPrefersUpperBoundThenLowerBound() {
        assertEquals(5, EngineMath.clamp(9, 0, 5));
        assertEquals(0, EngineMath.clamp(-3, 0, 5));
        assertEquals(3, EngineMath.clamp(3, 0, 5));
    }

    @Test void minTreatsNonFiniteAndNegativeAsNothingToDo() {
        assertEquals(0, EngineMath.min(1, Double.NaN, 2));
        assertEquals(0, EngineMath.min(-4, 2));
        assertEquals(1.5, EngineMath.min(3, 1.5, 7));
    }

    @Test void rpmDeltaAtStandstillIsMaxRpmTimesMultiplier() {
        assertEquals(125, EngineMath.rpmDelta(0, MAX_RPM, MULT, EXP, 1), 1e-9);
        assertEquals(125 * 1.4, EngineMath.rpmDelta(0, MAX_RPM, MULT, EXP, 1.4), 1e-9);
    }

    /** First tick of a tier 1 turbine (100 FE/t) with full steam: the original reaches 100 RPM, not 125. */
    @Test void spinUpFromStandstillMatchesOriginal() {
        double maxSteam = 100 / DENSITY;
        double delta = EngineMath.rpmDelta(0, MAX_RPM, MULT, EXP, 1);
        double rpm = 0 - delta * BASE_DRAG; // -25, not floored yet
        double average = maxSteam / 20;     // one full tick in a 20 tick window
        assertEquals(100, EngineMath.spinUp(rpm, average, maxSteam, MAX_RPM, delta, SPIN_UP), 1e-9);
    }

    @Test void spinUpIsLimitedByAvailableSteam() {
        double maxSteam = 50, delta = 125;
        // target is 250 RPM; a step of 125 from 400 RPM would overshoot, so the lower bound (current speed) wins
        assertEquals(400, EngineMath.spinUp(400, 2.5, maxSteam, MAX_RPM, delta, SPIN_UP), 1e-9);
        // plenty of steam from 400 RPM: limited by delta
        assertEquals(525, EngineMath.spinUp(400, maxSteam, maxSteam, MAX_RPM, delta, SPIN_UP), 1e-9);
    }

    @Test void heatTransferIsLimitedByTheScarcestOfBudgetInputAndSpace() {
        assertEquals(2, EngineMath.heatTransfer(10, 10, 100, 5, 1), 1e-9);   // input: 10 / 5
        assertEquals(10, EngineMath.heatTransfer(10, 1000, 1000, 5, 1), 1e-9); // budget
        assertEquals(3, EngineMath.heatTransfer(10, 1000, 3, 5, 1), 1e-9);   // space: 3 / 1
        assertEquals(0, EngineMath.heatTransfer(10, 10, 100, 0, 1));
    }

    /**
     * Tier 2 turbine (150 FE/t, inertia 1.1) with steam always available. Both the original 1.20.1 mod and this port,
     * run on dedicated servers, gave 3260 RPM after 960 ticks and 4545 RPM after 1860 ticks.
     */
    @Test void steamTurbineRampMatchesOriginalServerRun() {
        double maxOutput = 150, inertia = 1.1, maxSteam = maxOutput / DENSITY;
        double[] history = new double[20];
        double rpm = 0;
        for (int tick = 1; tick <= 1860; tick++) {
            double delta = EngineMath.rpmDelta(rpm, MAX_RPM, MULT, EXP, inertia);
            if (rpm > 1) rpm -= delta * .75;
            rpm -= delta * BASE_DRAG;
            history[tick % 20] = Math.ceil(maxSteam);
            double average = java.util.Arrays.stream(history).sum() / 20;
            if (average > 0) rpm = EngineMath.spinUp(rpm, average, maxSteam, MAX_RPM, delta, SPIN_UP);
            if (rpm < 1) rpm = 0;
            if (tick == 960) assertEquals(3260, rpm, 1);
        }
        assertEquals(4545, rpm, 1);
    }
}
