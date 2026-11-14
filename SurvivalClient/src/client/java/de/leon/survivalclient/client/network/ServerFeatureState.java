package de.leon.survivalclient.client.network;

/** Server-authoritative feature flags received from LifestealCore. */
public final class ServerFeatureState {
    private static volatile boolean fastPlace;
    private static volatile boolean fastBreak;
    private static volatile boolean connected;
    private ServerFeatureState() {}
    public static boolean fastPlace() { return fastPlace; }
    public static boolean fastBreak() { return fastBreak; }
    public static boolean connected() { return connected; }
    public static void setFastPlace(boolean value) { fastPlace=value; connected=true; }
    public static void setFastBreak(boolean value) { fastBreak=value; connected=true; }
    public static void reset() { fastPlace=false; fastBreak=false; connected=false; }
}
