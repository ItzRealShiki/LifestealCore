package de.leon.lifesteal.combat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.PlayerData;

import java.util.UUID;

public final class AltChecker {

    public static final String BYPASS_PERMISSION = "Combat.AltBypass";

    private final LifestealCore plugin;

    public AltChecker(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("alt-check.enabled", true);
    }

    public boolean sameIp(UUID a, UUID b) {
        if (a.equals(b)) return false;
        PlayerData da = plugin.data().cached(a);
        PlayerData dbb = plugin.data().cached(b);
        if (da == null || dbb == null) return false;
        String ha = da.ipHash();
        return ha != null && ha.equals(dbb.ipHash());
    }
}
