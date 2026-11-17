package de.leon.lifesteal.worldguard;

/**
 * Legacy constant holder retained for source compatibility.
 * KOTH flags are now native LifestealCore region flags and do not register
 * themselves with the external WorldGuard plugin.
 */
@Deprecated
public final class WorldGuardKothFlags {
    public static final String PVP_ARENA = "KOTH_PVP_ARENA";
    public static final String CAPTURE = "KOTH_CAPTURE";
    public static final String PVP_ARENA_NAME = PVP_ARENA;
    public static final String CAPTURE_NAME = CAPTURE;

    private WorldGuardKothFlags() { }
}
