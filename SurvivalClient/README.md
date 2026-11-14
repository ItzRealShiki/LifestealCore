# ProVisuals

ProVisuals is the single client-side Fabric mod for Minecraft Java 26.2. The former internal project name "SurvivalClient" is only retained in some source paths and identifiers; the mod itself is distributed as ProVisuals.

## Included systems

- Main ProVisuals configuration screen, opened with **Right Shift**.
- ProVisuals settings and saved configuration.
- Movement editor.
- Floating music bar with Windows media-session metadata and album-cover artwork when the active media app provides a thumbnail.
- World visual rendering.
- Client/server feature handshake for the supported Fast Place and Fast Break features.

All of these systems are built into the same ProVisuals mod JAR. There is no separate SurvivalClient mod build.

## Build

Requirements:
- JDK 25
- Gradle 9.8.0

From this directory run:

```powershell
gradle build
```

The mod JAR is created in `build/libs/`.

## Version notes

- Minecraft Java 26.2
- Fabric Loader 0.20.1
- Fabric API 0.161.0+26.2
- Fabric Loom 1.19-SNAPSHOT
- Java 25
