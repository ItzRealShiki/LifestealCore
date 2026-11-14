package dev.spesp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class EspClient implements ClientModInitializer {

    public static final String MOD_ID = "spesp";

    private static KeyMapping toggleKey;
    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.spesp.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));

        ClientTickEvents.END_CLIENT_TICK.register(EspClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        boolean allowed = SingleplayerLock.isAllowed(mc);

        while (toggleKey.consumeClick()) {
            if (allowed) {
                EspState.enabled = !EspState.enabled;
                message(mc, EspState.enabled ? "ESP an" : "ESP aus");
            } else {
                EspState.enabled = false;
                message(mc, "ESP nur im Singleplayer verfuegbar.");
            }
        }

        if (!allowed) EspState.enabled = false;   // Sperre: sofort aus, z.B. beim Serverbeitritt

        if (EspState.enabled) {
            if (tickCounter++ % 20 == 0) ChestMarkers.update(mc);
        } else {
            tickCounter = 0;
            ChestMarkers.clear(mc);
        }
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }
}
