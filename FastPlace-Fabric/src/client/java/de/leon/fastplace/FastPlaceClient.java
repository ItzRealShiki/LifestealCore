package de.leon.fastplace;

import de.leon.fastplace.mixin.MinecraftClientAccessor;
import de.leon.fastplace.mixin.MultiPlayerGameModeAccessor;
import de.leon.fastplace.network.FastBreakStatePayload;
import de.leon.fastplace.network.HelloPayload;
import de.leon.fastplace.network.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class FastPlaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> FastPlaceState.setEnabled(payload.enabled()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FastBreakStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> FastPlaceState.setFastBreakEnabled(payload.enabled()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FastPlaceState.reset();
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FastPlaceState.reset());

        ClientTickEvents.END_CLIENT_TICK.register(FastPlaceClient::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (FastPlaceState.isEnabled()) {
            ((MinecraftClientAccessor) client).fastplace$setRightClickDelay(0);
        }

        if (FastPlaceState.isFastBreakEnabled()
                && client.gameMode != null
                && client.gameMode.getPlayerMode().isCreative()) {
            ((MultiPlayerGameModeAccessor) client.gameMode).fastplace$setDestroyDelay(0);
        }
    }
}
