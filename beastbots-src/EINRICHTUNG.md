# BeastSMP: simulierte Spieler

Eingerichtet am 16.10.2026 auf der Lobby `/root/lobbyMc` (Paper 1.21.11-132, Java 25.0.4.1).
Die Aktivierung erfolgte nach deiner ausdrücklichen Neustart-Freigabe. BungeeCord wurde nicht neu gestartet.

## Ergebnis

Zehn serverseitig simulierte Spieler mit normalen Namen, Skins und ohne BOT-Zusatz in TAB.
Die Spielfiguren sind unsichtbar, ohne Kollision, unverwundbar und sammeln keine Gegenstände ein.
Sie chatten nicht, bauen nichts ab und haben keine OP-Rechte. Bestehende Weltblöcke wurden nicht verändert.

Namen: CedarVale, LumaFox, NoxRaven, SkyMarlow, EmberOak, PixelFinn, NovaBirch, FrostKian, MiloQuartz, RiverRune.

Paper-Plugins erkennen sie über die normale Online-Spielerliste. Interne Metadaten markieren sie zusätzlich
als simulierte NPCs, damit kompatible Plugins sie ausschließen können. BeastHUD, Begrüßungen,
Ressourcenpakete und Lobby-Partikel überspringen die Bots.

Die öffentliche Bungee-Spielerzahl bleibt die Zahl echter Verbindungen: bei der Prüfung **1**.
Die Lobby führte gleichzeitig **1 echten + 10 simulierte Spieler**; ihre TAB-Anzeige zeigte folglich 11.
Es wurden keine Minecraft-Konten, externen Bot-Verbindungen oder Änderungen an der Proxy-Authentifizierung angelegt.
Die Bots belegen keine Proxy-Verbindungen. Der Addon-Login-Handler berücksichtigt Bots bei der Prüfung voller Backend-Slots.

## Installierte und geänderte Plugins

| Plugin | Version | Aufgabe |
|---|---|---|
| FakePlayer-CE | fp.build14 | Serverseitige Spielerinstanzen |
| CommandAPI Paper | 12.1.0 | Abhängigkeit von FakePlayer-CE |
| BeastBots | 1.0.0 | Konfiguration, unsichtbare Spieler, Skin-Laden, Wiederherstellung, Admin-Befehle |
| BeastHudThemes, bestehend | 1.0.0 | Kompatibilitätsfilter für simulierte Spieler |

Citizens, ProtocolLib, LuckPerms und TAB wurden nicht benötigt und nicht installiert.
Die bestehende TAB-Gestaltung von CustomLobby bleibt erhalten.

Offizielle Downloads: [FakePlayer-CE build14](https://github.com/EndlessPixel-Studio/FakePlayer-CE/releases/tag/fp.build14),
[CommandAPI 12.1.0](https://github.com/CommandAPI/CommandAPI/releases/tag/12.1.0).
Lizenzangaben und SHA-256-Prüfsummen stehen im Repository unter `beastbots-src/THIRD_PARTY.md`.

## Dateien auf dem Server

- `/root/lobbyMc/beastbots-src/`: vollständiger Addon-Quellcode, Buildskript, Dokumentation, Testclient-Skripte.
- `/root/lobbyMc/plugins/BeastBots-1.0.0.jar`
- `/root/lobbyMc/plugins/BeastBots/config.yml`: Anzahl, Namen, Unsichtbarkeit, TAB, Skin-Quellen, Spawn, Limits.
- `/root/lobbyMc/plugins/FakePlayer-CE-build14.jar`
- `/root/lobbyMc/plugins/FakePlayer-CE/config.yml`: Grenzen, Namensstil, deaktivierte Inventarpersistenz/HTTP-Verwaltung.
- `/root/lobbyMc/plugins/CommandAPI-12.1.0-Paper.jar`
- `/root/lobbyMc/beasthud-src/src/de/beastsmp/hud/{BeastHudPlugin,BeastLobbyFeatures,BeastThemePacks}.java`: Bot-Filter.
- `/root/lobbyMc/plugins/BeastHudThemes-1.0.0.jar`: neu gebauter Kompatibilitätsstand.

Laufzeit-Datenbanken und generierte Kennungen von FakePlayer-CE werden über `.gitignore` ausgeschlossen.
Server- und Proxy-Konfiguration wurden für diese Einrichtung nicht verändert.

## Befehle

Nur Konsole oder Berechtigung `beastbots.admin` (standardmäßig OP):

```
/beastbots help
/beastbots add <Name>
/beastbots remove <Name>
/beastbots setamount <0–10>
/beastbots list
/beastbots reload
/beastbots status
```

`setamount 0` deaktiviert alle verwalteten Spieler. Die konfigurierte Anzahl wird nach einem Neustart wiederhergestellt.
Bei einer Namenskollision hat ein echter Spieler Vorrang. Bereits vorhandene echte Spielerdaten werden nicht als Bot geladen.
Die Sichtweite der Bots ist auf zwei Chunks begrenzt; die Sichtweite echter Spieler bleibt unverändert.

## Tests

- Privater Testserver mit eigener neuer flacher Welt und Bindung an 127.0.0.1; nach dem Test gestoppt und entfernt.
- Ein Minecraft-Protokollclient empfing **10 TAB-Einträge**, **10 Skin-Texturen** und **10 unsichtbare Spielerentitäten**.
- Derselbe Test bestand nach einem Neustart erneut.
- Hinzufügen, Entfernen, Mengenänderung, Abschalten auf 0 und Reload wurden dort getestet.
- Der Testserver erreichte 20 TPS. Die Lastmessung ist keine Garantie für alle anderen Lobby-Plugins.
- In der laufenden Lobby: zehn Spieler mit `online=true`, `invisible=true`, `op=false`, `skin=true` bestätigt.
- TAB zusätzlich im geöffneten Minecraft-Client mit Skins geprüft; Screenshot liegt neben diesem Bericht.
- Mengenänderung 10 → 9 → 10 auch in der laufenden Lobby ohne Abmeldefehler geprüft.
- HUD und Ressourcenpaket des echten Spielers wurden weiterhin erfolgreich geladen.
- Die öffentliche Serverabfrage zeigte weiterhin eine echte Verbindung.
- Nach der Sichtweitenbegrenzung: alle zehn Bots mit `view=2`, `simulation=2`; im letzten 5-Sekunden-Fenster durchschnittlich 4,3 ms pro Tick. TPS-Mittelwert nach dem Start: 19,7.

## Grenzen

Es sind simulierte Backend-Spieler, keine authentifizierten menschlichen Verbindungen am Proxy.
Andere Plugins können sie daher bei Online-Zahlen und sonstigen Spielermechaniken mitzählen.
Standardmäßig werden zwei normale Skin-Quellen verwendet; pro Name sind andere Quellen konfigurierbar.
Skin-Änderungen greifen beim nächsten Spawn. Bei fehlender Mojang-Erreichbarkeit bleibt der Standard-Skin erhalten.
ViaVersion meldet beim Erzeugen erwartbar eine fehlende Netzwerkverbindung für die simulierten Spieler.
Namensabfragen beim Erzeugen können kurze Tick-Spitzen verursachen. Die Figuren benötigen keine künstliche Bewegung.

Die Beast-Befehle verwenden den direkten Bereinigungspfad, weil FakePlayer-CEs Kick-Pfad mit dieser
Paper-Version im Test eine doppelte Abmeldung auslöste. Verwende für die verwalteten Spieler `/beastbots`.
Versionen sind festgehalten; Updates von FakePlayer-CE vorher getrennt testen.

## Sicherung und Deinstallation

Backup-Verzeichnis: `/root/beastbots-backup-20261016/`

- `lobby-before-bots.tar.gz`: betroffene bestehende Dateien vor der Änderung.
- `bungeecord-config.yml`: zusätzliche Sicherung der unveränderten Proxy-Konfiguration.
- `git-head.txt`: vorheriger Commit.
- `staging-tests.tar.gz`: Testlogs und Testkonfigurationen.

Zum Entfernen zuerst `/beastbots setamount 0` ausführen. Dann die Lobby kontrolliert stoppen.
Die neuen JARs von BeastBots, FakePlayer-CE und CommandAPI entfernen und ihre neuen Datenordner zur Seite verschieben.
Das Backup nach `/root/lobbyMc` entpacken, um die vorherigen BeastHUD-Dateien wiederherzustellen.
Anschließend wie bisher mit `./start.sh` im Screen `Lobby` starten. Ein Proxy-Neustart ist nicht erforderlich.
