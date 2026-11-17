package de.leon.lifesteal.modules.worldguard.api;

import java.util.Collection;

/**
 * Adapter implemented by LifestealCore or a supported plugin integration.
 * An adapter must only expose flags that it can actually enforce.
 */
public interface ThirdPartyFlagAdapter {

    /**
     * Stable plugin identifier, normally the plugin's lowercase name.
     */
    String pluginId();

    /**
     * Display name used in the integration overview.
     */
    String displayName();

    /**
     * Whether the backing plugin is currently installed and enabled.
     */
    boolean isAvailable();

    /**
     * Flags supported by this adapter. Keys must be globally namespaced.
     */
    Collection<ThirdPartyFlagDefinition> flags();
}
