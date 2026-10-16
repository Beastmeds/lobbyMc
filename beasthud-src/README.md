# BeastHudThemes

Personal BetterHUD designs and the lobby Halloween quest for Paper 1.21.11 / Java 25.
Requires BetterHud 2.2.0-SNAPSHOT-449 and PlaceholderAPI.

## Players

- `/design` opens the selection menu.
- `/design halloween`, `/design standard`, `/design weihnachten` selects and saves a design.
- `/kuerbisse` explains the ten-pumpkin quest and shows progress.
- Pumpkins are visible in the Halloween design. Click each once to collect it; collected pumpkins disappear only for that player. Completion triggers a title, sound and particles.
- A personal greeting and themed overlay play on join. Bats, snowflakes or neutral sparkles pass over the logo every 25–45 seconds.
- The top nameplate is decorative branding and is always titled `BeastSMP`. Its frame switches with the selected theme. BetterHUD draws the seasonal frame and title; the legacy Bukkit bar stays hidden.

## Administration

`beasthud` in the server console reports the quest objects and their coordinates.
`beasthud inspect PLAYER` reports the selected theme, active HUD and saved progress.
`beasthud preview PLAYER [THEME]` previews a personal greeting; the optional theme changes and saves that player's design.
Admin commands require `beasthud.admin` (operator by default), or the server console.

Quest object coordinates are in `plugins/BeastHudThemes/config.yml`. Player designs and quest progress are stored in `players.yml` in that directory. Do not publish player files. Changing a location requires a lobby restart. The plugin places tagged display and interaction entities, not world blocks, and removes its entities when disabled.

## Build

Run `bash beasthud-src/build.sh` from the server checkout; it uses the existing Paper libraries and installed BetterHud/PlaceholderAPI jars. Copy `beasthud-src/build/BeastHudThemes-1.0.0.jar` to `plugins/` and restart the lobby. Rebuild after source changes.

BetterHUD resource definitions are versioned under `plugins/BetterHud/`. `/hud reload` rebuilds the resource pack and this plugin reapplies each online player's chosen theme.


## Lobby menus and atmosphere

- `/beast` (alias `/menu`): main menu.
- `/serverauswahl`: live status and BungeeCord navigation for BeastSMP, SMP and Lobby. Offline destinations do not trigger a transfer.
- `/design`: themed design-selection menu, with back/effects/close controls.
- `/events`: the live Halloween quest and a Christmas atmosphere action; no invented event timers.
- `/effekte an|aus|auto|winter|halloween|status`: personal lobby atmosphere, saved in effects.yml. `an`/`auto` follows the selected design: Halloween particle fog, winter snow, no particles for standard.
- `/lobbyeffekte an|aus|auto|winter|halloween|status`: operator/console controls. Global off takes precedence over player preferences.
- `/lobbyeffekte menu PLAYER home|servers|events|effects|design`: operator/console menu preview.

Hotbar: compass (0) opens servers, nether star (4) opens the main menu, comparator (8) opens effects. Atmosphere packets are sent only to each player in the lobby world within 128 blocks horizontally of the spawn. Snow emits at most 28 particles/second/player; Halloween emits at most 24. It does not change world weather, apply blindness, or place blocks. Global defaults are in config.yml under effects; personal effect preferences are not committed.

Server availability uses bounded asynchronous TCP checks every 20 seconds. Player counts and transfers use BungeeCord plugin messaging. The current proxy destinations are configured in BeastLobbyFeatures.java.

## Personal language

`/settings` opens a themed English/German selector. `/settings en` and `/settings de` also work. English is the default; the choice is stored per UUID. HUD labels, custom menu art, tooltips, greetings, quest messages and ESC artwork follow the selection. Third-party plugin messages and client-owned interface language retain their own settings. The full required pack updates when changing language.

For menu generation, run `menu-art/build_menu_art.py` with `--language en --overlay plugins/BeastHudThemes/languages/en-menu.zip`, then with `de` and `de-menu.zip`. Run `esc-menu/build_esc_menu.py` afterward to produce the final language overlays.
