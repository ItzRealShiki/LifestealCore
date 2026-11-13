# FastPlace Fabric Mod

Client-only Fabric mod for Minecraft Java 26.2.

## Purpose

The mod has no local toggle, keybind, or configuration screen. It only enables fast placement after a compatible server sends the enabled state.

## Network protocol

- Client -> server: `fastplace:hello` (empty payload)
- Server -> client: `fastplace:state` (one boolean)

The Paper implementation will be added to LifestealCore after the standalone client mod has been built and checked.

## Build

Requirements:
- Java 25
- Gradle 9.5.1
- Fabric Loom 1.17.17

Run:

```bash
gradle clean build
```

The JAR is created in `build/libs/`.

## Important

The client resets Minecraft's item-use cooldown only while the server-authorized state is enabled. The server remains authoritative and can still reject placement requests.
