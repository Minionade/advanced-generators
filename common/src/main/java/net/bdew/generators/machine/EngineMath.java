package net.bdew.generators.machine;

/** Pure arithmetic shared by both loaders, independently testable without starting Minecraft. */
public final class EngineMath {
    private EngineMath() {}
    public static double min(double... values) {
        double result = Double.POSITIVE_INFINITY;
        for (double v : values) { if (!Double.isFinite(v)) return 0; result = Math.min(result, v); }
        return Math.max(0, result);
    }
    public static double heatTransfer(double heatBudget, double input, double outputSpace, double inPerHeat, double outPerHeat) {
        if (inPerHeat <= 0 || outPerHeat <= 0) return 0;
        return min(heatBudget, input / inPerHeat, outputSpace / outPerHeat);
    }
    public static double rpmDelta(double rpm, double maxRpm, double multiplier, double exponent, double inertia) {
        return maxRpm * multiplier * Math.exp(exponent * Math.max(0, rpm) / maxRpm) * inertia;
    }
    /** Same semantics as BDLib's {@code Misc.clamp}: the upper bound wins, then the lower bound. */
    public static double clamp(double value, double min, double max) {
        return value > max ? max : value < min ? min : value;
    }
    /**
     * One spin-up step of the steam turbine. {@code rpm} is the speed after drag was applied, which may be
     * negative: the original only floors it afterwards, and that is what limits spin-up from a standstill.
     */
    public static double spinUp(double rpm, double steamAverage, double maxSteamPerTick, double maxRpm, double delta, double spinUpMultiplier) {
        return clamp(steamAverage / maxSteamPerTick * maxRpm, rpm, rpm + delta * spinUpMultiplier);
    }
}
