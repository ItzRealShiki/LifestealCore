package de.leon.fastplace;

public final class FastPlaceState {
    private static volatile boolean enabled;
    private static volatile boolean fastBreakEnabled;

    private FastPlaceState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isFastBreakEnabled() {
        return fastBreakEnabled;
    }

    public static void setFastBreakEnabled(boolean value) {
        fastBreakEnabled = value;
    }

    public static void reset() {
        enabled = false;
        fastBreakEnabled = false;
    }
}
