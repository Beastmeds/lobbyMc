# BeastSMP seasonal ESC menu

All three variants follow the saved `/design` selection: Halloween, Standard and Weihnachten.
Each farewell graphic has 512x172 source pixels at a displayed width of 256 GUI pixels.
The image uses two 256px font tiles at half scale, providing four times as many image pixels
as the previous version. German and English labels remain at their normal displayed size.

Generate with Pillow from the repository root:

```
python3 beasthud-src/esc-menu/build_esc_menu.py . --client-jar /path/to/26.2.jar
```

Outputs:
- `plugins/BetterHud/beastsmp-base.zip`: shared font definitions and default Halloween skin.
- `plugins/BeastHudThemes/esc-themes/*.zip`: theme-specific image and button overrides.

BetterHUD still builds `plugins/BetterHud/build.zip`, but `enable-self-host: false` disables
its own sender and HTTP listener. `BeastThemePacks` merges that build with each skin and hosts
complete hashed packs on port 8163. Only the chosen complete pack is sent, using a stable pack UUID.
The legacy standalone `server.properties` pack URL stays empty. Switching design necessarily
reloads Minecraft's resources; declined packs are not repeatedly requested during the session.

On a BetterHUD reload the theme packs are rebuilt automatically. Restart the Lobby when changing
Java code or the host configuration. Generated full packs live only in memory and are not committed.
