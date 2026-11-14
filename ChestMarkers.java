package dev.spesp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Hebt Behaelter durch Waende hervor. Statt eine eigene Render-Pipeline zu bauen,
 * werden rein clientseitige, unsichtbare, leuchtende Marker-Entities auf die Behaelter
 * gesetzt. Der Glow-Umriss wird von Minecraft selbst durch Bloecke gezeichnet.
 * Der Server weiss nichts von diesen Entities.
 */
public final class ChestMarkers {

    private static final int RADIUS = 24;
    private static final Map<BlockPos, Entity> MARKERS = new HashMap<>();
    private static ClientLevel markerLevel = null;
    private static int nextId = 0x70000000;

    private ChestMarkers() {}

    public static void update(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        if (level != markerLevel) {          // Welt gewechselt
            MARKERS.clear();
            markerLevel = level;
        }

        Set<BlockPos> found = new HashSet<>();
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                c.offset(-RADIUS, -RADIUS, -RADIUS), c.offset(RADIUS, RADIUS, RADIUS))) {
            if (isContainer(level.getBlockState(p).getBlock())) {
                found.add(p.immutable());
            }
        }

        Iterator<Map.Entry<BlockPos, Entity>> it = MARKERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entity> e = it.next();
            if (!found.contains(e.getKey())) {
                level.removeEntity(e.getValue().getId(), Entity.RemovalReason.DISCARDED);
                it.remove();
            }
        }

        for (BlockPos p : found) {
            if (!MARKERS.containsKey(p)) {
                MARKERS.put(p, spawn(level, p));
            }
        }
    }

    public static void clear(Minecraft mc) {
        if (MARKERS.isEmpty()) return;
        ClientLevel level = mc.level;
        if (level != null && level == markerLevel) {
            for (Entity e : MARKERS.values()) {
                level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        MARKERS.clear();
    }

    private static Entity spawn(ClientLevel level, BlockPos p) {
        Shulker marker = new Shulker(EntityType.SHULKER, level) {
            @Override
            public boolean isPickable() {
                return false;               // Rechtsklick geht weiter auf die echte Kiste
            }
        };
        marker.setId(nextId++);
        marker.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        marker.setNoAi(true);
        marker.setNoGravity(true);
        marker.setSilent(true);
        marker.setInvisible(true);
        marker.setGlowingTag(true);
        level.addEntity(marker);
        return marker;
    }

    private static boolean isContainer(Block b) {
        return b instanceof ChestBlock          // inkl. Trapped Chest
            || b instanceof EnderChestBlock
            || b instanceof BarrelBlock
            || b instanceof ShulkerBoxBlock;
    }
}
