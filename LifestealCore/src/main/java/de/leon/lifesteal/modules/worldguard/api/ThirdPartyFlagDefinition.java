package de.leon.lifesteal.modules.worldguard.api;

import java.util.List;
import java.util.Objects;

/**
 * Describes a region flag exposed by an integration adapter.
 *
 * @param key stable, namespaced key (for example "exampleplugin:mob-spawn")
 * @param displayName human-readable name for the admin GUI
 * @param category category used to group flags in the GUI
 * @param valueType expected value type
 * @param defaultValue serialized default value
 * @param description short explanation shown to administrators
 * @param allowedValues optional values for ENUM flags
 */
public record ThirdPartyFlagDefinition(
        String key,
        String displayName,
        String category,
        FlagValueType valueType,
        String defaultValue,
        String description,
        List<String> allowedValues
) {
    public ThirdPartyFlagDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(defaultValue, "defaultValue");
        description = description == null ? "" : description;
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Flag keys must be namespaced: " + key);
        }
        if (valueType == FlagValueType.ENUM && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("ENUM flags must define allowed values");
        }
    }
}
