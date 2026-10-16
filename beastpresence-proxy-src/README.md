# BeastPresence for BungeeCord
Adds the active BeastBots players to the public multiplayer-list online count and hover samples. Genuine connections retain their normal proxy routing and internal counts.

Backend BeastBots writes `plugins/BeastBots/presence.json` atomically once per second, including the current online managed players and a timestamp. The proxy reads the local snapshot once per second. Missing, invalid, future-dated or older-than-15-second data contributes no players. Shutdown publishes an empty snapshot. No public HTTP service or credentials are required.

Build with Java 25: `bash build.sh` (uses `/root/Bungeecord/BungeeCord.jar`). Install `build/BeastPresence-1.0.0.jar` in `/root/Bungeecord/plugins/` while the proxy is stopped, then restart. The default snapshot path is `/root/lobbyMc/plugins/BeastBots/presence.json`; override with JVM property `-Dbeastsmp.presence.file=/absolute/path` if moving the backend.

`/beastbots on`, `/beastbots off`, and `/beastbots toggle` require `beastbots.admin` (operators by default). They persist `enabled`, preserve the configured amount and names, and update the public count. Creation runs at most one player per second. `setamount`, `add`, `remove`, `reload`, `list` and `status` remain available.

Validation: isolated Bungee ping with ten fixture players, expired/empty snapshot returning zero; production public ping with one real connection showed 11 → 1 → 11 after off/on.
