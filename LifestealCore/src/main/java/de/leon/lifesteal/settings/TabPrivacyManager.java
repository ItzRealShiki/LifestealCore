package de.leon.lifesteal.settings;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;

import java.util.ArrayList;
import java.util.List;

/** Masks player-list latency for viewers when the listed player enabled Hide Ping. */
public final class TabPrivacyManager {
    private final PacketListenerAbstract listener;

    public TabPrivacyManager(SettingsManager settings) {
        listener = new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;
                WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
                List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
                boolean changed = false;
                for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo copy =
                            new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(entry);
                    if (settings.isEnabled(copy.getProfileId(), "hide-ping")) {
                        copy.setLatency(-1);
                        changed = true;
                    }
                    entries.add(copy);
                }
                if (changed) {
                    packet.setEntries(entries);
                    event.markForReEncode(true);
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
}
