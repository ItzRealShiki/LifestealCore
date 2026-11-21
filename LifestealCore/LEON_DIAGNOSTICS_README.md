# ItzRealShiki Plugin Management & Console Diagnostics

## Commands

- `/pl` or `/plugin` — opens the plugin dashboard.
- `/console-error` — opens the paginated console warning/error viewer.

Both commands require `Admin.Core`.

## Included

- Six-row plugin dashboard with Lime, Orange and Red status dyes.
- Plugin detail view and required/optional dependency view.
- LifestealCore module overview and individual module checks.
- Booster overview.
- Update Center using GitHub Releases for configured projects (including ViaVersion).
- Release notes, release URL and estimated update priority.
- Paginated console warning/error GUI with filters and stacktrace pages.
- Unicode small-cap GUI labels in English.

## Update sources

The default list is in `plugins/LifestealCore/update-checks.yml`. Add entries using:

```yaml
repositories:
  PluginName: Owner/GitHubRepository
```

The checker compares the installed plugin version with the latest published GitHub release. It does not update plugins automatically. The priority label is a best-effort estimate from release-note keywords, not a security audit. Plugins without a configured GitHub release repository show that update checking is unavailable.

## Console log capture

The console viewer captures WARN/ERROR events routed through Paper's Log4j logger and Java Util Logging after LifestealCore starts. It keeps the latest 500 captured entries in memory. It does not import old entries from previous log files, and events emitted before this plugin starts cannot be shown.

## Build

Requirements: JDK 25 and Maven 3.9+.

From the project root (the folder containing `pom.xml`):

```bash
mvn clean package
```

The output JAR is created in `target/` as `LifestealCore-1.0.20.jar`.

Install that JAR in the Paper server's `plugins/` folder, keep the existing plugin data folder/backups, then restart the server. Do not use `/reload` to test a core plugin.

## Notes

- The project targets Paper 26.2 and Java 25.
- Existing `de.leon.lifesteal` sources are retained; the duplicate `de.lok3ss` tree was removed.
- Update checks require outbound internet access from the Minecraft server. If the server cannot reach GitHub, update status remains unknown rather than falsely reporting "up to date".
- Plugin status checks confirm load state, configured dependencies and the checks implemented for LifestealCore. A third-party plugin's internal features cannot be exhaustively tested without a dedicated integration/API.
