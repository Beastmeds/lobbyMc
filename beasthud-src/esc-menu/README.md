# BeastSMP ESC menu

The resource pack reskins vanilla button sprites and overrides `menu.game` and `menu.disconnect`
with bitmap glyphs. The normal Minecraft button actions remain intact. The default design is Halloween.
The farewell art is original generated artwork. German and English text is rendered with the client's
native ASCII font, extracted only during generation.

Regenerate from the repository root with Pillow:

```
python3 beasthud-src/esc-menu/build_esc_menu.py . --client-jar /path/to/26.2.jar
```

This updates `plugins/BetterHud/beastsmp-base.zip`, merged by BetterHUD into its hosted pack.
The standalone `server.properties` resource pack URL is deliberately empty to avoid a second download.
Reload BetterHUD after regeneration; restart the server when changing server.properties.

Menu labels are images, so their visual language is supplied in German and English. Other languages
fall back to English. Vanilla buttons and all inventory menus share the purple widget texture.
