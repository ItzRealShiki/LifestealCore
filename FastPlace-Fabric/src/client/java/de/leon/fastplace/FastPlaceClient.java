package de.leon.fastplace;

import de.leon.fastplace.mixin.MinecraftClientAccessor;
import de.leon.fastplace.network.HelloPayload;
import de.leon.fastplace.network.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class FastPlaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> FastPlaceState.setEnabled(payload.enabled()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FastPlaceState.setEnabled(false);
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                FastPlaceState.setEnabled(false)
        );

        ClientTickEvents.END_CLIENT_TICK.register(FastPlaceClient::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        if (!FastPlaceState.isEnabled() || client.player == null || client.level == null) {
            return;
        }

        ((MinecraftClientAccessor) client).fastplace$setItemUseCooldown(0);
    }
}
