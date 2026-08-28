# Minecraft Infinite server plugins

The three server-side addons running on `hamsite.lol`, built against server jar
**1.0-010926**.

They are "mods" in the loader's language -- a jar with `META-INF/infinite.mods.toml` in it --
but they behave the way a Bukkit plugin does: drop them in, restart, configure with a text file.

```
mods/     the jars. Copy into  /opt/infinite/server/mods/
config/   anticheat.properties. Lives in the WORLD folder, not next to the jar.
src/      full source for all three, so they can be rebuilt or edited.
```

## Installing

1. Copy everything in `mods/` into the server's `mods/` folder.
2. Restart the server. `sudo systemctl restart infinite`
3. On boot the loader prints what it found:

```
[infinite] mods      : 3 to load
[infinite]             anticheat 0.1.0  (anticheat-0.1.0.jar)
[infinite]             landclaim 0.1.0  (landclaim-0.1.0.jar)
[infinite]             perms 0.1.0  (perms-0.1.0.jar)
```

> [!IMPORTANT]
> Only files ending in `.jar` are loaded. Keep backups somewhere else -- a stray
> `something.jar.bak` in `mods/` is ignored, but it warns about it on every boot.
> To turn a mod off without deleting it, rename it to `.jar.disabled`.

## What each one does

| Mod | Purpose | Data it writes, in the world folder |
| --- | --- | --- |
| **perms** | Ranks and nameplates: `[Owner]`, `[Admin]`, `[Player]`. `/perms`, `/nick`. Owner-only commands are gated here. | `perm-groups.tsv`, `perm-players.tsv`, `nicknames.tsv` |
| **landclaim** | Block-protection claims. | `landclaims.tsv` |
| **anticheat** | Detection only, see below. `/ac` shows recent flags. | `ac-alerts.tsv`, `ac-seen.tsv` |

Ops bypass every check in all three -- that was a deliberate choice, so an op has `*`.

## Anticheat configuration

`config/anticheat.properties` goes in the **world folder**
(`/opt/infinite/server/world/`), not beside the jar. It is created with defaults on first
boot if missing.

> [!WARNING]
> Every check ships with `kick=false`. Nothing kicks anyone until you turn it on, and that
> is deliberate -- this build has real desync bugs, so a movement check tuned blind will
> kick real players. Watch `/ac` through a few days of normal play before enabling any kick.

Two settings exist specifically because of false positives seen in the log:

- `movement.consecutive-packets=3` -- one fast packet is not a speed hack. Mob knockback, a
  teleporter pad, standing up out of a crawl after a relog and a plain lag spike all cover
  several blocks between two packets. A real speed hack sustains it.
- `rate.max-breaks-per-second=16` -- sand, gravel and leaves break almost instantly, and
  mining them by hand reached 11 per second in the log. A nuker breaks far more than this.

## Rebuilding

Needs JDK 8, the server jar, `InfiniteLoader.jar`, and `mixin-0.8.5.jar`.

```sh
javac -source 8 -target 8 -proc:none -nowarn \
  -cp "server.jar:InfiniteLoader.jar:mixin-0.8.5.jar" \
  -d build $(find src -name '*.java')
cp -r resources/. build/
jar cf anticheat-0.1.0.jar -C build .
```

`-proc:none` is not optional. Mixin's annotation processor assumes an obfuscated game and
fails on this one.
