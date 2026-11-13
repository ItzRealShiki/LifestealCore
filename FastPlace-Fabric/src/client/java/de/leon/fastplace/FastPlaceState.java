package de.leon.fastplace;

public final class FastPlaceState {
    private static volatile boolean enabled;

    private FastPlaceState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
