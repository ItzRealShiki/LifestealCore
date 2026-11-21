# LifestealCore

All-in-one Paper server core maintained by **ItzRealShiki**.

## Build

Requirements: JDK 25 and Maven 3.9+.

From the repository root:

```bash
mvn -B -f LifestealCore/pom.xml clean package
```

The Maven version is defined in `LifestealCore/pom.xml`. The current project version is **1.0.20**; the resulting JAR is written to `LifestealCore/target/LifestealCore-1.0.20.jar`.

The GitHub Actions workflow builds the plugin, publishes the versioned JAR under `releases/`, and uploads the plugin JAR and source package as workflow artifacts.

## Documentation

- [German setup and migration guide](LifestealCore/ANLEITUNG.md)
- [Plugin management and console diagnostics](LifestealCore/LEON_DIAGNOSTICS_README.md)

## Credits

Original work by **ItzRealShiki**. If you use LifestealCore on a Minecraft server, follow the server-credit requirement in the repository's [LICENSE](LICENSE).

## License

See [LICENSE](LICENSE). Third-party components remain subject to their own license terms.
