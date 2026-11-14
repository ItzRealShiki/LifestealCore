# SurvivalClient

Fabric client mod for Minecraft 26.2 / Java 25.

## Server access

The ESP toggle is available in singleplayer and on multiplayer servers whose hostnames you have manually added to:

`config/survivalclient-servers.txt`

The file is created on first multiplayer use. Add one hostname per line, without a port, for example:

```text
play.example.net
```

Remove a line to revoke access. Restarting the game is recommended after editing the file.

There is no hard-coded Srino.net block: Srino.net is treated exactly like every other hostname and is enabled only if you add it to the local allow-list. Only add servers where the server rules or staff explicitly permit this feature. A local allow-list does not itself mean the server permits ESP.

## Controls

Press **V** to toggle ESP. The key can be changed in Minecraft's Controls menu under SurvivalClient.
