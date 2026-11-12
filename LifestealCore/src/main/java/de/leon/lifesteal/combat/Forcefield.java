package de.leon.lifesteal.combat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Arena regions (WorldGuard flag "combat-forcefield"). Without WorldGuard nothing is an arena. */
public interface Forcefield {

    boolean isForcefield(Location location);

    /** Shows red glass at the arena border near the player. */
    void update(Player player);

    /** Removes the red glass again. */
    void clear(Player player);

    Forcefield NONE = new Forcefield() {
        @Override
        public boolean isForcefield(Location location) {
            return false;
        }

        @Override
        public void update(Player player) {
        }

        @Override
        public void clear(Player player) {
        }
    };
}
