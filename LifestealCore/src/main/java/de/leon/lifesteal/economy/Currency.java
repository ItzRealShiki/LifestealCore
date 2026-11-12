package de.leon.lifesteal.economy;

import java.util.Locale;

/** The two currencies of the server. */
public enum Currency {
    MONEY("money", "Money"),
    GEMS("gems", "Gems");

    private final String key;
    private final String display;

    Currency(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public Currency other() {
        return this == MONEY ? GEMS : MONEY;
    }

    /** "money", "m", "$", "gems", "gem", "g" -> currency, otherwise null. */
    public static Currency parse(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "money", "m", "$", "geld" -> MONEY;
            case "gems", "gem", "g" -> GEMS;
            default -> null;
        };
    }
}
