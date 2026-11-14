# ProVisuals

A client-side Fabric mod for Minecraft Java 26.2.

## Current scope

This first milestone is intentionally a design-only prototype:
- A custom dark main configuration screen with purple accents.
- Sidebar navigation for future PvP, HUD, Survival, Visuals, Performance and Profiles sections.
- Search field and module cards as visual placeholders.
- Apply, Reset and Close controls for preview feedback.
- Press **Right Shift** to open the screen.

No gameplay modules, HUD overlays, combat assistance, or server-side features are implemented yet. The mod is client-only and does not need to be installed on a server.

## Build

Requirements:
- JDK 25
- Gradle 9.5.1 (or the compatible version recommended by Fabric for 26.2)

From this directory run:

```powershell
gradle build
```

The mod JAR is created in `build/libs/`.

## Version notes

The project follows the official Fabric 26.2 example project conventions:
- Minecraft 26.2
- Fabric Loader 0.20.1
- Fabric API 0.161.0+26.2
- Fabric Loom 1.19-SNAPSHOT
- Java 25

The project uses Minecraft's `GuiGraphicsExtractor` rendering API for 26.2.
