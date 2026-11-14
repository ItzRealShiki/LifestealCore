package de.leon.fastplace;

import de.leon.fastplace.network.FastBreakStatePayload;
import de.leon.fastplace.network.HelloPayload;
import de.leon.fastplace.network.StatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class FastPlaceMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FastBreakStatePayload.TYPE, FastBreakStatePayload.CODEC);
    }
}
