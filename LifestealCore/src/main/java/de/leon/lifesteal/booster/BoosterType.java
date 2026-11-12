package de.leon.lifesteal.booster;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum BoosterType {
    MONEY_X3("x3 Money", Rarity.EPIC, Material.PURPLE_DYE, Category.NORMAL, 3.0, 1.0, 0),
    GEMS_X2_5("x2.50 Gems", Rarity.ROYALE, Material.ORANGE_DYE, Category.NORMAL, 1.0, 2.5, 0),
    HASTE_X3("x3 Haste Effect", Rarity.RARE, Material.BLUE_DYE, Category.NORMAL, 1.0, 1.0, 3),

    P_STRENGTH_X1("x1 Strength", Rarity.EPIC, Material.PURPLE_DYE, Category.PASSIVE, 1.0, 1.0, 1),
    P_STRENGTH_X2("x2 Strength", Rarity.ROYALE, Material.ORANGE_DYE, Category.PASSIVE, 1.0, 1.0, 2),
    P_SPEED_X1("x1 Speed", Rarity.RARE, Material.BLUE_DYE, Category.PASSIVE, 1.0, 1.0, 1),
    P_SPEED_X2("x2 Speed", Rarity.EPIC, Material.PURPLE_DYE, Category.PASSIVE, 1.0, 1.0, 2),
    P_MONEY_X2("x2 Money", Rarity.EPIC, Material.PURPLE_DYE, Category.PASSIVE, 2.0, 1.0, 0),
    P_GEMS_X1_5("x1.50 Gems", Rarity.EPIC, Material.PURPLE_DYE, Category.PASSIVE, 1.0, 1.5, 0),
    P_GEMS_X2("x2 Gems", Rarity.ROYALE, Material.ORANGE_DYE, Category.PASSIVE, 1.0, 2.0, 0),
    P_FIRE_RES("x1 Fire Resistance", Rarity.RARE, Material.BLUE_DYE, Category.PASSIVE, 1.0, 1.0, 1),

    S_MONEY_X2("x2 Money", Rarity.EPIC, Material.PURPLE_DYE, Category.SERVER, 2.0, 1.0, 0),
    S_MONEY_X3("x3 Money", Rarity.ROYALE, Material.ORANGE_DYE, Category.SERVER, 3.0, 1.0, 0),
    S_GEMS_X2("x2 Gems", Rarity.EPIC, Material.PURPLE_DYE, Category.SERVER, 1.0, 2.0, 0),
    S_GEMS_X1_5("x1.50 Gems", Rarity.RARE, Material.BLUE_DYE, Category.SERVER, 1.0, 1.5, 0),
    S_XP_X3("x3 XP Booster", Rarity.EPIC, Material.PURPLE_DYE, Category.SERVER, 1.0, 1.0, 0),
    S_RANDOM_TICK_X10("x10 Random Tick Speed", Rarity.ROYALE, Material.ORANGE_DYE, Category.SERVER, 1.0, 1.0, 0);

    private final String displayName;
    private final Rarity rarity;
    private final Material dyeMaterial;
    private final Category category;
    private final double moneyMultiplier;
    private final double gemsMultiplier;
    private final int amplifier;

    BoosterType(String displayName, Rarity rarity, Material dyeMaterial, Category category,
                double moneyMultiplier, double gemsMultiplier, int amplifier) {
        this.displayName = displayName;
        this.rarity = rarity;
        this.dyeMaterial = dyeMaterial;
        this.category = category;
        this.moneyMultiplier = moneyMultiplier;
        this.gemsMultiplier = gemsMultiplier;
        this.amplifier = amplifier;
    }

    public String getDisplayName() { return displayName; }
    public Rarity getRarity() { return rarity; }
    public Material getDyeMaterial() { return dyeMaterial; }
    public Category getCategory() { return category; }
    public double getMoneyMultiplier() { return moneyMultiplier; }
    public double getGemsMultiplier() { return gemsMultiplier; }
    public int getAmplifier() { return amplifier; }

    public static BoosterType parse(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public enum Category {
        NORMAL("Booster"),
        PASSIVE("P. Booster"),
        SERVER("S. Booster");

        private final String prefix;
        Category(String prefix) { this.prefix = prefix; }
        public String getPrefix() { return prefix; }
    }

    public enum Rarity {
        RARE("Rare", NamedTextColor.BLUE),
        EPIC("Epic", NamedTextColor.DARK_PURPLE),
        ROYALE("Royale", NamedTextColor.GOLD);

        private final String name;
        private final NamedTextColor color;

        Rarity(String name, NamedTextColor color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public NamedTextColor getColor() { return color; }
    }
}
