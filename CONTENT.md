## What it does

Loads Slimefun and its addons against the real game code of every supported Minecraft version, from 1.8.8
to 26.x, **without starting a server**, then runs Slimefun's own checks against what they registered, and
fails when anything they print is a warning or an error that has not been accepted.

Each version runs in its own JVM, on the JDK that version's servers run on. The harness:

- loads only vanilla's static game data, and from 1.20.5 its built-in datapack, the way the server does
  before any world exists: no level, no network;
- allocates the version's real `CraftServer` without running its constructor and gives it its own real
  scheduler, plugin manager and command map, so plugins load and enable through the server's real code;
- rewrites, as the classes load, only the few server methods that would read a running game;
- advances the scheduler on a virtual clock, so delayed startup tasks run in milliseconds;
- runs Slimefun's own checks through the check system the plugin ships, up to
  `-Pstage=<off|low|medium|high|max>` (default `max`): the registry checks (items, recipes, menu presets,
  addon staleness), and from `medium` the checks that place every machine as a player would, open each menu
  once per language, and click every slot with every click type, failing when a click makes items appear or
  vanish.

## Sandbox players and blocks

The deeper checks act in Slimefun's own checks sandbox: a world, blocks, entities and players held in memory,
the same sandbox a live server uses for `/sf check`. Every item in it is a real server item in a real server
inventory, and every event goes through the plugins' real listeners. Only in the harness are the sandbox's
players spliced in as the server's online players and its world registered with the server; on a live server
neither happens. A call the sandbox cannot answer yet is reported as `[check:sandbox-gap]` and
fails the run, so a check that could not really run never passes. Universal menus are skipped, because the
sandbox refuses them, and each skip is logged.

## Running it

```
./gradlew verifyAllVersions
./gradlew verifyAllVersions -Pversions=1.8.8,26.2 -Pstage=medium
```

`harness.serverRoot` in `gradle.properties` points at a folder holding one folder per version with that
version's Paper server jar (and its `libraries/` from 1.18). `harness.plugins` lists the plugin jars to load.

Each version writes `build/harness/<version>/`: `report.properties` (the verdict), `warnings.txt` (what
failed the run), `accepted.txt`, `harness.log` and Paper's own `logs/latest.log`.

## Accepting a warning

`allowlist.txt` holds one regular expression per line, matched against a warning's first line. It starts
empty: every warning fails the run until it is fixed at its source or accepted there with a comment saying why.
