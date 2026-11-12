package de.leon.lifesteal.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Num {

    public static final double HARD_LIMIT = 1_000_000_000_000_000.0;

    private static final String[] SUFFIX = {"", "k", "m", "b", "t", "q"};

    private Num() {
    }

    public static String format(double value) {
        DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.DOWN);
        double shown = value;
        int index = 0;
        while (Math.abs(shown) >= 1000 && index < SUFFIX.length - 1) {
            shown /= 1000;
            index++;
        }
        return df.format(shown) + Msg.sc(SUFFIX[index]);
    }

    public static String full(double value) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }

    public static double parse(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT)
                .replace("$", "").replace("€", "").replace(" ", "").replace("_", "");
        if (s.isEmpty()) return -1;

        double multiplier = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> multiplier = 1e3;
            case 'm' -> multiplier = 1e6;
            case 'b' -> multiplier = 1e9;
            case 't' -> multiplier = 1e12;
            case 'q' -> multiplier = 1e15;
            default -> { }
        }
        if (multiplier > 1) s = s.substring(0, s.length() - 1);

        if (multiplier > 1) {
            s = s.replace(',', '.');
        } else if (s.matches("\\d{1,3}([.,]\\d{3})+")) {
            s = s.replace(".", "").replace(",", "");
        } else {
            s = s.replace(',', '.');
        }
        if (s.isEmpty() || s.length() > 20 || !s.matches("\\d+(\\.\\d+)?")) return -1;

        try {
            double value = Double.parseDouble(s) * multiplier;
            if (!Double.isFinite(value) || value <= 0 || value > HARD_LIMIT) return -1;
            return value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static long parseLong(String input) {
        double v = parse(input);
        if (v < 1) return -1;
        return (long) Math.floor(v);
    }
}
