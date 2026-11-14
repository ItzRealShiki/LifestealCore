package de.leon.survivalclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.leon.survivalclient.client.gui.FloatingMusicBar;
import de.leon.survivalclient.client.gui.MediaSessionBridge;
import de.leon.survivalclient.client.gui.MovementEditorScreen;
import de.leon.survivalclient.client.gui.ProVisualsSettings;
import de.leon.survivalclient.client.mixin.MinecraftFastPlaceAccessor;
import de.leon.survivalclient.client.mixin.MultiPlayerGameModeFastBreakAccessor;
import de.leon.survivalclient.client.network.FastBreakStatePayload;
import de.leon.survivalclient.client.network.HelloPayload;
import de.leon.survivalclient.client.network.ServerFeatureState;
import de.leon.survivalclient.client.network.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SurvivalClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("survivalclient","main"));
    private static KeyMapping openConfig;

    @Override public void onInitializeClient() {
        // Same custom payload IDs and codecs as the existing Server-Mod.
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FastBreakStatePayload.TYPE, FastBreakStatePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ServerFeatureState.setFastPlace(payload.enabled())));
        ClientPlayNetworking.registerGlobalReceiver(FastBreakStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ServerFeatureState.setFastBreak(payload.enabled())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerFeatureState.reset();
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) ClientPlayNetworking.send(new HelloPayload());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerFeatureState.reset());

        ProVisualsSettings.load();
        MediaSessionBridge.start();
        FloatingMusicBar.register();
        openConfig=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.survivalclient.open_config",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_SHIFT,CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(openConfig.consumeClick()){
                if(client.gui.screen()==null)client.gui.setScreen(new MovementEditorScreen(null));
            }
            applyServerFeatures(client);
        });
    }

    private static void applyServerFeatures(Minecraft client) {
        if (client.player == null || client.level == null || !ServerFeatureState.connected()) return;
        if (ServerFeatureState.fastPlace()) {
            ((MinecraftFastPlaceAccessor) client).survivalclient$setRightClickDelay(0);
        }
        if (ServerFeatureState.fastBreak() && client.gameMode != null
                && client.gameMode.getPlayerMode().isCreative()) {
            ((MultiPlayerGameModeFastBreakAccessor) client.gameMode).survivalclient$setDestroyDelay(0);
        }
    }
}
