# Minecraft Infinite server plugins

The server-side addons running on `mc.hamsite.lol`, the public beta server for Minecraft
Infinite Reborn.

They are "mods" in the loader's language — a jar with `META-INF/infinite.mods.toml` in it — but
they behave the way a Bukkit plugin does: drop them in, restart, configure with a text file.
None of them registers a block, item or entity, so the client needs no changes and the registry
sync is untouched. All are server-side only.

```
mods/     the jars. Copy into  /opt/infinite/server/mods/
src/      full source for every addon, including two that are no longer shipped
config/   example config. These live in the WORLD folder, not next to the jar.
bot/      the Discord companion bot for chatbridge. Its own process, not a jar.
```

## Installing

1. Copy everything in `mods/` into the server's `mods/` folder.
2. Restart. `sudo systemctl restart infinite`
3. The loader prints what it found:

```
[infinite] mods      : 4 to load
[infinite]             anticheat 0.1.0     (anticheat-0.1.0.jar)
[infinite]             landclaim 0.1.0     (landclaim-0.1.0.jar)
[infinite]             perms 0.1.0         (perms-0.1.0.jar)
[infinite]             worldprotect 0.1.0  (worldprotect-0.1.0.jar)
```

> [!IMPORTANT]
> Only files ending in `.jar` load. To turn one off without deleting it, rename it to
> `.jar.disabled` — a stray `.jar.bak` is ignored but warned about on every boot.

## What each one does

| Mod | Purpose | Data it writes, in the world folder |
| --- | --- | --- |
| **worldprotect** | Operator regions with flags — spawn protection and anywhere else. `/rg`. Also gives operators control over land claims. | `regions.tsv` |
| **perms** | Ranks and nameplates: `[Owner]`, `[Admin]`, `[Player]`. `/perms`, `/nick`. | `perm-groups.tsv`, `perm-players.tsv`, `nicknames.tsv` |
| **landclaim** | Player block-protection claims, marked out with a gold shovel and budgeted by playtime. | `landclaims.tsv`, `playtime.tsv` |
| **anticheat** | Detection only — nothing kicks until you enable it. `/ac` shows recent flags. | `ac-alerts.tsv`, `ac-seen.tsv` |
| **blocklog** | Every block placed and broken, with lookup and rollback. `/bl wand` to inspect. | `blocklog.log`, `blocklog.properties` |
| **chatbridge** | Mirrors chat, joins and deaths to a Discord channel, and back again. `/discord`. | `chatbridge.properties`, `chatbridge-spool/`, `chatbridge-status.tsv` |
| **sweeper** | Merges dropped item stacks to cut entity lag. Can expire drops and cap mobs. `/sweep`. | `sweeper.properties` |
| **basics** | Everyday commands — afk, near, mail, ignore, seen, me, rules — and tidier `/help` ordering. | `basics-seen.tsv`, `basics-mail.tsv`, `basics-ignores.tsv`, `rules.txt`, `motd.txt` |

### worldprotect

Operator-only. Not a second land claim system: claims are owned by a player and bought with
playtime, a region is placed by an operator, is bounded in Y as well as X and Z, and carries
flags.

```
/rg wand                    then left-click one corner, right-click the other
/rg define spawn
/rg flag spawn build deny
```

Flags: `build`, `interact`, `pvp`, `mobs`, `explosions`, `entry`, plus `greeting` and
`farewell` messages. Every one is enforced — a flag the command accepts but nothing honours is
worse than none, because whoever set it believes the area is protected. `explosions` suppresses
block damage only; the creeper still detonates and still hurts.

Overlapping regions resolve by priority, and ties go to the **smaller** region, so a carve-out
inside a large protected area behaves as drawn.

`/rg claim` inspects, trusts, transfers and deletes player land claims. That bridge is
reflective, so worldprotect still loads and works if landclaim is absent or disabled.

> [!NOTE]
> Its mixin sits at priority 500, below the default. landclaim's place hook selects a claim
> corner and cancels, so whichever ran first decided whether a player could mark out a claim
> inside spawn protection — load order must not be what settles that.

## Appearing in /help

Addons file their commands under a heading through `HelpCategories`, which lives in the server
jar. See `docs/MODDING.md` in the Reborn repo. Wrap the call if the addon should also run on an
older server that predates it.

## Building from source

Needs a JDK 8, the server jar, and `InfiniteLoader.jar`.

```bash
cp /opt/infinite/server/minecraft-infinite-server.jar server.jar
cp /opt/infinite/server/InfiniteLoader.jar .
./build-all.sh
```

`build-all.ps1` is the same thing for Windows. `-proc:none` is not optional in either: Mixin's
annotation processor assumes an obfuscated game and fails on this one.

## Retired

`src/retired/` keeps two that are no longer shipped, because the code is still a useful
reference:

- **hamfix** — early server fixes, since folded into the server jar itself.
- **moderncmds** — added commands, since folded into the server jar as the `Infinite` command set.


## Permissions

Everything is gated through **perms**. A group holds command nodes; operators bypass all of it,
so a broken permission file can never lock you out of your own server.

```
*              everything
bl             every /bl subcommand
bl.*           the same, written explicitly
bl.rollback    just that one
```

Nodes follow the command name, optionally dotted with a subcommand — the same shape as the
`whitelist.add` that has always shipped in the default group file. Aliases fold onto the long
form, so `bl.rb` and `bl.rollback` are one node.

| Addon | Nodes |
| --- | --- |
| blocklog | `bl.wand` `bl.inspect` `bl.lookup` `bl.page` `bl.rollback` `bl.restore` `bl.purge` `bl.status` |
| worldprotect | `rg.define` `rg.remove` `rg.flag` `rg.wand` `rg.bypass` `rg.claim` `rg.list` `rg.info` |
| sweeper | `sweep.now` `sweep.status` |
| chatbridge | `chatbridge.status` `chatbridge.test` |
| anticheat | `ac.recent` `ac.reload` … |
| landclaim | `claim.override` (build inside anyone's claim), `claim.unlimited` (skip the playtime allowance) |
| basics | `tphere` `feed` `repair` `burn` `smite` `top` `broadcast` — each is a top-level command |

The shipped `admin` group gets all of these except `stop`, `op`, `deop` and `perms`. Those
either end the server or let someone grant themselves owner, at which point the group split
stops meaning anything.

> [!NOTE]
> The `//` region-editor commands stay operator-only. They are gated by an op check inside the
> server jar itself, so perms cannot grant them without a server build.

## The Discord bridge

Two halves that work independently.

**Minecraft → Discord** is the plugin, over a webhook. Create one in the channel's
Integrations settings and put the URL in `chatbridge.properties`. That alone is a working
one-way feed and needs no bot account. Nothing sent from Minecraft can ping anyone: the
webhook payload sets `allowed_mentions` to nothing, so a player typing `@everyone` in game
does not notify the server.

**Discord → Minecraft** is either the plugin polling the REST API with a bot token, or —
better — the companion bot in `bot/`, which holds a gateway connection and hears messages
instantly. Run one or the other, never both, or every message arrives twice.

The bot hands messages over by writing one file per message into `chatbridge-spool/` under a
temp name and renaming it into place. Rename is atomic on one filesystem, so the server never
reads a half-written message. It reads `chatbridge-status.tsv` for its `/players` and
`/status` commands rather than querying the server, which has no RCON or query port — so a
wedged bot can never affect a tick.

Setting it up:

```bash
cd bot
python3 -m venv venv && ./venv/bin/pip install -r requirements.txt
cp .env.example .env && chmod 600 .env     # fill in TOKEN and CHANNEL_ID
sudo cp infinite-chatbot.service /etc/systemd/system/
sudo systemctl enable --now infinite-chatbot
```

> [!IMPORTANT]
> The bot needs the **Message Content** privileged intent enabled in the Discord developer
> portal. Without it every message arrives with an empty body and the relay forwards blank
> lines. This is also why the bridge is a separate bot rather than part of an existing one —
> requesting that intent costs an application its App Discovery eligibility.
