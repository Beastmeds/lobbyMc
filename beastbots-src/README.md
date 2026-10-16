# BeastBots 1.0.0

Manages ten server-side players in the Lobby using FakePlayer-CE build14 and CommandAPI 12.1.0 Paper.
Names are ordinary configurable aliases; entities are invisible, non-colliding, invulnerable and cannot
pick up items. They have no OP rights, do not send chat, and do not mine, place blocks or run player commands.
`Bukkit.getOnlinePlayers()` includes these simulated players. No Minecraft accounts or Bungee connections
are created. Bungee's external online count remains the actual connected count.

## Commands and configuration

All `/beastbots` commands require `beastbots.admin` (default OP; console allowed):

- `help`: usage.
- `add <name>`: configure and activate a name (3–16 letters/digits/underscores).
- `remove <name>`: remove it from the pool and reduce the active count when applicable.
- `setamount <0–10>`: change active count; zero disables all managed players.
- `list`: active simulated players.
- `reload`: reread configuration, update invisibility/TAB/range and reconcile the active set. Skin source changes apply on the next bot spawn.
- `status`: show actual/simulated counts and online, invisible, OP and skin diagnostics.

Runtime config: `plugins/BeastBots/config.yml`. All listed options are implemented.
The configured maximum is hard-capped at 40 for bounded processing. Bots use two chunks of view/send/simulation distance; genuine players retain the server settings. Names used by existing real player
data are skipped. A real player connecting with a simulated name takes priority; the bot is removed.
Skin sources are Minecraft profile names, independently configurable for each alias. Signed textures
are fetched from Mojang and cached per source during a server run; failure leaves the default skin.
The sample uses the standard MHF_Steve and MHF_Alex skins, not ten unique custom outfits.

The addon restores configured players after startup, at most one spawn per second. It does not edit
world blocks or existing player inventories. FakePlayer-CE persistence is disabled; the addon owns the
roster. Its direct cleanup API avoids the duplicate removal path observed with Paper 1.21.11.
Use BeastBots commands rather than FakePlayer-CE's kick commands for managed players.

The BeastHudThemes source has compatibility guards so simulated players do not receive resource packs,
greetings, HUD animations or lobby effects. Player-list appearance remains compatible with CustomLobby.
Citizens, ProtocolLib, TAB and LuckPerms are not required or installed by this setup.

## Build

From this directory, on the server's Java 25 runtime:

```
bash build.sh
```

Output: `build/BeastBots-1.0.0.jar`. Dependencies come from the existing Lobby libraries and installed
FakePlayer-CE jar. Do not hot-load these plugins. Back up, stop the Lobby with owner approval, install
the compiled BeastHudThemes compatibility build, and start with `./start.sh` in screen `Lobby`.

## Tests

A private staging server bound to 127.0.0.1:25577 is used, with a newly generated flat world.
The optional Node client checks require `minecraft-protocol`; scripts expect an SSH tunnel to port25577.
They verify all ten TAB entries and signed skins; `invisible.cjs` additionally checks all ten nearby
player entities carry the invisibility flag. Live integration and client checks follow activation.

## Rollback

Backup: `/root/beastbots-backup-20261016/lobby-before-bots.tar.gz` (plus proxy config and Git HEAD).
Stop the Lobby with owner approval. Remove the newly installed BeastBots, FakePlayer-CE and CommandAPI
jars from `plugins/` and move their new data directories aside. Extract the backup into `/root/lobbyMc`
to restore BeastHudThemes code/jar/config. Restart using the existing start script. The Bungee configuration
is unchanged; no proxy restart is needed.
