package net.bdew.generators.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number and unit formatting for the machine screens, the same rules the original (BDLib) used. */
public final class GuiFormat {
    private GuiFormat() {}

    /** More than 10: no decimals; more than 1: one decimal; otherwise two. Thousands are separated. */
    public static String shortNumber(double x) { return shortNumber(x, Locale.getDefault(Locale.Category.FORMAT)); }

    public static String shortNumber(double x, Locale locale) {
        String pattern = x > 10 ? "#,##0" : x > 1 ? "#,##0.0" : "#,##0.00";
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(x).replace(' ', ' ');
    }

    public static String roundNumber(double x) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT))).format(x).replace(' ', ' ');
    }

    public static MutableComponent unit(String unit) { return Component.translatable("bdlib.unit." + unit); }
    public static MutableComponent amount(double n, String unit) { return Component.translatable("bdlib.format.amount.unit", shortNumber(n), unit(unit)); }
    public static MutableComponent perTick(double n, String unit) { return Component.translatable("bdlib.format.rate.unit", shortNumber(n), unit(unit)); }
    public static MutableComponent perTick(double n) { return Component.translatable("bdlib.format.rate", shortNumber(n)); }
    public static MutableComponent delta(double n, String unit) { return Component.translatable("bdlib.format.rate.unit", (n > 0 ? "+" : "") + shortNumber(n), unit(unit)); }
    public static MutableComponent energyPerTick(double n) { return perTick(n, "fe"); }
    public static MutableComponent fluidPerTick(double n) { return perTick(n, "mb"); }
}
