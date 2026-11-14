# SP-ESP (Fabric, Minecraft 26.2)

Clientseitige Mod: Kisten, Ender-Kisten, Faesser, Shulker-Kisten und Spieler leuchten durch Waende.

- Taste **V** schaltet um (aenderbar unter Steuerung)
- **Sperre:** funktioniert nur in einer eigenen Singleplayer-Welt. Auf Servern, Realms
  und ohne Welt ist sie gesperrt und schaltet sich automatisch ab (`SingleplayerLock.java`).
- Eigene LAN-Welt erlaubt; zum Sperren `ALLOW_OWN_LAN = false` setzen.

## Bauen
1. JDK 25 installieren
2. Projekt in IntelliJ IDEA oeffnen (Gradle-Import) oder in der Konsole `gradle wrapper` und dann `./gradlew build`
3. Fertige Jar: `build/libs/spesp-1.0.0.jar` (nicht die `-sources.jar`) in den `mods`-Ordner, plus Fabric API
