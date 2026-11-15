package de.leon.simplelitematica.client;

import com.mojang.brigadier.Command;
import de.leon.simplelitematica.client.gui.SchematicScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class SimpleLitematicaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("liteschematics").executes(context -> {
                    Minecraft.getInstance().setScreen(new SchematicScreen());
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
