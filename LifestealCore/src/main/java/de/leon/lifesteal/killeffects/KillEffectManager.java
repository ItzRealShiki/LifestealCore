package de.leon.lifesteal.killeffects;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class KillEffectManager {
    public enum Effect {
        NONE("None", Material.BARRIER, "Default", false),
        BLOOD_BURST("Blood Burst", Material.REDSTONE, "Common", false),
        SOUL_RELEASE("Soul Release", Material.SOUL_LANTERN, "Common", false),
        THUNDER_STRIKE("Thunder Strike", Material.LIGHTNING_ROD, "Rare", false),
        VOID_COLLAPSE("Void Collapse", Material.ENDER_PEARL, "Rare", false),
        DIVINE("Divine", Material.GOLDEN_APPLE, "Epic", false),
        BLOOD_MOON("Blood Moon", Material.REDSTONE_BLOCK, "Rare", false),
        BLACK_HOLE("Black Hole", Material.OBSIDIAN, "Legendary", false),
        FROZEN("Frozen", Material.PACKED_ICE, "Common", false),
        METEOR("Meteor", Material.MAGMA_BLOCK, "Epic", false),
        CREEPER_SURPRISE("Creeper Surprise", Material.CREEPER_HEAD, "Epic", true),
        HEAVEN("Heaven", Material.QUARTZ, "Rare", false),
        TOXIC("Toxic", Material.SLIME_BLOCK, "Common", false),
        SHADOW("Shadow", Material.BLACK_CONCRETE, "Rare", false),
        HEARTBREAKER("Heartbreaker", Material.RED_DYE, "Rare", false),
        GLITCH("Glitch", Material.AMETHYST_SHARD, "Epic", false),
        MIMIC_CHEST("Mimic Chest", Material.CHEST, "Legendary", true),
        ANVIL_CRUSH("Anvil Crush", Material.ANVIL, "Epic", true),
        VOID_PORTAL("Void Portal", Material.END_PORTAL_FRAME, "Legendary", true),
        PISTON_TRAP("Piston Trap", Material.STICKY_PISTON, "Epic", true),
        SHULKER_PRISON("Shulker Prison", Material.SHULKER_BOX, "Epic", true),
        DRAGON_EAT("Dragon Eat", Material.DRAGON_HEAD, "Mythic", true),
        TOILET_FLUSH("Toilet Flush", Material.WATER_BUCKET, "Mythic", true),
        GIFT_BOX("Gift Box", Material.CHEST, "Rare", true),
        GRAVE("Grave", Material.PODZOL, "Rare", true);

        private final String title;
        private final Material icon;
        private final String rarity;
        private final boolean animated;

        Effect(String title, Material icon, String rarity, boolean animated) {
            this.title = title;
            this.icon = icon;
            this.rarity = rarity;
            this.animated = animated;
        }
        public String title() { return title; }
        public Material icon() { return icon; }
        public String rarity() { return rarity; }
        public boolean animated() { return animated; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public static Effect byKey(String key) {
            if (key == null) return BLOOD_BURST;
            return Arrays.stream(values()).filter(e -> e.key().equalsIgnoreCase(key)).findFirst().orElse(BLOOD_BURST);
        }
    }

    private final LifestealCore plugin;
    private final File file;
    private final YamlConfiguration data;

    public KillEffectManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kill-effects.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public Effect selected(UUID player) {
        return Effect.byKey(data.getString("players." + player + ".selected", "blood_burst"));
    }

    public void select(Player player, Effect effect) {
        data.set("players." + player.getUniqueId() + ".selected", effect.key());
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save kill effects", e);
        }
    }

    public void playKill(Player killer, Player victim) {
        if (killer == null || victim == null) return;
        Effect effect = selected(killer.getUniqueId());
        if (effect != Effect.NONE) render(killer, victim.getLocation().clone(), victim, effect, false);
    }

    public void preview(Player viewer, Effect effect) {
        render(viewer, viewer.getLocation().clone(), viewer, effect, true);
    }

    private void render(Player owner, Location loc, Player model, Effect effect, boolean preview) {
        if (effect == Effect.NONE) return;
        if (effect.animated()) {
            animated(owner, loc, model, effect, preview);
            return;
        }
        var world = loc.getWorld();
        if (world == null) return;
        switch (effect) {
            case BLOOD_BURST, BLOOD_MOON, HEARTBREAKER -> {
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 65, .8, .8, .8, new Particle.DustOptions(Color.RED, 1.8f));
                world.spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1, 0), 18, .6, .7, .6, .05);
                if (effect == Effect.HEARTBREAKER) world.spawnParticle(Particle.HEART, loc.clone().add(0, 1.4, 0), 14, .7, .6, .7, .05);
            }
            case SOUL_RELEASE, SHADOW -> {
                world.spawnParticle(effect == Effect.SOUL_RELEASE ? Particle.SOUL : Particle.LARGE_SMOKE, loc.clone().add(0, .5, 0), 55, .6, .8, .6, .06);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 25, .5, .7, .5, .02);
            }
            case THUNDER_STRIKE, METEOR -> {
                world.strikeLightningEffect(loc);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 35, .8, .8, .8, .1);
            }
            case VOID_COLLAPSE, BLACK_HOLE, GLITCH -> {
                world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 100, .8, .9, .8, .8);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 35, .5, .6, .5, .1);
            }
            case DIVINE, HEAVEN -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 65, .7, .9, .7, .15);
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.5, 0), 30, .5, 1, .5, .02);
            }
            case FROZEN -> {
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 80, .8, .8, .8, .03);
                world.spawnParticle(Particle.CLOUD, loc.clone(), 25, .6, .2, .6, .02);
            }
            case TOXIC -> {
                world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 65, .8, .8, .8, .1);
                world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 30, .6, .7, .6, .03);
            }
            default -> world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 40, .7, .7, .7, .08);
        }
        world.playSound(loc, sound(effect), .9f, pitch(effect));
    }

    private Sound sound(Effect effect) {
        return switch (effect) {
            case THUNDER_STRIKE, METEOR -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case SOUL_RELEASE, SHADOW -> Sound.ENTITY_WITHER_AMBIENT;
            case VOID_COLLAPSE, BLACK_HOLE, GLITCH -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case DIVINE, HEAVEN -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case FROZEN -> Sound.BLOCK_GLASS_BREAK;
            case TOXIC -> Sound.BLOCK_BREWING_STAND_BREW;
            default -> Sound.ENTITY_PLAYER_ATTACK_CRIT;
        };
    }

    private float pitch(Effect effect) {
        return effect == Effect.GLITCH ? 1.8f : effect == Effect.BLACK_HOLE ? .55f : 1f;
    }

    private void animated(Player owner, Location loc, Player model, Effect effect, boolean preview) {
        var world = loc.getWorld();
        if (world == null) return;
        Material block = switch (effect) {
            case MIMIC_CHEST -> Material.CHEST;
            case ANVIL_CRUSH -> Material.ANVIL;
            case VOID_PORTAL -> Material.END_PORTAL;
            case CREEPER_SURPRISE -> Material.CREEPER_HEAD;
            case PISTON_TRAP -> Material.STICKY_PISTON;
            case SHULKER_PRISON -> Material.PURPLE_SHULKER_BOX;
            case DRAGON_EAT -> Material.DRAGON_HEAD;
            case TOILET_FLUSH -> Material.WATER_CAULDRON;
            case GIFT_BOX -> Material.CHEST;
            case GRAVE -> Material.COARSE_DIRT;
            default -> Material.CHEST;
        };
        ArmorStand mannequin = world.spawn(loc.clone(), ArmorStand.class, stand -> {
            stand.setVisible(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCanPickupItems(false);
            copyEquipment(model, stand);
        });
        BlockDisplay prop = world.spawn(loc.clone().add(0, effect == Effect.ANVIL_CRUSH ? 3.5 : .2, 0), BlockDisplay.class,
                display -> display.setBlock(block.createBlockData()));
        world.playSound(loc, animatedSound(effect), 1f, 1f);
        if (effect == Effect.MIMIC_CHEST || effect == Effect.GIFT_BOX) {
            world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 25, .5, .5, .5, .04);
        } else if (effect == Effect.VOID_PORTAL || effect == Effect.DRAGON_EAT) {
            world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 100, .7, .9, .7, .4);
        } else if (effect == Effect.TOILET_FLUSH) {
            world.spawnParticle(Particle.BUBBLE, loc.clone().add(0, 1, 0), 45, .5, .6, .5, .08);
        } else if (effect == Effect.GRAVE) {
            world.spawnParticle(Particle.BLOCK, loc.clone(), 60, .5, .3, .5, Material.DIRT.createBlockData());
        }

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!prop.isValid() || !mannequin.isValid()) { cancel(); return; }
                tick++;
                if (effect == Effect.ANVIL_CRUSH) {
                    prop.teleport(loc.clone().add(0, Math.max(.2, 3.5 - tick * .28), 0));
                    if (tick == 8) {
                        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, .2, 0), 45, .6, .2, .6, Material.ANVIL.createBlockData());
                        mannequin.remove();
                    }
                } else if (effect == Effect.VOID_PORTAL || effect == Effect.DRAGON_EAT || effect == Effect.TOILET_FLUSH) {
                    mannequin.teleport(mannequin.getLocation().add(0, -.11, 0));
                    world.spawnParticle(Particle.PORTAL, mannequin.getLocation().add(0, 1, 0), 12, .3, .4, .3, .2);
                    if (tick >= 12) mannequin.remove();
                } else if (effect == Effect.PISTON_TRAP) {
                    prop.teleport(loc.clone().add(tick < 6 ? -.8 + tick * .13 : 0, .2, 0));
                    if (tick == 8) mannequin.remove();
                } else if (effect == Effect.SHULKER_PRISON || effect == Effect.CREEPER_SURPRISE || effect == Effect.MIMIC_CHEST || effect == Effect.GIFT_BOX || effect == Effect.GRAVE) {
                    if (tick == 8) mannequin.remove();
                    if (effect == Effect.MIMIC_CHEST && tick >= 4 && tick <= 10) {
                        prop.teleport(loc.clone().add(0, .2 + Math.sin(tick * .5) * .08, 0));
                    }
                }
                if (tick >= 22) {
                    prop.remove();
                    mannequin.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Sound animatedSound(Effect effect) {
        return switch (effect) {
            case MIMIC_CHEST -> Sound.BLOCK_CHEST_OPEN;
            case ANVIL_CRUSH -> Sound.BLOCK_ANVIL_LAND;
            case VOID_PORTAL, DRAGON_EAT -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case PISTON_TRAP -> Sound.BLOCK_PISTON_EXTEND;
            case SHULKER_PRISON -> Sound.BLOCK_SHULKER_BOX_CLOSE;
            case CREEPER_SURPRISE -> Sound.ENTITY_CREEPER_PRIMED;
            case TOILET_FLUSH -> Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE;
            case GIFT_BOX -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case GRAVE -> Sound.BLOCK_GRAVEL_PLACE;
            default -> Sound.ENTITY_PLAYER_ATTACK_STRONG;
        };
    }

    private void copyEquipment(Player source, ArmorStand stand) {
        EntityEquipment from = source.getEquipment();
        EntityEquipment to = stand.getEquipment();
        if (from == null || to == null) return;
        to.setHelmet(copy(from.getHelmet()));
        to.setChestplate(copy(from.getChestplate()));
        to.setLeggings(copy(from.getLeggings()));
        to.setBoots(copy(from.getBoots()));
        to.setItemInMainHand(copy(from.getItemInMainHand()));
        to.setItemInOffHand(copy(from.getItemInOffHand()));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(source);
            head.setItemMeta(skull);
            to.setHelmet(head);
        }
    }

    private ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
}
