package de.leon.lifesteal.modules.worldguard.api;

/**
 * Public extension point for registering adapters that contribute region flags.
 *
 * Implementations should reject duplicate plugin IDs and duplicate flag keys.
 * Registration does not imply that a flag is active unless its adapter reports
 * that the backing plugin is available.
 */
public interface WorldGuardIntegrationApi {

    void registerAdapter(ThirdPartyFlagAdapter adapter);

    boolean unregisterAdapter(String pluginId);
}
