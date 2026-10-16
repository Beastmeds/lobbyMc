#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BEAST_HUD_LIBS=$(find ../libraries -name '*.jar' -print | paste -sd: -)
mkdir -p build/hud-classes dist
javac -encoding UTF-8 -cp "$BEAST_HUD_LIBS:../plugins/PlaceholderAPI-2.12.3.jar" -d build/hud-classes hud/BeastPresenceExpansion.java
jar cf dist/Expansion-BeastPresence.jar -C build/hud-classes .
