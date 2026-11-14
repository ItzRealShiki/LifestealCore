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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    public void playKill(Player killer, Player victim) {
        if (killer == null || victim == null) return;
        Effect effect = selected(killer.getUniqueId());
        if (effect != Effect.NONE) render(killer, victim.getLocation().clone(), victim, effect, false);
    }

    public void preview(Player viewer, Effect effect) {
        if (effect == Effect.NONE || cinematicSessions.containsKey(viewer.getUniqueId())) return;
        Location center = viewer.getLocation().clone();
        render(viewer, center, viewer, effect, true);
        startCinematicCamera(viewer, center);
    }

    private void startCinematicCamera(Player viewer, Location center) {
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
                double angle = Math.toRadians(tick * 4.0);
                double radius = 4.8 - Math.min(tick, 24) * 0.025;
                Location cameraLocation = center.clone().add(Math.cos(angle) * radius, 2.0, Math.sin(angle) * radius);
                cameraLocation.setDirection(center.clone().add(0, 1.0, 0).toVector().subtract(cameraLocation.toVector()));
                camera.teleport(cameraLocation);
                if (tick >= 48) {
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
        if (effect == Effect.MIMIC_CHEST) {
            animateMimicChest(owner, loc, model);
            return;
        }
        if (effect == Effect.DRAGON_EAT) {
            animateDragonEat(owner, loc, model);
            return;
        }
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
        activeEntities.add(mannequin.getUniqueId());
        activeEntities.add(prop.getUniqueId());
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
                if (!prop.isValid() || !mannequin.isValid()) { cleanup(prop, mannequin); cancel(); return; }
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
                    cleanup(prop, mannequin);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }


    private void animateMimicChest(Player viewer, Location loc, Player skinSource) {
        var world = loc.getWorld();
        if (world == null || !viewer.isOnline()) return;

        Location chestLoc = loc.clone();
        chestLoc.setPitch(0);
        chestLoc.add(0, 0, 1.15);
        BlockDisplay chest = world.spawn(chestLoc, BlockDisplay.class, display -> {
            display.setBlock(Material.CHEST.createBlockData());
            display.setInvulnerable(true);
            display.setPersistent(false);
        });
        PlayerNpc npc = new PlayerNpc(viewer);
        Location start = loc.clone().add(0, 0.05, 0);
        npc.spawn(skinSource, start);
        activeEntities.add(chest.getUniqueId());
        world.playSound(chestLoc, Sound.BLOCK_CHEST_OPEN, 1.2f, 0.65f);
        world.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.7f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!viewer.isOnline() || !chest.isValid() || !npc.isSpawned()) {
                    npc.remove();
                    cleanup(chest);
                    cancel();
                    return;
                }
                tick++;
                double progress = Math.min(1.0, tick / 30.0);
                double eased = progress * progress * (3.0 - 2.0 * progress);
                Location target = chestLoc.clone().add(0, 0.15, 0);
                Location moving = start.clone().add(
                        (target.getX() - start.getX()) * eased,
                        (target.getY() - start.getY()) * eased - Math.sin(progress * Math.PI) * 0.35,
                        (target.getZ() - start.getZ()) * eased
                );
                moving.setYaw(chestLoc.getYaw());
                npc.teleport(moving);
                if (tick == 8) {
                    world.playSound(chestLoc, Sound.BLOCK_CHEST_OPEN, 1.4f, 0.8f);
                    world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0, 0.5, 0), 18, 0.35, 0.25, 0.35, 0.03);
                }
                if (tick > 8 && tick < 25) {
                    world.spawnParticle(Particle.PORTAL, moving.clone().add(0, 0.9, 0), 5, 0.22, 0.35, 0.22, 0.12);
                    chest.teleport(chestLoc.clone().add(0, Math.sin(tick * 0.45) * 0.035, 0));
                }
                if (tick == 30) {
                    world.playSound(chestLoc, Sound.BLOCK_CHEST_CLOSE, 1.5f, 0.7f);
                    world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0, 0.5, 0), 35, 0.45, 0.35, 0.45, 0.06);
                    world.spawnParticle(Particle.PORTAL, chestLoc.clone().add(0, 0.8, 0), 55, 0.45, 0.5, 0.45, 0.18);
                    npc.remove();
                }
                if (tick >= 42) {
                    npc.remove();
                    cleanup(chest);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void animateDragonEat(Player viewer, Location loc, Player skinSource) {
        var world = loc.getWorld();
        if (world == null || !viewer.isOnline()) return;

        Location center = loc.clone().add(0, 0.2, 0);
        org.bukkit.util.Vector direction = loc.getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) direction = new org.bukkit.util.Vector(0, 0, 1);
        direction.normalize();
        final org.bukkit.util.Vector forward = direction;
        final org.bukkit.util.Vector side = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX());

        Location start = center.clone().add(forward.clone().multiply(-17)).add(0, 7.0, 0);
        Location orbitEnd = center.clone().add(forward.clone().multiply(-2.5)).add(side.clone().multiply(5.5)).add(0, 4.6, 0);
        Location bite = center.clone().add(0, 1.0, 0);

        org.bukkit.entity.EnderDragon dragon = world.spawn(start, org.bukkit.entity.EnderDragon.class, entity -> {
            entity.setAI(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
        });
        PlayerNpc npc = new PlayerNpc(viewer);
        npc.spawn(skinSource, center.clone());
        activeEntities.add(dragon.getUniqueId());
        world.playSound(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.72f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.1f, 0.8f);
        world.spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 35, 0.8, 0.8, 0.8, 0.12);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!viewer.isOnline() || !dragon.isValid() || !npc.isSpawned()) {
                    npc.remove();
                    cleanup(dragon);
                    cancel();
                    return;
                }

                tick++;
                Location flight;

                if (tick <= 26) {
                    double p = smooth(tick / 26.0);
                    flight = interpolate(start, orbitEnd, p);
                    flight.add(0, Math.sin(p * Math.PI) * 2.0, 0);
                } else if (tick <= 54) {
                    double p = (tick - 26) / 28.0;
                    double angle = -Math.PI * 0.35 + p * Math.PI * 1.35;
                    flight = center.clone().add(
                            forward.getX() * Math.cos(angle) * 5.2 + side.getX() * Math.sin(angle) * 5.2,
                            4.0 + Math.sin(p * Math.PI * 2.0) * 0.65,
                            forward.getZ() * Math.cos(angle) * 5.2 + side.getZ() * Math.sin(angle) * 5.2
                    );
                } else if (tick <= 68) {
                    double p = smooth((tick - 54) / 14.0);
                    Location orbit = center.clone().add(forward.clone().multiply(-2.0)).add(side.clone().multiply(-5.2)).add(0, 4.0, 0);
                    flight = interpolate(orbit, bite, p);
                    flight.add(0, Math.sin(p * Math.PI) * 1.2, 0);
                } else {
                    double p = smooth((tick - 68) / 16.0);
                    Location departure = bite.clone().add(forward.clone().multiply(13)).add(0, 11, 0);
                    flight = interpolate(bite, departure, p);
                }

                // Move the dragon with real entity velocity instead of teleporting it every tick.
                // Repeated teleports can look stationary or snap on some clients/server versions.
                org.bukkit.util.Vector movement = flight.toVector().subtract(dragon.getLocation().toVector());
                if (movement.lengthSquared() > 0.01) {
                    double distance = movement.length();
                    org.bukkit.util.Vector velocity = movement.normalize().multiply(Math.min(1.15, distance));
                    dragon.setVelocity(velocity);
                    Location facing = flight.clone();
                    facing.setDirection(velocity);
                    dragon.setRotation(facing.getYaw(), facing.getPitch());
                } else {
                    dragon.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }

                if (tick % 2 == 0 && tick <= 68) {
                    Location trail = flight.clone().add(0, 0.5, 0);
                    world.spawnParticle(Particle.DRAGON_BREATH, trail, 9, 0.42, 0.35, 0.42, 0.018);
                    world.spawnParticle(Particle.PORTAL, trail, 4, 0.3, 0.25, 0.3, 0.12);
                }
                if (tick == 26 || tick == 54) world.playSound(flight, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.75f);
                if (tick == 55) world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.15f);
                if (tick == 68) {
                    world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.8f, 0.65f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                    world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 1, 0), 85, 0.8, 0.9, 0.8, 0.045);
                    world.spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 95, 0.7, 0.8, 0.7, 0.22);
                    world.spawnParticle(Particle.CLOUD, center, 35, 0.55, 0.35, 0.55, 0.08);
                    npc.remove();
                }
                if (tick >= 84) {
                    npc.remove();
                    cleanup(dragon);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private double smooth(double value) {
        double p = Math.max(0.0, Math.min(1.0, value));
        return p * p * (3.0 - 2.0 * p);
    }

    private Location interpolate(Location from, Location to, double progress) {
        return from.clone().add(
                (to.getX() - from.getX()) * progress,
                (to.getY() - from.getY()) * progress,
                (to.getZ() - from.getZ()) * progress
        );
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
