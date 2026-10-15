BeastSMP illustrated lobby menus

All five menus use the same seasonal artwork system: home, servers, events, effects and designs.
The original HUD logos are combined with generated portal/palette/chest/orb artwork from menu-motifs.png.
A six-row inventory provides native click targets. Each background is rendered at double GUI resolution,
split into four 256px bitmap font tiles (logical height 128), with explicit negative-space advances.
The decorative header extends 20 GUI pixels above the inventory. Player slots retain vanilla coordinates.

Regenerate from the repository root (Pillow required):
python3 beasthud-src/menu-art/build_menu_art.py .

Then reload/rebuild BetterHUD. Output is merged through plugins/BetterHud/beastsmp-base.zip.
Bitmap font: beastsmp:lobby_menu. Blank click-target item model: beastsmp:menu_blank.
Keep the three/four-card layouts in BeastLobbyFeatures.java synchronized with the artwork.
