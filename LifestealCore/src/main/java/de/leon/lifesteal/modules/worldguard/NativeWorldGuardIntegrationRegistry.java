package de.leon.lifesteal.modules.worldguard;

import de.leon.lifesteal.modules.worldguard.api.ThirdPartyFlagAdapter;
import de.leon.lifesteal.modules.worldguard.api.ThirdPartyFlagDefinition;
import de.leon.lifesteal.modules.worldguard.api.WorldGuardIntegrationApi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime registry for dedicated adapters and external API integrations.
 */
public final class NativeWorldGuardIntegrationRegistry implements WorldGuardIntegrationApi {
    private final Map<String, ThirdPartyFlagAdapter> adapters = new LinkedHashMap<>();

    @Override
    public synchronized void registerAdapter(ThirdPartyFlagAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        String pluginId = normalize(adapter.pluginId());
        if (!pluginId.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid adapter plugin ID: " + pluginId);
        }
        if (!adapter.isAvailable()) {
            throw new IllegalStateException("Cannot register an unavailable adapter: " + pluginId);
        }
        Collection<ThirdPartyFlagDefinition> definitions =
                Objects.requireNonNull(adapter.flags(), "adapter flags");
        for (ThirdPartyFlagDefinition definition : definitions) {
            Objects.requireNonNull(definition, "flag definition");
            if (!definition.key().startsWith(pluginId + ":")) {
                throw new IllegalArgumentException("Flag " + definition.key()
                        + " must use the adapter namespace " + pluginId + ":");
            }
            for (ThirdPartyFlagAdapter existing : adapters.values()) {
                if (existing == adapter) continue;
                if (existing.flags().stream().anyMatch(flag -> flag.key().equalsIgnoreCase(definition.key()))) {
                    throw new IllegalArgumentException("Duplicate third-party flag key: " + definition.key());
                }
            }
        }
        if (adapters.containsKey(pluginId)) {
            throw new IllegalArgumentException("An adapter is already registered for " + pluginId);
        }
        adapters.put(pluginId, adapter);
    }

    @Override
    public synchronized boolean unregisterAdapter(String pluginId) {
        return adapters.remove(normalize(pluginId)) != null;
    }

    public synchronized Collection<ThirdPartyFlagAdapter> adapters() {
        return java.util.List.copyOf(adapters.values());
    }

    public synchronized Collection<ThirdPartyFlagDefinition> availableFlags() {
        return adapters.values().stream().filter(ThirdPartyFlagAdapter::isAvailable)
                .flatMap(adapter -> adapter.flags().stream()).toList();
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "pluginId").trim().toLowerCase(Locale.ROOT);
    }
}
