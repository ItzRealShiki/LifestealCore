package de.leon.simplelitematica;

import de.leon.simplelitematica.command.SchematicCommands;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleLitematica implements ModInitializer {
    public static final String MOD_ID = "simplelitematica";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SchematicCommands.register();
        LOGGER.info("SimpleLitematica initialized.");
    }
}
