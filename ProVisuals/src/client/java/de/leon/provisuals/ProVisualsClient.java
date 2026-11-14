package de.leon.provisuals;

import de.leon.provisuals.mixin.MinecraftClientAccessor;
import de.leon.provisuals.mixin.MultiPlayerGameModeAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProVisualsClient implements ClientModInitializer {
    public static final String MOD_ID = "provisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static KeyMapping openMenuKey;
    private static boolean fastPlace;
    private static boolean fastBreak;
    private static boolean serverFastPlace;
    private static boolean serverFastBreak;
    private static boolean serverControls;

    public record HelloPayload() implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastplace", "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.unit(new HelloPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record StatePayload(boolean enabled) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastplace", "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, StatePayload::enabled, StatePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record TogglePayload(byte feature, boolean enabled) implements CustomPacketPayload {
        public static final Type<TogglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastplace", "toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, TogglePayload::feature,
                        ByteBufCodecs.BOOL, TogglePayload::enabled, TogglePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record FastBreakPayload(boolean enabled) implements CustomPacketPayload {
        public static final Type<FastBreakPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastplace", "fastbreak_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FastBreakPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, FastBreakPayload::enabled, FastBreakPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(TogglePayload.TYPE, TogglePayload.CODEC);
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.provisuals.open_menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> { serverControls = true; serverFastPlace = payload.enabled(); }));
        ClientPlayNetworking.registerGlobalReceiver(FastBreakPayload.TYPE, (payload, context) ->
                context.client().execute(() -> { serverControls = true; serverFastBreak = payload.enabled(); }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverControls = false;
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) ClientPlayNetworking.send(new HelloPayload());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverControls = false);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) client.gui.setScreen(new ProVisualsScreen());
            if (client.player == null || client.level == null) return;
            boolean place = serverControls ? serverFastPlace : fastPlace;
            boolean breaking = serverControls ? serverFastBreak : fastBreak;
            if (place) ((MinecraftClientAccessor) client).provisuals$setRightClickDelay(0);
            if (breaking && client.gameMode != null && client.gameMode.getPlayerMode().isCreative()) {
                ((MultiPlayerGameModeAccessor) client.gameMode).provisuals$setDestroyDelay(0);
            }
        });
        LOGGER.info("ProVisuals initialized. Open the workspace with Right Shift.");
    }
    public static boolean isFastPlaceEnabled() { return serverControls ? serverFastPlace : fastPlace; }
    public static boolean isFastBreakEnabled() { return serverControls ? serverFastBreak : fastBreak; }
    public static boolean isServerControlled() { return serverControls; }
    public static void toggleFastPlace() {
        if (serverControls) {
            if (ClientPlayNetworking.canSend(TogglePayload.TYPE))
                ClientPlayNetworking.send(new TogglePayload((byte) 0, !serverFastPlace));
        } else fastPlace = !fastPlace;
    }
    public static void toggleFastBreak() {
        if (serverControls) {
            if (ClientPlayNetworking.canSend(TogglePayload.TYPE))
                ClientPlayNetworking.send(new TogglePayload((byte) 1, !serverFastBreak));
        } else fastBreak = !fastBreak;
    }
}