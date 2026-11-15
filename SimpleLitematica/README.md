# SimpleLitematica

A lightweight Fabric schematic mod for Minecraft **26.3**.

## Current first-stage features
- Own `.slite` JSON schematic format (versioned).
- Select and save cuboid regions with `/liteselect pos1 x y z` and `/liteselect pos2 x y z`.
- Save the selected region with `/litesave <name>`.
- Schematic manager GUI with `/liteschematics`.
- Drop `.slite` files into `config/simplelitematica/schematics`, then press **Refresh**.
- Select a schematic and delete it with the **X** button.
- Creative-only server-side print commands: `/liteprint` and `/liteprint slow`.

## Print behaviour
- `/liteprint`: fast queue, up to 4096 block updates per server tick.
- `/liteprint slow`: 24 block updates per server tick.
- Printing skips air blocks, so it does not clear existing builds.
- The server checks Creative mode and applies normal server-side block updates. On multiplayer, the server must have the mod installed and the schematic file available on the server. It does not bypass server permissions.

## File format
A `.slite` file is UTF-8 JSON with:
- `format`: `simplelitematica`
- `version`: `1`
- `sizeX`, `sizeY`, `sizeZ`
- `blocks`: relative x/y/z coordinates and a Minecraft block-state codec value.

## Build
Requires Java 25, Fabric Loader 0.20.1+, Fabric API 0.166.2+26.3 and Fabric Loom 1.19-SNAPSHOT.
Run `gradle build`; the JAR is in `build/libs`.

## Planned next
- Transparent world preview and layer visibility controls.
- Move/rotate controls.
- Improved import/export and GUI polish.
