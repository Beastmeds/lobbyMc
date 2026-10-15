# BeastHudThemes

Personal BetterHUD designs and the lobby Halloween quest for Paper 1.21.11 / Java 25.
Requires BetterHud 2.2.0-SNAPSHOT-449 and PlaceholderAPI.

## Players

- `/design` opens the selection menu.
- `/design halloween`, `/design standard`, `/design weihnachten` selects and saves a design.
- `/kuerbisse` explains the ten-pumpkin quest and shows progress.
- Pumpkins are visible in the Halloween design. Click each once to collect it; collected pumpkins disappear only for that player. Completion triggers a title, sound and particles.
- A personal greeting and themed overlay play on join. Bats, snowflakes or neutral sparkles pass over the logo every 25–45 seconds.
- The full boss bar is decorative branding and is always titled `BeastSMP`.

## Administration

`beasthud` in the server console reports the quest objects and their coordinates.
`beasthud inspect PLAYER` reports the selected theme, active HUD and saved progress.
`beasthud preview PLAYER [THEME]` previews a personal greeting; the optional theme changes and saves that player's design.
Admin commands require `beasthud.admin` (operator by default), or the server console.

Quest object coordinates are in `plugins/BeastHudThemes/config.yml`. Player designs and quest progress are stored in `players.yml` in that directory. Do not publish player files. Changing a location requires a lobby restart. The plugin places tagged display and interaction entities, not world blocks, and removes its entities when disabled.

## Build

Run `bash beasthud-src/build.sh` from the server checkout; it uses the existing Paper libraries and installed BetterHud/PlaceholderAPI jars. Copy `beasthud-src/build/BeastHudThemes-1.0.0.jar` to `plugins/` and restart the lobby. Rebuild after source changes.

BetterHUD resource definitions are versioned under `plugins/BetterHud/`. `/hud reload` rebuilds the resource pack and this plugin reapplies each online player's chosen theme.
