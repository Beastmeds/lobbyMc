#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
javac -encoding UTF-8 -cp /root/Bungeecord/BungeeCord.jar -d build/classes src/de/beastsmp/presence/BeastPresence.java
cp bungee.yml build/classes/
jar cf build/BeastPresence-1.0.0.jar -C build/classes .
mkdir -p dist
cp build/BeastPresence-1.0.0.jar dist/
