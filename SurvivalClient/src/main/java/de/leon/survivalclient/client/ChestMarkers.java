package de.leon.survivalclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class ChestMarkers {
    private static final int RADIUS = 24;
    private static final Map<BlockPos, Entity> MARKERS = new HashMap<>();
    private static ClientLevel markerLevel;
    private static int nextId = 0x70000000;

    private ChestMarkers() {}

    public static void update(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        if (level != markerLevel) {
            MARKERS.clear();
            markerLevel = level;
        }

        Set<BlockPos> found = new HashSet<>();
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS, -RADIUS, -RADIUS),
                center.offset(RADIUS, RADIUS, RADIUS))) {
            if (isContainer(level.getBlockState(pos).getBlock())) found.add(pos.immutable());
        }

        Iterator<Map.Entry<BlockPos, Entity>> iterator = MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Entity> entry = iterator.next();
            if (!found.contains(entry.getKey())) {
                level.removeEntity(entry.getValue().getId(), Entity.RemovalReason.DISCARDED);
                iterator.remove();
            }
        }
        for (BlockPos pos : found) {
            if (!MARKERS.containsKey(pos)) MARKERS.put(pos, spawn(level, pos));
        }
    }

    public static void clear(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != null && level == markerLevel) {
            for (Entity entity : MARKERS.values()) {
                level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        MARKERS.clear();
        markerLevel = null;
    }

    private static Entity spawn(ClientLevel level, BlockPos pos) {
        // An invisible glowing armor stand is used as a client-side marker.
        // It does not modify the world or send anything to the server.
        ArmorStand marker = new ArmorStand(EntityType.ARMOR_STAND, level) {
            @Override public boolean isPickable() { return false; }
        };
        marker.setId(nextId++);
        marker.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        marker.setNoAi(true);
        marker.setNoGravity(true);
        marker.setSilent(true);
        marker.setInvisible(true);
        marker.setGlowingTag(true);
        level.addEntity(marker);
        return marker;
    }

    private static boolean isContainer(Block block) {
        return block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }
}
