package net.bdew.generators.menu;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Expected strings are what the original showed in its screens (checked against a running 1.20.1 client). */
class GuiFormatTest {
    private static String s(double x) { return GuiFormat.shortNumber(x, Locale.US); }

    @Test void largeNumbersHaveNoDecimalsAndThousandsSeparators() {
        assertEquals("346", s(346.4));
        assertEquals("2,356", s(2356.4));
        assertEquals("100", s(100));
        assertEquals("52", s(52));
    }

    @Test void numbersBetweenOneAndTenHaveOneDecimal() {
        assertEquals("9.9", s(9.9));
        assertEquals("1.1", s(1.1));
        assertEquals("5.5", s(5.5));
    }

    @Test void numbersUpToOneHaveTwoDecimals() {
        assertEquals("0.09", s(0.0909));
        assertEquals("0.10", s(0.1));
        assertEquals("0.08", s(0.0833));
        assertEquals("1.00", s(1.0), "exactly one still counts as 'up to one'");
        assertEquals("0.00", s(0));
    }

    @Test void negativeValuesKeepTheSameTwoDecimals() {
        assertEquals("-0.50", s(-0.5));
    }
}
