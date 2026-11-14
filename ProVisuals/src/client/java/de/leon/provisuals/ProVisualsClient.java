package de.leon.provisuals;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main client entry point for ProVisuals.
 *
 * The clean implementation starts here. UI, workspace, HUD and extension
 * systems will be added as independent components instead of reusing the
 * previous SurvivalClient implementation.
 */
public final class ProVisualsClient implements ClientModInitializer {
    public static final String MOD_ID = "provisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("ProVisuals core initialized.");
    }
}
