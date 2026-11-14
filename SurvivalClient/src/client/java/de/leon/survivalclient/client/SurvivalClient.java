package de.leon.survivalclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import de.leon.survivalclient.client.gui.FloatingMusicBar;
import de.leon.survivalclient.client.gui.MovementEditorScreen;
import de.leon.survivalclient.client.gui.ProVisualsSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class SurvivalClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("survivalclient","main"));
    private static KeyMapping openConfig;
    @Override public void onInitializeClient() {
        ProVisualsSettings.load(); MediaSessionBridge.start(); FloatingMusicBar.register();
        openConfig=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.survivalclient.open_config",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_SHIFT,CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client->{while(openConfig.consumeClick()){if(client.gui.screen()==null)client.gui.setScreen(new MovementEditorScreen(null));}});
    }
}
