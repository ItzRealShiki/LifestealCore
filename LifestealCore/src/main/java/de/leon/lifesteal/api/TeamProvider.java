package de.leon.lifesteal.api;

import java.util.UUID;

public interface TeamProvider {

    boolean sameTeam(UUID a, UUID b);

    default String teamName(UUID player) {
        return null;
    }
}
