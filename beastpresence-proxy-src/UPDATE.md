# BeastSMP update — 16 October 2026

Custom HUD labels, all five illustrated menu types, tooltips, greetings, pumpkin quest messages, effect commands, bot administration and the themed ESC screens now use English. Existing saved designs and pumpkin progress are preserved. CustomLobby now uses its English messages. German command aliases remain compatible; English suggestions use `/design christmas`, `/effects`, `/lobbyeffects`, `/servers`, and `/pumpkins`. `/design helloween` also works.

All three complete seasonal packs are required. The resource-pack packet carries `forced=true`, an English installation prompt, and a theme-specific identifier. Rejection, invalid URL, download/reload failure or discard disconnects the player. Missing completion times out after two minutes. Simulated players are excluded from resource-pack delivery.

Only Halloween has the pumpkin icon and quest row. Quest display entities and interaction hitboxes are hidden by default and shown only to Halloween players who have not already found that pumpkin.

## Commands

- `/beastbots on` — restore the configured players gradually.
- `/beastbots off` — remove the managed players.
- `/beastbots toggle` — switch between enabled and disabled.
- `/beastbots status` and `/beastbots list` — inspect the current state.

These commands require operator status or `beastbots.admin`. The enabled state survives restart without losing names or configured amount.

BeastPresence on BungeeCord adds the currently active managed players to the public server-list count and hover names. The backend shares an atomic local snapshot every second. Missing or stale snapshots expire after 15 seconds, and shutdown clears the snapshot. Actual proxy connections are counted separately internally.

## Validation

- All three Java plugins compiled successfully.
- Public ping: 11 with ten NPCs and one real player; 1 after off; 11 after on.
- Isolated proxy fixture: ten names and count 10; empty or expired snapshot count 0.
- Protocol client received a required pack and was disconnected after declining it.
- Downloaded all three full seasonal packs and verified their SHA-1 hashes.
- Protocol visibility test: Halloween 10 pumpkin displays, Standard 0, Christmas 0, Halloween alias 10.
- Checked shared HUD definitions: exactly one quest row and pumpkin icon, both in Halloween.
- Checked generated English ESC strings and valid ZIP archives.

## Files and rollback

Backend source: `/root/lobbyMc/beasthud-src/`, `/root/lobbyMc/beastbots-src/`.
Proxy source: `/root/lobbyMc/beastpresence-proxy-src/`.
Proxy plugin: `/root/Bungeecord/plugins/BeastPresence-1.0.0.jar`.
Backup before this update: `/root/beastsmp-backup-english-20261016/before.tar.gz`, plus `customlobby-config.yml` and `proxy-config.yml` in that folder.

To roll back, stop the relevant servers, restore the backed-up source/configuration/assets/JARs, remove the new BeastPresence proxy JAR, and restart. Player saves and world files are not part of the update commit.

Visual check: the real client successfully loaded Halloween, Christmas and Standard packs. The Standard HUD showed English labels and no pumpkin quest row. Screenshot: `BeastSMP-English-Standard.png`.
