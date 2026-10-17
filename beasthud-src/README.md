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
- `/serverauswahl`: live status and BungeeCord navigation for Citybuild and Lobby (Citybuild uses the private SSH tunnel on 127.0.0.1:25568). Offline destinations do not trigger a transfer.
- `/design`: themed design-selection menu, with back/effects/close controls.
- `/events`: the live Halloween quest and a Christmas atmosphere action; no invented event timers.
- `/effekte an|aus|auto|winter|halloween|status`: personal lobby atmosphere, saved in effects.yml. `an`/`auto` follows the selected design: Halloween particle fog, winter snow, no particles for standard.
- `/lobbyeffekte an|aus|auto|winter|halloween|status`: operator/console controls. Global off takes precedence over player preferences.
- `/lobbyeffekte menu PLAYER home|servers|events|effects|design`: operator/console menu preview.

Hotbar: compass (0) opens servers, nether star (4) opens the main menu, comparator (8) opens effects. Atmosphere packets are sent only to each player in the lobby world within 128 blocks horizontally of the spawn. Snow emits at most 28 particles/second/player; Halloween emits at most 24. It does not change world weather, apply blindness, or place blocks. Global defaults are in config.yml under effects; personal effect preferences are not committed.

Server availability uses bounded asynchronous TCP checks every 20 seconds. Player counts and transfers use BungeeCord plugin messaging. The current proxy destinations are configured in BeastLobbyFeatures.java.

## Personal language

`/settings` opens the themed personal settings hub (language, chat, display and effects). `/settings en` and `/settings de` also work. English is the default; the choice is stored per UUID. HUD labels, custom menu art, tooltips, greetings, quest messages and ESC artwork follow the selection. Third-party plugin messages and client-owned interface language retain their own settings. The full required pack updates when changing language.

For menu generation, run `menu-art/build_menu_art.py` with `--language en --overlay plugins/BeastHudThemes/languages/en-menu.zip`, then with `de` and `de-menu.zip`. Run `esc-menu/build_esc_menu.py` afterward to produce the final language overlays.

## Larger menu artwork

Pass `--client-jar /path/to/26.2.jar` to each menu-art build to use Minecraft-native caption lettering. The outer menu frame is now 208 GUI pixels wide, while slot coordinates are preserved by a matching -24 starting advance. Home has Settings instead of Effects, no back shortcut, and a clear X close control. Home cards suppress obstructive tooltips. Effects remain reachable inside Settings.

Personal chat/display preferences persist per UUID, with eight independent ON/OFF switches. Chat visibility affects other player messages; own chat and administrative system messages remain visible. `/settings bots` provides OP-only scheduling controls, including day/night quantities and start times. The home menu has three taller cards and separate Events/Close controls.

## Inventory-free menus

Menus now use Paper dialogs, with a wide seasonal Beast banner, illustrated action labels, wider two-/three-column layouts and a dedicated Close control. Main action order is preserved. Operators retain exclusive access to Bot Settings; callbacks recheck OP status. Bot dialogs expose a Decrease values checkbox instead of shift-click. The existing inventory renderer remains available with `menus.dialog: false` for compatibility. Modern Java clients supporting dialogs are required for the default mode.

## Compact layout and Events access

Home uses four equal 210-pixel buttons in two columns: Servers/Designs above Settings/Events, placing Events at the bottom right. Other two-column menus use 210 pixels, three-column menus 140. Text uses the native Minecraft font size; buttons use small 10-pixel server/palette/gear symbols instead of illustrations. The seasonal banner is 384×88 GUI pixels without the yellow bottom line. Button textures are neutral charcoal without purple outlines or stripes.

The bundled `datapack/` registers the legacy key `beastsmp:events` in `minecraft:pause_screen_additions` as the ESC **Menu** entry. It displays the same four main destinations as `/menu`: server selection, designs, settings and events. Actions run the ordinary player commands; server selection uses the unambiguous `/serverauswahl` command because CustomLobby owns the `/servers` alias. Install its contents in `world/datapacks/beastsmp-menu/` before restarting. Minecraft chooses the ESC entry position. Event handlers accept only the exact public identifiers. Required packs supply the selected theme and English/German captions. `/events` remains available.

## Visible companions and player profiles

`/profile` (`/profil`) opens personal lobby playtime, four earned achievements, pumpkin collection progress and selected title. Titles unlock through the first/all pumpkins, selecting a companion, and one hour played. `/pet` (`/companion`, `/begleiter`) selects Ghost, Snowman, Bat or Off. Choices and achievements persist in `plugins/BeastHudThemes/social.yml`; pumpkin progress remains in the existing profile storage. Selected titles currently appear in the profile, not as chat/TAB prefixes.

Home and the ESC Menu include Player profile and Companions as a new third row. Menus and messages follow the player's English/German setting. Companions are real public entities: a small ghost made of vanilla block displays, a snow golem, or a bat. They follow their owner, cannot be damaged/interacted with, cannot cause snow trails, do not collide, and are removed on logout/world change/shutdown. Vanish/player visibility is respected. No resource-pack model is needed. Simulated players cannot spawn companions. No parkour is included.
