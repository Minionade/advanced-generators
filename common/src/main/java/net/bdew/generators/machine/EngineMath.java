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
}
