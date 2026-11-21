# LifestealCore – alle 9 Plugins in einem

Enthält: **EcoExtension** (Money/Gems, Shop, Sell, Kontoauszug), **BoosterPlugin**, **Scoreboard**
(Scoreboard, Tablist, Herzen in der Tablist), **CombatPlugin** (Combat, Herzen, Schutz, Arena),
**RankPlugin**, **ModCore**, **ItemEdit** und **Bounty**.

## 1. Plugin bauen (IntelliJ + Maven, wie bisher)
1. ZIP entpacken, in IntelliJ: **File → Open** → den Ordner `LifestealCore` wählen.
2. Rechts oben auf **„Load Maven Changes“** klicken und warten.
3. Java 25 einstellen: **File → Project Structure → SDK** (falls nötig: Add SDK → Download JDK → 25, geht ohne Admin-Rechte).
4. Rechts **Maven** → `LifestealCore → Lifecycle → package` doppelklicken.
5. Fertige Datei: `target/LifestealCore-1.0.20.jar`

## 2. Auf den Server
1. Server stoppen.
2. **Die alten Jars aus `plugins/` löschen:** EcoExtension, BoosterPlugin, Scoreboard, CombatPlugin,
   RankPlugin, ModCore, ItemEdit, Bounty. (Die alten **Ordner** NICHT löschen – daraus werden die Daten übernommen.)
3. `LifestealCore-1.0.20.jar` in `plugins/` legen und Server starten.

Beim **ersten Start** werden automatisch übernommen:
- Herzen, Banns, Schutzzeit, Herz-Verlauf, eingelöste Herzen (aus `plugins/CombatPlugin/data.yml`)
- aktive Kopfgelder und Cooldowns (aus `plugins/Bounty/bounties.yml`)
- Shop-Items (aus `plugins/EcoExtension/shop.yml`)

Bereits gedroppte Herz-Items und mit ItemEdit bearbeitete Items funktionieren weiter.
Wenn alles läuft, kannst du die alten Ordner später löschen.

**Optional auf dem Server:** WorldGuard + WorldEdit (für die Arena) und LuckPerms (für `/rank` und Rang-Prefixe).
Ohne sie startet das Plugin trotzdem, nur diese Teile sind dann aus.

## 3. Einstellungen
- `plugins/LifestealCore/config.yml` – **alles**: Nachrichten-Stil, Farben, Module an/aus, Economy, Shop, Booster,
  Scoreboard, Combat, Herzen, Bounty. Nach Änderungen: `/lsc reload`
- `plugins/LifestealCore/database.yml` – die **Datenbank-Config**: `sqlite` (eine Datei, nichts einzurichten)
  oder `mysql` (wenn mehrere Server dieselben Daten teilen sollen). Braucht einen Neustart.

Nachrichten-Stil (für alle Module gleich): Small Caps, Englisch, **grün** = positiv, **orange** = neutral, **rot** = negativ.
Farben und Prefix änderst du unter `style:`.

## 4. Was jetzt besser ist
- **Money und Gems werden gespeichert.** (EcoExtension hat beim Neustart alles auf 1000/50 zurückgesetzt.)
- **Eine Datenbank** für alles statt vieler YAML-Dateien.
- **Weniger Lag:** Daten werden beim Einloggen im Hintergrund geladen, im Speicher gehalten und im Hintergrund
  gespeichert. Der Server wartet nie auf die Datenbank. Scoreboard/Tablist schicken nur geänderte Zeilen.
  Booster-Effekte werden nur erneuert, wenn sie fast abgelaufen sind. Statt 9 Menü-Listenern gibt es einen.
- **Alles verbunden:** Scoreboard liest Geld/Herzen/Booster direkt, Money-/Gems-Booster wirken auf `/sell`,
  der XP-Booster wirkt jetzt wirklich, Bounty nutzt die Economy direkt.
- **Booster werden gespeichert** (Inventar + aktive passive Booster) und der Server-Booster-Timer läuft nach einem Neustart weiter.
- Herzen können nicht doppelt eingelöst werden – auch nicht auf zwei Servern mit derselben MySQL-Datenbank.
- Kontoauszug (`/eco money check <Spieler>`) zeigt jetzt alle Buchungen (Pay, Shop, Sell, Bounty, Admin).
- Fixes: `/request accept` ging vorher für **jeden** Spieler – jetzt nur mit `Support.Core`.
  ItemEdit-Klickbefehle wurden doppelt ausgeführt – behoben. `/sell` hat Items gelöscht, wenn das Inventar voll war – jetzt fallen sie auf den Boden.

## 5. Befehle (unverändert)
| Befehl | Recht |
|---|---|
| `/pay`, `/bal`, `/shop`, `/sell`, `/booster`, `/protection`, `/heart check`, `/bounty`, `/request` | alle |
| `/eco`, `/shop editor`, `/combat`, `/heart unban`, `/rank`, `/lsc reload` | `Admin.Core` |
| `/gm` (`/gms` `/gmc` `/gmsp` `/gma`), `/godmode` | `Mod.Core` |
| `/tp`, `/support`, `/request accept/deny` | `Support.Core` |
| `/booster give`, `/sbooster skip` | `booster.admin` |
| `/itemedit` (`/it`) | `itemedit.use` |
| `/bounty remove` | `bounty.admin` |

Neu: `/lsc reload` (Config neu laden), `/lsc save` (sofort speichern), `/lsc` (Info).
`/scoreboard reload` gibt es nicht mehr – das macht jetzt `/lsc reload`.

## 6. Für andere Plugins (z. B. dein Jobs-Plugin)
LifestealCore meldet sich auch als **„EcoExtension“** an. Plugins, die EcoExtension per
`getEconomyManager()` → `getMoney/addMoney/removeMoney/getGems/addGems/removeGems` nutzen, laufen weiter.
Ein Team-Plugin kann `de.leon.lifesteal.api.TeamProvider` anmelden (gleiches Team = kein Herz, Team auf dem Scoreboard).
