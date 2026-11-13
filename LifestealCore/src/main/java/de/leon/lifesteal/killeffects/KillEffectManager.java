package de.leon.lifesteal.killeffects;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class KillEffectManager implements Listener {
    private final Set<UUID> activeEntities = ConcurrentHashMap.newKeySet();
    private final Set<PlayerNpc> activeNpcs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CinematicSession> cinematicSessions = new java.util.HashMap<>();
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer.isOnline()) playKill(killer, victim);
    }

    public void playKill(Player killer, Player victim) {
        if (killer == null || victim == null) return;
        Effect effect = selected(killer.getUniqueId());
        if (effect != Effect.NONE) {
            Location center = victim.getLocation().clone();
            render(killer, center, victim, effect, false);
            if (effect.animated()) startCinematicCamera(killer, center, 120);
        }
    }

    public void preview(Player viewer, Effect effect) {
        if (effect == Effect.NONE || cinematicSessions.containsKey(viewer.getUniqueId())) return;
        Location center = viewer.getLocation().clone();
        render(viewer, center, viewer, effect, true);
        startCinematicCamera(viewer, center, 120);
    }

    private void startCinematicCamera(Player viewer, Location center, int duration) {
        viewer.closeInventory();
        CinematicSession session = new CinematicSession(viewer.getLocation().clone(), viewer.getGameMode(), viewer.getSpectatorTarget());
        ArmorStand camera = center.getWorld().spawn(center.clone(), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        session.camera = camera;
        activeEntities.add(camera.getUniqueId());
        cinematicSessions.put(viewer.getUniqueId(), session);
        if (viewer.getGameMode() != GameMode.SPECTATOR) viewer.setGameMode(GameMode.SPECTATOR);
        viewer.setSpectatorTarget(camera);
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!viewer.isOnline() || !camera.isValid()) {
                    finishCinematic(viewer, session, false);
                    cancel();
                    return;
                }
                tick++;
                double angle = Math.toRadians(tick * 3.0);
                double radius = tick <= 30 ? 2.5 + tick * 0.22 : 9.1 - Math.min(1.0, (tick - 30) / (double) Math.max(1, duration - 30)) * 3.2;
                double height = 2.2 + Math.sin(tick * 0.055) * 0.35;
                Location cameraLocation = center.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                cameraLocation.setDirection(center.clone().add(0, 1.0, 0).toVector().subtract(cameraLocation.toVector()));
                camera.teleport(cameraLocation);
                if (tick >= duration) {
                    finishCinematic(viewer, session, true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onPreviewQuit(PlayerQuitEvent event) {
        CinematicSession session = cinematicSessions.get(event.getPlayer().getUniqueId());
        if (session != null) finishCinematic(event.getPlayer(), session, false);
    }

    private void finishCinematic(Player viewer, CinematicSession session, boolean restorePlayer) {
        cinematicSessions.remove(viewer.getUniqueId());
        if (session.camera != null) {
            activeEntities.remove(session.camera.getUniqueId());
            if (session.camera.isValid()) session.camera.remove();
        }
        if (!restorePlayer || !viewer.isOnline()) return;
        viewer.setSpectatorTarget(null);
        viewer.setGameMode(session.gameMode);
        if (session.gameMode == GameMode.SPECTATOR && session.previousTarget != null && session.previousTarget.isValid()) {
            viewer.setSpectatorTarget(session.previousTarget);
        } else {
            viewer.teleport(session.location);
        }
    }

    private static final class CinematicSession {
        private final Location location;
        private final GameMode gameMode;
        private final Entity previousTarget;
        private ArmorStand camera;
        private CinematicSession(Location location, GameMode gameMode, Entity previousTarget) {
            this.location = location;
            this.gameMode = gameMode;
            this.previousTarget = previousTarget;
        }
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
        if (world == null || !owner.isOnline()) return;

        Material block = switch (effect) {
            case MIMIC_CHEST, GIFT_BOX -> Material.CHEST;
            case ANVIL_CRUSH -> Material.ANVIL;
            case VOID_PORTAL -> Material.END_PORTAL;
            case CREEPER_SURPRISE -> Material.CREEPER_HEAD;
            case PISTON_TRAP -> Material.STICKY_PISTON;
            case SHULKER_PRISON -> Material.PURPLE_SHULKER_BOX;
            case DRAGON_EAT -> Material.DRAGON_HEAD;
            case TOILET_FLUSH -> Material.WATER_CAULDRON;
            case GRAVE -> Material.COARSE_DIRT;
            default -> Material.CHEST;
        };

        PlayerNpc npc = new PlayerNpc(owner);
        npc.spawn(model, loc.clone());
        activeNpcs.add(npc);

        BlockDisplay prop = world.spawn(loc.clone().add(0, effect == Effect.ANVIL_CRUSH ? 4.5 : .15, 0), BlockDisplay.class,
                display -> {
                    display.setBlock(block.createBlockData());
                    display.setPersistent(false);
                    display.setInvulnerable(true);
                });
        activeEntities.add(prop.getUniqueId());

        EnderDragon dragon = null;
        if (effect == Effect.DRAGON_EAT) {
            Location dragonStart = loc.clone().add(-13, 5, -2);
            dragon = world.spawn(dragonStart, EnderDragon.class, entity -> {
                entity.setAI(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setCollidable(false);
                entity.setPersistent(false);
                entity.setPhase(EnderDragon.Phase.CIRCLING);
            });
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(owner.getUniqueId())) online.hideEntity(plugin, dragon);
            }
            activeEntities.add(dragon.getUniqueId());
        }

        world.playSound(loc, animatedSound(effect), 1f, 1f);
        if (effect == Effect.MIMIC_CHEST || effect == Effect.GIFT_BOX) {
            world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 35, .6, .5, .6, .05);
            world.spawnParticle(Particle.CRIT, loc.clone().add(0, 1.2, 0), 20, .4, .5, .4, .1);
        } else if (effect == Effect.VOID_PORTAL || effect == Effect.DRAGON_EAT) {
            world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 110, .8, 1, .8, .45);
        } else if (effect == Effect.TOILET_FLUSH) {
            world.spawnParticle(Particle.BUBBLE, loc.clone().add(0, 1, 0), 55, .5, .7, .5, .1);
        } else if (effect == Effect.GRAVE) {
            world.spawnParticle(Particle.BLOCK, loc.clone(), 70, .6, .35, .6, Material.DIRT.createBlockData());
        }

        final EnderDragon cinematicDragon = dragon;
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!owner.isOnline() || !prop.isValid() || !npc.isSpawned()) {
                    npc.remove();
                    activeNpcs.remove(npc);
                    cleanup(prop, cinematicDragon);
                    cancel();
                    return;
                }
                tick++;

                if (effect == Effect.DRAGON_EAT && cinematicDragon != null && cinematicDragon.isValid()) {
                    double progress = Math.min(1.0, tick / 68.0);
                    double x = -13.0 + progress * 12.5;
                    double y = 5.0 + Math.sin(progress * Math.PI) * 2.2;
                    double z = -2.0 + Math.sin(progress * Math.PI * 2.0) * 2.8;
                    Location dragonLoc = loc.clone().add(x, y, z);
                    dragonLoc.setYaw(90f + (float) (Math.sin(progress * Math.PI * 2) * 22));
                    dragonLoc.setPitch((float) (-10 + progress * 16));
                    cinematicDragon.teleport(dragonLoc);

                    if (tick < 65) {
                        Location victimLoc = loc.clone().add(-.2 + progress * .6, .0 + Math.sin(tick * .18) * .12, 0);
                        npc.teleport(victimLoc);
                        world.spawnParticle(Particle.PORTAL, victimLoc.clone().add(0, 1, 0), 7, .25, .35, .25, .12);
                    }
                    if (tick == 66) {
                        npc.remove();
                        activeNpcs.remove(npc);
                        world.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 100, .7, .9, .7, .3);
                        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 55, .7, .7, .7, .1);
                        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, .9f);
                    }
                    if (tick > 68) {
                        Location exit = loc.clone().add(7 + (tick - 68) * .22, 5 + Math.sin(tick * .1), 0);
                        exit.setYaw(90f);
                        cinematicDragon.teleport(exit);
                    }
                } else if (effect == Effect.MIMIC_CHEST) {
                    if (tick <= 12) {
                        npc.teleport(loc.clone().add(0, Math.sin(tick * .4) * .08, 0));
                        prop.teleport(loc.clone().add(0, .15 + Math.sin(tick * .35) * .12, 0));
                    } else if (tick <= 52) {
                        double pull = (tick - 12) / 40.0;
                        npc.teleport(loc.clone().add(0, -Math.min(.9, pull * 1.05), 0));
                        world.spawnParticle(Particle.PORTAL, loc.clone().add(0, .5, 0), 6, .25, .25, .25, .08);
                        if (tick % 5 == 0) world.playSound(loc, Sound.BLOCK_CHEST_OPEN, .8f, 1.2f);
                    } else if (tick == 53) {
                        npc.remove();
                        activeNpcs.remove(npc);
                        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, .6, 0), 50, .5, .35, .5, .08);
                        world.spawnParticle(Particle.PORTAL, loc.clone().add(0, .8, 0), 45, .45, .5, .45, .15);
                        world.playSound(loc, Sound.BLOCK_CHEST_CLOSE, 1f, .8f);
                    }
                } else if (effect == Effect.ANVIL_CRUSH) {
                    prop.teleport(loc.clone().add(0, Math.max(.2, 4.5 - tick * .12), 0));
                    if (tick == 28) {
                        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, .2, 0), 55, .6, .2, .6, Material.ANVIL.createBlockData());
                        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, .6, 0), 35, .5, .3, .5, .08);
                        npc.remove();
                        activeNpcs.remove(npc);
                    }
                } else {
                    if (tick <= 38) {
                        npc.teleport(loc.clone().add(0, -tick * .025, 0));
                        world.spawnParticle(Particle.PORTAL, npc.location().add(0, 1, 0), 6, .25, .35, .25, .1);
                    }
                    if (tick == 39) {
                        npc.remove();
                        activeNpcs.remove(npc);
                    }
                }

                if (tick >= 110) {
                    npc.remove();
                    activeNpcs.remove(npc);
                    cleanup(prop, cinematicDragon);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void cleanup(Entity... entities) {
        for (Entity entity : entities) {
            if (entity == null) continue;
            activeEntities.remove(entity.getUniqueId());
            if (entity.isValid()) entity.remove();
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, CinematicSession> entry : Map.copyOf(cinematicSessions).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) finishCinematic(player, entry.getValue(), true);
        }
        for (PlayerNpc npc : Set.copyOf(activeNpcs)) npc.remove();
        activeNpcs.clear();
        for (UUID id : Set.copyOf(activeEntities)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) entity.remove();
        }
        activeEntities.clear();
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
