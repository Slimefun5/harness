# harness

Tests Slimefun and its addons against every supported Minecraft version without starting a server.

## What it does

Loads Slimefun and its addons against the real game code of every supported Minecraft version, from 1.8.8
to 26.x, **without starting a server**, and fails when anything they print at startup is a warning or an
error that has not been accepted.

Each version runs in its own JVM, on the JDK that version's servers run on. The harness:

- loads only vanilla's static game data, and from 1.20.5 its built-in datapack, the way the server does
  before any world exists: no world, no network, no ticking;
- allocates the version's real `CraftServer` without running its constructor and gives it its own real
  scheduler, plugin manager and command map, so plugins load and enable through the server's real code;
- rewrites, as the classes load, only the few server methods that would read a running game;
- advances the scheduler on a virtual clock, so delayed startup tasks run in milliseconds.

## Running it

```
./gradlew verifyAllVersions
./gradlew verifyAllVersions -Pversions=1.8.8,26.2
```

`harness.serverRoot` in `gradle.properties` points at a folder holding one folder per version with that
version's Paper server jar (and its `libraries/` from 1.18). `harness.plugins` lists the plugin jars to load.

Each version writes `build/harness/<version>/`: `report.properties` (the verdict), `warnings.txt` (what
failed the run), `accepted.txt`, `harness.log` and Paper's own `logs/latest.log`.

## Accepting a warning

`allowlist.txt` holds one regular expression per line, matched against a warning's first line. It starts
empty: every warning fails the run until it is fixed at its source or accepted there with a comment saying why.
