# Spracheinstellungen und Bots

- Der Chat mit Codex bleibt Deutsch.
- `/settings` öffnet die grafische Sprachauswahl im jeweiligen Beast-Design.
- `/settings en` wählt Englisch, `/settings de` Deutsch. Englisch ist voreingestellt.
- Die Auswahl wird pro Spieler gespeichert. Sie gilt für die eigenen HUD-Beschriftungen, Menügrafiken, Beschreibungen, Begrüßung, Quest-Meldungen und das ESC-Design. Das vollständige Pflichtpaket wird bei einem Sprachwechsel aktualisiert. Die allgemeine Minecraft-/Client-Sprache bleibt eine Einstellung des Clients; Drittanbieter-Pluginmeldungen verwenden weiterhin deren Konfiguration.
- Bot-Obergrenze: 40. Die aktuelle Anzahl bleibt 10; mit `/beastbots setamount 20` können beispielsweise 20 aktiviert werden. Ein Pool aus 40 Namen und passenden Skin-Quellen ist vorbereitet. Eigene Namen bleiben über `/beastbots add <Name>` möglich.
- Jeder verwaltete Bot erhält ausschließlich `customlobby.afk.exempt`, keine OP-Rechte. Bereits gesetzter AFK-Status wird beim Konfigurieren gelöscht. Die AFK-Erkennung echter Spieler bleibt aktiv.
- FakePlayer-CE und BeastBots sind beide auf dieselbe Obergrenze eingestellt. Die Bungee-Anzeige unterstützt bereits bis zu 40 aktive Bots.

Vorheriger Stand: `/root/beastsmp-backup-settings-20261016/before.tar.gz`.

Vor Aktivierung geprüft: Java-Builds, deutsche/englische Sprachpakete, alle sechs Menügrafiken pro Design, ESC-Übersetzungen und Erhalt von Spielernamen bei übersetzten Begrüßungen.

## Aktivierung und Live-Prüfung

Nach der erteilten Freigabe wurde nur die Lobby neu gestartet; BungeeCord lief weiter. Die Änderungen sind jetzt aktiv.

- Alle zehn regulären Bots: `afkExempt=true`, `invisible=true`, `op=false`, Skins geladen.
- Ein elfter Bot wurde erfolgreich erstellt; danach wurde die Soll-Anzahl auf zehn zurückgestellt.
- Testspieler wechselte über `/settings de` und `/settings en` erfolgreich zwischen beiden Paketen; Download und SHA-1-Prüfung waren erfolgreich.
- Deutsche Bestätigung „Sprache gespeichert.“, englische Bestätigung „Language saved.“ und grafisches `/settings`-Menü wurden im Protokoll geprüft.
- Die HUD-Spielerzahl enthält weiterhin die aktiven Bots.
