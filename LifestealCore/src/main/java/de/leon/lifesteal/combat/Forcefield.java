package de.leon.lifesteal.combat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Forcefield {

    boolean isForcefield(Location location);

    void update(Player player);

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
