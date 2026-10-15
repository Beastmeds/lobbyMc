BeastSMP graphical /beast menu

The main menu uses a dedicated bitmap font, invisible item models and native inventory click slots.
Textures reuse the existing HUD artwork. Inventory placement is fixed to the vanilla 5-row chest layout,
so Minecraft GUI scaling applies to artwork and click targets together.

Regenerate from the repository root:
python3 beasthud-src/menu-art/build_menu_art.py .

Requires Pillow. Updates plugins/BetterHud/beastsmp-base.zip. Rebuild/reload BetterHUD afterwards.
The inventory title uses beastsmp:lobby_menu, U+E000 negative eight pixel space and U+E001..E003 artwork.
