#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BEAST_LIBS=$(find ../libraries -name '*.jar' -print | paste -sd: -)
mkdir -p build/classes
javac -encoding UTF-8 -cp "$BEAST_LIBS:../plugins/FakePlayer-CE-build14.jar:../plugins/CustomLobby-1.0.0.jar" -d build/classes src/de/beastsmp/bots/*.java
cp plugin.yml config.yml build/classes/
jar --create --file build/BeastBots-1.0.0.jar -C build/classes .
