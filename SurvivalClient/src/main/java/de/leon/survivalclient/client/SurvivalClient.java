package de.leon.survivalclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SurvivalClient implements ClientModInitializer {
    private static KeyMapping toggleKey;
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        ServerAccess.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("survivalclient", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.survivalclient.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        ClientTickEvents.END_CLIENT_TICK.register(SurvivalClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        boolean allowed = ServerAccess.isAllowed(mc);
        if (!allowed && EspState.enabled) {
            EspState.enabled = false;
            ChestMarkers.clear(mc);
            message(mc, "ESP deaktiviert: Server nicht manuell freigegeben.");
        }

        while (toggleKey.consumeClick()) {
            if (!allowed) {
                message(mc, "Server nicht freigegeben. Prüfe die Regeln und trage den Host in config/survivalclient-servers.txt ein.");
                continue;
            }
            EspState.enabled = !EspState.enabled;
            message(mc, EspState.enabled ? "ESP aktiviert." : "ESP deaktiviert.");
        }

        if (EspState.isActive()) {
            if (tickCounter++ % 20 == 0) ChestMarkers.update(mc);
        } else {
            tickCounter = 0;
            ChestMarkers.clear(mc);
        }
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) mc.gui.setOverlayMessage(Component.literal(text), false);
    }
}
