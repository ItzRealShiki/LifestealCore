package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative runtime operations for the plugin management GUI.
 * This uses Bukkit's public enable/disable API; it cannot unload a plugin classloader.
 */
public final class LeonPluginOperations {
    private LeonPluginOperations() { }

    public static List<String> hardDependents(Plugin target) {
        List<String> dependents = new ArrayList<>();
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate == target || !candidate.isEnabled()) continue;
            if (candidate.getDescription().getDepend().stream()
                    .anyMatch(name -> name.equalsIgnoreCase(target.getName()))) {
                dependents.add(candidate.getName());
            }
        }
        return dependents;
    }

    public static Result reload(LifestealCore core, Plugin target) {
        if (target == null) return new Result(false, "Plugin is no longer registered.");
        if (target == core) return new Result(false, "LifestealCore cannot reload itself safely.");
        List<String> dependents = hardDependents(target);
        if (!dependents.isEmpty()) {
            return new Result(false, "Reload blocked: enabled plugins depend on it: " + String.join(", ", dependents));
        }
        try {
            Bukkit.getPluginManager().disablePlugin(target);
            if (target.isEnabled()) return new Result(false, "Plugin did not disable cleanly; restart required.");
            Bukkit.getPluginManager().enablePlugin(target);
            if (!target.isEnabled()) return new Result(false, "Plugin did not enable; check console and restart the server.");
            return new Result(true, "Plugin was disabled and enabled again. This is not a full classloader unload; a restart is safer.");
        } catch (Throwable error) {
            core.getLogger().log(java.util.logging.Level.SEVERE, "Plugin reload failed for " + target.getName(), error);
            return new Result(false, "Reload failed: " + error.getClass().getSimpleName() + ". Check console; restart may be required.");
        }
    }

    public record Result(boolean success, String message) { }
}
