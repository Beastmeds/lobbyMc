#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BEAST_LIBS=$(find ../libraries -name '*.jar' -print | paste -sd: -)
BEAST_CP="$BEAST_LIBS:../plugins/BetterHud-bukkit-2.2.0-SNAPSHOT-449.jar:../plugins/PlaceholderAPI-2.12.3.jar"
mkdir -p build/classes
javac -encoding UTF-8 -cp "$BEAST_CP" -d build/classes src/de/beastsmp/hud/*.java
cp plugin.yml config.yml build/classes/
jar --create --file build/BeastHudThemes-1.0.0.jar -C build/classes .
