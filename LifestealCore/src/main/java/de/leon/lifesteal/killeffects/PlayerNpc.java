package de.leon.lifesteal.killeffects;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client-side-only player entity. It is sent only to the cinematic viewer.
 */
final class PlayerNpc {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private final Player viewer;
    private final UUID uuid = UUID.randomUUID();
    private final int entityId = NEXT_ENTITY_ID.getAndIncrement();
    private boolean spawned;
    private Location currentLocation;

    PlayerNpc(Player viewer) {
        this.viewer = viewer;
    }

    void spawn(Player skinSource, Location location) {
        if (!viewer.isOnline() || skinSource == null || location.getWorld() == null) return;

        this.currentLocation = location.clone();
        UserProfile profile = new UserProfile(uuid, skinSource.getName());
        for (ProfileProperty property : skinSource.getPlayerProfile().getProperties()) {
            if ("textures".equals(property.getName())) {
                profile.getTextureProperties().add(new TextureProperty(
                        property.getName(), property.getValue(), property.getSignature()));
            }
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, false, 0, GameMode.SURVIVAL, null, null, 0, true);

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerPlayerInfoUpdate(
                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, List.of(info)));

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerSpawnEntity(
                        entityId, Optional.of(uuid), EntityTypes.PLAYER,
                        new Vector3d(location.getX(), location.getY(), location.getZ()),
                        location.getPitch(), location.getYaw(), location.getYaw(), 0, Optional.empty()));
        spawned = true;
    }

    void teleport(Location location) {
        if (!spawned || !viewer.isOnline() || location.getWorld() == null) return;
        this.currentLocation = location.clone();
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport(
                        entityId,
                        new Vector3d(location.getX(), location.getY(), location.getZ()),
                        location.getYaw(), location.getPitch(), true));
    }

    Location location() {
        return currentLocation == null ? viewer.getLocation() : currentLocation.clone();
    }

    boolean isSpawned() {
        return spawned;
    }

    void remove() {
        if (!spawned) return;
        spawned = false;
        if (!viewer.isOnline()) return;
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerDestroyEntities(entityId));
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerPlayerInfoRemove(uuid));
    }
}
